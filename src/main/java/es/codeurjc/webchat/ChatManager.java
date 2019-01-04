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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChatManager {

	private ConcurrentMap<String, Chat> chats = new ConcurrentHashMap<>();
	private ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
	private ConcurrentMap<String, CustomPair> taskPerUser = new ConcurrentHashMap<>();
	private int maxChats;
	private Lock chatCreationLock = new ReentrantLock();
	private Condition chatCreationCondition = chatCreationLock.newCondition();

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
		if ( null != pair ) {
			ExecutorService executor = pair.getExecutor();
			executor.shutdown();
			executor.awaitTermination(2, TimeUnit.SECONDS);
			taskPerUser.remove(user.getName(), pair);
		}
	}

	public Chat newChat(String name, long timeout, TimeUnit unit) throws InterruptedException,
			TimeoutException {
		
		boolean isChatCreated = false;
		Chat theChat = null;
		Chat obtainedChat = null;
		try {
			chatCreationLock.lock();
			boolean mayWait = true;
			mayWait = chats.size() == maxChats ? true :false;
			//PrintlnI.printlnI("The chats size is: "+chats.size(), "");

			//spurious wakeup control
			while (mayWait) {
				//this solution could make that the timeoutException delay more than timeout
				//this could be avoided controlling the time elapses from the first previous try.
				//The unit should be always the same and at least milliseconds
				//It is not guarantee which thread will be awaken when the condition is signaled
				if ( false == chatCreationCondition.await(timeout, unit)) {
					throw new TimeoutException("Timeout waiting for chat creation. \'"
							+"Time: " + timeout + " Unit: " + unit + "\'");
				}
				mayWait = chats.size() == maxChats ? true :false;
				PrintlnI.printlnI("Inside the While. The chats size is: "+chats.size(), "");
			}
			theChat = new Chat(this, name, taskPerUser);
			obtainedChat = chats.putIfAbsent(name, theChat);
		} finally {
			chatCreationLock.unlock();
		}

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
		//does not make sense use lock because the signal is sent
		//afterwards and between the remove and the signal could be
		//an exception throw
		Chat removedChat = chats.remove(chat.getName());
		if (removedChat == null) {
			throw new IllegalArgumentException("Trying to remove an unknown chat with name \'"
					+ chat.getName() + "\'");
		}

		chatCreationLock.lock();
		chatCreationCondition.signal();
		chatCreationLock.unlock();

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
