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
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;

public class ScheduleCache {
	
	public static final String CACHE_DIRECTORY = "ScheduleCache";
	public static final String CACHE_DB_FILENAME = "content.db";
	public static final long CACHE_TIME_CLEAN = 1000*60*60*24;
	public static final long CACHE_TIME_INTERVAL = 1000*60*60*6;
	public static final long CACHE_TIME_TEST = 1000*60*60;
	
//	private static String DROP_CACHE_TABLE_SQL = "drop table if exists content";
	private static String CREAT_CACHE_TABLE_SQL = "create table if not exists content (channelid integer, uri string, filename string, starttime timestamp, endtime timestamp)";
	private static String SELECT_DELETE_PROGRAM_SQL = "select * from content where filename not in (select filename from content where endtime >= ?)";
	private static String DELETE_PROGRAM_SQL = "delete from content where filename not in (select filename from content where endtime >= ?)";
	private static String INSERT_PROGRAM_SQL = "insert into content values(?, ?, ?, ?, ?)";
	private static WMSLogger log = WMSLoggerFactory.getLogger(ScheduleCache.class);
	
	private String epglink;
	private IApplicationInstance appInstance;
	private File cacheDir;
	private Object lock =  new Object();
		
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
		synchronized(lock) {
			Connection connection = getConnection();
			if (connection == null) {
				log.error("SQLite Error");
				return ;
			}
		
			try {
				Statement statement = connection.createStatement(); 
				statement.setQueryTimeout(5);
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
	}
	
	public void doCache(long cachetimemillis) {
		Date cachetime = new Date(System.currentTimeMillis() + cachetimemillis);
		ScheduleEPGList epglist= null;
		try {
			epglist = new ScheduleEPGList(epglink);
			epglist.init();
		} catch (Exception e) {
			log.error(e.getMessage());
			return ;
		}
		
		Iterator<Integer> iter_epglist = epglist.getChannelIdSet().iterator();
		while (iter_epglist.hasNext()) {
			ScheduleEPG epg = epglist.getScheduleEPG(iter_epglist.next());
			int channelId = epg.getChannelId();
			try {
				epg.update(cachetime);
			} catch (Exception e) {
				log.error(e.getMessage());
				continue;
			}
		
			ScheduleProgram program = null;
			COPY_FILE:
				while ((program = epg.getProgram()) != null) {
					Path copyMeFile = Paths.get(appInstance.getStreamStorageDir() + program.getUri());
					Path copyToFile = Paths.get(appInstance.getStreamStorageDir(), CACHE_DIRECTORY).resolve(program.getFileName());
					
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
					synchronized(lock) {
						if (!addProgram(channelId, program.getUri(), program.getFileName(), program.getStartTimeStamp() , program.getEndTimeStamp()))
							log.error("Add Program to DB failed");
					}
				}	
		}
	}
	
	public void doCache(long cachetimemillis, int channelId) {
		Date cachetime = new Date(System.currentTimeMillis() + cachetimemillis);
		ScheduleEPGList epglist= null;
		try {
			epglist = new ScheduleEPGList(epglink);
			epglist.init();
		} catch (Exception e) {
			log.error(e.getMessage());
			return ;
		}
		
		ScheduleEPG epg = epglist.getScheduleEPG(channelId);
		if (epg == null)
			return ;
		
		try {
			epg.update(cachetime);
		} catch (Exception e) {
			log.error(e.getMessage());
			return ;
		}
	
		ScheduleProgram program = null;
		COPY_FILE:
			while ((program = epg.getProgram()) != null) {
				Path copyMeFile = Paths.get(appInstance.getStreamStorageDir() + program.getUri());
				Path copyToFile = Paths.get(appInstance.getStreamStorageDir(), CACHE_DIRECTORY).resolve(program.getFileName());
		
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
				synchronized(lock) {
					if (!addProgram(channelId, program.getUri(), program.getFileName(), program.getStartTimeStamp() , program.getEndTimeStamp()))
						log.error("Add Program to DB failed");
				}
			}	
	}
	
	public void doCleanAllCache() {
		File[] fs = cacheDir.listFiles();
		for (File f : fs) {
			f.delete();
		}
		
		synchronized(lock) {
			Connection connection = getConnection();
			try {
				connection = getConnection();
		
				if (connection == null) {
					log.error("SQLite Error");
					return ;
				}
				
				Statement statement = connection.createStatement(); 
				statement.setQueryTimeout(5);
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
	}
	
	public void doCleanCache() {
		synchronized(lock) {
			Connection connection = null;
			try {
				connection = getConnection();
		
				if (connection == null) {
					log.error("SQLite Error");
					return ;
				}
				
				Calendar c = Calendar.getInstance();
				PreparedStatement pstatement = connection.prepareStatement(SELECT_DELETE_PROGRAM_SQL);
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
	
	private boolean addProgram(int channelId, String uri, String filename, Date startstamp, Date endstamp)  {
		synchronized(lock) {
			Connection connection = null;
			try {
				connection = getConnection();
		
				if (connection == null) {
					log.error("SQLite Error");
					return false;
				}
				
				PreparedStatement pstatement = connection.prepareStatement(INSERT_PROGRAM_SQL);
				pstatement.setQueryTimeout(5);
				pstatement.setInt(1, channelId);
				pstatement.setString(2, uri);
				pstatement.setString(3, filename);
				pstatement.setTimestamp(4, new Timestamp(startstamp.getTime()));
				pstatement.setTimestamp(5, new Timestamp(endstamp.getTime()));
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
}
