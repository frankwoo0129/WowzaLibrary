package com.wowza.wms.plugin.collection.module;

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

import com.wowza.wms.amf.*;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.client.*;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.module.*;
import com.wowza.wms.request.*;
import com.wowza.wms.stream.publish.IStreamActionNotify;
import com.wowza.wms.stream.publish.Playlist;
import com.wowza.wms.stream.publish.PlaylistItem;
import com.wowza.wms.stream.publish.Stream;

public class ModuleStreamPublisherExample extends ModuleBase {
	
	Map<String, Stream> streamMap = new HashMap<String, Stream>();
	Map<String, Playlist> playlistMap = new HashMap<String, Playlist>();
    
 	String ret = "";

	public void loadSchedule(IClient client, RequestFunction function,
			AMFDataList params) {
		getLogger().info("loadSchedule");

		Boolean passThruMetaData = client.getAppInstance().getProperties().getPropertyBoolean("PassthruMetaData", true);
        
        String storageDir = client.getAppInstance().getStreamStorageDir();
        
        try
        {           
        String smilLoc = storageDir + "/streamschedule.smil";
        File playlistxml = new File(smilLoc);
            
        if (playlistxml.exists() == false){
            getLogger().info("ServerListenerStreamPublisher: Could not find playlist file: " + smilLoc);
            return; 
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        
        
        DocumentBuilder db = null;
        Document document = null;
        try {
        
        db = dbf.newDocumentBuilder();
        document = db.parse("file:///" + smilLoc);
        
        } catch (Exception e ) { 
        	getLogger().info("ServerListenerStreamPublisher: XML Parse failed"); 
        	ret = "ServerListenerStreamPublisher: XML Parse failed";
        	return; }

        
        document.getDocumentElement().normalize();
        
        NodeList streams = document.getElementsByTagName("stream");
        for (int i = 0; i < streams.getLength(); i++)
        {
            Node streamItem = streams.item(i);
            if (streamItem.getNodeType() == Node.ELEMENT_NODE)
            { 
                Element e = (Element) streamItem;
                String streamName = e.getAttribute("name");
                
                getLogger().info("ServerListenerStreamPublisher: Streame name is '"+streamName+"'");
                
                Stream stream = Stream.createInstance(client.getAppInstance(), streamName);
                streamMap.put(streamName, stream);
                client.getAppInstance().getProperties().setProperty(streamName, stream);
            }
        }
        
        NodeList playList = document.getElementsByTagName("playlist");
        if (playList.getLength() == 0){
            getLogger().info("ServerListenerStreamPublisher: No playlists defined in smil file");
            return;
        } 
        for (int i = 0; i < playList.getLength(); i++)
        {
            Node scheduledPlayList = playList.item(i);
            
            if (scheduledPlayList.getNodeType() == Node.ELEMENT_NODE)
            {
                Element e = (Element) scheduledPlayList;    
                
                NodeList videos = e.getElementsByTagName("video");
                if (videos.getLength() == 0){
                     getLogger().info("ServerListenerStreamPublisher: No videos defined in stream");
                    return;
                }
                
                String streamName = e.getAttribute("playOnStream");
                if (streamName.length()==0)
                    continue;
                
                Playlist playlist = new Playlist(streamName);
                playlist.setRepeat((e.getAttribute("repeat").equals("false"))?false:true);
                
                playlistMap.put(e.getAttribute("name"), playlist);
                
                for (int j = 0; j < videos.getLength(); j++)
                {
                    Node video = videos.item(j);                
                    if (video.getNodeType() == Node.ELEMENT_NODE)
                    {
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
                } catch (Exception z ) { getLogger().info("Parsing schedule time failed."); return ; }
                Stream stream = streamMap.get(streamName);
                stream.setSendOnMetadata(passThruMetaData);
                ScheduledItem item = new ScheduledItem(startTime, playlist, stream);
                item.start();
                IStreamActionNotify actionNotify  = new StreamListener(client.getAppInstance());
                stream.addListener(actionNotify);
                getLogger().info("ServerListenerStreamPublisher Scheduled: " + stream.getName() + " for: " + scheduled);
            }    
        }
    }
    catch(Exception ex)
    {
        getLogger().info("ServerListenerStreamPublisher: Error from playlist manager is '"+ex.getMessage()+"'");
    }	
        if (ret=="")
        	ret = "DONE!";
        
        sendResult(client, params, ret);
	}
	
	private class ScheduledItem {
        public Timer mTimer;
        public TimerTask mTask;
        public Date mStart;
        public Playlist mPL;
        public Stream mStream;
        public ScheduledItem(Date d, Playlist pl, Stream s){
            mStart = d;
            mPL = pl;
            mStream = s;
            mTask = new TimerTask(){
                public void run() {
                    //synchronized(mStream.getLock())
                    //{
                        mPL.open(mStream);
                    //}
                    getLogger().info("ServerListenerStreamPublisher Scheduled stream is now live: " + mStream.getName());
                }
            };
            mTimer = new Timer();
        }
        
        public void start(){ 
            
            if (mTimer==null)
                mTimer = new Timer();
            mTimer.schedule(mTask, mStart);
            getLogger().info("scheduled playlist: "+mPL.getName()+
                        " on stream: "+mStream.getName()+
                        " for:"+mStart.toString());
        }
        
        public void stop(){
            if (mTimer != null){
                mTimer.cancel();
                mTimer=null;
                getLogger().info("cancelled playlist: "+mPL.getName()+
                        " on stream: "+mStream.getName()+
                        " for:"+mStart.toString());
            }
        }
        
        
        
    }

	class StreamListener implements IStreamActionNotify
    {
        StreamListener(IApplicationInstance appInstance)
        {
        }    
        public void onPlaylistItemStop(Stream stream, PlaylistItem item)
        {
        	if (item.getIndex() == (stream.getPlaylist().size() - 1))
        	{
        	if (! stream.getRepeat())
        	{
        	stream.close();
        	WMSLoggerFactory.getLogger(null).info("ServerListenerStreamPublisher: closing stream: " + stream.getName());
        	}
        	}
        }
        public void onPlaylistItemStart(Stream stream, PlaylistItem item) 
        {
            try
            {
            String name = stream.getCurrentItem().getName();
            WMSLoggerFactory.getLogger(null).info("ServerListenerStreamPublisher PlayList Item Start: " + name);
            }
            catch(Exception ex)
            {
                WMSLoggerFactory.getLogger(null).info("ServerListenerStreamPublisher Get Item error: " + ex.getMessage());
            }
        }
    }
	
}