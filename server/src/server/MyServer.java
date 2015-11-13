package server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MyServer {

	public static void main(String[] args) {		
		
		final HeartRate James = new HeartRate();
		
		Runnable Timer = new Runnable() {
			public void run(){
				James.sendHeartRate();
			}
		};
		
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
		executor.scheduleAtFixedRate(Timer, 0, 200, TimeUnit.MILLISECONDS);
	}
}
