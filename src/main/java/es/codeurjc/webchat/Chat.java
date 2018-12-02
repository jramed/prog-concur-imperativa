package es.codeurjc.webchat;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Chat {

	private String name;
	private ChatManager chatManager;

	private ConcurrentMap<String, User> users = new ConcurrentHashMap<>();


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
		}
		synchronized (users) {
			for(User u : users.values()){
				if (u != user) {
					u.newUserInChat(this, user);
				}
			}
		}
	}

	public void removeUser(User user) {
		try {
			users.remove(user.getName(), user);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
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
				u.newMessage(this, user, message);
			}
		}
	}

	public void close() {
		this.chatManager.closeChat(this);
	}
}
