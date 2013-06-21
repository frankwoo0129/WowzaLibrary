package com.wowza.wms.plugin.broadcast.multicast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wowza.util.FileUtils;
import com.wowza.util.HTTPUtils;
import com.wowza.util.SystemUtils;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.rtp.model.RTPDestination;
import com.wowza.wms.rtp.model.RTPPushPublishSession;
import com.wowza.wms.rtp.model.RTPSession;
import com.wowza.wms.rtp.model.RTPStream;
import com.wowza.wms.rtsp.RTSPRequestMessage;
import com.wowza.wms.rtsp.RTSPResponseMessages;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.util.RTPUtils;

public class ScheduleMulticast extends ModuleBase {
	
	private final static WMSLogger logger = WMSLoggerFactory.getLogger(ScheduleMulticast.class);
	public static final String QUERYSTRING_MULTICASTPLAY = "multicastplay";
	public static final String SDPFILEEXTENSION = ".sdp";
	
	private String sdpStorageDir = "${com.wowza.wms.context.VHostConfigHome}/applications/${com.wowza.wms.context.Application}/sdp";
	
	private Map<IMediaStream, ScheduleMulticastSession> streamToSession = new HashMap<IMediaStream, ScheduleMulticastSession>();
	private Map<String, ScheduleMulticastSession> streamNameToSession = new HashMap<String, ScheduleMulticastSession>();
	private List<ScheduleMulticastSession> sessionToStart = new ArrayList<ScheduleMulticastSession>();
	private Set<IMediaStream> localStreamSet = new HashSet<IMediaStream>();
	private Object lock = new Object();
	
	private IApplicationInstance appInstance = null;
	private ScheduleMulticastMapper mapper = null;
	private ScheduleMulticastThread t = null;

	public void onAppStart(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		logger.info("onAppStart: " + fullname);
		
		// Multicast init
		this.appInstance = appInstance;
		this.init();
		
		// Mapper init
		mapper = new ScheduleMulticastMapper(appInstance);
		mapper.init();
		
		// New Thread to run Multicast
		t = new ScheduleMulticastThread(this);
		t.setDaemon(true);
		t.start();
	}
	
	private void init() {
		logger.info("ScheduleMulticast.init");
		Map<String, String> pathMap = new HashMap<String, String>();
		pathMap.put("com.wowza.wms.context.VHost", appInstance.getVHost().getName());
		pathMap.put("com.wowza.wms.context.VHostConfigHome", appInstance.getVHost().getHomePath());
		pathMap.put("com.wowza.wms.context.Application", appInstance.getApplication().getName());
		pathMap.put("com.wowza.wms.context.ApplicationInstance", appInstance.getName());
		
		// Create SDP directory
		sdpStorageDir =  SystemUtils.expandEnvironmentVariables(sdpStorageDir, pathMap);
		try {
			File file = new File(sdpStorageDir);
			if (!file.exists())
				file.mkdirs();
		} catch(Exception e) {
			logger.error("init["+appInstance.getContextStr()+"]: "+e.toString());
		}
	
	}

	public void onAppStop(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		logger.info("onAppStop: " + fullname);
		if (t != null)
			t.doStop();
		t = null;
	}

	public void onStreamCreate(IMediaStream stream) {
		logger.info("onStreamCreate: " + stream.getSrc());
		stream.addClientListener(new ScheduleStreamActionNotify(this));
	}

	public void onStreamDestroy(IMediaStream stream) {
		logger.info("onStreamDestroy: " + stream.getSrc());
		ScheduleMulticastSession oldSession = streamToSession.get(stream);
		if (oldSession != null) {
			stopMulticastStream(stream, oldSession.getStreamName());
		}
	}

	public void onRTPSessionCreate(RTPSession rtpSession) {
		logger.info("onRTPSessionCreate: " + rtpSession.getSessionId());
		rtpSession.addActionListener(new ScheduleRTSPActionNotify(this));
	}
	
	public void onDescribe(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
		logger.info("onDescribe: " + rtspSession.getSessionId());
		
		// If no rtpStream, return.
		RTPStream rtpStream = rtspSession.getRTSPStream();
		if (rtpStream == null) {
			return ;
		} else {
			logger.info("rtsp stream name: " + rtpStream.getStreamName());
		}
				
		// If no query, return.
		String queryStr = rtspSession.getQueryStr();
		if (queryStr == null) {
			return ;
		} else {
			logger.info("queryStr: "+queryStr);
		}
		
		// If not multicastplay, return.
		Map<String, String> queryMap = HTTPUtils.splitQueryStr(queryStr);
		if (!queryMap.containsKey(QUERYSTRING_MULTICASTPLAY)) {
			return ;
		}
		
		// If multicastplay, change session to multicastIP and multicastPort.
		ScheduleMulticastSession multicastSession = null;
		RTPDestination rtpDestination = null;
		String streamName = rtpStream.getStreamName();
		String destinationName = queryMap.get(QUERYSTRING_MULTICASTPLAY);
		
		synchronized (lock) {
			multicastSession = streamNameToSession.get(streamName);
			if (multicastSession != null) {
				List<ScheduleMulticastSessionDestination> destinations = multicastSession.getRTPPublishDestinations();
				if (destinationName != null && destinations.size() > 1) {
					Iterator<ScheduleMulticastSessionDestination> iter = destinations.iterator();
					while (iter.hasNext()) {
						RTPDestination localrtpDestination = iter.next().getRTPDestination();
						if (destinationName.equals(localrtpDestination.getName())) {
							rtpDestination = localrtpDestination;
							break ;
						}
					}
				}
				
				if (rtpDestination == null && destinations.size() > 0) {
					rtpDestination = destinations.get(0).getRTPDestination();
				}
			}
		
			if (rtpDestination == null) {
				logger.warn("onDescribe: It Can't Play multicast[" + streamName + "]");
				return ;
			}
		
			logger.info("onDescribe: Play multicast[" + streamName + "]:" + rtpDestination.toString());
			rtpStream.setRTPDestination(rtpDestination);
		}
	}
	
	public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
		logger.info("onPublish: streamName=" + streamName);
		if (!localStreamSet.contains(stream)) {
			startMulticastStream(stream, streamName);
		}
	}
	
	public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
		logger.info("onUnPublish: streamName=" + streamName);
		stopMulticastStream(stream, streamName);
	}
	
	private void startMulticastStream(IMediaStream stream, String streamName) {
		ScheduleMulticastSession oldSession = streamNameToSession.get(streamName);
		if (oldSession != null) {
			this.closeMulticastSession(oldSession);
		}
		
		localStreamSet.add(stream);
		ScheduleMulticastSession newSession = new ScheduleMulticastSession(streamName);
		newSession.setStream(stream);
		this.openMulticastSession(newSession);		
	}
	
	private void stopMulticastStream(IMediaStream stream, String streamName) {
		ScheduleMulticastSession oldSession = streamNameToSession.get(streamName);
		if (oldSession != null) {
			this.closeMulticastSession(oldSession);
		}
		localStreamSet.remove(stream);
	}

	public void runMulticast() {
		// TODO
	}
	
	public void openMulticastSession(ScheduleMulticastSession multicastSession) {
		synchronized (lock) {
			// Get Destinations
			mapper.getDestinations(multicastSession);
		
			// Add MulticastSession to HashMap and Set
			streamToSession.put(multicastSession.getStream(), multicastSession);
			streamNameToSession.put(multicastSession.getStreamName(), multicastSession);
			sessionToStart.add(multicastSession);
		}
	}
	
	public void closeMulticastSession(ScheduleMulticastSession multicastSession) {
		synchronized (lock) {
			// Stop Multicast RTP
			List<ScheduleMulticastSessionDestination> destinations = multicastSession.getRTPPublishDestinations();
			Iterator<ScheduleMulticastSessionDestination> iter = destinations.iterator();
			while (iter.hasNext()) {
				ScheduleMulticastSessionDestination destination = iter.next();
				RTPDestination rtpDestination = destination.getRTPDestination();
				if (rtpDestination == null) {
					continue;
				}
			
				try {
					RTPPushPublishSession rtpPushPublishSession = destination.getRTPPushPublishSession();
					if (rtpPushPublishSession != null)
						RTPUtils.stopRTPPull(rtpPushPublishSession);
				} catch (Exception e) {
					logger.warn("closeMulticastSession[" + appInstance.getContextStr() + "][stop]: " + e.toString());
				}
			}
		
			// Remove MulticastSession from HashMap and Set
			streamToSession.remove(multicastSession.getStream());
			streamNameToSession.remove(multicastSession.getStreamName());
			sessionToStart.remove(multicastSession);
			
			// Recycle Destinations
			mapper.recycleDestiantions(multicastSession);
		
			// Remove SDP file
			destinations = multicastSession.getRTPPublishDestinations();
			iter = destinations.iterator();
			while(iter.hasNext()) {
				ScheduleMulticastSessionDestination destination = iter.next();
				RTPDestination rtpDestination = destination.getRTPDestination();
				String appendStr = "";
				if (destinations.size() > 1)
					appendStr = "_" + rtpDestination.getName();
				String streamName = multicastSession.getStreamName();
				File file = new File(sdpStorageDir+"/" + FileUtils.streamNameToValidFilename(streamName) + appendStr + SDPFILEEXTENSION);
				try {
					if (file.exists())
						file.delete();
				} catch(Exception e) {
					logger.warn("deleteSDPFile[" + appInstance.getContextStr() + "]: " + e.toString());
				}
			}
		}
	}
}
