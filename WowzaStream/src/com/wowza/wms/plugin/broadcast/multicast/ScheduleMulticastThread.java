package com.wowza.wms.plugin.broadcast.multicast;

public class ScheduleMulticastThread extends Thread {
	
	private ScheduleMulticast multicast = null;
	private boolean isContinue = true;
	private int interval = 250;
	
	public ScheduleMulticastThread(ScheduleMulticast multicast) {
		this.multicast = multicast;
	}
	
	public void setInterval(int interval) {
		this.interval = interval;
	}
	
	@Override
	public void run() {
		while (isContinue) {
			this.multicast.runMulticast();
			
			if (!isContinue)
				break ;
			
			try {
				sleep(interval);
			} catch (InterruptedException e) {
			}
		}
	}
	
	public void doStop() {
		isContinue = false;
	}
}
