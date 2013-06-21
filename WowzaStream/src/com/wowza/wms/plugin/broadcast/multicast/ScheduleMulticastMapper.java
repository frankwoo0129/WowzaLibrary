package com.wowza.wms.plugin.broadcast.multicast;

import java.util.HashMap;
import java.util.Map;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.application.WMSProperties;

public class ScheduleMulticastMapper {
	
	private IApplicationInstance appInstance = null;
	private String multicastMapPath = "${com.wowza.wms.context.VHostConfigHome}/conf/multicastmap.txt";
	
	public ScheduleMulticastMapper(IApplicationInstance appInstance) {
		this.appInstance = appInstance;
	}
	
	public void init() {
		WMSProperties props = this.appInstance.getProperties();
		this.multicastMapPath = props.getPropertyStr("multicastPublishMulticastMapPath", multicastMapPath);
		
		Map<String, String> pathMap = new HashMap<String, String>();
		pathMap.put("com.wowza.wms.context.VHost", appInstance.getVHost().getName());
		pathMap.put("com.wowza.wms.context.VHostConfigHome", appInstance.getVHost().getHomePath());
		pathMap.put("com.wowza.wms.context.Application", appInstance.getApplication().getName());
		pathMap.put("com.wowza.wms.context.ApplicationInstance", appInstance.getName());
		
	}
	
	public void loadMapperByFile() {
		
	}
	
	public void loadMapperByServer() {
		
	}
	
	public void getDestinations(ScheduleMulticastSession multicastSession) {
		
	}
	
	public void recycleDestiantions(ScheduleMulticastSession multicastSession) {
		
	}
}
