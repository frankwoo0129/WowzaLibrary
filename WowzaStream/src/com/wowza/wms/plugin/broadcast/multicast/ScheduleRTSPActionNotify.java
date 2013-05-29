package com.wowza.wms.plugin.broadcast.multicast;

import com.wowza.wms.rtp.model.IRTSPActionNotify;
import com.wowza.wms.rtp.model.RTPSession;
import com.wowza.wms.rtsp.RTSPRequestMessage;
import com.wowza.wms.rtsp.RTSPResponseMessages;

public class ScheduleRTSPActionNotify implements IRTSPActionNotify {
	
	private ScheduleMulticast schedulemulticast = null;
	
	public ScheduleRTSPActionNotify(ScheduleMulticast schedulemulticast) {
		this.schedulemulticast = schedulemulticast;
	}

	@Override
	public void onAnnounce(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

	@Override
	public void onDescribe(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
		this.schedulemulticast.onDescribe(rtspSession, req, resp);
	}

	@Override
	public void onGetParameter(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

	@Override
	public void onOptions(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

	@Override
	public void onPause(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

	@Override
	public void onPlay(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

	@Override
	public void onRecord(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

	@Override
	public void onRedirect(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

	@Override
	public void onSetParameter(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

	@Override
	public void onSetup(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

	@Override
	public void onTeardown(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp) {
	}

}
