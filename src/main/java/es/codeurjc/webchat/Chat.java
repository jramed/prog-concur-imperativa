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

public class Chat {
	
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

	private String name;
	private ChatManager chatManager;

	private ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
	private ConcurrentMap<String, CustomPair> taskPerUser = new ConcurrentHashMap<>();


	public Chat(ChatManager chatManager, String name) {
		this.chatManager = chatManager;
		this.name = name;		
	}

	public String getName() {
		return name;
	}

	public void addUser(User user) {
		User previousUser = users.putIfAbsent(user.getName(), user);
		if (previousUser != null) {
			PrintlnI.printlnI("There was a previous user with the name: " + user.getName(), "");
		} else {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
			CustomPair pair = new CustomPair(executor,completionService);
			taskPerUser.putIfAbsent(user.getName(), pair);
		}
		synchronized (users) {
			for(User u : users.values()){
				if (u != user) {
					u.newUserInChat(this, user);
				}
			}
		}
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

		for(User u : users.values()){
			u.userExitedFromChat(this, user);
		}
	}

	public Collection<User> getUsers() {
		return Collections.unmodifiableCollection(users.values());
	}

	public User getUser(String name) {
		return users.getOrDefault(name, null);
	}

	public void sendMessage(User user, String message) {
		synchronized (users) {
			for(User u : users.values()){
				taskPerUser.get(user.getName()).getCompletionServices().submit(()->sendMessageToUser(u, message));
			}
		}
	}

	public void close() {
		this.chatManager.closeChat(this);
	}
	
	private String sendMessageToUser(User user, String message) {	
		user.newMessage(this, user, message);
		return user.getName()+message;
	}
}
