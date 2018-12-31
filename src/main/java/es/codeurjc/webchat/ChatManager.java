package es.codeurjc.webchat;

import java.util.Collection;
import java.util.Collections;
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
	private int maxChats;

	public ChatManager(int maxChats) {
		this.maxChats = maxChats;
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
		ExecutorService executor = pair.getExecutor();
		executor.shutdown();
		executor.awaitTermination(10, TimeUnit.SECONDS);
		taskPerUser.remove(user.getName(), pair);
	}

	public Chat newChat(String name, long timeout, TimeUnit unit) throws InterruptedException,
			TimeoutException {

		//A new user could be been added while the checking is done
		//
		boolean mayThrow = false;
		//synchronized (chats) {
			mayThrow = chats.size() == maxChats ? true :false;
		//}
		
		//If this part in synchronized as well, the exception throwing can consume some time
		//It is better to have it out of the mutual exclusion zone even if the situation 
		//can change between the checking and the exception throw. However, with this same
		//point of view, there is no need for synchronize the reading of the size.
		if (mayThrow) {
			throw new TimeoutException("There is no enought capacity to create a new chat");
		}


		Chat theChat = null;
		boolean isChatCreated = false;

		theChat = new Chat(this, name, taskPerUser);
		Chat obtainedChat = chats.putIfAbsent(name, theChat);
		if (null != obtainedChat )
		{
			PrintlnI.printlnI("Chat: "+name+" already created.","");
			return obtainedChat;
		} else {
			isChatCreated = true;
			PrintlnI.printlnI("Creating chat: "+name, "");
		}
		
		if (isChatCreated) {
			final Chat theUsedChat = theChat;
			//this is quite similar to the code in closeChat
			for(User u : users.values()){
				CustomPair pair = taskPerUser.get(u.getName());
				CompletionService<String> completionService;
				if (pair != null)
				{
					completionService = pair.getCompletionServices();
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


		final Chat theUsedChat = removedChat;
		//this is quite similar to the code in newChat
		for(User u : users.values()){
			CustomPair pair = taskPerUser.get(u.getName());
			CompletionService<String> completionService;
			if (pair != null)
			{
				completionService = pair.getCompletionServices();
				if (completionService != null)
					completionService.submit(()->notifyClosedChat(u,theUsedChat));
			}
		}
	}

	public Collection<Chat> getChats() {
		//TODO should it be included in a mutual exclusion zone?
		return Collections.unmodifiableCollection(chats.values());
	}

	public Chat getChat(String chatName) {
		//TODO add mutual exclusion protection
		return chats.get(chatName);
	}

	public Collection<User> getUsers() {
		//TODO should it be included in a mutual exclusion zone?
		return Collections.unmodifiableCollection(users.values());
	}

	public User getUser(String userName) {
		//TODO add mutual exclusion protection
		return users.get(userName);
	}

	public void close() {}
	
	private String notifyNewChat(User user, Chat chat) throws InterruptedException {
		//TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(0, 500 + 1));
		user.newChat(chat);
		//PrintlnI.printlnI("New user "+userNew.getName()+" in chat message to user "+user.getName(),"");
		return "New Chat "+ chat.getName()+" message for user "+user.getName();
	}
	
	private String notifyClosedChat(User user, Chat chat) throws InterruptedException {
		//TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(0, 500 + 1));
		user.chatClosed(chat);
		//PrintlnI.printlnI("New user "+userNew.getName()+" in chat message to user "+user.getName(),"");
		return "Closed Chat "+ chat.getName()+" message for user "+user.getName();
	}
}
