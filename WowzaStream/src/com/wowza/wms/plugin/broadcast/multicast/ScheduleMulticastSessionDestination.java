package com.wowza.wms.plugin.broadcast.multicast;

import com.wowza.wms.rtp.model.RTPDestination;
import com.wowza.wms.rtp.model.RTPPushPublishSession;

public class ScheduleMulticastSessionDestination {
	
	private RTPDestination rtpDestination = null;
	private String sdpData = null;
	private RTPPushPublishSession rtpPushPublishSession = null;
	
	public RTPDestination getRTPDestination()
	{
		return rtpDestination;
	}
	public void setRTPDestination(RTPDestination rtpDestination)
	{
		this.rtpDestination = rtpDestination;
	}
	public String getSDPData()
	{
		return sdpData;
	}
	public void setSDPData(String sdpData)
	{
		this.sdpData = sdpData;
	}
	public RTPPushPublishSession getRTPPushPublishSession()
	{
		return rtpPushPublishSession;
	}
	public void setRTPPushPublishSession(RTPPushPublishSession rtpPushPublishSession)
	{
		this.rtpPushPublishSession = rtpPushPublishSession;
	}
	
}
