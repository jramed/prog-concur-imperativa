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
		synchronized (chats) {
			if(chats.containsKey(name)){
				PrintlnI.printlnI("Chat: "+name+" already created.","");
				//System.out.println(Thread.currentThread().getName()+"Chat: "+name+" already created." + " In thread: "+Thread.currentThread().getName());
				theChat = chats.get(name);
			} else {
				theChat = new Chat(this, name);
				//No need to use putIfAbsent because it is synchronized
				chats.put(name, theChat);
				isChatCreated = true;
				//System.out.println(Thread.currentThread().getName()+"Creating chat: "+name+ " In thread: "+Thread.currentThread().getName());
				PrintlnI.printlnI("Creating chat: "+name, "");
			}
		}
		
		if (isChatCreated) {
			synchronized (users) {
				for(User user : users.values()){
					//System.out.println("Sent message or new chat to user: "+ user.getName());
					user.newChat(theChat);
					PrintlnI.printlnI("Sent message of new chat: "+ theChat.getName() +" to user: "+ user.getName(),"");
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
		synchronized (users) {
			for(User user : users.values()){
				user.chatClosed(removedChat);
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
}
