package es.codeurjc.webchat;

import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorService;

public class CustomPair {
    private ExecutorService executor;
    private CompletionService<String> completionServices;
    
	public CustomPair(ExecutorService executor2, CompletionService<String> completionServices) {
		super();
		this.executor = executor2;
		this.completionServices = completionServices;
	}
	public ExecutorService getExecutor() {
		return executor;
	}

	public CompletionService<String> getCompletionServices() {
		return completionServices;
	}

}
