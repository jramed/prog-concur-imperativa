package es.codeurjc.webchat;

import java.util.ArrayList;

public class PrintlnI {
	private static final ArrayList<String> spaces = new ArrayList<String>();
	
	public static void initPerThread() 
	{
		if (spaces.size() == 0) {
			spaces.add("");
		} else {
			spaces.add(spaces.get(spaces.size() - 1) + "        ");
		}
	}
	
	public static void reset() 
	{
		for (int i = 0; i<spaces.size(); i++)
		{
			spaces.remove(i);
		}
	}

	public static void printlnI(String text, String threadName)
	{
		if (threadName.isEmpty()) {
			threadName = Thread.currentThread().getName();
		}
		String parts[] = threadName.split("-");
		int threadNum = 1;
		if (parts.length >= 3)
		{
			threadNum = Integer.parseInt(parts[3]);
		} 
		sleepRandom(10);
		System.out.println(spaces.get(threadNum-1) + threadName + ": " + text);
		sleepRandom(10);
	}

	public static void sleepRandom(long millis) {
		sleep((long) (Math.random() * millis));
	}

	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}
