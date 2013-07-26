package com.wowza.wms.plugin.broadcast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.wowza.wms.logging.WMSLoggerFactory;

public class ScheduleUtils {
	public static String getObject(String link) {
		try {
			WMSLoggerFactory.getLogger(ScheduleUtils.class).debug("getObject: link=" + link);
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
			return bais.toString(); 
		} catch (Exception e) {
			WMSLoggerFactory.getLogger(ScheduleUtils.class).error("getObject: Error from Exception is '" + e.getMessage() + "'");
			return null;
		}
	}
	
	public static JSONArray getJSONArray(String link) {
		try {
			return (JSONArray) new JSONParser().parse(getObject(link));
		} catch (Exception e) {
			WMSLoggerFactory.getLogger(ScheduleUtils.class).error("getJSONArray: Error from Exception is '" + e.getMessage() + "'");
			return null;
		}
	}
	
	public static JSONObject getJSONObject(String link) {
		try {
			return (JSONObject) new JSONParser().parse(getObject(link));
		} catch (Exception e) {
			WMSLoggerFactory.getLogger(ScheduleUtils.class).error("getJSONObject: Error from Exception is '" + e.getMessage() + "'");
			return null;
		}
	}
}
