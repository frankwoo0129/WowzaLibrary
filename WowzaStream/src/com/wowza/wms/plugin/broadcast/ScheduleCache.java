package com.wowza.wms.plugin.broadcast;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;

public class ScheduleCache {
	
	public static final String CACHE_DIRECTORY = "ScheduleCache";
	public static final String CACHE_DB_FILENAME = "content.db";
	public static final long CACHE_TIME_INTERVAL = 1000*60*60*6;
	public static final long CACHE_TIME_TEST = 1000*60*60;
	
//	private static String DROP_CACHE_TABLE_SQL = "drop table if exists content";
	private static String CREAT_CACHE_TABLE_SQL = "create table if not exists content (channelid integer, uri string, filename string, starttime timestamp, endtime timestamp)";
	private static String SELECT_DELETE_PROGRAM_SQL = "select * from content where filename not in (select filename from content where endtime >= ?)";
	private static String DELETE_PROGRAM_SQL = "delete from content where filename not in (select filename from content where endtime >= ?)";
	private static String INSERT_PROGRAM_SQL = "insert into content values(?, ?, ?, ?, ?)";
	private static WMSLogger log = WMSLoggerFactory.getLogger(ScheduleCache.class);
	private static SimpleDateFormat parser = new SimpleDateFormat("yyyyMMddHHmmss");
	
	private String epglink;
	private IApplicationInstance appInstance;
	private File cacheDir;
		
	static {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			log.error("SQLite Driver Not found");
		}
	}
	
	public ScheduleCache(IApplicationInstance appInstance, String epglink) {
		this.appInstance = appInstance;
		this.epglink = epglink;
		this.cacheDir = new File(appInstance.getStreamStorageDir(), CACHE_DIRECTORY);
		log.info("Cache Directory: " + cacheDir.getAbsolutePath());
		if (!cacheDir.exists()) {
			if(!cacheDir.mkdir())
				log.error("Creat Cache Directory Failed");
		}
	}
	
	public void init() {
		Connection connection = getConnection();
		
		if (connection == null) {
			log.error("SQLite Error");
			return ;
		}
		
		try {
			Statement statement = connection.createStatement(); 
			statement.setQueryTimeout(30);
			statement.executeUpdate(CREAT_CACHE_TABLE_SQL);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				log.warn("SQLite Connection close ERROR");
			}
		}
		doCache(CACHE_TIME_TEST);
	}
	
	@SuppressWarnings("rawtypes")
	public void doCache(long cachetimemilis) {
		String cachetime = parser.format(new Date(System.currentTimeMillis() + cachetimemilis));
		JSONArray epglist = ScheduleUtils.getJSONArray(epglink);
		if (epglist == null) {
			log.error("EPG Server Error");
			return ;
		}
		
		Iterator iter_epglist = epglist.iterator();
		while (iter_epglist.hasNext()) {
			JSONObject obj = (JSONObject) iter_epglist.next();
			
			int channelId = ((Long) obj.get("channelid")).intValue();
			JSONObject epg = ScheduleUtils.getJSONObject((String) obj.get("epg") + "?totimestamp=" + cachetime);
			if (epg == null) {
				continue ;
			}
			JSONArray programs = (JSONArray) epg.get("Programs");
			if (programs == null || programs.size() == 0) {
				continue;
			}
					
			Iterator iter_programs = programs.iterator();
			COPY_FILE:
			while (iter_programs.hasNext()) {
				JSONObject program = (JSONObject) iter_programs.next();
				String suri = (String) program.get("suri");
				String uri = (String) program.get("uri");
				String filename = suri.substring(suri.lastIndexOf('/') + 1);
				String starttime = (String) program.get("startTimestamp");
				String endtime = (String) program.get("endTimestamp");
				
				Path copyMeFile = Paths.get(appInstance.getStreamStorageDir() + uri);
				Path copyToFile = Paths.get(appInstance.getStreamStorageDir(), CACHE_DIRECTORY).resolve(filename);
				
				while (true) {
					if (copyToFile.toFile().exists())
						break;
				
					try {
						Files.copy(copyMeFile, copyToFile, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
						log.info("The file was successfully copied!");
						break ;
					} catch (IOException e) {
						log.error("The file was copyed unsuccessfully: " + e.getMessage());
						continue COPY_FILE;
					}
				}
				
				if (!addProgram(channelId, uri, filename, starttime , endtime))
		        	log.error("Add Program to DB failed");
			}
			
		}
		
	}
	
	public void doCleanAllCache() {
		File[] fs = cacheDir.listFiles();
		for (File f : fs) {
			f.delete();
		}
		
		Connection connection = getConnection();
		
		if (connection == null) {
			log.error("SQLite Error");
			return ;
		}
		
		try {
			Statement statement = connection.createStatement(); 
			statement.setQueryTimeout(30);
			statement.executeUpdate(CREAT_CACHE_TABLE_SQL);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				log.warn("SQLite Connection close ERROR");
			}
		}
	}
	
	public void doCleanCache() {
		Connection connection = getConnection();
		
		if (connection == null) {
			log.error("SQLite Error");
			return ;
		}
		
		PreparedStatement pstatement = null;
		try {
			Calendar c = Calendar.getInstance();
			pstatement = connection.prepareStatement(SELECT_DELETE_PROGRAM_SQL);
			pstatement.setTimestamp(1, new Timestamp(c.getTimeInMillis() - CACHE_TIME_TEST));
			ResultSet rs = pstatement.executeQuery();
			while (rs.next()) {
				File f = Paths.get(appInstance.getStreamStorageDir(), CACHE_DIRECTORY).resolve(rs.getString("filename")).toFile();
				f.delete();
			}
			pstatement = connection.prepareStatement(DELETE_PROGRAM_SQL);
			pstatement.setTimestamp(1, new Timestamp(c.getTimeInMillis() - CACHE_TIME_TEST));
			pstatement.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				log.warn("SQLite Connection close ERROR");
			}
		}
	}
	
	private Connection getConnection() {
		try {
			Connection connection = DriverManager.getConnection("jdbc:sqlite:" + Paths.get(appInstance.getStreamStorageDir(), CACHE_DIRECTORY).resolve(CACHE_DB_FILENAME).toFile());
			return connection;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private boolean addProgram(int channelId, String uri, String filename, String startstamp, String endstamp)  {
		Connection connection = getConnection();
		
		if (connection == null) {
			log.error("SQLite Error");
			return false;
		}
		
		try {
        	PreparedStatement pstatement = connection.prepareStatement(INSERT_PROGRAM_SQL);
        	pstatement.setQueryTimeout(30);
			pstatement.setInt(1, channelId);
			pstatement.setString(2, uri);
			pstatement.setString(3, filename);
			pstatement.setTimestamp(4, new Timestamp(parser.parse(startstamp).getTime()));
			pstatement.setTimestamp(5, new Timestamp(parser.parse(endstamp).getTime()));
			pstatement.executeUpdate();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				log.warn("SQLite Connection close ERROR");
			}
		}
	}
}
