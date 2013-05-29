package com.wowza.wms.plugin.collection.module;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.stream.publish.IStreamActionNotify;
import com.wowza.wms.stream.publish.Playlist;
import com.wowza.wms.stream.publish.PlaylistItem;
import com.wowza.wms.stream.publish.Stream;

public class Schedule {
	
	private WMSLogger log = WMSLoggerFactory.getLogger(null);
	private static String link = "http://api.nsbg.foxconn.com/0/channels/broadcast";
	private static int count = 5;
	private Map<String, ScheduleItem> streamMap= new HashMap<String, ScheduleItem>();
	
	private class ScheduleItem implements IStreamActionNotify {
		
		private Timer mTimer;
		private TimerTask mTask;
		private Stream stream;
		private Playlist playlist;
		private int channelId;
		private String epglink;
		private Date startTime;
		
		public ScheduleItem(Stream stream, int channelId, String epglink) {
			if (stream == null)
				throw new NullPointerException("ScheduleItem: stream is NULL");
			this.stream = stream;
			this.channelId = channelId;
			this.epglink = epglink;
			this.mTimer = new Timer();
		}
		
		@Override
		public void onPlaylistItemStart(Stream stream, PlaylistItem playlistItem) {
			if (playlistItem.getIndex() == (stream.getPlaylist().size() - 1)) {
				loadScheduleItem(playlist.getName());
			}
			
			try {
				String name = stream.getCurrentItem().getName();
				log.info("Schedule PlayList Item Start: " + name);
			} catch(Exception ex) {
				log.error("Schedule Get Item error: " + ex.getMessage());
			}
		}

		@Override
		public void onPlaylistItemStop(Stream stream, PlaylistItem playlistItem) {
			log.info("Schedule PlayList Item Stop: " + playlistItem.getName());
		}
		
		public void loadScheduleItem(String epgId) {
			String ret = null;
			JSONObject epgList = null;
			
			if (epgId == null)
				epgList = getJSONObject(epglink + "?count=" + count);
			else
				epgList = getJSONObject(epglink + "?count=" + count + "&epgid=" + epgId);
			
			if (epgList == null) {
				ret = "ScheduleItem: No connecction to epg Server, channelid: " + channelId;
				log.error(ret);
				return ;
			}
			JSONArray epgProgram = (JSONArray) epgList.get("Programs");
			if (epgProgram == null || epgProgram.size() == 0) {
				ret = "ScheduleItem: No epglist on Server";
				log.warn(ret);
				return ;
			}
						
			playlist = new Playlist((String) ((JSONObject) epgProgram.get(epgProgram.size()-1)).get("epgId"));
			playlist.setRepeat(false);
						
			String scheduled = (String) ((JSONObject) epgProgram.get(0)).get("startTimestamp");
			SimpleDateFormat parser = new SimpleDateFormat("yyyyMMddHHmmss");
			Date systemTime = null;
			try {
				startTime = parser.parse(scheduled);
				systemTime = parser.parse((String) epgList.get("SystemDateTime"));
			} catch (Exception e2) {
				ret = "ScheduleItem: Parsing time failed.";
				log.error(ret);
				return ;
			}
			
			long sub = (systemTime.getTime() - startTime.getTime())/1000;
			String src = (String) ((JSONObject) epgProgram.get(0)).get("uri");
			File srcfile = new File(src);
			if (!srcfile.exists()) {
				log.error("ScheduleItem: File is Not Found: " + src);
				return ;
			}
			StringBuilder sb = new StringBuilder();
			sb.append(src.substring(0, src.lastIndexOf('/')+1)).append("mp4:").append(src.substring(src.lastIndexOf('/')+1, src.length()));
			if (sub > 0 && epgId == null) {
				playlist.addItem(sb.toString(), Long.valueOf(sub).intValue(), -1);
			} else {
				playlist.addItem(sb.toString(), 0, -1);
			}
			
			for (int i = 1; i < epgProgram.size() - 1; i++) {
				src = (String) ((JSONObject) epgProgram.get(i)).get("uri");
				srcfile = new File(src);
				if (!srcfile.exists()) {
					log.error("ScheduleItem: File is Not Found: " + src);
					return ;
				}
				sb = new StringBuilder();
				sb.append(src.substring(0, src.lastIndexOf('/')+1)).append("mp4:").append(src.substring(src.lastIndexOf('/')+1, src.length()));
				playlist.addItem(sb.toString(), 0, -1);
			}
			
			mTask = new TimerTask() {
				public void run() {
					if(playlist.open(stream))
						log.info("ScheduledItem: '" + stream.getName() + "' is now live");
					else {
						log.warn("ScheduledItem: '" + stream.getName() + "' is NOT live");
						stream.removeListener(ScheduleItem.this);
						stop();
					}
				}
			};
			
			if (mTimer == null)
				mTimer = new Timer();
			mTimer.schedule(mTask, startTime);
			log.info("ScheduledItem: " + stream.getName() + " for: " + scheduled);
		}
		
		public void start() {
			stream.addListener(this);
			this.loadScheduleItem(null);
			
			log.info("scheduled stream: " + stream.getName() +
					" for: " + startTime.toString());
		}
		
		public void stop() {
			stream.removeListener(this);
			if (mTimer != null) {
				mTimer.cancel();
				mTimer = null;
				log.info("cancelled stream: " + stream.getName() +
						" for: " + startTime.toString());
			}
			stream.close();
		}
	}
	
	public void loadSchedule(IApplicationInstance appInstance) {
		log.info("Schedule: loadSchedule");
		String ret = null;
		
		try {
			JSONArray epg = getJSONArray(link);
			if (epg == null) {
				ret = "Schedule: Connection Failed to epg Server";
				log.error(ret);
				return ;
			}
			if (epg.size() == 0) {
				ret = "Schedule: No channel to publish";
				log.warn(ret);
				return ;
			}
			for (Object obj : epg) {
				int channelId = ((Long) ((JSONObject) obj).get("channelid")).intValue();
				log.info("Schedule: Stream channelid is " + channelId);
				
				String streamName = "stream" + channelId;
				Stream stream = Stream.createInstance(appInstance, streamName);
				String epgListLink = (String) ((JSONObject) obj).get("epg");
				if (epgListLink == null) {
					ret = "Schedule: No epg Server link";
					log.warn(ret);
					return ;
				}
				log.info("starting scheduled: channelid " + channelId);
				ScheduleItem item = new ScheduleItem(stream, channelId, epgListLink);
				Boolean passThruMetaData = appInstance.getProperties().getPropertyBoolean("PassthruMetaData", true);
				appInstance.getProperties().setProperty(streamName, stream);
				stream.setSendOnMetadata(passThruMetaData);
				streamMap.put(streamName, item);
				item.start();
				log.info("ScheduleItem Started");
			}
		} catch (Exception e) {
			log.error("Schedule: Error from loadSchedule is '" + e.getMessage() + "'");
		}
	}
	
	public void stopSchedule() {
		log.info("stopSchedule");
		for (Map.Entry<String, ScheduleItem> entry : streamMap.entrySet()) {
			try {
				ScheduleItem item = entry.getValue();
				if (item != null)
					item.stop();
				item = null;
				log.info("Schedule Closed Stream: " + entry.getKey());
			} catch(Exception e) {
				log.error("Schedule: Error from stopSchedule Stream" + entry.getValue().channelId + " is '" + e.getMessage() + "'");
			}
		}
		if (streamMap != null)
			streamMap.clear();
	}
		
	public static Object getObject(String link) {
		try {
			URL url = new URL(link);
			InputStream in = url.openStream();
			byte[] b = new byte[1024];
			int len = -1;
			ByteArrayOutputStream bais = new ByteArrayOutputStream();
			while((len = in.read(b, 0, 1024)) != -1) {
				bais.write(b, 0, len);
			}
			bais.flush();
			in.close();
			return new JSONParser().parse(bais.toString()); 
		} catch (Exception e) {
			WMSLoggerFactory.getLogger(null).error("getObject: Error from Exception is '" + e.getMessage() + "'");
			return null;
		}
	}
	
	public static JSONArray getJSONArray(String link) {
		try {
			return (JSONArray) getObject(link);
		} catch (Exception e) {
			return null;
		}
	}
	
	public static JSONObject getJSONObject(String link) {
		try {
			return (JSONObject) getObject(link);
		} catch (Exception e) {
			return null;
		}
	}
	
}
