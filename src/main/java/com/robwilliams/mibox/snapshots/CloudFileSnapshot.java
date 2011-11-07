package com.robwilliams.mibox.snapshots;

import java.util.Date;
import java.util.List;

import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;
import com.robwilliams.mibox.util.DateUtil;

public class CloudFileSnapshot extends FileSnapshot {
	private String source;
	private int pendingDeletes;

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}
	public int getPendingDeletes() {
		return pendingDeletes;
	}

	public void setPendingDeletes(int pendingDeletes) {
		this.pendingDeletes = pendingDeletes;
	}

	public CloudFileSnapshot(String fileName, Date lastModifiedDate, String hash,
			String source, int pendingDeletes) {
		
		this.fileName = fileName;
		this.lastModifiedDate = lastModifiedDate;
		this.hash = hash;
		this.action = null;
		this.source = source;
		this.pendingDeletes = pendingDeletes;
	}
	
	public CloudFileSnapshot(Item item) throws Exception {
		 fileName = item.getName();
		 // parse attributes
		 List<Attribute> attributes = item.getAttributes();
		 for (Attribute attribute : attributes) {
	   		 String attributeName = attribute.getName();
	   		 if (attributeName.equals("lastModifiedDate")) {
	   			 lastModifiedDate = DateUtil.parse(attribute.getValue());
	   		 } else if (attributeName.equals("hash")) {
	   			 hash = attribute.getValue();
	   		 } else if (attributeName.equals("source")) {
	   			 source = attribute.getValue();
	   		 } else if (attributeName.equals("pendingDeletes")) {
	   			 pendingDeletes = Integer.parseInt(attribute.getValue());
	   		 }
	   	 }
	   	 if (fileName == null || lastModifiedDate == null || hash == null || source == null) {
	   		 throw new Exception("Missing attributes from cloud DB for file " + item.getName());
	   	 }
	}
	
}
