package com.wowza.wms.plugin.multicastpublish;

public class MulticastPublishWorkerThread extends Thread
{
	ModuleMulticastPublish multicastPublishModule = null;
	boolean running = true;
	Object lock = new Object();
	int interval = 250;
	
	public MulticastPublishWorkerThread(ModuleMulticastPublish multicastPublishModule)
	{
		this.multicastPublishModule = multicastPublishModule;
	}
	
	public void doStop()
	{
		synchronized(lock)
		{
			running = false;
		}
	}

	public int getInterval()
	{
		return interval;
	}

	public void setInterval(int interval)
	{
		this.interval = interval;
	}

	public void run()
	{
		while(true)
		{
			this.multicastPublishModule.workerRun();
			
			synchronized(lock)
			{
				if (!running)
					break;
			}
			
			try
			{
				sleep(interval);
			}
			catch(Exception e)
			{
			}
			
			synchronized(lock)
			{
				if (!running)
					break;
			}
		}
	}

}
