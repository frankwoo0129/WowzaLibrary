package com.wowza.wms.plugin.broadcast.multicast;

import java.util.ArrayList;
import java.util.List;

import com.wowza.wms.stream.IMediaStream;

public class ScheduleMulticastSession {
	
	private IMediaStream stream = null;
	private final String streamName;
	private List<ScheduleMulticastSessionDestination> destinations = new ArrayList<ScheduleMulticastSessionDestination>();
	private boolean isStreamVideo = true;
	private boolean isStreamAudio = true;
	private Object lock = new Object();
	
	public List<ScheduleMulticastSessionDestination> getRTPPublishDestinations() {
		List<ScheduleMulticastSessionDestination> ret = new ArrayList<ScheduleMulticastSessionDestination>();
		synchronized(lock) {
			ret.addAll(destinations);
		}
		return ret;
	}
	
	public void addRTPPublishDestination(ScheduleMulticastSessionDestination destination) {
		synchronized (lock) {
			destinations.add(destination);
		}
	}
	
	public void clearRTPPublishDestinations() {
		synchronized (lock) {
			destinations.clear();
		}
	}
	
	public void addAllRTPPublishDestinations(List<ScheduleMulticastSessionDestination> destinations) {
		synchronized (lock) {
			destinations.addAll(destinations);
		}
	}
	
	public ScheduleMulticastSession(String streamName) {
		this.streamName = streamName;
	}
	
	public String getStreamName() {
		return this.streamName;
	}
	
	public void setStream(IMediaStream stream) {
		this.stream = stream;
	}
	
	public IMediaStream getStream() {
		return this.stream;
	}
	
	public boolean isStreamVideo() {
		return isStreamVideo;
	}
	
	public void setStreamVideo(boolean isStreamVideo) {
		this.isStreamVideo = isStreamVideo;
	}
	
	public boolean isStreamAudio() {
		return isStreamAudio;
	}
	
	public void setStreamAudio(boolean isStreamAudio) {
		this.isStreamAudio = isStreamAudio;
	}
	
}
