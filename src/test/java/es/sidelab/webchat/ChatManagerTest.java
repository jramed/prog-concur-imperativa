package es.sidelab.webchat;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CompletionService;
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
			completionService.submit(()->checkMsgOrderTest(count, hasUserSentReceiveMsgInOrder, chatManager, chat));
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
		for (int i = 0; i < numThreads; i++)
		{
			valuesToCheck[i]=true;
		}

		assertTrue("Messages sent for users "+Arrays.asList(valuesToCheck).toString()+" , but the value is "
				+ Arrays.asList(hasUserSentReceiveMsgInOrder).toString(), Arrays.equals(hasUserSentReceiveMsgInOrder, valuesToCheck));

		PrintlnI.reset();
	}


	private String checkMsgOrderTest(int count, Boolean[] hasUserSentReceiveMsgInOrder, ChatManager chatManager,
			Chat chat) throws InterruptedException, TimeoutException {
		TestUser user = new TestUser("user"+count) {
			int previousReceivedMsg = 0;
			public void newMessage(Chat chat, User user, String message) {
				PrintlnI.printlnI("User: " + this.name +", new message: "+message, "");
				try {
					boolean isInOrder = false;
					hasUserSentReceiveMsgInOrder[count] = false;
					if (previousReceivedMsg == Integer.valueOf(message)-1)
					{
						previousReceivedMsg = Integer.valueOf(message);
						isInOrder = true;
						hasUserSentReceiveMsgInOrder[count] = true;
					}

					exchanger.exchange(isInOrder,1000L, TimeUnit.MILLISECONDS);
					Thread.sleep(500);
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

				PrintlnI.printlnI("User: " + this.name + " "+hasUserSentReceiveMsgInOrder[count], "");
			}
		};

		chatManager.newUser(user);

		// Crear un nuevo chat en el chatManager
		chat.addUser(user);
		if (count == 0)
		{
			for (int i = 1; i <= 5; i++)
			{
				chat.sendMessage(user, String.valueOf(i));
				Boolean result = exchanger.exchange(null,1000L, TimeUnit.MILLISECONDS);
				if ( !result.booleanValue() )
				{
					PrintlnI.printlnI("False received in the exchange","");
					hasUserSentReceiveMsgInOrder[count] = false;
					return user.getName();
				}
			}
			PrintlnI.printlnI("After the for","");
			hasUserSentReceiveMsgInOrder[count] = true;
		}

		return user.getName();
	}

}
