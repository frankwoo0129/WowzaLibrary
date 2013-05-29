package com.wowza.wms.plugin.broadcast;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.wowza.wms.application.IApplicationInstance;

public class ScheduleEPGList {
	
	private IApplicationInstance appInstance = null;
	private String link = null;
	private Map<Integer, ScheduleEPG> map = new HashMap<Integer, ScheduleEPG>();
	
	public ScheduleEPGList(IApplicationInstance appInstance, String link) {
		this.appInstance = appInstance;
		this.link = link;
	}
	
	public void init() {
		String ret;
		map.clear();
		JSONArray epglist = ScheduleUtils.getJSONArray(this.link);
		if (epglist == null) {
			ret = "Connection Failed to epg Server";
			throw new RuntimeException(ret);
		}
		if (epglist.size() == 0) {
			ret = "ScheduleController.loadSchedule: No channel to publish";
			throw new RuntimeException(ret);
		}
		for (Object obj : epglist) {
			String link = (String) ((JSONObject) obj).get("epg");
			int channelid = ((Long) ((JSONObject) obj).get("channelid")).intValue();
//			ScheduleEPG epg = new ScheduleEPG(link, channelid);
//			map.put(channelid, epg);
		}
	}
	
	public ScheduleEPG getScheduleEPG(int channelid) {
		return map.get(channelid);
	}
	
	public Set<Integer> getChannelIdSet() {
		return map.keySet();
	}
	
}
