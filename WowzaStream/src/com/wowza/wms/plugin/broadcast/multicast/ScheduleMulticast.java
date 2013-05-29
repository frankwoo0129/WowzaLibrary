package com.wowza.wms.plugin.broadcast.multicast;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
//	private RTPPushPublishSession rtpPushPublishSession;
//	private RTPDestination rtpDestination;
//	private String sdpData;
	
//	private Object lock = new Object();

	public void onAppStart(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		logger.info("ScheduleMulticast.onAppStart: " + fullname);
		this.appInstance = appInstance;
		init();
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
			logger.error("ScheduleMulticast.init["+appInstance.getContextStr()+"]: "+e.toString());
		}
//		rtpDestination = new RTPDestination();
//		rtpDestination.setHost("224.1.1.1");
//		rtpDestination.setAudioPort(25552);
//		rtpDestination.setVideoPort(25550);
//		rtpDestination.setRTPWrapped(false);
//		rtpPushPublishSession = RTPUtils.startRTPPull(appInstance, "stream1", rtpDestination);
//		sdpData = rtpPushPublishSession.getSDPData();
//		sdpData = RTPUtils.updateSDPDestination(rtpDestination, sdpData);
//	
	}

	public void onAppStop(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		logger.info("ScheduleMulticast.onAppStop: " + fullname);
//		RTPUtils.stopRTPPull(rtpPushPublishSession);
	}

	public void onStreamCreate(IMediaStream stream) {
		logger.info("ScheduleMulticast.onStreamCreate: " + stream.getSrc());
		stream.addClientListener(new ScheduleStreamActionNotify(this));
	}

	public void onStreamDestroy(IMediaStream stream) {
		logger.info("ScheduleMulticast.onStreamDestroy: " + stream.getSrc());
	}

	public void onRTPSessionCreate(RTPSession rtpSession) {
		logger.info("ScheduleMulticast.onRTPSessionCreate: " + rtpSession.getSessionId());
		rtpSession.addActionListener(new ScheduleRTSPActionNotify(this));
	}

	public void onRTPSessionDestroy(RTPSession rtpSession) {
		logger.info("ScheduleMulticast.onRTPSessionDestroy: " + rtpSession.getSessionId());
	}
	
	public void onDescribe(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
		// TODO Auto-generated method stub
		String queryStr = rtspSession.getQueryStr();
//		System.out.println("queryStr: "+queryStr);
		logger.info("queryStr: "+queryStr);

		if (queryStr == null) {
			logger.warn("queryStr is NULL");
			return ;
		}
		
		Map<String, String> queryMap = HTTPUtils.splitQueryStr(queryStr);
		if (!queryMap.containsKey(QUERYSTRING_MULTICASTPLAY)) {
			logger.warn("NOT multicast play");
			return ;
		}

		RTPStream rtpStream = rtspSession.getRTSPStream();
		if (rtpStream == null) {
			logger.warn("rtpStream is NULL");
			return ;
		}
//		logger.info("host: " + rtpStream.getOutHost());
//		logger.info("StreamName: " + rtpStream.getStreamName());
//		logger.info("vudio: " + rtpStream.getVideoTrack().getRTCPOutPortNum());
//		rtpStream.setRTPDestination(rtpDestination);
		
	}
	
	public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
		// TODO
	}
	
	public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
		// TODO
	}

}
