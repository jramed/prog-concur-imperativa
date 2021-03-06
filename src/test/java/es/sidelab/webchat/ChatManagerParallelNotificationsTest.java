package es.sidelab.webchat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CompletionService;
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
import es.codeurjc.webchat.User;

public class ChatManagerParallelNotificationsTest {
	private CountDownLatch latch;

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

		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		long startTime = System.currentTimeMillis();
		latch = new CountDownLatch(numThreads-1);
		for (int i = 0; i < numThreads; i++)
		{
			final int count = i;
			completionService.submit(()->simulateUserParallelTest(count, hasUserSentReceiveMsg, chatManager, chat, numThreads));
		}

		serviceInvocation(numThreads, completionService);

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		long endTime = System.currentTimeMillis();
		int threshold = 1500;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime <= threshold);

		//Check 
		Boolean[] valuesToCheck = new Boolean[numThreads];
		Arrays.fill(valuesToCheck,true);

		assertTrue("Messages sent for users "+Arrays.asList(valuesToCheck).toString()+" , but the value is "
				+ Arrays.asList(hasUserSentReceiveMsg).toString(), Arrays.equals(hasUserSentReceiveMsg, valuesToCheck));
	}


	private void serviceInvocation(int numThreads, CompletionService<String> completionService) {
		String[] returnedValues = new String[numThreads];
		for (int i = 0; i < numThreads; ++i) {
			try {
				Future<String> f = completionService.take();
				String returnedValue = f.get();
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
	}


	private String simulateUserParallelTest(int count, Boolean[] hasUserSentReceiveMsg,
			ChatManager chatManager, Chat chat, int numThreads) throws
								InterruptedException, TimeoutException {

		TestUser user = new TestUser("user"+count) {
			public void newMessage(Chat chat, User user, String message) {
				try {
					Thread.sleep(1000);
					hasUserSentReceiveMsg[count] = true;
				} catch (InterruptedException intExcep)
				{
					intExcep.printStackTrace();
				}

				latch.countDown();
			}
		};

		chatManager.newUser(user);

		chat.addUser(user);

		if (count+1 == numThreads)
		{
			//to ensure the destination users have been created
			Thread.sleep(10);
			chat.sendMessage(user, "Message from user: "+user.getName());
			try
			{
				hasUserSentReceiveMsg[count] = latch.await(5000L,TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException e)
			{
				System.out.println("Exception waiting from count donw latch");
			}
		}


		return user.getName();
	}

}
