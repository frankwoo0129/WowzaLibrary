package com.wowza.wms.plugin.broadcast;

import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.stream.publish.IStreamActionNotify;
import com.wowza.wms.stream.publish.Playlist;
import com.wowza.wms.stream.publish.PlaylistItem;
import com.wowza.wms.stream.publish.Stream;

public class ScheduleItem implements IStreamActionNotify {
	
	private boolean onCache = false;
	private Timer mTimer;
	private TimerTask mTask;
	private IApplicationInstance appInstance;
	private Stream stream;
	private Playlist playlist;
	private int channelId;
	private String epglink;
	private Date startTime;
	private WMSLogger log = WMSLoggerFactory.getLogger(null);
	private static int count = 5;
	
	public ScheduleItem(IApplicationInstance appInstance, Stream stream, int channelId, String epglink) {
		if (stream == null)
			throw new NullPointerException("ScheduleItem: stream is NULL");

		this.appInstance = appInstance;
		this.stream = stream;
		this.channelId = channelId;
		this.epglink = epglink;
		this.mTimer = new Timer();
		this.onCache = appInstance.getProperties().getPropertyBoolean("ScheduleOnCache", onCache);
	}
	
	@Override
	public void onPlaylistItemStart(Stream stream, PlaylistItem playlistItem) {
		if (playlistItem.getIndex() == (stream.getPlaylist().size() - 1)) {
			loadScheduleItem(playlist.getName());
		}
		
		try {
			log.info("Schedule PlayList Item Start: " + playlistItem.getName());
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
			epgList = ScheduleUtils.getJSONObject(epglink + "?count=" + count);
		else
			epgList = ScheduleUtils.getJSONObject(epglink + "?count=" + count + "&epgid=" + epgId);
		
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
		
		int sub = Long.valueOf((systemTime.getTime() - startTime.getTime())/1000).intValue();
		String temp = (String) ((JSONObject) epgProgram.get(0)).get("suri");
		String src = (onCache) ? ScheduleCache.CACHE_DIRECTORY + "/" + temp.substring(temp.lastIndexOf('/') + 1) : (String) ((JSONObject) epgProgram.get(0)).get("uri");
		int filmTime = ((Long) ((JSONObject) epgProgram.get(0)).get("FilmTime")).intValue();
		File srcfile = (onCache) ? Paths.get(appInstance.getStreamStorageDir()).resolve(src).toFile() : new File(appInstance.getStreamStorageDir(), src);
		if (!srcfile.exists()) {
			ret = "ScheduleItem ChannelId "+ channelId + ": File is Not Found: " + src;
			log.error(ret);
			return ;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(src.substring(0, src.lastIndexOf('/')+1)).append("mp4:").append(src.substring(src.lastIndexOf('/')+1, src.length()));
		log.info("=================================================: " + sb.toString());
		if (sub > 0 && epgId == null) {
			playlist.addItem(sb.toString(), sub, filmTime-sub);
		} else {
			playlist.addItem(sb.toString(), 0, filmTime);
		}
		
		for (int i = 1; i < epgProgram.size() - 1; i++) {
			temp = (String) ((JSONObject) epgProgram.get(i)).get("suri");
			src = (onCache) ? ScheduleCache.CACHE_DIRECTORY + "/" + temp.substring(temp.lastIndexOf('/') + 1) : (String) ((JSONObject) epgProgram.get(i)).get("uri") ;
			filmTime = ((Long) ((JSONObject) epgProgram.get(i)).get("FilmTime")).intValue();
			srcfile = (onCache) ? Paths.get(appInstance.getStreamStorageDir()).resolve(src).toFile() : new File(appInstance.getStreamStorageDir(), src) ;
			if (!srcfile.exists()) {
				ret = "ScheduleItem ChannelId "+ channelId + ": File is Not Found: " + src;
				log.error(ret);
				return ;
			}
			sb = new StringBuilder();
			sb.append(src.substring(0, src.lastIndexOf('/')+1)).append("mp4:").append(src.substring(src.lastIndexOf('/')+1, src.length()));
			log.info("=================================================: " + sb.toString());
			playlist.addItem(sb.toString(), 0, filmTime);
		}
		
		mTask = new TimerTask() {
			public void run() {
				if(playlist.open(stream))
					log.info("ScheduledItem: '" + stream.getName() + "' is now live");
				else {
					log.warn("ScheduledItem: '" + stream.getName() + "' is NOT live");
					stream.removeListener(ScheduleItem.this);
					close();
				}
			}
		};
		
		if (mTimer == null)
			mTimer = new Timer();
		mTimer.schedule(mTask, startTime);
		log.info("ScheduledItem: " + stream.getName() + " for: " + scheduled);
	}
	
	public void open() {
		stream.addListener(this);
		this.loadScheduleItem(null);
	}
	
	public void close() {
		stream.removeListener(this);
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
		stream.close();
	}
	
	public int getChannelId() {
		return this.channelId;
	}
}
