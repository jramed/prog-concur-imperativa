package es.sidelab.webchat;

import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.ChatManager;
import es.codeurjc.webchat.User;
import es.codeurjc.webchat.PrintlnI;

public class ChatManagerTest {

	private CountDownLatch latch;
	private CountDownLatch latchCreateChat;
	private CountDownLatch latchCloseChat;
	private Exchanger<Boolean> exchanger;

	@Test
	public void newChat() throws InterruptedException, TimeoutException, ExecutionException {

		// Crear el chat Manager
		final ChatManager chatManager = new ChatManager(50);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String[]> completionService = new ExecutorCompletionService<>(executor);
		final String[] chatName = new String[4];


		for (int i = 0; i < numThreads; ++i) {
			final int count = i;
			completionService.submit(()->simulateUser(count, chatName, chatManager));
			PrintlnI.initPerThread();
		}


		String[][] returnedValues = new String[numThreads][5];
		for (int i = 0; i < numThreads; ++i) {
			try {
				// Crear un usuario que guarda en chatName el nombre del nuevo chat
				Future<String[]> f = completionService.take();
				String[] returnedValue = f.get();
				returnedValues[i] = returnedValue;
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

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		System.out.println(chatName[0] + " " + chatName[1] + " " + chatName[2] + " " + chatName[3]);
		// Comprobar que el chat recibido en el método 'newChat' se llama 'Chat'
		String[] valuesToCheck = {"Chat0","Chat1","Chat2", "Chat3","Chat4"};
		for (int i = 0; i < 4; i++) {
			assertTrue("The method 'newChat' should be invoked with "+Arrays.asList(valuesToCheck).toString()+" , but the value is "
					+ Arrays.asList(returnedValues[i]).toString(), Arrays.equals(returnedValues[i], valuesToCheck));
		}

		PrintlnI.reset();
	}


	private String[] simulateUser(int count, String[] chatName, ChatManager chatManager) throws
								InterruptedException, TimeoutException {
		final int numIterations = 5;
		String[] chatCreated = new String[numIterations];

		TestUser user = new TestUser("user"+Thread.currentThread().getName()) {
			public void newChat(Chat chat) {
				if ( null != chatName[count]) {
					chatName[count] = chatName[count]+chat.getName();
				}
				else {
					chatName[count] = chat.getName();
				}

				PrintlnI.printlnI("TestUser class for user: " + this.name +", new chat created: "+chat.getName() +" for thread number: " +count, "");
				PrintlnI.printlnI("TestUser class for user: " + this.name + chatName[count], "");
			}
		};

		if (count % 2 == 0)
		{
			TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(0, 100 + 1));
		}
		chatManager.newUser(user);

		for (int i = 0; i < numIterations; i++) {
			// Crear un nuevo chat en el chatManager
			Chat chat = chatManager.newChat("Chat"+i, 5, TimeUnit.SECONDS);
			chat.addUser(user);
			chatCreated[i] = chat.getName();
		}
		return chatCreated;
	}



	@Test
	public void newUserInChat() throws InterruptedException, TimeoutException {

		System.out.println("==============NEW test=====================");
		PrintlnI.initPerThread();
		ChatManager chatManager = new ChatManager(5);

		final String[] newUser = new String[1];

		TestUser user1 = new TestUser("user1") {
			@Override
			public void newUserInChat(Chat chat, User user) {
				PrintlnI.printlnI("Received notification from user: "+user.getName(),"");
				newUser[0] = user.getName();
			}
		};

		TestUser user2 = new TestUser("user2");

		chatManager.newUser(user1);
		chatManager.newUser(user2);

		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		chat.addUser(user1);
		chat.addUser(user2);

		//because the notification of a new user is sent from another thread
		//that must be created, the sleep is necessary to give room enough to
		//the new thread to be created and does it work.
		Thread.sleep(550);
		PrintlnI.reset();
		assertTrue("Notified new user '" + newUser[0] + "' is not equal than user name 'user2'",
				"user2".equals(newUser[0]));
	}

	@Test
	public void parallelMessageSending() throws InterruptedException, TimeoutException
	{
		System.out.println("==============NEW test parallelMessageSending=====================");
		// Crear el chat Manager
		final ChatManager chatManager = new ChatManager(5);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
		final Boolean[] hasUserSentReceiveMsg = new Boolean[numThreads];
		Arrays.fill(hasUserSentReceiveMsg, false);

		PrintlnI.initPerThread();
		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		long startTime = System.currentTimeMillis();
		latch = new CountDownLatch(numThreads-1);
		for (int i = 0; i < numThreads; i++)
		{
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->simulateUserParallelTest(count, hasUserSentReceiveMsg, chatManager, chat));
		}

		//4 thread for 4 users
		String[] returnedValues = new String[numThreads];
		for (int i = 0; i < numThreads; ++i) {
			try {
				// Crear un usuario que guarda en chatName el nombre del nuevo chat
				Future<String> f = completionService.take();
				String returnedValue = f.get();
				returnedValues[i] = returnedValue;
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

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		long endTime = System.currentTimeMillis();
		long difference = endTime-startTime;
		PrintlnI.printlnI("startTime: "+startTime+ " endTime: "+endTime+" difference: "+ difference ,"");
		int threshold = 1500;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime < threshold);
		PrintlnI.printlnI(Arrays.asList(hasUserSentReceiveMsg).toString(),"");

		// Comprobar que el chat recibido en el método 'newChat' se llama 'Chat'
		Boolean[] valuesToCheck = new Boolean[numThreads];
		for (int i = 0; i < numThreads; i++)
		{
			valuesToCheck[i]=true;
		}

		assertTrue("Messages sent for users "+Arrays.asList(valuesToCheck).toString()+" , but the value is "
				+ Arrays.asList(hasUserSentReceiveMsg).toString(), Arrays.equals(hasUserSentReceiveMsg, valuesToCheck));

		PrintlnI.reset();
	}


	private String simulateUserParallelTest(int count, Boolean[] hasUserSentReceiveMsg, ChatManager chatManager, Chat chat) throws
								InterruptedException, TimeoutException {

		TestUser user = new TestUser("user"+count) {
			public void newMessage(Chat chat, User user, String message) {
				hasUserSentReceiveMsg[count] = true;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException intExcep)
				{
					PrintlnI.printlnI("Exception received: " + intExcep.toString(),"");
					intExcep.printStackTrace();
				}

				PrintlnI.printlnI("User: " + this.name +", new message: "+message +" for thread number: " +count, "");
				PrintlnI.printlnI("User: " + this.name + " "+hasUserSentReceiveMsg[count], "");

				latch.countDown();
			}
		};

		chatManager.newUser(user);

		// Crear un nuevo chat en el chatManager
		chat.addUser(user);

		if (count == 0)
		{
			chat.sendMessage(user, "Message from user: "+user.getName());
			try
			{
				hasUserSentReceiveMsg[count] = latch.await(2000L,TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException e)
			{
				PrintlnI.printlnI("Exception waiting from count donw latch", "");
			}
		}


		return user.getName();
	}

	@Test
	public void messageOrderCheck() throws InterruptedException, TimeoutException
	{
		System.out.println("==============NEW test messageOrderCheck=====================");
		final ChatManager chatManager = new ChatManager(5);

		int numThreads = 2;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
		final Boolean[] hasUserSentReceiveMsgInOrder = new Boolean[numThreads];
		Arrays.fill(hasUserSentReceiveMsgInOrder, false);

		PrintlnI.initPerThread();
		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);
		exchanger = new Exchanger<Boolean>();

		for (int i = 0; i < numThreads; i++)
		{
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->checkMsgOrderTest(count, hasUserSentReceiveMsgInOrder, chatManager, chat, numThreads));
		}

		//2 thread for 2 users
		String[] returnedValues = new String[numThreads];
		for (int i = 0; i < numThreads; ++i) {
			try {
				Future<String> f = completionService.take();
				String returnedValue = f.get();
				returnedValues[i] = returnedValue;
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

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		PrintlnI.printlnI(Arrays.asList(hasUserSentReceiveMsgInOrder).toString(),"");

		Boolean[] valuesToCheck = new Boolean[numThreads];
		Arrays.fill(valuesToCheck, true);

		assertTrue("Messages sent for users "+Arrays.asList(valuesToCheck).toString()+" , but the value is "
				+ Arrays.asList(hasUserSentReceiveMsgInOrder).toString(), Arrays.equals(hasUserSentReceiveMsgInOrder, valuesToCheck));

		PrintlnI.reset();
	}


	private String checkMsgOrderTest(int count, Boolean[] hasUserSentReceiveMsgInOrder, ChatManager chatManager,
			Chat chat, int numThreads) throws InterruptedException, TimeoutException {
		TestUser user = new TestUser("user"+count) {
			int previousReceivedMsg = 0;
			public void newMessage(Chat chat, User user, String message) {
				PrintlnI.printlnI("User: " + this.name +", new message: "+message, "");
				boolean isInOrder = false;
				try {
					if (previousReceivedMsg == Integer.valueOf(message)-1)
					{
						previousReceivedMsg = Integer.valueOf(message);
						isInOrder = true;
						hasUserSentReceiveMsgInOrder[count] = isInOrder;
					}
					Thread.sleep(500);
					exchanger.exchange(isInOrder,1000L, TimeUnit.MILLISECONDS);
				} catch (InterruptedException intExcep)
				{
					PrintlnI.printlnI("Exception received: " + intExcep.toString(),"");
					intExcep.printStackTrace();
				}
				catch (TimeoutException timeOutExcep)
				{
					PrintlnI.printlnI("Exception received: " + timeOutExcep.toString(),"");
					timeOutExcep.printStackTrace();
				}

				PrintlnI.printlnI("User: " + this.name + " "+isInOrder, "");
			}
		};

		chatManager.newUser(user);

		// Crear un nuevo chat en el chatManager
		chat.addUser(user);
		if (count+1 == numThreads)
		{
			//ensure that the messages are sent after all user has been created
			TimeUnit.MILLISECONDS.sleep(100);
			for (int i = 1; i <= 5; i++)
			{
				chat.sendMessage(user, String.valueOf(i));
				Boolean result = exchanger.exchange(null,1000L, TimeUnit.MILLISECONDS);
				PrintlnI.printlnI(result+" received from the exchange","");
				if ( false == result )
				{
					PrintlnI.printlnI("False received from the exchange","");
					hasUserSentReceiveMsgInOrder[count] = false;
					return user.getName();
				} 
			}
			PrintlnI.printlnI("After the for","");
			hasUserSentReceiveMsgInOrder[count] = true;
		}

		return user.getName();
	}

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
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
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
	}


	private String checkMsgWhenUserAddToChat(int count, ChatManager chatManager,
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
		return user.getName();
	}

	@Test
	public void removeUserFromChatCheckMsgReception() throws InterruptedException, TimeoutException, ExecutionException {

		System.out.println("==============NEW test removeUserFromChatCheckMsgReception=====================");
		// Crear el chat Manager
		final ChatManager chatManager = new ChatManager(5);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
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

		PrintlnI.reset();
	}

	private String checkMsgWhenUserRemoveFromChat(int count, ChatManager chatManager,
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
		return user.getName();
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
					//to simulate a dealy in the handling of the notification
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
