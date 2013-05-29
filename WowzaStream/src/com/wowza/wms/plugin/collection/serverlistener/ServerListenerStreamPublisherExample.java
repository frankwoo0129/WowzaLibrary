package com.wowza.wms.plugin.collection.serverlistener;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.wowza.wms.application.IApplication;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.server.IServer;
import com.wowza.wms.server.IServerNotify;
import com.wowza.wms.stream.publish.IStreamActionNotify;
import com.wowza.wms.stream.publish.Playlist;
import com.wowza.wms.stream.publish.PlaylistItem;
import com.wowza.wms.stream.publish.Stream;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.vhost.VHostSingleton;

public class ServerListenerStreamPublisherExample implements IServerNotify {
	
	WMSLogger log = WMSLoggerFactory.getLogger(null); 

	Map<String, Stream> streamMap = new HashMap<String, Stream>();
	Map<String, ScheduledItem> playlistMap = new HashMap<String, ScheduledItem>();

	@Override
	public void onServerCreate(IServer server) {
		log.info("onServerCreate");
	}

	@Override
	public void onServerInit(IServer server) {
		log.info("onServerInit");
		IVHost vhost = null;
		IApplication app = null;

		try {
			vhost = VHostSingleton.getInstance(server.getProperties().getPropertyStr("PublishToVHost", "_defaultVHost_"));
		} catch (Exception evhost) {
			log.info("ServerListenerStreamPublisher: Failed to get Vhost can not run.");
			return;
		}
		
		try {
			app = vhost.getApplication(server.getProperties().getPropertyStr("PublishToApplication", "live"));
		} catch (Exception eapp) {
			log.info("ServerListenerStreamPublisher: Failed to get Application can not run.");
			return;
		}
		
		// Belt and Braces check for VHost and App
		if ( vhost == null || app == null ) {
			log.info("ServerListenerStreamPublisher: VHost or Application failed, not running.");
			return;
		}
		Boolean passThruMetaData = server.getProperties().getPropertyBoolean("PassthruMetaData", true);
		
		String storageDir = app.getAppInstance("_definst_").getStreamStorageDir();

		try {
			
			String smilLoc = storageDir + "/streamschedule.smil";
			File playlistxml = new File(smilLoc);
			
			if (playlistxml.exists() == false){
				log.info("ServerListenerStreamPublisher: Could not find playlist file: " + smilLoc);
				return; 
			}
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = null;
			Document document = null;
			try {
				db = dbf.newDocumentBuilder();
				document = db.parse("file:///" + smilLoc);
			} catch (Exception e ) {
				log.info("ServerListenerStreamPublisher: XML Parse failed");
				return;
			}
			document.getDocumentElement().normalize();
			NodeList streams = document.getElementsByTagName("stream");
			for (int i = 0; i < streams.getLength(); i++) {
				Node streamItem = streams.item(i);
				if (streamItem.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element) streamItem;
					String streamName = e.getAttribute("name");
					log.info("ServerListenerStreamPublisher: Streame name is '"+streamName+"'");
					Stream stream = Stream.createInstance(vhost, app.getName(), streamName);
					streamMap.put(streamName, stream);
					app.getAppInstance("_definst_").getProperties().setProperty(streamName, stream);
				}
			}

			NodeList playList = document.getElementsByTagName("playlist");
			if (playList.getLength() == 0) {
				log.info("ServerListenerStreamPublisher: No playlists defined in smil file");
				return;
			} 
			for (int i = 0; i < playList.getLength(); i++) {
				Node scheduledPlayList = playList.item(i);
				if (scheduledPlayList.getNodeType() == Node.ELEMENT_NODE) {
					Element e = (Element) scheduledPlayList;    
					NodeList videos = e.getElementsByTagName("video");
					if (videos.getLength() == 0) {
						log.info("ServerListenerStreamPublisher: No videos defined in stream");
						return;
					}
					String streamName = e.getAttribute("playOnStream");
					if (streamName.length()==0)
						continue;
					Playlist playlist = new Playlist(streamName);
					playlist.setRepeat((e.getAttribute("repeat").equals("false"))?false:true);
					
					for (int j = 0; j < videos.getLength(); j++) {
						Node video = videos.item(j);                
						if (video.getNodeType() == Node.ELEMENT_NODE) {
							Element e2 = (Element) video;
							String src = e2.getAttribute("src");
							Integer start = Integer.parseInt(e2.getAttribute("start"));
							Integer length = Integer.parseInt(e2.getAttribute("length"));
							playlist.addItem(src, start, length);
						}
					}
					String scheduled = e.getAttribute("scheduled");
					SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					Date startTime = null;
					try {
						startTime = parser.parse(scheduled);
					} catch (Exception z ) {
						log.info("Parsing schedule time failed.");
						return ;
					}
					Stream stream = streamMap.get(streamName);
					stream.setSendOnMetadata(passThruMetaData);
					ScheduledItem item = new ScheduledItem(startTime, playlist, stream);
					playlistMap.put(e.getAttribute("name"), item);
					item.start();
					IStreamActionNotify actionNotify  = new StreamListener(app.getAppInstance("_definst_"));
					stream.addListener(actionNotify);
					log.info("ServerListenerStreamPublisher Scheduled: " + stream.getName() + " for: " + scheduled);
				}    
			}
		} catch(Exception ex) {
			log.info("ServerListenerStreamPublisher: Error from playlist manager is '"+ex.getMessage()+"'");
		}
	}
	
	@Override
	public void onServerShutdownComplete(IServer server) {
		log.info("onServerShutdownComplete");
	}

	@Override
	public void onServerShutdownStart(IServer server) {
		log.info("onServerShutdownStart");
		for (Map.Entry<String, Stream> entry : streamMap.entrySet()) {
			try {
				Stream stream = entry.getValue();
				stream.close();
				stream = null;
				log.info("ServerListenerStreamPublisher Closed Stream: " + entry.getKey());
			} catch(Exception ex) {
				log.error(ex.getMessage());
			}
		}
		streamMap.clear();
		for (Map.Entry<String, ScheduledItem> entry : playlistMap.entrySet()) {
			try {
				ScheduledItem item = entry.getValue();
				item.stop();
				item = null;
				log.info("ServerListenerStreamPublisher Closed Stream: " + entry.getKey());
			} catch(Exception ex) {
				log.error(ex.getMessage());
			}
		}
		playlistMap.clear();
	}
	
	public class StreamListener implements IStreamActionNotify {
		
		public StreamListener(IApplicationInstance appInstance) {
			
		}

		@Override
		public void onPlaylistItemStart(Stream stream, PlaylistItem playlistItem) {
			try {
				String name = stream.getCurrentItem().getName();
				stream.getPublisher().getAppInstance().broadcastMsg("PlaylistItemStart", name);
				WMSLoggerFactory.getLogger(null).info("ServerListenerStreamPublisher PlayList Item Start: " + name);
			} catch(Exception ex) {
				WMSLoggerFactory.getLogger(null).info("ServerListenerStreamPublisher Get Item error: " + ex.getMessage());
			}
		}

		@Override
		public void onPlaylistItemStop(Stream stream, PlaylistItem playlistItem) {
			if (playlistItem.getIndex() == (stream.getPlaylist().size() - 1)) {
				if (! stream.getRepeat()) {
					stream.close();
					WMSLoggerFactory.getLogger(null).info("ServerListenerStreamPublisher: closing stream: " + stream.getName());
				}
			}
		}
	}

	private class ScheduledItem {
		public Timer mTimer = null;
		public TimerTask mTask;
		public Date mStart;
		public Playlist mPL;
		public Stream mStream;
		
		public ScheduledItem (Date d, Playlist pl, Stream s) {
			mStart = d;
			mPL = pl;
			mStream = s;
			mTask = new TimerTask() {
				public void run() {
					mPL.open(mStream);
					log.info("ServerListenerStreamPublisher Scheduled stream is now live: " + mStream.getName());
				}
			};
		}

		public void start() { 
			if (mTimer == null)
				mTimer = new Timer();
			mTimer.schedule(mTask, mStart);
			log.info("scheduled playlist: "+mPL.getName()+
					" on stream: "+mStream.getName()+
					" for:"+mStart.toString());
		}
        
		public void stop() {
			if (mTimer != null) {
				mTimer.cancel();
				mTimer = null;
				log.info("cancelled playlist: "+mPL.getName()+
						" on stream: "+mStream.getName()+
						" for:"+mStart.toString());
			}
		}
	}
}
