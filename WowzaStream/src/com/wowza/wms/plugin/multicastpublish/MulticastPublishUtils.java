package com.wowza.wms.plugin.multicastpublish;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;

import com.wowza.io.WowzaRandomAccessFile;
import com.wowza.wms.amf.AMFPacket;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.vhost.IVHost;

public class MulticastPublishUtils
{	
	public static void writeSDPFile(File outFile, String sdpData)
	{
		RandomAccessFile outf = null;
		try
		{
			if (outFile.exists())
			{
				try
				{
					outFile.delete();
				}
				catch(Exception e)
				{
					
				}
			}
			
			File parentFile = outFile.getParentFile();
			if (parentFile != null)
			{
				if (!parentFile.exists())
				{
					try
					{
						parentFile.mkdirs();
					}
					catch(Exception e)
					{
						
					}
				}
			}
			
			outf = new WowzaRandomAccessFile(outFile, "rw");
			outf.write(sdpData.getBytes());
		}
		catch(Exception e)
		{
			WMSLoggerFactory.getLogger(MulticastPublishUtils.class).error("MulticastPublishUtils.writeSDPFile: "+ e.toString());
		}
		
		try
		{
			if (outf != null)
				outf.close();
			outf = null;
		}
		catch(Exception e)
		{
			WMSLoggerFactory.getLogger(MulticastPublishUtils.class).error("MulticastPublishUtils.writeSDPFile[close]: "+ e.toString());
		}
	}
	
	public static AMFPacket[] getLastPacketsByType(IMediaStream localStream)
	{
		AMFPacket[] returnPackets = new AMFPacket[4];
		
		int audioCodecId = localStream.getPublishAudioCodecId();
		int videoCodecId = localStream.getPublishVideoCodecId();

		AMFPacket lastAudioPacket = null;
		AMFPacket lastVideoPacket = null;
		AMFPacket lastAudioConfig = null;
		AMFPacket lastVideoConfig = null;
		
		while(true)
		{
			List<AMFPacket> packets = localStream.getPlayPackets();
			if (packets == null)
				break;
			if (packets.size() <= 0)
				break;
			AMFPacket packet = null;
			
			for(int i=(packets.size()-1);i>=0;i--)
			{
				packet = packets.get(i);
				int packetType = packet.getType();
				if (packetType == IVHost.CONTENTTYPE_AUDIO)
				{
					if (lastAudioPacket == null)
					{
						while(true)
						{
							if (packet.getSize() < 2)
								break;
							int secondByte = packet.getSecondByte();
							if (audioCodecId == IVHost.CODEC_AUDIO_AAC && (secondByte != 0x01))
								break;
							
							lastAudioPacket = packet;
							lastAudioConfig = localStream.getAudioCodecConfigPacket(packet.getAbsTimecode());
							break;
						}
					}
				}
				else if (packetType == IVHost.CONTENTTYPE_VIDEO)
				{
					if (lastVideoPacket == null)
					{
						while(true)
						{
							if (packet.getSize() < 2)
								break;
							int secondByte = packet.getSecondByte();
							if (videoCodecId == IVHost.CODEC_VIDEO_H264 && (secondByte != 0x01))
								break;
							
							lastVideoPacket = packet;
							lastVideoConfig = localStream.getVideoCodecConfigPacket(packet.getAbsTimecode());
							break;
						}
					}
				}
				
				if (lastVideoPacket != null && lastAudioPacket != null)
					break;
			}
			break;
		}
		
		returnPackets[0] = lastAudioPacket;
		returnPackets[1] = lastVideoPacket;
		returnPackets[2] = lastAudioConfig;
		returnPackets[3] = lastVideoConfig;
		return returnPackets;
	}

}
