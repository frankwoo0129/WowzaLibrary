package com.wowza.wms.plugin.broadcast;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.stream.publish.Stream;

public class ScheduleEPG {
	
	private static int COUNT = 5;
	private static WMSLogger log = WMSLoggerFactory.getLogger(ScheduleEPG.class);
	
	private IApplicationInstance appInstance;
	private Stream stream;
	private final String epglink;
	private final int channelId;
	private List<ScheduleProgram> list;
	
	public ScheduleEPG(IApplicationInstance appInstance, Stream stream, String epglink, int channelId) {
		this.appInstance = appInstance;
		this.stream = stream;
		this.epglink = epglink;
		this.channelId = channelId;
		this.list = new LinkedList<ScheduleProgram>();
	}
	
	public int getChannelId() {
		return this.channelId;
	}
	
	public String getEPGlink() {
		return this.epglink;
	}
	
	@SuppressWarnings("rawtypes")
	public void update() {
		list.clear();
		JSONObject json = ScheduleUtils.getJSONObject(this.epglink);
		JSONArray programs = (JSONArray) json.get("Programs");
		Iterator iter = programs.iterator();
		while (iter.hasNext()) {
			list.add(new ScheduleProgram((JSONObject) iter.next()));
		}
	}
	
	@SuppressWarnings("rawtypes")
	public void init() {
		String ret = null;
		JSONObject epg = null;
		if (list.size() == 0)
			epg = ScheduleUtils.getJSONObject(epglink + "?count=" + COUNT);
		else
			epg = ScheduleUtils.getJSONObject(epglink + "?count=" + COUNT + "&epgid=" + list.get(list.size()-1).getEPGId());
		
		if (epg == null) {
			ret = "ScheduleEPG: No connecction to epg Server, channelid: " + channelId;
			log.error(ret);
			return ;
		}
		
		JSONArray epgProgram = (JSONArray) epg.get("Programs");
		if (epgProgram == null || epgProgram.size() == 0) {
			ret = "ScheduleItem: No epglist on Server";
			log.warn(ret);
			return ;
		}
		Iterator iter = epgProgram.iterator();
		while (iter.hasNext()) {
			list.add(new ScheduleProgram((JSONObject) iter.next()));
		}
		
	}
}
