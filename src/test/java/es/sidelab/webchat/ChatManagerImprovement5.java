package es.sidelab.webchat;

import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.ChatManager;
import es.codeurjc.webchat.PrintlnI;
import es.codeurjc.webchat.User;

public class ChatManagerImprovement5 {

	private CountDownLatch latchCreateChat;
	private CountDownLatch latchCloseChat;
	
	@Test
	public void newChatMsgReception() throws InterruptedException, TimeoutException, ExecutionException {

		System.out.println("==============NEW test newChatMsgReception=====================");
		// Crear el chat Manager
		final ChatManager chatManager = new ChatManager(5);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
		final Boolean[] hasUserReceiveNotifNewChat = new Boolean[numThreads];
		Arrays.fill(hasUserReceiveNotifNewChat, false);

		PrintlnI.initPerThread();

		long startTime = System.currentTimeMillis();
		for (int i = 0; i < numThreads; ++i) {
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->checkMsgInChatCreation(count, chatManager, hasUserReceiveNotifNewChat, numThreads));
		}

		serviceInvocation(numThreads, completionService);

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		long endTime = System.currentTimeMillis();
		long difference = endTime-startTime;
		PrintlnI.printlnI("startTime: "+startTime+ " endTime: "+endTime+" difference: "+ difference ,"");
		int threshold = 1500;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime < threshold);

		PrintlnI.printlnI(Arrays.asList(hasUserReceiveNotifNewChat).toString(),"");

		Boolean[] valuesToCheck = new Boolean[numThreads];
		Arrays.fill(valuesToCheck, true);

		assertTrue("Messages sent for users "+Arrays.asList(valuesToCheck).toString()+" , but the value is "
				+ Arrays.asList(hasUserReceiveNotifNewChat).toString(), Arrays.equals(hasUserReceiveNotifNewChat, valuesToCheck));

		PrintlnI.reset();
	}


	private void serviceInvocation(int numThreads, CompletionService<String> completionService) {
		String[] returnedValues = new String[numThreads];
		for (int i = 0; i < numThreads; ++i) {
			try {
				// Crear un usuario que guarda en chatName el nombre del nuevo chat
				Future<String> f = completionService.take();
				returnedValues[i] = f.get();
				System.out.println("The returned value from the Thread is: "+ Arrays.asList(returnedValues[i]).toString());
			} catch (ConcurrentModificationException e) {
				System.out.println("Exception: " + e.toString());
				assertTrue("Exception received" + e.toString(), false);
			} catch (InterruptedException e) {
				System.out.println("Exception: " + e.toString());
				assertTrue("Exception received" + e.toString(), false);
			} catch (ExecutionException e) {
				System.out.println("Exception: " + e.toString());
				e.printStackTrace();
				assertTrue("Exception received" + e.toString(), false);
			}
		}
	}


	private String checkMsgInChatCreation(int count, ChatManager chatManager, 
			Boolean[] hasUserReceiveNotif, int numThreads ) throws InterruptedException, TimeoutException {

		TestUser user = new TestUser("user"+count) {
			public void newChat(Chat chat) {
				PrintlnI.printlnI("User: " + this.name +", new Chat has been created:" + chat.getName(), "");
				try {
					Thread.sleep(500);
					hasUserReceiveNotif[count] =  true;
					PrintlnI.printlnI("User: " + this.name + " "+hasUserReceiveNotif[count], "");
					latchCreateChat.countDown();
				} catch (InterruptedException intExcep)
				{
					PrintlnI.printlnI("Exception received: " + intExcep.toString(),"");
					intExcep.printStackTrace();
				}
			}
		};

		chatManager.newUser(user);

		
		if (count+1 == numThreads) {
			// Create the chat from chatManager just once. For the last user
			// ensure that all the user are created.
			Thread.sleep(100);
			chatManager.newChat("Chat", 5, TimeUnit.SECONDS);
			latchCreateChat = new CountDownLatch(numThreads);
			latchCreateChat.await(2000L, TimeUnit.MILLISECONDS);
		}
		
		return user.getName();
	}

	@Test
	public void closeChatMsgReception() throws InterruptedException, TimeoutException, ExecutionException {

		System.out.println("==============NEW test closeChatMsgReception=====================");
		// Crear el chat Manager
		final ChatManager chatManager = new ChatManager(5);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
		final Boolean[] hasUserReceiveNotifNewChat = new Boolean[numThreads];
		Arrays.fill(hasUserReceiveNotifNewChat, false);

		PrintlnI.initPerThread();

		long startTime = System.currentTimeMillis();
		for (int i = 0; i < numThreads; ++i) {
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->checkMsgInChatRemoval(count, chatManager, hasUserReceiveNotifNewChat, numThreads));
		}

		serviceInvocation(numThreads, completionService);

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		long endTime = System.currentTimeMillis();
		long difference = endTime-startTime;
		PrintlnI.printlnI("startTime: "+startTime+ " endTime: "+endTime+" difference: "+ difference ,"");
		int threshold = 1500;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime < threshold);

		PrintlnI.printlnI(Arrays.asList(hasUserReceiveNotifNewChat).toString(),"");

		Boolean[] valuesToCheck = new Boolean[numThreads];
		Arrays.fill(valuesToCheck, true);

		assertTrue("Messages sent for users "+Arrays.asList(valuesToCheck).toString()+" , but the value is "
				+ Arrays.asList(hasUserReceiveNotifNewChat).toString(), Arrays.equals(hasUserReceiveNotifNewChat, valuesToCheck));

		PrintlnI.reset();
	}


	private String checkMsgInChatRemoval(int count, ChatManager chatManager,
			Boolean[] hasUserReceiveNotif, int numThreads ) throws InterruptedException, TimeoutException {

		TestUser user = new TestUser("user"+count) {
			public void chatClosed(Chat chat) {
				PrintlnI.printlnI("User: " + this.name +", Chat: " + chat.getName() + " has been closed", "");
				try {
					Thread.sleep(500);
					if (chat.getName() == "Chat") {
						hasUserReceiveNotif[count] =  true;
					} else {
						hasUserReceiveNotif[count] =  false;
					}
					PrintlnI.printlnI("User: " + this.name + " "+hasUserReceiveNotif[count], "");
					latchCloseChat.countDown();
				} catch (InterruptedException intExcep)
				{
					PrintlnI.printlnI("Exception received: " + intExcep.toString(),"");
					intExcep.printStackTrace();
				}
			}

		};

		chatManager.newUser(user);

		if (count+1 == numThreads) {
			// Create the chat from chatManager just once. For the last user
			Thread.sleep(100);
			Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);
			chatManager.newChat("Chat2", 5, TimeUnit.SECONDS);
			//close the chant and wait until all the user notify the reception
			//of the message
			chat.close();
			latchCloseChat = new CountDownLatch(numThreads);
			latchCloseChat.await(2000L, TimeUnit.MILLISECONDS);
		}

		Thread.sleep(300);
		return user.getName();
	}

	@Test
	public void addNewUserToChatCheckMsgReception() throws InterruptedException, TimeoutException, ExecutionException {

		System.out.println("==============NEW test addNewUserToChatCheckMsgReception=====================");
		// Crear el chat Manager
		final ChatManager chatManager = new ChatManager(5);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<User> completionService = new ExecutorCompletionService<>(executor);
		final ConcurrentMap<String, Integer> newUserInChatReceivedNotif = new ConcurrentHashMap<>();
		//The countDownLatch is used to guarantee the correct number of notification are received
		//for a given user.
		ConcurrentMap<String, CountDownLatch> controlNotifPerUser = new ConcurrentHashMap<>();

		PrintlnI.initPerThread();

		long startTime = System.currentTimeMillis();
		for (int i = 0; i < numThreads; ++i) {
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->checkMsgWhenUserAddToChat(count, chatManager, newUserInChatReceivedNotif, numThreads, controlNotifPerUser));
		}


		User[] returnedValues = serviceInvocationUser(numThreads, completionService);

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		long endTime = System.currentTimeMillis();
		long difference = endTime-startTime;
		PrintlnI.printlnI("startTime: "+startTime+ " endTime: "+endTime+" difference: "+ difference ,"");
		int threshold = 2000;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime < threshold);

		//Thread.sleep(2000);
		PrintlnI.printlnI(Arrays.asList(newUserInChatReceivedNotif).toString(),"");

		assertThat("One user with 3 notifications",newUserInChatReceivedNotif.values(),hasItem(3));
		assertThat("One user with 2 notifications",newUserInChatReceivedNotif.values(),hasItem(2));
		//in some test could happen that two users have one notification. That is right.
		assertThat("At least one user with 1 notifications",newUserInChatReceivedNotif.values(),hasItem(1));

		PrintlnI.reset();
		for (User u: returnedValues) {
			chatManager.removeUser(u);
		}
	}


	private User checkMsgWhenUserAddToChat(int count, ChatManager chatManager,
			ConcurrentMap<String, Integer> newUSerInChatMsgs, int numThreads,
			ConcurrentMap<String, CountDownLatch> controlNotifPerUser) throws InterruptedException, TimeoutException {

		TestUser user = new TestUser("user"+count) {
			public void newUserInChat(Chat chat, User user) {
				PrintlnI.printlnI("TestUSer: "+this.name + ": New user " + user.getName() +
						" in chat " + chat.getName() +
						" Number of notif: " + newUSerInChatMsgs.get(this.getName()),"");
				try {
					//to simulate a dealy in the handling of the notification
					Thread.sleep(500);
					Integer value = newUSerInChatMsgs.putIfAbsent(this.getName(), 1);
					if ( null !=  value) {
						//This can be done because there is just one thread
						//to handle the new user in chat notification for an user
						newUSerInChatMsgs.replace(this.getName(), value+1);
					}
					PrintlnI.printlnI("TestUser: " + this.name + " value: " +
							newUSerInChatMsgs.get(this.getName()), "");
				} catch (InterruptedException intExcep)
				{
					PrintlnI.printlnI("Exception received: " + intExcep.toString(),"");
					intExcep.printStackTrace();
				}
				controlNotifPerUser.get(this.getName()).countDown();
				PrintlnI.printlnI("CountDown for user " + this.getName(),"");
			}

		};

		if (count+1 != numThreads) {
			controlNotifPerUser.putIfAbsent(user.getName(), new CountDownLatch(numThreads-count-1));
			PrintlnI.printlnI("Set CountDown for user " + user.getName()+ " with value: " + (numThreads-count-1),"");
		}
		chatManager.newUser(user);

		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);
		//This sleep is to guarantee the execution order. If not in some executions
		//due to different execution speed the distribution of the notification is
		//different, although those are also right
		Thread.sleep(count*100);

		chat.addUser(user);
		if (count+1 != numThreads) {
			controlNotifPerUser.get(user.getName()).await(2000L, TimeUnit.MILLISECONDS);
			PrintlnI.printlnI("Finished countdown for user " + user.getName() +
					" with value: " +controlNotifPerUser.get(user.getName()).getCount(),"");
		}
		chat.removeUser(user);
		return user;
	}

	@Test
	public void removeUserFromChatCheckMsgReception() throws InterruptedException, TimeoutException, ExecutionException {

		System.out.println("==============NEW test removeUserFromChatCheckMsgReception=====================");
		// Crear el chat Manager
		final ChatManager chatManager = new ChatManager(5);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<User> completionService = new ExecutorCompletionService<>(executor);
		final ConcurrentMap<String, Integer> removeUserFromChatReceivedNotif = new ConcurrentHashMap<>();
		//The countDownLatch is used to guarantee the correct number of notification are received
		//for a given user.
		ConcurrentMap<String, CountDownLatch> controlNotifPerUser = new ConcurrentHashMap<>();

		PrintlnI.initPerThread();

		long startTime = System.currentTimeMillis();
		for (int i = 0; i < numThreads; ++i) {
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->checkMsgWhenUserRemoveFromChat(count, chatManager, removeUserFromChatReceivedNotif, numThreads, controlNotifPerUser));
		}

		User[] returnedValues = serviceInvocationUser(numThreads, completionService);

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		long endTime = System.currentTimeMillis();
		long difference = endTime-startTime;
		PrintlnI.printlnI("startTime: "+startTime+ " endTime: "+endTime+" difference: "+ difference ,"");
		int threshold = 2000;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime < threshold);

		//Thread.sleep(2000);
		PrintlnI.printlnI(Arrays.asList(removeUserFromChatReceivedNotif).toString(),"");

		assertThat("At least one user with 1 notifications",removeUserFromChatReceivedNotif.values(),everyItem(is(1)));

		for (User u: returnedValues) {
			chatManager.removeUser(u);
		}
		PrintlnI.reset();
	}


	private User[] serviceInvocationUser(int numThreads, CompletionService<User> completionService) {
		User[] returnedValues = new User[numThreads];
		for (int i = 0; i < numThreads; ++i) {
			try {
				// Crear un usuario que guarda en chatName el nombre del nuevo chat
				Future<User> f = completionService.take();
				returnedValues[i] = f.get();
				System.out.println("The returned value from the Thread is: "+ Arrays.asList(returnedValues[i].getName()).toString());
			} catch (ConcurrentModificationException e) {
				System.out.println("Exception: " + e.toString());
				assertTrue("Exception received" + e.toString(), false);
			} catch (InterruptedException e) {
				System.out.println("Exception: " + e.toString());
				assertTrue("Exception received" + e.toString(), false);
			} catch (ExecutionException e) {
				System.out.println("Exception: " + e.toString());
				e.printStackTrace();
				assertTrue("Exception received" + e.toString(), false);
			}
		}
		return returnedValues;
	}

	private User checkMsgWhenUserRemoveFromChat(int count, ChatManager chatManager,
			ConcurrentMap<String, Integer> removeUserFromChatMsgs, int numThreads,
			ConcurrentMap<String, CountDownLatch> controlNotifPerUser) throws InterruptedException, TimeoutException {

		TestUser user = new TestUser("user"+count) {
			public void userExitedFromChat(Chat chat, User user) {
				PrintlnI.printlnI("TestUSer: "+this.name + ": existed user " + user.getName() +
						" from chat " + chat.getName() +
						" Number of notif: " + removeUserFromChatMsgs.get(this.getName()),"");
				try {
					//to simulate a dealy in the handling of the notification
					Thread.sleep(500);
					Integer value = removeUserFromChatMsgs.putIfAbsent(this.getName(), 1);
					if ( null !=  value) {
						//This can be done because there is just one thread
						//to handle the new user in chat notification for an user
						removeUserFromChatMsgs.replace(this.getName(), value+1);
					}
					PrintlnI.printlnI("TestUser: " + this.name + " value: " +
							removeUserFromChatMsgs.get(this.getName()), "");
				} catch (InterruptedException intExcep)
				{
					PrintlnI.printlnI("Exception received: " + intExcep.toString(),"");
					intExcep.printStackTrace();
				}
				controlNotifPerUser.get(this.getName()).countDown();
				PrintlnI.printlnI("CountDown for user " + this.getName(),"");
			}

		};

		if (count+1 != numThreads) {
			int countDown = 1;
			controlNotifPerUser.putIfAbsent(user.getName(), new CountDownLatch(countDown));
			PrintlnI.printlnI("Set CountDown for user " + user.getName()+ " with value: "+ countDown,"");
		}
		chatManager.newUser(user);

		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		chat.addUser(user);
		//to guarantee all the user are created. Some other more complicated method, for example
		//countDownLatch could be used to guarantee all the users are created, but the sleep is fair
		//enough for our aim.
		Thread.sleep(100);
		if (count+1 == numThreads) {
			chat.removeUser(user);
		}
		if (count+1 != numThreads) {
			controlNotifPerUser.get(user.getName()).await(2000L, TimeUnit.MILLISECONDS);
			PrintlnI.printlnI("Finished countdown for user " + user.getName() +
					" with value: " +controlNotifPerUser.get(user.getName()).getCount(),"");
		}
		return user;
	}


	@Test
	public void sendMsgToUsersInChat() throws InterruptedException, TimeoutException, ExecutionException {

		System.out.println("==============NEW test sendMsgToUsersInaChat=====================");
		// Crear el chat Manager
		final ChatManager chatManager = new ChatManager(5);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
		final ConcurrentMap<String, Integer> userReceptionMsg = new ConcurrentHashMap<>();
		//The countDownLatch is used to guarantee the correct number of notification are received
		//for a given user.
		ConcurrentMap<String, CountDownLatch> controlNotifPerUser = new ConcurrentHashMap<>();

		PrintlnI.initPerThread();

		long startTime = System.currentTimeMillis();
		for (int i = 0; i < numThreads; ++i) {
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->checkMsgSentToOtherUsersInChat(count, chatManager, userReceptionMsg, numThreads, controlNotifPerUser));
		}

		serviceInvocation(numThreads, completionService);

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		long endTime = System.currentTimeMillis();
		long difference = endTime-startTime;
		PrintlnI.printlnI("startTime: "+startTime+ " endTime: "+endTime+" difference: "+ difference ,"");
		int threshold = 1500;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime < threshold);

		PrintlnI.printlnI(Arrays.asList(userReceptionMsg).toString(),"");

		assertThat("At least one user with 1 notifications",userReceptionMsg.values(),everyItem(is(1)));

		PrintlnI.reset();
	}

	private String checkMsgSentToOtherUsersInChat(int count, ChatManager chatManager,
			ConcurrentMap<String, Integer> newMsgReceived, int numThreads,
			ConcurrentMap<String, CountDownLatch> controlNotifPerUser) throws InterruptedException, TimeoutException {

		TestUser user = new TestUser("user"+count) {
			public void newMessage(Chat chat, User user, String message) {
				PrintlnI.printlnI("TestUSer: "+this.name + ": msg received from user " + user.getName() +
						" in chat " + chat.getName() +
						" Number of msgs: " + newMsgReceived.get(this.getName()) +
						" msg received: " + message,"");
				try {
					//to simulate a delay in the handling of the notification
					Thread.sleep(500);
					Integer value = newMsgReceived.putIfAbsent(this.getName(), 1);
					if ( null !=  value) {
						//This can be done because there is just one thread
						//to handle the new user in chat notification for an user
						newMsgReceived.replace(this.getName(), value+1);
					}
					PrintlnI.printlnI("TestUser: " + this.name + " value: " +
							newMsgReceived.get(this.getName()), "");
				} catch (InterruptedException intExcep)
				{
					PrintlnI.printlnI("Exception received: " + intExcep.toString(),"");
					intExcep.printStackTrace();
				}
				controlNotifPerUser.get(this.getName()).countDown();
				PrintlnI.printlnI("CountDown for user " + this.getName(),"");
			}

		};

		if (count+1 != numThreads) {
			int countDown = 1;
			controlNotifPerUser.putIfAbsent(user.getName(), new CountDownLatch(countDown));
			PrintlnI.printlnI("Set CountDown for user " + user.getName()+ " with value: " + countDown,"");
		}
		chatManager.newUser(user);

		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);
		Chat chat2 = chatManager.newChat("Chat2", 5, TimeUnit.SECONDS);

		chat.addUser(user);
		chat2.addUser(user);
		Thread.sleep(count*100);
		if (count+1 != numThreads) {
			controlNotifPerUser.get(user.getName()).await(2000L, TimeUnit.MILLISECONDS);
			PrintlnI.printlnI("Finished countdown for user " + user.getName() +
					" with value: " +controlNotifPerUser.get(user.getName()).getCount(),"");
		} else {
			chat.sendMessage(user, "message from "+user.getName());
		}
		return user.getName();
	}

}