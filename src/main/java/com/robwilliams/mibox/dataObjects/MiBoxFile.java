package com.robwilliams.mibox.dataObjects;

import java.util.Date;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import com.robwilliams.mibox.snapshots.LocalFileSnapshot;

@DatabaseTable(tableName = "local_files")
public class MiBoxFile {
	@DatabaseField(id = true)
    private String name; // name of the file (including path relative to MiBox root)
    @DatabaseField
    private String hash; // hash of the file, used to determine if contents changed
	@DatabaseField
    private Date lastModifiedTime; // last modified time of file, used to naively tell if files changed (obviously much quicker than hash)
	@DatabaseField
	private Date lastSyncedLastModifiedTime; // last modified time of file, as of the last sync
	@DatabaseField
	private String lastSyncedHash; // hash of file, as of the last sync
	@DatabaseField
	private Date lastSyncTime; // time of last sync with cloud
	
	public MiBoxFile() {
        // ORMLite needs a no-arg constructor 
    }
	 
	public MiBoxFile(String name, String hash, Date lastModifiedTime, Date lastSyncedLastModifiedTime, String lastSyncedHash, Date lastSyncTime) {	
		this.name = name;
		this.hash = hash;
		this.lastModifiedTime = lastModifiedTime;
		this.lastSyncedLastModifiedTime = lastSyncedLastModifiedTime;
		this.lastSyncedHash = lastSyncedHash;
		this.lastSyncTime = lastSyncTime;
	}
	
	public MiBoxFile(LocalFileSnapshot snapshot) {
		this.name = snapshot.getFileName();
		this.hash = snapshot.getHash();
		this.lastModifiedTime = snapshot.getLastModifiedDate();
		// next two lines are NOT typos. When creating data object from snapshot,
		// use the current hash/last-mod-date, not the old last sync'd ones.
		this.lastSyncedLastModifiedTime = snapshot.getLastModifiedDate();
		this.lastSyncedHash = snapshot.getHash();
		// and similarly, use current date for last sync time
		this.lastSyncTime = new Date();
	}

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getHash() {
		return hash;
	}
	
	public void setHash(String hash) {
		this.hash = hash;
	}
	
	public Date getLastModifiedTime() {
		return lastModifiedTime;
	}
	
	public void setLastModifiedTime(Date lastModifiedTime) {
		this.lastModifiedTime = lastModifiedTime;
	}

	public Date getLastSyncedLastModifiedTime() {
		return lastSyncedLastModifiedTime;
	}

	public void setLastSyncedLastModifiedTime(Date lastSyncedLastModifiedTime) {
		this.lastSyncedLastModifiedTime = lastSyncedLastModifiedTime;
	}

	public String getLastSyncedHash() {
		return lastSyncedHash;
	}

	public void setLastSyncedHash(String lastSyncedHash) {
		this.lastSyncedHash = lastSyncedHash;
	}
	
	public Date getLastSyncTime() {
		return lastSyncTime;
	}

	public void setLastSyncTime(Date lastSyncTime) {
		this.lastSyncTime = lastSyncTime;
	}

	@Override
	public String toString() {
		return getName() + ", " + getHash() + ", " + getLastModifiedTime().toString() + ", " 
				+ getLastSyncedLastModifiedTime().toString() + ", " + getLastSyncedHash().toString()
				+ ", " + getLastSyncTime().toString();
	}
}
