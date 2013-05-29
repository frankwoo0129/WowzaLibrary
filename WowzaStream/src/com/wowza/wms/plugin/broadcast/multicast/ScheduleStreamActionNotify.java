package com.wowza.wms.plugin.broadcast.multicast;

import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.IMediaStreamActionNotify;

public class ScheduleStreamActionNotify implements IMediaStreamActionNotify{

	private final static WMSLogger log = WMSLoggerFactory.getLogger(ScheduleStreamActionNotify.class);
	
	private ScheduleMulticast schedulemulticast = null;
	
	public ScheduleStreamActionNotify(ScheduleMulticast schedulemulticast) {
		this.schedulemulticast = schedulemulticast;
	}
	
	@Override
	public void onPause(IMediaStream stream, boolean isPause, double location) {
	}

	@Override
	public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset) {
	}

	@Override
	public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
		log.info("ScheduleStreamActionNotify.onPublish: " + streamName);
		this.schedulemulticast.onPublish(stream, streamName, isRecord, isAppend);
	}

	@Override
	public void onSeek(IMediaStream stream, double location) {
	}

	@Override
	public void onStop(IMediaStream stream) {
	}

	@Override
	public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend) {
		log.info("ScheduleStreamActionNotify.onUnPublish: " + streamName);
		this.schedulemulticast.onUnPublish(stream, streamName, isRecord, isAppend);
	}

}
