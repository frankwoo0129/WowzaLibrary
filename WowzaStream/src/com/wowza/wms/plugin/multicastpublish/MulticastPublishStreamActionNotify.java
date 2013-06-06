package com.wowza.wms.plugin.multicastpublish;

import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.IMediaStreamActionNotify;

public class MulticastPublishStreamActionNotify implements IMediaStreamActionNotify
{
	ModuleMulticastPublish multicastPublishModule = null;
	
	public MulticastPublishStreamActionNotify(ModuleMulticastPublish multicastPublishModule)
	{
		this.multicastPublishModule = multicastPublishModule;
	}
	
	public void onPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
	{
		this.multicastPublishModule.onStreamPublish(stream, streamName, isRecord, isAppend);
	}

	public void onUnPublish(IMediaStream stream, String streamName, boolean isRecord, boolean isAppend)
	{
		this.multicastPublishModule.onStreamUnPublish(stream, streamName, isRecord, isAppend);
	}

	public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset)
	{
	}

	public void onPause(IMediaStream stream, boolean isPause, double location)
	{
	}

	public void onSeek(IMediaStream stream, double location)
	{
	}

	public void onStop(IMediaStream stream)
	{
	}
	
}
