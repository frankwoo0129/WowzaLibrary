package com.wowza.wms.plugin.broadcast;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.json.simple.JSONObject;

import com.wowza.wms.application.IApplication;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.http.HTTPProvider2Base;
import com.wowza.wms.http.IHTTPRequest;
import com.wowza.wms.http.IHTTPResponse;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.server.IServer;
import com.wowza.wms.server.IServerNotify;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.vhost.VHostSingleton;

public class ScheduleController extends HTTPProvider2Base implements IServerNotify {
	
	private static boolean onCache = false;
	private static ScheduleCache cache = null;
	private static Timer timer = null;
	private static IVHost vhost = null;
	private static IApplication app = null;
	private static IApplicationInstance appInstance = null;
	private static Map<Integer, ScheduleItem> streamMap = new HashMap<Integer, ScheduleItem>();
	private static ScheduleEPGList epglist = null;
	private final static WMSLogger log = WMSLoggerFactory.getLogger(ScheduleController.class);
	private final static String link = "http://api.nsbg.foxconn.com/0/channels/broadcast";

	@SuppressWarnings("unchecked")
	@Override
	public void onHTTPRequest(IVHost vhost, IHTTPRequest req, IHTTPResponse resp) {
		// TODO Auto-generated method stub
		log.info("onHTTPRequest");
		
		if (!this.doHTTPAuthentication(vhost, req, resp))
			return ;
		
		String commit = req.getParameter("commit");
		String channelId = req.getParameter("channelid");
		JSONObject json = new JSONObject();
		TimerTask task = null;
		if ("start".equals(commit)) {
			if (channelId == null) {
				task = new TimerTask(){
					public void run() {
						cache.doCache(ScheduleCache.CACHE_TIME_INTERVAL);
						loadSchedule(app.getAppInstance("_definst_"));
					}
				};
				json.put("msg", "starting all schedule");
			} else {
				final int id = Integer.valueOf(channelId);
				task = new TimerTask(){
					public void run() {
						cache.doCache(ScheduleCache.CACHE_TIME_INTERVAL, id);
						loadSchedule(app.getAppInstance("_definst_"), id);
					}
				};
				json.put("msg", "starting schedule: channel=" + id);
				json.put("channelid", id);
			}
		} else if ("stop".equals(commit)) {
			if (channelId == null) {
				unloadSchedule();
				json.put("msg", "stopping all schedule");
			} else {
				int id = Integer.valueOf(channelId);
				unloadSchedule(id);
				json.put("msg", "stopping schedule: channel=" + id);
				json.put("channelid", id);
			}
		} else if ("reload".equals(commit)) {
			if (channelId == null) {
				task = new TimerTask(){
					public void run() {
						cache.doCache(ScheduleCache.CACHE_TIME_INTERVAL);
						reloadSchedule();
					}
				};
				json.put("msg", "Reloading all schedule");
			} else {
				final int id = Integer.valueOf(channelId);
				task = new TimerTask(){
					public void run() {
						cache.doCache(ScheduleCache.CACHE_TIME_INTERVAL, id);
						reloadSchedule(id);
					}
				};
				json.put("msg", "Reloading schedule: channel=" + id);
				json.put("channelid", id);
			}
		} else {
			json.put("msg", "No commit");
		}
		
		if (task != null) {
			if (timer != null)
				timer = new Timer();
			timer.schedule(task, 0);
		}
		
		try	{
			resp.setHeader("Content-Type", "text/html");
			OutputStream out = resp.getOutputStream();
			byte[] outBytes = json.toString().getBytes();
			out.write(outBytes);
			out.close();
		} catch (Exception e) {
			log.error("onHTTPRequest: " + e.getClass().getName());
		}
	}

	@Override
	public void onServerCreate(IServer server) {
		log.info("onServerCreate");
	}

	@Override
	public void onServerInit(IServer server) {
		log.info("onServerInit");
		
		try {
			vhost = VHostSingleton.getInstance(server.getProperties().getPropertyStr("PublishToVHost", "_defaultVHost_"));
		} catch (Exception evhost) {
			log.error("ScheduleController: Failed to get Vhost can not run.");
			return;
		}
		
		try {
			app = vhost.getApplication(server.getProperties().getPropertyStr("PublishToApplication", "live"));
		} catch (Exception eapp) {
			log.error("ScheduleController: Failed to get Application can not run.");
			return;
		}
		
		if ( vhost == null || app == null ) {
			log.warn("ScheduleController: VHost or Application failed, not running.");
			return;
		}
		try {
			appInstance = app.getAppInstance("_definst_");
		} catch (Exception eappInstance) {
			log.error("ScheduleController: Failed to get ApplicationInstance can not run.");
			return ;
		}

		// Set Cache init		
		onCache = appInstance.getProperties().getPropertyBoolean("ScheduleOnCache", onCache);
		
		if (onCache) {
			log.info("========On Cache========");
			cache = new ScheduleCache(appInstance, link);
			cache.init();
		} else {
			log.info("========NO Cache========");
		}
		
		epglist = new ScheduleEPGList(link);
		this.loadSchedule(appInstance);
				
		// Set Cache Task
		if (onCache) {
			doCache();
		}
		
	}
	
	private void doCache() {
		if (timer == null)
			timer = new Timer();
		TimerTask doCacheTask = new TimerTask(){
			@Override
			public void run() {
				cache.doCache(ScheduleCache.CACHE_TIME_INTERVAL);
			}
		};
		timer.scheduleAtFixedRate(doCacheTask, 0, ScheduleCache.CACHE_TIME_TEST);
		
		TimerTask doCleanCacheTask = new TimerTask() {
			@Override
			public void run() {
				cache.doCleanCache();
			}
		};
		timer.scheduleAtFixedRate(doCleanCacheTask, 0, ScheduleCache.CACHE_TIME_CLEAN);
	}

	@Override
	public void onServerShutdownComplete(IServer server) {
		log.info("onServerShutdownComplete");
	}

	@Override
	public void onServerShutdownStart(IServer server) {
		log.info("onServerShutdownStart");
		if (timer != null)
			timer.cancel();
		timer = null;
		unloadSchedule();
		streamMap = null;
		app = null;
		vhost = null;
	}
	
	public void loadSchedule(IApplicationInstance appInstance) {
		log.info("ScheduleController.loadSchedule");
		String ret = null;

		try {
			epglist.init();
		} catch (Exception e) {
			log.error(e.getMessage());
			return ;
		}
		
		Iterator<Integer> iter = epglist.getChannelIdSet().iterator();
		while (iter.hasNext()) {
			try {
				openScheduleItem(appInstance, iter.next());
			} catch (Exception e) {
				ret = "ScheduleController.loadSchedule: Error from loadSchedule is '" + e.getClass().getName() + "'";
				e.printStackTrace();
				log.error(ret);
			}
		}
	}
	
	public void loadSchedule(IApplicationInstance appInstance, int channelId) {
		log.info("ScheduleController.loadSchedule, channelId=" + channelId);
		String ret = null;		
		
		try {
			epglist.init();
		} catch (Exception e) {
			log.error(e.getMessage());
			return ;
		}
		
		try {
			openScheduleItem(appInstance, channelId);
		} catch (Exception e) {
			ret = "ScheduleController.loadSchedule: Error from loadSchedule is '" + e.getClass().getName() + "'";
			e.printStackTrace();
			log.error(ret);
		}
	}
	
	public void unloadSchedule() {
		log.info("ScheduleController.unloadSchedule");
		for (Map.Entry<Integer, ScheduleItem> entry : streamMap.entrySet()) {
			closeScheduleItem(entry.getKey());
		}
		streamMap.clear();
	}
	
	public void unloadSchedule(int channelId) {
		log.info("ScheduleController.unloadSchedule, channelId=" + channelId);
		closeScheduleItem(channelId);
		streamMap.remove(channelId);
	}
	
	public void reloadSchedule() {
		log.info("ScheduleController.reloadSchedule");
		for (Map.Entry<Integer, ScheduleItem> entry : streamMap.entrySet()) {
			reloadSchedule(entry.getKey());
		}
	}
	
	public void reloadSchedule(int channelId) {
		log.info("ScheduleController.reloadSchedule, channelId=" + channelId);
		try {
			ScheduleItem item = streamMap.get(channelId);
			if (item != null) {
				item.loadScheduleItem(null);
			}
		} catch(Exception e) {
			log.error("ScheduleController.reloadSchedule: Error from reloadSchedule Stream" + channelId + " is '" + e.getClass().getName() + "'");
		}
	}
	
	private void openScheduleItem(IApplicationInstance appInstance, int channelId) {
		log.debug("ScheduleController.openScheduleItem: channelId=" + channelId);
		closeScheduleItem(channelId);
		ScheduleEPG epg = epglist.getScheduleEPG(channelId);
		if (epg == null) {
			log.info("ScheduleController.openScheduleItem: No EPG in EPGList, channelid=" + channelId);
		} else {
			ScheduleItem newItem = new ScheduleItem(appInstance, epg);
			streamMap.put(channelId, newItem);
			newItem.open();
			log.info("ScheduleController.openScheduleItem: channelId=" + channelId);
		}
	}
	
	private void closeScheduleItem(int channelId) {
		log.debug("ScheduleController.closeScheduleItem: channelId=" + channelId);
		ScheduleItem oldItem = streamMap.get(channelId);
		if (oldItem != null) {
			oldItem.close();
			log.info("ScheduleController.closeScheduleItem: channelId=" + channelId);
			oldItem = null;
		} else {
			log.info("ScheduleController.closeScheduleItem: No scheduleItem, channelid=" + channelId);
		}
	}
	
}
