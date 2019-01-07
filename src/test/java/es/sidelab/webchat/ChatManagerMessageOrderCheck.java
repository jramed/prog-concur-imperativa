package es.sidelab.webchat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Exchanger;
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

public class ChatManagerMessageOrderCheck {

	private Exchanger<Boolean> msgInOrderExchanger;
	
	@Test
	public void messageOrderCheck() throws InterruptedException, TimeoutException
	{
		System.out.println("==============Test messageOrderCheck=====================");
		final ChatManager chatManager = new ChatManager(5);

		int numThreads = 2;

		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
		CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
		final Boolean[] hasUserSentReceiveMsgInOrder = new Boolean[numThreads];
		Arrays.fill(hasUserSentReceiveMsgInOrder, false);

		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);
		msgInOrderExchanger = new Exchanger<Boolean>();

		for (int i = 0; i < numThreads; i++)
		{
			final int count = i;
			completionService.submit(()->checkMsgOrderTest(count, hasUserSentReceiveMsgInOrder, chatManager, chat, numThreads));
		}

		serviceInvocation(numThreads, completionService);

		executor.shutdown();

		executor.awaitTermination(10, TimeUnit.SECONDS);

		Boolean[] valuesToCheck = new Boolean[numThreads];
		Arrays.fill(valuesToCheck, true);

		assertTrue("Messages sent for users "+Arrays.asList(valuesToCheck).toString()+" , but the value is "
				+ Arrays.asList(hasUserSentReceiveMsgInOrder).toString(), Arrays.equals(hasUserSentReceiveMsgInOrder, valuesToCheck));
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


	private String checkMsgOrderTest(int count, Boolean[] hasUserSentReceiveMsgInOrder, ChatManager chatManager,
			Chat chat, int numThreads) throws InterruptedException, TimeoutException {
		TestUser user = new TestUser("user"+count) {
			int previousReceivedMsg = 0;
			public void newMessage(Chat chat, User user, String message) {
				boolean isInOrder = false;
				try {
					if (previousReceivedMsg == Integer.valueOf(message)-1)
					{
						previousReceivedMsg = Integer.valueOf(message);
						isInOrder = true;
						hasUserSentReceiveMsgInOrder[count] = isInOrder;
					}
					//Simulate a delay
					Thread.sleep(500);
					msgInOrderExchanger.exchange(isInOrder,1000L, TimeUnit.MILLISECONDS);
				} catch (InterruptedException intExcep)
				{
					intExcep.printStackTrace();
				}
				catch (TimeoutException timeOutExcep)
				{
					timeOutExcep.printStackTrace();
				}
			}
		};

		chatManager.newUser(user);

		chat.addUser(user);
		//Send 5 messages just from one user.
		if (count+1 == numThreads)
		{
			//ensure that the messages are sent after all user have been created
			TimeUnit.MILLISECONDS.sleep(100);

			int maxMsgNumber = 5;
			for (int i = 1; i <= maxMsgNumber; i++)
			{
				chat.sendMessage(user, String.valueOf(i));
				//This could be also done with a queue, producer/consumer schema 
				Boolean result = msgInOrderExchanger.exchange(null,1000L, TimeUnit.MILLISECONDS);
				if ( false == result )
				{
					hasUserSentReceiveMsgInOrder[count] = false;
					return user.getName();
				} 
			}
			hasUserSentReceiveMsgInOrder[count] = true;
		}

		return user.getName();
	}

}
