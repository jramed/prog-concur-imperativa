package es.sidelab.webchat;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
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
			//Thread.sleep(300);
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
				//synchronized (chatName) {
					if ( null != chatName[count]) {
						chatName[count] = chatName[count]+chat.getName();
					}
					else {
						chatName[count] = chat.getName();
					}
				//}
				PrintlnI.printlnI("TestUser class for user: " + this.name +", new chat created: "+chat.getName() +" for thread number: " +count, "");
				PrintlnI.printlnI("TestUser class for user: " + this.name + chatName[count], "");

				//System.out.println("Test("+Thread.currentThread().getName()+"): TestUser class for user: " + this.name +", new chat created: "+chat.getName());
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

			//for (User userInChat: chat.getUsers()) {
				//System.out.println("Test("+Thread.currentThread().getName()+"): User: "+ userInChat.getName() + " in chat: " + chat.getName());
			//}
		}
		return chatCreated;
		//return Thread.currentThread().getName();

	}



	@Test
	public void newUserInChat() throws InterruptedException, TimeoutException {

		Thread.sleep(1000);
		System.out.println("==============NEW test=====================");
		PrintlnI.initPerThread();
		ChatManager chatManager = new ChatManager(5);

		final String[] newUser = new String[1];

		TestUser user1 = new TestUser("user1") {
			@Override
			public void newUserInChat(Chat chat, User user) {
				PrintlnI.printlnI("Received notification from user: "+user.getName(),"");
				//System.out.println("Received notification from user: "+user.getName());
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

		//Set<String> valuesToCheck = new HashSet<>();
		//for (int i = 0; i < numThreads; i++)
		//{
		//	valuesToCheck.add("user"+i);
		//}

		//for (int i = 0; i < numThreads; i++) {
		//	assertTrue("The method 'newChat' should be invoked with "+Arrays.asList(valuesToCheck).toString()+" , but the value is "
		//			+ returnedValues[i], valuesToCheck.contains(returnedValues[i]));
		//}
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
		Thread.sleep(10);
		if (count == 3)
		{
			chat.sendMessage(user, "Message from user: "+user.getName());
			try
			{
				latch.await(3000L,TimeUnit.MILLISECONDS);
				hasUserSentReceiveMsg[count] = true;
			}
			catch (InterruptedException e)
			{
				PrintlnI.printlnI("Exception waiting from count donw latch", "");
			}
		}


		return user.getName();
	}
}
