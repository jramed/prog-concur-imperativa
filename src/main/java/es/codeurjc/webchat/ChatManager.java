package es.codeurjc.webchat;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChatManager {

	private ConcurrentMap<String, Chat> chats = new ConcurrentHashMap<>();
	private ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
	private ConcurrentMap<String, CustomPair> taskPerUser = new ConcurrentHashMap<>();
	private BlockingQueue<Boolean> chatsQueue;

	public ChatManager(int maxChats) {
		this.chatsQueue = new ArrayBlockingQueue<>(maxChats);
		for (int i = 0; i < maxChats; i++) {
			chatsQueue.add(true);
		}
	}

	public void newUser(User user) {
		
		if(null != users.putIfAbsent(user.getName(), user)) {
			throw new IllegalArgumentException("There is already a user with name \'"
					+ user.getName() + "\'");
		}
		ExecutorService executor = Executors.newSingleThreadExecutor();
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
		CustomPair pair = new CustomPair(executor,completionService);
		taskPerUser.putIfAbsent(user.getName(), pair);
	}
	
	public void removeUser(User user) throws InterruptedException {
		try {
			users.remove(user.getName(), user);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		CustomPair pair = taskPerUser.get(user.getName());
		if ( null != pair ) {
			ExecutorService executor = pair.getExecutor();
			executor.shutdown();
			executor.awaitTermination(2, TimeUnit.SECONDS);
			taskPerUser.remove(user.getName(), pair);
		}
	}

	public Chat newChat(String name, long timeout, TimeUnit unit) throws InterruptedException,
			TimeoutException {

		if (null == chatsQueue.poll(timeout, unit) ) {
			throw new TimeoutException("Timeout waiting for chat creation. \'"
					+"Time: " + timeout + " Unit: " + unit + "\'");
		}

		Chat theChat = new Chat(this, name, taskPerUser);
		Chat obtainedChat = chats.putIfAbsent(name, theChat);

		if (null != obtainedChat )
		{
			System.out.println("Chat: "+name+" already created.");
			//If the chat was already created, return a token.
			chatsQueue.put(true);
			return obtainedChat;
		} else {
			final Chat theUsedChat = theChat;
			//this is quite similar to the code in closeChat
			for(User u : users.values()){
				CustomPair pair = taskPerUser.get(u.getName());
				if (pair != null)
				{
					CompletionService<String> completionService = pair.getCompletionServices();
					if (completionService != null)
						completionService.submit(()->notifyNewChat(u,theUsedChat));
				}
			}
		}

		return theChat;
	}

	public void closeChat(Chat chat) {
		//The remove operation is performed atomically
		Chat removedChat = chats.remove(chat.getName());
		if (removedChat == null) {
			throw new IllegalArgumentException("Trying to remove an unknown chat with name \'"
					+ chat.getName() + "\'");
		}

		try {
			chatsQueue.put(true);
		} catch (InterruptedException e) {
			//If it is interrupted while waiting, 
			//a token is lost and the chats capacity reduced by one.
			e.printStackTrace();
			return;
		}

		final Chat theUsedChat = removedChat;
		//this is quite similar to the code in newChat
		for(User u : users.values()){
			CustomPair pair = taskPerUser.get(u.getName());
			if (pair != null)
			{
				CompletionService<String> completionService = pair.getCompletionServices();
				if (completionService != null)
					completionService.submit(()->notifyClosedChat(u,theUsedChat));
			}
		}
	}

	public Collection<Chat> getChats() {
		return Collections.unmodifiableCollection(chats.values());
	}

	public Chat getChat(String chatName) {
		return chats.get(chatName);
	}

	public Collection<User> getUsers() {
		return Collections.unmodifiableCollection(users.values());
	}

	public User getUser(String userName) {
		return users.get(userName);
	}

	public void close() {}
	
	private String notifyNewChat(User user, Chat chat) throws InterruptedException {
		user.newChat(chat);
		return "New Chat "+ chat.getName()+" message for user "+user.getName();
	}
	
	private String notifyClosedChat(User user, Chat chat) throws InterruptedException {
		user.chatClosed(chat);
		return "Closed Chat "+ chat.getName()+" message for user "+user.getName();
	}
}
