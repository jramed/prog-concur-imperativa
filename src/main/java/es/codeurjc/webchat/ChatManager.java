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
	
	private class CustomPair {
	    private ExecutorService executor;
	    private CompletionService<String> completionServices;
	    
		public CustomPair(ExecutorService executor2, CompletionService<String> completionServices) {
			super();
			this.executor = executor2;
			this.completionServices = completionServices;
		}
		public ExecutorService getExecutor() {
			return executor;
		}

		public CompletionService<String> getCompletionServices() {
			return completionServices;
		}
    
	}

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
		/*synchronized (chats) {
			if(chats.containsKey(name)){
				PrintlnI.printlnI("Chat: "+name+" already created.","");
				//System.out.println(Thread.currentThread().getName()+"Chat: "+name+" already created." + " In thread: "+Thread.currentThread().getName());
				theChat = chats.get(name);
				return theChat;
			} else {
				theChat = new Chat(this, name);
				//No need to use putIfAbsent because it is synchronized
				chats.put(name, theChat);
				isChatCreated = true;
				//System.out.println(Thread.currentThread().getName()+"Creating chat: "+name+ " In thread: "+Thread.currentThread().getName());
				PrintlnI.printlnI("Creating chat: "+name, "");
			}
		}*/
		theChat = new Chat(this, name);
		Chat obtainedChat = chats.putIfAbsent(name, theChat);
		if (null != obtainedChat )
		{
			PrintlnI.printlnI("Chat: "+name+" already created.","");
			return obtainedChat;
		} else {
			isChatCreated = true;
			PrintlnI.printlnI("Creating chat: "+name, "");
		}
		
		/*if (isChatCreated) {
			//synchronized (users) {
				for(User user : users.values()){
					//System.out.println("Sent message or new chat to user: "+ user.getName());
					user.newChat(theChat);
					//PrintlnI.printlnI("Sent message of new chat: "+ theChat.getName() +" to user: "+ user.getName(),"");
				}
			//}		
		}*/
		
		if (isChatCreated) {
			final Chat theUsedChat = theChat;
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

		//TODO should it be included in a mutual exclusion zone?
		//synchronized (users) {
			for(User user : users.values()){
				user.chatClosed(removedChat);
			}
		//}
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
}
