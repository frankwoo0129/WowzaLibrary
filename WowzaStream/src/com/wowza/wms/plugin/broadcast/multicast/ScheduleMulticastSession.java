package com.wowza.wms.plugin.broadcast.multicast;

import com.wowza.wms.stream.IMediaStream;


public class ScheduleMulticastSession {
	
	private IMediaStream stream = null;
	private final String streamName;
//	private List<MulticastPublishSessionDestination> destinations = new ArrayList<MulticastPublishSessionDestination>();
//	private boolean isStreamVideo = true;
//	private boolean isStreamAudio = true;
//	private Object lock = new Object();
//	private long startTime = System.currentTimeMillis();
	
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
}
