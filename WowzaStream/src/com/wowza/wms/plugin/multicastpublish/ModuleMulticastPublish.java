package com.wowza.wms.plugin.multicastpublish;

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
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;
import com.wowza.wms.module.ModuleBase;
import com.wowza.wms.rtp.model.RTPDestination;
import com.wowza.wms.rtp.model.RTPPushPublishSession;
import com.wowza.wms.rtp.model.RTPSession;
import com.wowza.wms.rtp.model.RTPStream;
import com.wowza.wms.rtsp.RTSPRequestMessage;
import com.wowza.wms.rtsp.RTSPResponseMessages;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.util.RTPUtils;

public class ModuleMulticastPublish extends ModuleBase
{
	public static final String SDPFILEEXTENSION = ".sdp";
	public static final String QUERYSTRING_MULTICASTPLAY = "multicastplay";
		
	private Object lock = new Object();
	private IApplicationInstance appInstance = null;
	private MulticastPublishWorkerThread workerThread = null;
	private MulticastPublishAddressMapper addressMapper = null;

	private Set<IMediaStream> localStreamSet = new HashSet<IMediaStream>();
	
	private int maximumStartDelay = 10000;
	private String sdpStorageDir = "${com.wowza.wms.context.VHostConfigHome}/applications/${com.wowza.wms.context.Application}/sdp";
	private boolean writeSDPFiles = true;
	private boolean removeSDPFiles = true;
	
	private List<MulticastPublishSession> sessionsToStart = new ArrayList<MulticastPublishSession>();
	private List<MulticastPublishSession> sessions = new ArrayList<MulticastPublishSession>();
	private Map<String, MulticastPublishSession> streamNameToSession = new HashMap<String, MulticastPublishSession>();
	private Map<IMediaStream, MulticastPublishSession> streamToSession = new HashMap<IMediaStream, MulticastPublishSession>();
	
	public void onAppStart(IApplicationInstance appInstance)
	{
		getLogger().info("ModuleMulticastPublish.onAppStart["+appInstance.getContextStr()+"]");

		this.appInstance = appInstance;
		
		init();
		
		addressMapper = new MulticastPublishAddressMapper();
		addressMapper.init(appInstance);
		
		this.workerThread = new MulticastPublishWorkerThread(this);
		this.workerThread.setName("ModuleMulticastPublish");
		this.workerThread.setDaemon(true);
		this.workerThread.start();
	}
	
	public void init()
	{
		WMSProperties props = this.appInstance.getProperties();
		
		maximumStartDelay = props.getPropertyInt("multicastPublishMaximumStartDelay", maximumStartDelay);
		writeSDPFiles = props.getPropertyBoolean("multicastPublishWriteSDPFiles", writeSDPFiles);
		removeSDPFiles = props.getPropertyBoolean("multicastPublishRemoveSDPFiles", removeSDPFiles);
		sdpStorageDir = props.getPropertyStr("multicastPublishSDPStorageDir", sdpStorageDir);

		Map<String, String> pathMap = new HashMap<String, String>();
		pathMap.put("com.wowza.wms.context.VHost", appInstance.getVHost().getName());
		pathMap.put("com.wowza.wms.context.VHostConfigHome", appInstance.getVHost().getHomePath());
		pathMap.put("com.wowza.wms.context.Application", appInstance.getApplication().getName());
		pathMap.put("com.wowza.wms.context.ApplicationInstance", appInstance.getName());
		
		sdpStorageDir =  SystemUtils.expandEnvironmentVariables(sdpStorageDir, pathMap);
		
		try
		{
			File file = new File(sdpStorageDir);
			if (!file.exists())
				file.mkdirs();
		}
		catch(Exception e)
		{
			getLogger().error("ModuleMulticastPublish.init["+appInstance.getContextStr()+"]: "+e.toString());
		}
	}
	
	public void onAppStop(IApplicationInstance appInstance)
	{
		getLogger().info("ModuleMulticastPublish.onAppStop["+appInstance.getContextStr()+"]");
		
		if (this.workerThread != null)
			this.workerThread.doStop();
		this.workerThread = null;
	}
	
	public void onStreamCreate(IMediaStream stream)
	{
		stream.addClientListener(new MulticastPublishStreamActionNotify(this));
	}
	
	public void onStreamDestroy(IMediaStream stream)
	{
		synchronized(lock)
		{
			MulticastPublishSession oldSession = streamToSession.get(stream);
			if (oldSession != null)
				closePublishSession(oldSession);
			
			localStreamSet.remove(stream);
		}
	}
	
	public void closePublishSession(MulticastPublishSession publishSession)
	{

		synchronized(lock)
		{
			List<MulticastPublishSessionDestination> rtpDestinations = publishSession.getRTPPublishDestinations();
			Iterator<MulticastPublishSessionDestination> iterd = rtpDestinations.iterator();
			while(iterd.hasNext())
			{
				MulticastPublishSessionDestination destination = iterd.next();

				RTPDestination rtpDestination = destination.getRTPDestination();
				if (rtpDestination == null)
					continue;
				
				getLogger().info("ModuleMulticastPublish.stopStream["+appInstance.getContextStr()+"]: "+rtpDestination.toString());

				try
				{	
					RTPPushPublishSession rtpPushPublishSession = destination.getRTPPushPublishSession();
					if (rtpPushPublishSession != null)
						RTPUtils.stopRTPPull(rtpPushPublishSession);
				}
				catch(Exception e)
				{
					getLogger().warn("ModuleMulticastPublish.closePublishSession["+appInstance.getContextStr()+"][stop]: "+e.toString());
				}
			}

			streamNameToSession.remove(publishSession.getStreamName());
			streamToSession.remove(publishSession.getStream());
			sessions.remove(publishSession);
			sessionsToStart.remove(publishSession);

			addressMapper.recycleDestinations(publishSession);

			if (writeSDPFiles && removeSDPFiles)
			{
				rtpDestinations = publishSession.getRTPPublishDestinations();
				iterd = rtpDestinations.iterator();
				while(iterd.hasNext())
				{
					MulticastPublishSessionDestination destination = iterd.next();
					RTPDestination rtpDestination = destination.getRTPDestination();

					String appendStr = "";
					if (rtpDestinations.size() > 1)
						appendStr = "_"+rtpDestination.getName();

					String streamName = publishSession.getStreamName();
					File file = new File(sdpStorageDir+"/"+FileUtils.streamNameToValidFilename(streamName)+appendStr+SDPFILEEXTENSION);
					try
					{
						if (file.exists())
							file.delete();
					}
					catch(Exception e)
					{
					}
				}
			}
		}
		
	}
	
//	public void getDestinations(MulticastPublishSession publishSession)
//	{
//		synchronized(lock)
//		{
//			addressMapper.getDestinations(publishSession);
//		}
//	}
	
//	public void startStream(IMediaStream stream, String streamName)
//	{
//		synchronized(lock)
//		{
//			MulticastPublishSession oldSession = streamNameToSession.get(streamName);
//			if (oldSession != null)
//				closePublishSession(oldSession);
//			
//			localStreamSet.add(stream);
//			
//			MulticastPublishSession publishSession = new MulticastPublishSession();
//			publishSession.setStream(stream);
//			publishSession.setStreamName(streamName);
//			addressMapper.getDestinations(publishSession);
//			
//			sessionsToStart.add(publishSession);
//			streamNameToSession.put(streamName, publishSession);
//			streamToSession.put(stream, publishSession);
//		}
//	}
	
//	public void stopStream(IMediaStream stream, String streamName)
//	{
//		synchronized(lock)
//		{
//			MulticastPublishSession oldSession = streamNameToSession.get(streamName);
//			if (oldSession != null)
//				closePublishSession(oldSession);
//			
//			localStreamSet.remove(stream);
//		}
//	}

	public void onStreamPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
	{
		getLogger().info("ModuleMulticastPublish.onStreamPublish["+appInstance.getContextStr()+"]: "+streamName);
		if (!localStreamSet.contains(stream)) {
			synchronized(lock)
			{
				MulticastPublishSession oldSession = streamNameToSession.get(streamName);
				if (oldSession != null)
					closePublishSession(oldSession);
				
				localStreamSet.add(stream);
				
				MulticastPublishSession publishSession = new MulticastPublishSession();
				publishSession.setStream(stream);
				publishSession.setStreamName(streamName);
				addressMapper.getDestinations(publishSession);
				
				sessionsToStart.add(publishSession);
				streamNameToSession.put(streamName, publishSession);
				streamToSession.put(stream, publishSession);
			}
		}
	}
	
	public void onStreamUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
	{
		getLogger().info("ModuleMulticastPublish.onStreamUnPublish["+appInstance.getContextStr()+"]: "+streamName);
		synchronized(lock)
		{
			MulticastPublishSession oldSession = streamNameToSession.get(streamName);
			if (oldSession != null)
				closePublishSession(oldSession);
			
			localStreamSet.remove(stream);
		}
	}
	
	public void onRTPSessionCreate(RTPSession rtpSession)
	{
		rtpSession.addActionListener(new MulticastPublishRTSPActionNotify(this));
	}
	
	public void onRTPDescribe(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
		while(true)
		{
			String queryStr = rtspSession.getQueryStr();
//			System.out.println("queryStr: "+queryStr);

			if (queryStr == null)
				break;
			
			Map<String, String> queryMap = HTTPUtils.splitQueryStr(queryStr);
			if (!queryMap.containsKey(QUERYSTRING_MULTICASTPLAY))
				break;

			RTPStream rtpStream = rtspSession.getRTSPStream();
			if (rtpStream == null)
				break;
			
			String streamName = rtpStream.getStreamName();
			String destinationName = queryMap.get(QUERYSTRING_MULTICASTPLAY);
			
//			System.out.println("destinationName: "+destinationName);

			MulticastPublishSession publishSession = null;
			RTPDestination rtpDestination = null;
			synchronized(lock)
			{
				publishSession = streamNameToSession.get(streamName);
				if (publishSession != null)
				{
					List<MulticastPublishSessionDestination> rtpDestinations = publishSession.getRTPPublishDestinations();
					
					if (destinationName != null && rtpDestinations.size() > 1)
					{
						Iterator<MulticastPublishSessionDestination> iterd = rtpDestinations.iterator();
						while(iterd.hasNext())
						{
							MulticastPublishSessionDestination destination = iterd.next();
							RTPDestination localRTPDestination = destination.getRTPDestination();
							if (localRTPDestination != null)
							{
								if (destinationName.equals(localRTPDestination.getName()))
								{
									rtpDestination = localRTPDestination;
									break;
								}
							}
						}
					}

					if (rtpDestination == null && rtpDestinations.size() > 0)
						rtpDestination = rtpDestinations.get(0).getRTPDestination();
				}
			}
			
			//System.out.println("rtpDestination: "+rtpDestination);
			if (rtpDestination == null)
				break;
			
			getLogger().info("ModuleMulticastPublish.onRTPDescribe["+appInstance.getContextStr()+"]: Play multicast["+streamName+"]: "+rtpDestination.toString());
			rtpStream.setRTPDestination(rtpDestination);
			break;
		}
		
	}
	
	public void workerRun()
	{
		long currTime = System.currentTimeMillis();
		
		List<MulticastPublishSession> localSessions = new ArrayList<MulticastPublishSession>();
		synchronized(lock)
		{
			localSessions.addAll(sessionsToStart);
		}
				
		Iterator<MulticastPublishSession> iter = localSessions.iterator();
		while(iter.hasNext())
		{
			MulticastPublishSession publishSession = iter.next();
			
			try
			{
				boolean startPublish = false;
				
				IMediaStream stream = publishSession.getStream();
				String streamName = publishSession.getStreamName();
				
				if (!startPublish)
					startPublish = (currTime-publishSession.getStartTime()) > maximumStartDelay;

				if (!startPublish)
				{
					boolean streamReady = stream.isPublishStreamReady(publishSession.isStreamAudio(), publishSession.isStreamVideo());
					if (streamReady)
					{
						AMFPacket[] lastPackets = MulticastPublishUtils.getLastPacketsByType(stream);
						AMFPacket lastAudioPacket = lastPackets[0];
						AMFPacket lastVideoPacket = lastPackets[1];
						
						startPublish = ((!publishSession.isStreamAudio() || lastAudioPacket != null) && (!publishSession.isStreamVideo() || lastVideoPacket != null));
					}
				}
				
				if (startPublish)
				{
					List<MulticastPublishSessionDestination> rtpDestinations = publishSession.getRTPPublishDestinations();
					Iterator<MulticastPublishSessionDestination> iterd = rtpDestinations.iterator();
					while(iterd.hasNext())
					{
						MulticastPublishSessionDestination destination = iterd.next();
						RTPDestination rtpDestination = destination.getRTPDestination();
						
						if (rtpDestination == null)
							continue;
						
						getLogger().info("ModuleMulticastPublish.startStream["+stream.getContextStr()+"]: "+rtpDestination.toString());

						try
						{
							RTPPushPublishSession rtpPushPublishSession = RTPUtils.startRTPPull(this.appInstance, publishSession.getStreamName(), rtpDestination);
							if (rtpPushPublishSession != null)
							{
								String sdpData = rtpPushPublishSession.getSDPData();
								if (sdpData != null)
									sdpData = RTPUtils.updateSDPDestination(rtpDestination, sdpData);

								destination.setRTPPushPublishSession(rtpPushPublishSession);
								destination.setSDPData(sdpData);

								String appendStr = "";
								if (rtpDestinations.size() > 1)
									appendStr = "_"+rtpDestination.getName();
								
								if (writeSDPFiles)
									MulticastPublishUtils.writeSDPFile(new File(sdpStorageDir+"/"+FileUtils.streamNameToValidFilename(streamName)+appendStr+SDPFILEEXTENSION), sdpData);
							}
						}
						catch(Exception e)
						{
							getLogger().warn("ModuleMulticastPublish.workerRun["+stream.getContextStr()+"][start]: "+e.toString());
						}
					}

					synchronized(lock)
					{
						sessionsToStart.remove(publishSession);
						sessions.add(publishSession);
					}
				}
				
			}
			catch(Exception e)
			{
				getLogger().warn("ModuleMulticastPublish.workerRun["+appInstance.getContextStr()+"]: "+e.toString());
			}
		}
		
		addressMapper.work();
	}
}