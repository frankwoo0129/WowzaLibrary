package com.wowza.wms.plugin.broadcast;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.json.simple.JSONArray;
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
import com.wowza.wms.stream.publish.Stream;
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
	private final static WMSLogger log = WMSLoggerFactory.getLogger(ScheduleController.class);
	private final static String link = "http://api.nsbg.foxconn.com/0/channels/broadcast";

	@SuppressWarnings("unchecked")
	@Override
	public void onHTTPRequest(IVHost vhost, IHTTPRequest req, IHTTPResponse resp) {
		// TODO Auto-generated method stub
		log.info("onHTTPRequest");
		
		String commit = req.getParameter("commit");
		String channelId = req.getParameter("channelId");
		JSONObject json = new JSONObject();
		
		if ("start".equals(commit)) {
			if (channelId == null) {
				loadSchedule(app.getAppInstance("_definst_"));
				json.put("msg", "starting all schedule");
			} else {
				int id = Integer.valueOf(channelId);
				loadSchedule(app.getAppInstance("_definst_"), id);
				json.put("msg", "starting schedule: channel=" + id);
			}
		} else if ("stop".equals(commit)) {
			if (channelId == null) {
				unloadSchedule();
				json.put("msg", "stopping all schedule");
			} else {
				int id = Integer.valueOf(channelId);
				closeScheduleItem(id);
				json.put("msg", "stopping schedule: channel=" + id);
			}
		} else if ("reload".equals(commit)) {
			reloadSchedule();
			json.put("msg", "Reloading all schedule");
		} else {
			json.put("msg", "No commit");
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
		
		
//		File cacheDir = new File(app.getAppInstance("_definst_").getStreamStorageDir(), ScheduleCache.CACHE_DIRECTORY);
//		if (!cacheDir.exists())
//			cacheDir.mkdir();
//		File[] fs = cacheDir.listFiles();
//		for (File f : fs) {
//			if(f.delete())
//				log.info("Delete file successfully: " + f.getName());
//		}
//				
//		Connection connection = null; 
//		try {
//			Class.forName("org.sqlite.JDBC");
//			connection = DriverManager.getConnection("jdbc:sqlite:" + Paths.get(app.getAppInstance("_definst_").getStreamStorageDir(), ScheduleCache.CACHE_DIRECTORY).resolve("content.db").toFile());
//			Statement statement = connection.createStatement(); 
//			statement.setQueryTimeout(30);
//			statement.executeUpdate("drop table if exists test");
//			statement.executeUpdate("create table test (channelid integer, name string, starttime timestamp)");
//			
//			Calendar c = Calendar.getInstance();
//			PreparedStatement pstatement = null;
//			
//			c.set(2000, 0, 1, 0, 0, 0);
//			pstatement = connection.prepareStatement("insert into test values(1, 'aaa', ?)");
//			pstatement.setTimestamp(1, new Timestamp(c.getTimeInMillis()));
//			pstatement.executeUpdate();
//			
//			c.set(2000, 0, 1, 1, 0, 0);
//			pstatement = connection.prepareStatement("insert into test values(1, 'bbb', ?)");
//			pstatement.setTimestamp(1, new Timestamp(c.getTimeInMillis()));
//			pstatement.executeUpdate();
//			
//			c.set(2000, 0, 1, 2, 0, 0);
//			pstatement = connection.prepareStatement("insert into test values(1, 'aaa', ?)");
//			pstatement.setTimestamp(1, new Timestamp(c.getTimeInMillis()));
//			pstatement.executeUpdate();
//			
//			c.set(2000, 0, 1, 3, 0, 0);
//			pstatement = connection.prepareStatement("insert into test values(1, 'ccc', ?)");
//			pstatement.setTimestamp(1, new Timestamp(c.getTimeInMillis()));
//			pstatement.executeUpdate();
//			
//			c.set(2000, 0, 1, 2, 0, 0);
//			pstatement = connection.prepareStatement("select * from test where name not in (select name from test where starttime >= ?)");
//			pstatement.setTimestamp(1, new Timestamp(c.getTimeInMillis()));
//			ResultSet rs = pstatement.executeQuery();
//			
//			c.set(2000, 0, 1, 2, 0, 0);
//			pstatement = connection.prepareStatement("delete from test where name not in (select name from test where starttime >= ?)");
//			pstatement.setTimestamp(1, new Timestamp(c.getTimeInMillis()));
//			pstatement.executeUpdate();
//			
//			rs = statement.executeQuery("select * from test");
//			while(rs.next()) {
//				log.info("channelid = " + rs.getInt("channelid"));
//				log.info("name = " + rs.getString("name"));
//				log.info("starttime = " + rs.getTimestamp("starttime"));
//		    }
//			log.info("GOOD");
//		} catch (SQLException e) {
//			e.printStackTrace();
//			log.warn("ERROR:" + e.getMessage());
//		} catch (ClassNotFoundException e) {
//			e.printStackTrace();
//			log.warn("ERROR:" + e.getMessage());
//		} catch (Exception e) {
//			e.printStackTrace();
//			log.warn("ERROR:" + e.getMessage());
//		} finally {
//			try {
//				if (connection != null)
//					connection.close();
//			} catch (SQLException e) {
//				e.printStackTrace();
//			}
//		}
		
		onCache = appInstance.getProperties().getPropertyBoolean("ScheduleOnCache", onCache);
		
		if (onCache) {
			log.info("========On Cache========");
			cache = new ScheduleCache(appInstance, link);
			cache.init();
		} else {
			log.info("========NO Cache========");
		}
		
		loadSchedule(appInstance);
		
		if (onCache) {
			if (timer == null)
				timer = new Timer();
			TimerTask task = new TimerTask(){
				@Override
				public void run() {
					cache.doCache(ScheduleCache.CACHE_TIME_INTERVAL);
				}
			};
			timer.scheduleAtFixedRate(task, 0, ScheduleCache.CACHE_TIME_TEST);
		}
		
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
			JSONArray epg = ScheduleUtils.getJSONArray(link);
			if (epg == null) {
				ret = "ScheduleController.loadSchedule: Connection Failed to epg Server";
				log.error(ret);
				return ;
			}
			if (epg.size() == 0) {
				ret = "ScheduleController.loadSchedule: No channel to publish";
				log.warn(ret);
				return ;
			}
			for (Object obj : epg) {
				int channelId = ((Long) ((JSONObject) obj).get("channelid")).intValue();
				openScheduleItem(appInstance, channelId, obj);
			}
		} catch (Exception e) {
			ret = "ScheduleController.loadSchedule: Error from loadSchedule is '" + e.getClass().getName() + "'";
			log.error(ret);
		}
	}
	
	public void loadSchedule(IApplicationInstance appInstance, int channelId) {
		log.info("ScheduleController.loadSchedule, channelId=" + channelId);
		String ret = null;		
		try {
			JSONArray epg = ScheduleUtils.getJSONArray(link);
			if (epg == null) {
				ret = "ScheduleController.loadSchedule: Connection Failed to epg Server";
				log.error(ret);
				return ;
			}
			if (epg.size() == 0) {
				ret = "ScheduleController.loadSchedule: No channel to publish";
				log.warn(ret);
				return ;
			}
			for (Object obj : epg) {
				if(channelId == ((Long) ((JSONObject) obj).get("channelid")).intValue()) {
					openScheduleItem(appInstance, channelId, obj);
					break;
				} else
					continue;
			}
		} catch (Exception e) {
			ret = "ScheduleController.loadSchedule: " + e.getClass().getName();
			log.error(ret);
		}
	}
	
	
	public void unloadSchedule() {
		log.info("ScheduleController.unloadSchedule");
		if (streamMap != null) {
			for (Map.Entry<Integer, ScheduleItem> entry : streamMap.entrySet()) {
				try {
					ScheduleItem item = entry.getValue();
					if (item != null)
						item.close();
					item = null;
					log.info("Unload Schedule Stream: " + entry.getKey());
				} catch(Exception e) {
					log.error("ScheduleController.unloadSchedule: Error from unloadSchedule Stream" + entry.getValue().getChannelId() + " is '" + e.getClass().getName() + "'");
				}
			}
			streamMap.clear();
		}
	}
	
	public void reloadSchedule() {
		log.info("ScheduleController.reloadSchedule");
		for (Map.Entry<Integer, ScheduleItem> entry : streamMap.entrySet()) {
			try {
				ScheduleItem item = entry.getValue();
				if (item != null) {
					item.loadScheduleItem(null);
					log.info("Reload Schedule Stream: " + entry.getKey());
				}
			} catch(Exception e) {
				log.error("ScheduleController.reloadSchedule: Error from reloadSchedule Stream" + entry.getValue().getChannelId() + " is '" + e.getClass().getName() + "'");
			}
		}
	}
	
	private void openScheduleItem(IApplicationInstance appInstance, int channelId, Object obj) {
		String ret = null;
		log.debug("ScheduleController.openScheduleItem: channelId=" + channelId);
		String streamName = "stream" + channelId;
		closeScheduleItem(channelId);
		Stream stream = Stream.createInstance(appInstance, streamName);
		String epgListLink = (String) ((JSONObject) obj).get("epg");
		if (epgListLink == null) {
			ret = "ScheduleController: No epg Server link";
			log.warn(ret);
			return ;
		}
		ScheduleItem newItem = new ScheduleItem(appInstance, stream, channelId, epgListLink);
		Boolean passThruMetaData = appInstance.getProperties().getPropertyBoolean("PassthruMetaData", true);
		appInstance.getProperties().setProperty(streamName, stream);
		stream.setSendOnMetadata(passThruMetaData);
		streamMap.put(channelId, newItem);
		newItem.open();
		log.info("ScheduleController.openScheduleItem: channelId=" + channelId);
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
		streamMap.remove(channelId);
	}
	
}
