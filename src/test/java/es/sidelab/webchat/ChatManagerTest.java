package es.sidelab.webchat;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.CompletionService;
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
import es.codeurjc.webchat.User;
import es.codeurjc.webchat.PrintlnI;

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
			PrintlnI.initPerThread();
			//Thread.sleep(300);
		}


		for (int i = 0; i < numThreads; ++i) {
			try {
				// Crear un usuario que guarda en chatName el nombre del nuevo chat		
				Future<String[]> f = completionService.take();
				String[] returnedValue = f.get();
				System.out.println("The returned value from the Thread is: "+ Arrays.asList(returnedValue).toString());
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
		for (int i = 0; i < 4; i++) {
			assertTrue("The method 'newChat' should be invoked with 'Chat0Chat1Chat2Chat3Chat4', but the value is "
					+ chatName[i], Objects.equals(chatName[i], "Chat0Chat1Chat2Chat3Chat4"));
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
				PrintlnI.printlnI("TestUser class for user: " + chatName[count], "");

				//System.out.println("Test("+Thread.currentThread().getName()+"): TestUser class for user: " + this.name +", new chat created: "+chat.getName());
			}
		};
		
		chatManager.newUser(user);

		for (int i = 0; i < numIterations; i++) {
			// Crear un nuevo chat en el chatManager
			Chat chat = chatManager.newChat("Chat"+i, 5, TimeUnit.SECONDS);
			chat.addUser(user);
			for (User userInChat: chat.getUsers()) {
				chatCreated[i] = chat.getName();
				//System.out.println("Test("+Thread.currentThread().getName()+"): User: "+ userInChat.getName() + " in chat: " + chat.getName());
			}
		}
		return chatCreated;
		//return Thread.currentThread().getName();
		
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
		assertTrue("Notified new user '" + newUser[0] + "' is not equal than user name 'user2'",
				"user2".equals(newUser[0]));
	}
}
