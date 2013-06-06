package com.wowza.wms.plugin.multicastpublish;

import java.util.ArrayList;
import java.util.List;

import com.wowza.wms.stream.IMediaStream;

public class MulticastPublishSession
{
	private IMediaStream stream = null;
	private String streamName = null;
	private List<MulticastPublishSessionDestination> destinations = new ArrayList<MulticastPublishSessionDestination>();
	private boolean isStreamVideo = true;
	private boolean isStreamAudio = true;
	private MulticastPublishAddressMapperExtraData mapperExtraData = null;
	private Object lock = new Object();
	private long startTime = System.currentTimeMillis();
	
	public List<MulticastPublishSessionDestination> getRTPPublishDestinations()
	{
		List<MulticastPublishSessionDestination> ret = new ArrayList<MulticastPublishSessionDestination>();
		
		synchronized(lock)
		{
			ret.addAll(destinations);
		}
		
		return ret;
	}
	
	public void addAllRTPPublishDestination(List<MulticastPublishSessionDestination> rtpPublishDestinations)
	{
		synchronized(lock)
		{
			destinations.addAll(rtpPublishDestinations);
		}
	}

	public void addRTPPublishDestination(MulticastPublishSessionDestination rtpPublishDestination)
	{
		synchronized(lock)
		{
			destinations.add(rtpPublishDestination);
		}
	}
	
	public void clearRTPPublishDestination()
	{
		synchronized(lock)
		{
			destinations.clear();
		}
	}
	
	public IMediaStream getStream()
	{
		return stream;
	}
	public void setStream(IMediaStream stream)
	{
		this.stream = stream;
	}
	public String getStreamName()
	{
		return streamName;
	}
	public void setStreamName(String streamName)
	{
		this.streamName = streamName;
	}
	public boolean isStreamVideo()
	{
		return isStreamVideo;
	}
	public void setStreamVideo(boolean isStreamVideo)
	{
		this.isStreamVideo = isStreamVideo;
	}
	public boolean isStreamAudio()
	{
		return isStreamAudio;
	}
	public void setStreamAudio(boolean isStreamAudio)
	{
		this.isStreamAudio = isStreamAudio;
	}
	public MulticastPublishAddressMapperExtraData getMapperExtraData()
	{
		return mapperExtraData;
	}
	public void setMapperExtraData(MulticastPublishAddressMapperExtraData mapperExtraData)
	{
		this.mapperExtraData = mapperExtraData;
	}
	public long getStartTime()
	{
		return startTime;
	}
	public void setStartTime(long startTime)
	{
		this.startTime = startTime;
	}
}
