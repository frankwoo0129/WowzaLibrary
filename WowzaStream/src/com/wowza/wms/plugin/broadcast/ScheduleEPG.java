package com.wowza.wms.plugin.broadcast;

import java.util.Date;
import java.util.LinkedList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ScheduleEPG {
	
	private static int COUNT = 5;
				
	private final String epgLink;
	private final int channelId;
	private LinkedList<ScheduleProgram> list;
	private String systemTime = null;
		
	public ScheduleEPG(String epgLink, int channelId) {
		this.epgLink = epgLink;
		this.channelId = channelId;
		this.list = new LinkedList<ScheduleProgram>();
	}
	
	public int getChannelId() {
		return this.channelId;
	}
	
	public Date getSystemTime() {
		return ScheduleProgram.getTimeStamp(this.systemTime);
	}
	
	private void doUpdate(String link) {
		list.clear();
		JSONObject epgList = ScheduleUtils.getJSONObject(link);
		if (epgList == null) {
			String ret;
			ret = "ScheduleEPG: Connection Failed to epg Server";
			throw new RuntimeException(ret);
		}
		systemTime = (String) epgList.get("SystemDateTime");
		JSONArray epgPrograms = (JSONArray) epgList.get("Programs");
		for (Object obj : epgPrograms) {
			list.add(new ScheduleProgram((JSONObject) obj));
		}
	}
	
	public void update() {
		String link = epgLink + "?count=" + COUNT;
		doUpdate(link);
	}
	
	public void update(Date date) {
		String time = ScheduleProgram.parser.format(date);
		String link = epgLink + "?totimestamp=" + time;
		doUpdate(link);
	}
	
	public void update(String epgId) {
		String link = epgLink + "?count=" + COUNT + "&epgid=" + epgId;
		doUpdate(link);
	}
	
	public boolean isEmpty() {
		return list.isEmpty();
	}
	
	public ScheduleProgram getProgram() {
		return list.pollFirst();
	}
	
	public String getLastEPGId() {
		return list.getLast().getEPGId();
	}
	
}