package com.wowza.wms.plugin.broadcast;

import java.io.File;
import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedList;
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
		
		LinkedList<ScheduleProgram> programs = new LinkedList<ScheduleProgram>();
		for (Object obj : epgProgram) {
			ScheduleProgram objp = new ScheduleProgram((JSONObject) obj);
			programs.add(objp);
		}
		
		playlist = new Playlist(programs.getLast().getEPGId());
		playlist.setRepeat(false);
		
		ScheduleProgram program = programs.pollFirst();
		Date systemTime = ScheduleProgram.getTimeStamp((String) epgList.get("SystemDateTime"));
		startTime = program.getStartTimeStamp();
		int sub = Long.valueOf((systemTime.getTime() - startTime.getTime())/1000).intValue();
		String src = (onCache) ? ScheduleCache.CACHE_DIRECTORY + "/" + program.getFileName() : program.getUri();
		int filmTime = ((Long) program.getFilmTime()).intValue();
		File srcfile = Paths.get(appInstance.getStreamStorageDir()).resolve(src).toFile();
		if (!srcfile.exists()) {
			ret = "ScheduleItem ChannelId "+ channelId + ": File is Not Found: " + src;
			log.error(ret);
			return ;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(src).insert(src.lastIndexOf('/') + 1, "mp4:");
		if (sub > 0 && epgId == null) {
			playlist.addItem(sb.toString(), sub, filmTime-sub);
		} else {
			playlist.addItem(sb.toString(), 0, filmTime);
		}
		
		programs.pollLast();
		while (!programs.isEmpty()) {
			program = programs.pollFirst();
			src = (onCache) ? ScheduleCache.CACHE_DIRECTORY + "/" + program.getFileName() : program.getUri();
			filmTime = ((Long) program.getFilmTime()).intValue();
			srcfile = Paths.get(appInstance.getStreamStorageDir()).resolve(src).toFile();
			if (!srcfile.exists()) {
				ret = "ScheduleItem ChannelId "+ channelId + ": File is Not Found: " + src;
				log.error(ret);
				return ;
			}
			sb.delete(0, sb.length());
			sb.append(src).insert(src.lastIndexOf('/') + 1, "mp4:");
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
		log.info("ScheduledItem: " + stream.getName() + " for: " + startTime);
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
