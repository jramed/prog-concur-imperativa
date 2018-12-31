package es.codeurjc.webchat;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Chat {

	private String name;
	private ChatManager chatManager;

	private ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
	private ConcurrentMap<String, CustomPair> taskPerUser = new ConcurrentHashMap<>();


	public Chat(ChatManager chatManager, String name, ConcurrentMap<String, CustomPair> taskPerUser) {
		this.chatManager = chatManager;
		this.name = name;
		this.taskPerUser = taskPerUser;
	}

	public String getName() {
		return name;
	}

	//From my point of this method should return an error if the user already exist.
	//The original code it allows to add the same user again, it replace the old one.
	public void addUser(User user) {
		User previousUser = users.putIfAbsent(user.getName(), user);
		if (previousUser != null) {
			PrintlnI.printlnI("There was a previous user with the name: " + user.getName(), "");
		}

		for(User u : users.values()){
			if (!u.getName().equals(user.getName())) {
				CustomPair pair = taskPerUser.get(u.getName());
				CompletionService<String> completionService;
				if (pair != null)
				{
					completionService = pair.getCompletionServices();
					if (completionService != null)
						completionService.submit(()->newUserInChat(u, user));
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

		for(User u : users.values()){
			taskPerUser.get(u.getName()).getCompletionServices().submit(()->userExitedFromChat(u, user));
		}
	}

	public Collection<User> getUsers() {
		return Collections.unmodifiableCollection(users.values());
	}

	public User getUser(String name) {
		return users.getOrDefault(name, null);
	}

	public void sendMessage(User user, String message) {
		Collection<User> usersColl = this.getUsers();
		for(User u : usersColl){
			if (u != user)
			{
				PrintlnI.printlnI("Destination user: "+u.getName(),"");
				taskPerUser.get(u.getName()).getCompletionServices().submit(()->sendMessageToUser(u, user, message));
			}
		}
	}

	public void close() {
		this.chatManager.closeChat(this);
	}
	
	private String sendMessageToUser(User user, User userOrig, String message) {
		user.newMessage(this, userOrig, message);
		return user.getName()+" "+message;
	}

	private String userExitedFromChat(User user, User userExisted) {
		user.userExitedFromChat(this, userExisted);
		return "User "+userExisted.getName()+" existed from chat to user "+user.getName();
	}

	private String newUserInChat(User user, User userNew) throws InterruptedException {
		//TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(0, 500 + 1));
		user.newUserInChat(this, userNew);
		//PrintlnI.printlnI("New user "+userNew.getName()+" in chat message to user "+user.getName(),"");
		return "New user "+userNew.getName()+" in chat to user "+user.getName();
	}

}
