package es.sidelab.webchat;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
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
import es.codeurjc.webchat.PrintlnI;

public class ChatManagerChatCreationTimeOutTest {

	//Cannot be used (expected = TimeoutException.class)
	//because the timeoutException is encapsulated inside a ExecutionException
	@Test
	public void chatCreationTimeoutAfter3Seconds() throws InterruptedException,
		TimeoutException, ExecutionException {

		System.out.println("==============NEW test timeoutWaitingForChatCreation=====================");
		final ChatManager chatManager = new ChatManager(3);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

		PrintlnI.initPerThread();

		long startTime = System.currentTimeMillis();
		//Create a thread per user to test concurrent chat creation.

		for (int i = 0; i < numThreads; ++i) {
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->checkTimeoutWaitingForChatCreation(count, chatManager, numThreads));
		}

		serviceInvocation(numThreads, completionService);

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		long endTime = System.currentTimeMillis();
		long difference = endTime-startTime;
		PrintlnI.printlnI("startTime: "+startTime+ " endTime: "+endTime+" difference: "+ difference ,"");
		int threshold = 3500;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime < threshold);

		PrintlnI.reset();
	}

	private void serviceInvocation(int numThreads, CompletionService<String> completionService) {
		String[] returnedValues = new String[numThreads];
		for (int i = 0; i < numThreads; ++i) {
			try {
				Future<String> f = completionService.take();
				returnedValues[i] = f.get();
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
				assertThat(e.getMessage(), containsString("java.util.concurrent.TimeoutException: Timeout waiting for chat creation. 'Time: 3 Unit: SECONDS'"));
			}
		}
	}

	private String checkTimeoutWaitingForChatCreation(int count, ChatManager chatManager,
			int numThreads) throws InterruptedException, TimeoutException {
		TestUser user = new TestUser("user"+count);

		chatManager.newChat("Chat"+count, 3, TimeUnit.SECONDS);

		return user.getName();
	}

	@Test
	public void chatCreationAfterWaitSeveralSeconds() throws InterruptedException, TimeoutException, ExecutionException {

		System.out.println("==============NEW test timeoutWaitingForChatCreation=====================");
		final ChatManager chatManager = new ChatManager(2);

		int numThreads = 4;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

		PrintlnI.initPerThread();

		long startTime = System.currentTimeMillis();
		//Create a thread per user to test concurrent chat creation.

		for (int i = 0; i < numThreads; ++i) {
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->checkChatCreationAfterWaiting(count, chatManager, numThreads));
		}

		String[] returnedValues = new String[numThreads];
		for (int i = 0; i < numThreads; ++i) {
			try {
				Future<String> f = completionService.take();
				returnedValues[i] = f.get();
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

		long endTime = System.currentTimeMillis();
		long difference = endTime-startTime;
		PrintlnI.printlnI("startTime: "+startTime+ " endTime: "+endTime+" difference: "+ difference ,"");
		int threshold = 7000;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime < threshold);

		PrintlnI.reset();
	}

	private String checkChatCreationAfterWaiting(int count, ChatManager chatManager,
			int numThreads) throws InterruptedException, TimeoutException {
		TestUser user = new TestUser("user"+count);

		Thread.sleep(count*500);
		Chat chat = chatManager.newChat("Chat"+count, 3, TimeUnit.SECONDS);
		Thread.sleep((count+1)*1000);
		chat.close();

		return user.getName();
	}

	@Test
	public void chatSimultaneousCreationRemoval() throws InterruptedException, TimeoutException, ExecutionException {

		System.out.println("==============NEW test chatCreationRemoval=====================");
		final ChatManager chatManager = new ChatManager(1);

		int numThreads = 10;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

		PrintlnI.initPerThread();

		long startTime = System.currentTimeMillis();
		//Create a thread per user to test concurrent chat creation.

		for (int i = 0; i < numThreads; ++i) {
			final int count = i;
			PrintlnI.initPerThread();
			completionService.submit(()->checkSimultaneousChatCreationRemoval(count, chatManager, numThreads));
		}

		serviceInvocation(numThreads, completionService);

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		long endTime = System.currentTimeMillis();
		long difference = endTime-startTime;
		PrintlnI.printlnI("startTime: "+startTime+ " endTime: "+endTime+" difference: "+ difference ,"");
		int threshold = 7000;
		assertTrue("The elapse time between end time "+endTime+" and start time "+startTime+ " is bigger than "+threshold, endTime-startTime < threshold);

		PrintlnI.reset();
	}

	private String checkSimultaneousChatCreationRemoval(int count, ChatManager chatManager,
			int numThreads) throws InterruptedException, TimeoutException {
		TestUser user = new TestUser("user"+count);
		TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(0, 500 + 1));
		Chat chat = chatManager.newChat("Chat"+count, 3, TimeUnit.SECONDS);
		Thread.sleep((long) (Math.random() * 800));
		chat.close();

		return user.getName();
	}

}
