package com.wowza.wms.plugin.multicastpublish;

import com.wowza.wms.rtp.model.IRTSPActionNotify;
import com.wowza.wms.rtp.model.RTPSession;
import com.wowza.wms.rtsp.RTSPRequestMessage;
import com.wowza.wms.rtsp.RTSPResponseMessages;

public class MulticastPublishRTSPActionNotify implements IRTSPActionNotify
{
	ModuleMulticastPublish multicastPublishModule = null;
	
	public MulticastPublishRTSPActionNotify(ModuleMulticastPublish multicastPublishModule)
	{
		this.multicastPublishModule = multicastPublishModule;
	}

	public void onDescribe(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
		this.multicastPublishModule.onRTPDescribe(rtspSession, req, resp);
	}

	public void onAnnounce(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

	public void onGetParameter(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

	public void onSetParameter(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

	public void onOptions(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

	public void onPause(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

	public void onPlay(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

	public void onRecord(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

	public void onRedirect(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

	public void onSetup(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

	public void onTeardown(RTPSession rtspSession, RTSPRequestMessage req, RTSPResponseMessages resp)
	{
	}

}
