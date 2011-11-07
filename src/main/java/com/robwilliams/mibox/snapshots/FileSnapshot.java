package com.robwilliams.mibox.snapshots;

import java.util.Date;

import com.robwilliams.mibox.snapshots.actions.FileSnapshotAction;

public abstract class FileSnapshot {	
	protected String fileName;
	protected Date lastModifiedDate;
	protected String hash;
	protected FileSnapshotAction action;
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public Date getLastModifiedDate() {
		return lastModifiedDate;
	}
	public void setLastModifiedDate(Date lastModifiedDate) {
		this.lastModifiedDate = lastModifiedDate;
	}
	public String getHash() {
		return hash;
	}
	public void setHash(String hash) {
		this.hash = hash;
	}
	public FileSnapshotAction getAction() {
		return action;
	}
	public void setAction(FileSnapshotAction action) {
		this.action = action;
	}

}
