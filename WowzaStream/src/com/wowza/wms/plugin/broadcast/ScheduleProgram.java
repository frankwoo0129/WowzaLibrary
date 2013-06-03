package com.wowza.wms.plugin.broadcast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.simple.JSONObject;

public class ScheduleProgram {
	
	public static SimpleDateFormat parser = new SimpleDateFormat("yyyyMMddHHmmss");

	private final String epgId;
	private final String filename;
	private final long filmTime;
	private final String startTimeStamp;
	private final String endTimeStamp;
	private final String uri;
	private final String huri;
	private final String suri;
	
	public ScheduleProgram(JSONObject obj) {
		try {
			this.epgId = (String) obj.get("epgId");
			this.filmTime = (Long) obj.get("FilmTime");
			this.startTimeStamp = (String) obj.get("startTimestamp");
			this.endTimeStamp = (String) obj.get("endTimestamp");
			this.uri = (String) obj.get("uri");
			this.huri = (String) obj.get("huri");
			this.suri = (String) obj.get("suri");
			this.filename = this.suri.substring(this.suri.lastIndexOf('/') + 1);
		} catch (Exception e) {
			throw new RuntimeException("JSON error");
		}
	}
	
	public String getEPGId() {
		return this.epgId;
	}
	
	public String getFileName() {
		return this.filename;
	}
	
	public long getFilmTime() {
		return this.filmTime;
	}
	
	public Date getStartTimeStamp() {
		Date startTime = null;
		try {
			startTime = parser.parse(startTimeStamp);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return startTime;
	}
	
	public Date getEndTimeStamp() {
		Date endTime = null;
		try {
			endTime = parser.parse(endTimeStamp);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return endTime;
	}
	
	public static Date getTimeStamp(String timeStamp) {
		Date time = null;
		try {
			time = parser.parse(timeStamp);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return time;
	}
	
	public String getUri() {
		return this.uri;
	}
	
	public String getSUri() {
		return this.suri;
	}
	
	public String getHUri() {
		return this.huri;
	}
}
