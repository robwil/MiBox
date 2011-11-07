package com.robwilliams.mibox.snapshots;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.EncryptionUtil;
import org.jets3t.service.utils.Mimetypes;
import org.jets3t.service.utils.ObjectUtils;
import org.jets3t.service.utils.ServiceUtils;

import com.robwilliams.mibox.Global;
import com.robwilliams.mibox.dataObjects.MiBoxFile;
import com.robwilliams.mibox.util.S3Util;

public class LocalFileSnapshot extends FileSnapshot {
	private final Date lastSyncedLastModifiedDate;
	private final String lastSyncedHash;
	private S3Object s3Object; // not final because it needs to be modified as part of LocalFileConflictAction
	private final Date lastSyncTime;
	private final boolean existsLocally;
	public Date getLastSyncedLastModifiedDate() {
		return lastSyncedLastModifiedDate;
	}
	public String getLastSyncedHash() {
		return lastSyncedHash;
	}
	public S3Object getS3Object() {
		return s3Object;
	}
	public void setS3Object(S3Object s3Object) {
		this.s3Object = s3Object;
	}
	public Date getLastSyncTime() {
		return lastSyncTime;
	}
	public boolean existsLocally() {
		return existsLocally;
	}
	
	/**
	 * Constructor that is used to construct a snapshot straight from a local file.
	 * This implies that there is no local DB record for that file.
	 * Hash is calculated using JetS3t's S3Object
	 * @param fileName
	 * @param lastModifiedDate
	 * @param file
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	public LocalFileSnapshot(String fileName, Date lastModifiedDate, File file) throws Exception {
		
		this.fileName = fileName;
		this.lastModifiedDate = lastModifiedDate;
		
		// build up S3 object that will be used to send file to cloud Hash->Data bucket
		// this also computes the hash and handles encryption transparently
		s3Object = S3Util.createObjectForUpload(file);
		this.hash = s3Object.getKey();
				
		this.action = null;
		this.lastSyncedLastModifiedDate = new Date(0);
		this.lastSyncedHash = "";
		this.lastSyncTime = new Date(0);
		this.existsLocally = true; // true because we are creating this from local file
	}
	
	/**
	 * Constructor that gets new hash and file information from file, but everything else is from an existing DB record.
	 * This implies that there was a DB record, but it is out-dated. We use all the "last sync" data fields from the DB,
	 * but the file information is newly calculated.
	 * Hash is calculated using JetS3t's S3Object
	 * @param fileName
	 * @param lastModifiedDate
	 * @param snapshot
	 * @param file
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	public LocalFileSnapshot(String fileName, Date lastModifiedDate, LocalFileSnapshot snapshot, File file) throws Exception {
		
		this.fileName = fileName;
		this.lastModifiedDate = lastModifiedDate;
		
		// build up S3 object that will be used to send file to cloud Hash->Data bucket
		// this also computes the hash and handles encryption transparently
		s3Object = S3Util.createObjectForUpload(file);
		this.hash = s3Object.getKey();
		
		this.action = null;
		this.lastSyncedLastModifiedDate = snapshot.getLastSyncedLastModifiedDate();
		this.lastSyncedHash = snapshot.getLastSyncedHash();
		this.lastSyncTime = snapshot.getLastSyncTime();
		this.existsLocally = true; // true because we are creating this from local file
	}
	
	/**
	 * Constructor that is used to construct a snapshot straight from the existing DB record.
	 * This implies that the local DB record for the file is still applicable/up-to-date.
	 * @param fileDataRecord
	 * @param file
	 * @throws Exception 
	 */
	public LocalFileSnapshot(MiBoxFile fileDataRecord, File file) throws Exception {
		this.fileName = fileDataRecord.getName();
		this.lastModifiedDate = fileDataRecord.getLastModifiedTime();
		this.hash = fileDataRecord.getHash();
		
		// build up S3 object that will be used to send file to cloud Hash->Data bucket
		// since hash was already pre-computed, don't re-compute it
		if (file.exists()) {
			// this handles encryption transparently
			s3Object = S3Util.createObjectForUploadWithPrecomputedHash(this.hash, file, this.hash);
			this.existsLocally = true;
		} else {
			s3Object = new S3Object(this.hash);
			this.existsLocally = false;
		}
		s3Object.setMd5Hash(ServiceUtils.fromHex(this.hash));
		
		this.action = null;
		this.lastSyncedLastModifiedDate = fileDataRecord.getLastSyncedLastModifiedTime();
		this.lastSyncedHash = fileDataRecord.getLastSyncedHash();
		this.lastSyncTime = fileDataRecord.getLastSyncTime();
	}
	
	/**
	 * Warning! This should only be caused by the test scripts. Calling this is BAD, as you will
	 * probably get a NullPointerException since S3Object isn't initialized
	 */
	public LocalFileSnapshot(String fileName, Date lastModifiedDate, String hash,
			Date lastSyncedLastModifiedDate, String lastSyncedHash, Date lastSyncDate, boolean existsLocally) {
		this.fileName = fileName;
		this.lastModifiedDate = lastModifiedDate;
		this.hash = hash;
		this.action = null;
		this.lastSyncedLastModifiedDate = lastSyncedLastModifiedDate;
		this.lastSyncedHash = lastSyncedHash;
		this.lastSyncTime = lastSyncDate;
		this.existsLocally = existsLocally;
		this.s3Object = null;
	}

}
