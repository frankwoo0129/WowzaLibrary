package com.wowza.wms.plugin.collection.serverlistener;

import com.wowza.wms.application.IApplication;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.plugin.collection.module.Schedule;
import com.wowza.wms.server.IServer;
import com.wowza.wms.server.IServerNotify;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.vhost.VHostSingleton;

public class BroadCastServerListener implements IServerNotify {

	private WMSLogger log = WMSLoggerFactory.getLogger(null);
	private Schedule schedule;
	
	@Override
	public void onServerCreate(IServer server) {
		log.info("onServerCreate");
	}

	@Override
	public void onServerInit(IServer server) {
		log.info("onServerInit");
		schedule = new Schedule();
		
		IVHost vhost = null;
		IApplication app = null;

		try {
			vhost = VHostSingleton.getInstance(server.getProperties().getPropertyStr("PublishToVHost", "_defaultVHost_"));
		} catch (Exception evhost) {
			log.error("ServerListenerStreamPublisher: Failed to get Vhost can not run.");
			return;
		}
		
		try {
			app = vhost.getApplication(server.getProperties().getPropertyStr("PublishToApplication", "live"));
		} catch (Exception eapp) {
			log.error("ServerListenerStreamPublisher: Failed to get Application can not run.");
			return;
		}
		
		// Belt and Braces check for VHost and App
		if ( vhost == null || app == null ) {
			log.warn("ServerListenerStreamPublisher: VHost or Application failed, not running.");
			return;
		}
		schedule.loadSchedule(app.getAppInstance("_definst_"));
	}

	@Override
	public void onServerShutdownComplete(IServer server) {
		log.info("onServerShutdownComplete");
		if (schedule != null) {
			schedule.stopSchedule();
		}
	}

	@Override
	public void onServerShutdownStart(IServer server) {
		log.info("onServerShutdownStart");
	}
	
}