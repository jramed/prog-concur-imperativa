package es.codeurjc.webchat;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChatManager {

	private ConcurrentMap<String, Chat> chats = new ConcurrentHashMap<>();
	private ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
	private int maxChats;

	public ChatManager(int maxChats) {
		this.maxChats = maxChats;
	}

	public void newUser(User user) {
		
		if(null != users.putIfAbsent(user.getName(), user)) {
			throw new IllegalArgumentException("There is already a user with name \'"
					+ user.getName() + "\'");
		}
	}

	public Chat newChat(String name, long timeout, TimeUnit unit) throws InterruptedException,
			TimeoutException {

		//TODO add mutual exclusion protection to ensure the size is not changed
		//a new user could be been added while the checking is done	
		if (chats.size() == maxChats) {
			throw new TimeoutException("There is no enought capacity to create a new chat");
		}

		//TODO add mutal exclusion protection
		if(chats.containsKey(name)){
			return chats.get(name);
		} else {
			Chat newChat = new Chat(this, name);
			//TODO add mutual exclusion protection
			chats.put(name, newChat);
			
			//TODO should it be included in a mutual exclusion zone?
			for(User user : users.values()){
				user.newChat(newChat);
			}

			return newChat;
		}
	}

	public void closeChat(Chat chat) {
		//TODO add mutual exclusion protection
		Chat removedChat = chats.remove(chat.getName());
		if (removedChat == null) {
			throw new IllegalArgumentException("Trying to remove an unknown chat with name \'"
					+ chat.getName() + "\'");
		}

		//TODO should it be included in a mutual exclusion zone?
		for(User user : users.values()){
			user.chatClosed(removedChat);
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
}
