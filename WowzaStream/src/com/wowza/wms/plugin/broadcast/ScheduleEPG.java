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
import com.wowza.wms.stream.publish.Playlist;
import com.wowza.wms.stream.publish.Stream;

public class ScheduleEPG {
	
	private static int COUNT = 5;
	private static WMSLogger log = WMSLoggerFactory.getLogger(ScheduleEPG.class);
	private static String STREAMNAME = "stream";
		
	private IApplicationInstance appInstance;
	private Stream stream;
	private final String epgLink;
	private final int channelId;
	private LinkedList<ScheduleProgram> list;
	private boolean isInit = false;
	private Playlist playlist;
	private ScheduleProgram lastProgram = null;
	private TimerTask mTask = null;
	private Timer mTimer = null;
	private Date systemTime = null;
	
	public ScheduleEPG(IApplicationInstance appInstance, String epgLink, int channelId) {
		this.appInstance = appInstance;
		
		String streamName = STREAMNAME + channelId;
		Boolean passThruMetaData = appInstance.getProperties().getPropertyBoolean("PassthruMetaData", true);
		this.stream = Stream.createInstance(appInstance, streamName);
		appInstance.getProperties().setProperty(streamName, stream);
		stream.setSendOnMetadata(passThruMetaData);
		
		this.epgLink = epgLink;
		this.channelId = channelId;
		this.list = new LinkedList<ScheduleProgram>();
	}
	
	public int getChannelId() {
		return this.channelId;
	}
	
	public void updateList() {
		log.info("updateList: " + this.channelId);
		String ret = null;
		String link = null;
		if (list.size() <= 0)
			link = epgLink + "?count=" + COUNT;
		else
			link = epgLink + "?count=" + COUNT + "&epgid=" + list.peekLast().getEPGId();
				
		JSONObject json = ScheduleUtils.getJSONObject(link);
		if (json == null) {
			ret = "ScheduleEPG: Connection Failed to epg Server";
			log.error(ret);
			throw new RuntimeException(ret);
		}
		
		JSONArray epgPrograms = null;
		if (json.get("Programs") == null || (epgPrograms = (JSONArray) json.get("Programs")).size() == 0) {
			ret = "ScheduleEPG: No epg to publish";
			log.warn(ret);
			return ;
		}
		
		for (Object obj : epgPrograms) {
			list.add(new ScheduleProgram((JSONObject) obj));
		}
	}
	
	public void init() {
		list.clear();
		updateList();
		this.isInit = true;
	}
	
	public void open() {
		if (!isInit)
			init();
	}
	
	public void close() {
		
	}
	
	public void load() {
		playlist = new Playlist(list.get(list.size()-1).getEPGId());
		playlist.setRepeat(false);
		
		boolean onCache = true;
		String ret = null;
		
		
		ScheduleProgram program = list.poll();
		if (program == null)
			return ;
		
		Date startTime = program.getStartTimeStamp();
		int sub = Long.valueOf((systemTime.getTime() - startTime.getTime())/1000).intValue();
		int filmTime = ((Long) program.getFilmTime()).intValue();
				
		StringBuilder sb = new StringBuilder();
		while (program != null) {
			String src = (onCache) ? ScheduleCache.CACHE_DIRECTORY + "/" + program.getFileName() : program.getUri();
			filmTime = ((Long) program.getFilmTime()).intValue();
			File srcfile = Paths.get(appInstance.getStreamStorageDir()).resolve(src).toFile();
			if (!srcfile.exists()) {
				ret = "ScheduleEPG ChannelId "+ channelId + ": File is Not Found: " + src;
				log.error(ret);
				return ;
			}
			sb.delete(0, sb.length());
			sb.append(src).insert(src.lastIndexOf('/') + 1, "mp4:");
			if (sub > 0 && lastProgram == null) {
				playlist.addItem(sb.toString(), sub, filmTime-sub);
			} else {
				playlist.addItem(sb.toString(), 0, filmTime);
			}
			
			lastProgram = program;
			program = list.poll();
		}
		
		mTask = new TimerTask() {
			public void run() {
				if(playlist.open(stream))
					log.info("ScheduledItem: '" + stream.getName() + "' is now live");
				else {
					log.warn("ScheduledItem: '" + stream.getName() + "' is NOT live");
//					stream.removeListener(ScheduleItem.this);
					close();
				}
			}
		};
		
		if (mTimer == null)
			mTimer = new Timer();
		mTimer.schedule(mTask, startTime);
		log.info("ScheduledItem: " + stream.getName() + " for: " + startTime);
		
	}
}
