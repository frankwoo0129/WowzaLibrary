package com.wowza.wms.plugin.broadcast.multicast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wowza.util.HTTPUtils;
import com.wowza.util.SystemUtils;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.rtp.model.RTPSession;
import com.wowza.wms.rtp.model.RTPStream;
import com.wowza.wms.rtsp.RTSPRequestMessage;
import com.wowza.wms.rtsp.RTSPResponseMessages;
import com.wowza.wms.stream.IMediaStream;

public class ScheduleMulticast extends ModuleBase {
	
	private final static WMSLogger logger = WMSLoggerFactory.getLogger(ScheduleMulticast.class);
	public static final String QUERYSTRING_MULTICASTPLAY = "multicastplay";
	
	private String sdpStorageDir = "${com.wowza.wms.context.VHostConfigHome}/applications/${com.wowza.wms.context.Application}/sdp";
	private IApplicationInstance appInstance = null;
	private Map<IMediaStream, ScheduleMulticastSession> streamToSession = new HashMap<IMediaStream, ScheduleMulticastSession>();
	private Map<String, ScheduleMulticastSession> streamNameToSession = new HashMap<String, ScheduleMulticastSession>();
	private List<ScheduleMulticastSession> sessionToStart = new ArrayList<ScheduleMulticastSession>();
	private Set<IMediaStream> localStreamSet = new HashSet<IMediaStream>();
	private Object lock = new Object();

	public void onAppStart(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		logger.info("onAppStart: " + fullname);
		this.appInstance = appInstance;
		init();
		// TODO new Thread to runMulticast
	}
	
	private void init() {
		logger.info("ScheduleMulticast.init");
		Map<String, String> pathMap = new HashMap<String, String>();
		pathMap.put("com.wowza.wms.context.VHost", appInstance.getVHost().getName());
		pathMap.put("com.wowza.wms.context.VHostConfigHome", appInstance.getVHost().getHomePath());
		pathMap.put("com.wowza.wms.context.Application", appInstance.getApplication().getName());
		pathMap.put("com.wowza.wms.context.ApplicationInstance", appInstance.getName());
		
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
		// TODO stop Thread
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

	public void onRTPSessionDestroy(RTPSession rtpSession) {
		logger.info("onRTPSessionDestroy: " + rtpSession.getSessionId());
	}
	
	public void onDescribe(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
		logger.info("onDescribe: " + rtspSession.getSessionId());
		String queryStr = rtspSession.getQueryStr();
		logger.info("queryStr: "+queryStr);

		if (queryStr == null) {
			return ;
		}
		
		RTPStream rtpStream = rtspSession.getRTSPStream();
		if (rtpStream == null) {
			return ;
		}
		
		// If not multicastplay, return.
		Map<String, String> queryMap = HTTPUtils.splitQueryStr(queryStr);
		if (!queryMap.containsKey(QUERYSTRING_MULTICASTPLAY)) {
			return ;
		}
		
		// If multicastplay, change session to multicastIP and multicastPort.
		// TODO
		
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
		synchronized (lock) {
			ScheduleMulticastSession oldSession = streamNameToSession.get(streamName);
			if (oldSession != null) {
				this.closeMulticastSession(oldSession);
			}
		}
		
		localStreamSet.add(stream);
		ScheduleMulticastSession newSession = new ScheduleMulticastSession(streamName);
		newSession.setStream(stream);
		// TODO getDestination
		streamToSession.put(stream, newSession);
		streamNameToSession.put(streamName, newSession);
		sessionToStart.add(newSession);
	}
	
	private void stopMulticastStream(IMediaStream stream, String streamName) {
		synchronized (lock) {
			ScheduleMulticastSession oldSession = streamNameToSession.get(streamName);
			if (oldSession != null) {
				this.closeMulticastSession(oldSession);
			}
			localStreamSet.remove(stream);
		}
	}

	public void runMulticast() {
		// TODO
	}
	
	public void closeMulticastSession(ScheduleMulticastSession multicastSession) {
		// TODO
	}
}
