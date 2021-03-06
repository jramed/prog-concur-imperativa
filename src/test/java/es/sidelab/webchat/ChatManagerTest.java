package es.sidelab.webchat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CompletionService;
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

public class ChatManagerTest {

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
		}


		String[][] returnedValues = new String[numThreads][5];
		for (int i = 0; i < numThreads; ++i) {
			try {
				Future<String[]> f = completionService.take();
				String[] returnedValue = f.get();
				returnedValues[i] = returnedValue;
				System.out.println("The returned value from the Thread is: "+ Arrays.asList(returnedValues[i]).toString());
			} catch (ConcurrentModificationException e) {
				System.out.println("Exception: " + e.toString());
				fail("Exception received" + e.toString());
			} catch (InterruptedException e) {
				System.out.println("Exception: " + e.toString());
				fail("Exception received" + e.toString());
			} catch (ExecutionException e) {
				System.out.println("Exception: " + e.toString());
				e.printStackTrace();
				fail("Exception received" + e.toString());
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

		System.out.println("==============NEW test newUserInChat=====================");
		ChatManager chatManager = new ChatManager(5);

		final String[] newUser = new String[1];

		TestUser user1 = new TestUser("user1") {
			@Override
			public void newUserInChat(Chat chat, User user) {
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
		assertTrue("Notified new user '" + newUser[0] + "' is not equal than user name 'user2'",
				"user2".equals(newUser[0]));
	}

}
