package com.robwilliams.mibox.snapshots.actions;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.StorageObject;
import org.jets3t.service.multi.DownloadPackage;
import org.jets3t.service.multi.SimpleThreadedStorageService;
import org.jets3t.service.utils.ObjectUtils;

import com.robwilliams.mibox.Global;
import com.robwilliams.mibox.dataObjects.MiBoxFile;
import com.robwilliams.mibox.snapshots.CloudFileSnapshot;
import com.robwilliams.mibox.util.DateUtil;
import com.robwilliams.mibox.util.FileUtil;

/**
 * If the cloud file does not exist on local, this action adds it to local.
 * It will download file from cloud and insert row to local DB.
 * <br><br>
 * @author Rob Williams
 *
 */
public class CloudFileAddedAction implements FileSnapshotAction {

	private CloudFileSnapshot snapshot;

	public CloudFileAddedAction(CloudFileSnapshot snapshot) {
		this.snapshot = snapshot;
	}
	
	public void run() {
		RestS3Service s3 = Global.getS3();
		String hash = null;
		Date lastModDate = null;
		int retries = 0;
		while (true) {
			try {
				// get remote file information from metadata store
				StorageObject remoteFileMetadata = s3.getObjectDetails(Global.getConfig().getFilenameHashMapBucket(), snapshot.getFileName());
				
				// get remote file metadata for easy use			
				Map<String, Object> metadata = remoteFileMetadata.getMetadataMap();
				hash = (String) metadata.get("hash");
				lastModDate = DateUtil.parse((String) metadata.get("lastmodifieddate"));
				break;
			} catch (Exception ex) {
				// retry service calls
				if (ex instanceof ServiceException) {
					if (retries++ < Global.getConfig().getServiceCallRetries())
						continue;
				}
				Global.getLogger().writeErrorLine(ex.getMessage());
				Global.getLogger().writeErrorLine("Failed to retrieve metadata for file " + snapshot.getFileName() + ". Can't sync without it.");
				System.exit(1);
			}
		}
		
		// download the remote file from S3
		retries = 0;
		while (true) {
			try {	
				StorageObject remoteFile = s3.getObject(Global.getConfig().getHashDataMapBucket(), hash);
				File localFile = new File(FileUtil.getLocalFilePath(snapshot.getFileName()));
				DownloadPackage downloadPackage = ObjectUtils.createPackageForDownload(remoteFile, localFile, true, true, Global.getConfig().getEncryptionKey());
				DownloadPackage[] downloadPackages = {downloadPackage};
				SimpleThreadedStorageService simpleMulti = new SimpleThreadedStorageService(s3);
				simpleMulti.downloadObjects(Global.getConfig().getHashDataMapBucket(), downloadPackages);
				break;
			} catch (Exception ex) {
				// retry service calls
				if (ex instanceof ServiceException) {
					if (retries++ < Global.getConfig().getServiceCallRetries())
						continue;
				}
				Global.getLogger().writeErrorLine(ex.getMessage());
				Global.getLogger().writeErrorLine("Failed to retrieve file data for " + snapshot.getFileName() + " from cloud.");
				System.exit(1);
			}
		}
		
		/*// write the file to disk
		retries = 0;
		while (true) {
			try {		
				FileUtil.writeInputStreamToFileAndSetLastModDate(remoteFile.getDataInputStream(), 
						FileUtil.getLocalFilePath(snapshot.getFileName()), lastModDate);
				break;
			} catch (Exception ex) {
				// retry service calls
				if (ex instanceof ServiceException) {
					if (retries++ < Global.getConfig().getServiceCallRetries())
						continue;
				}
				Global.getLogger().writeErrorLine(ex.getMessage());
				Global.getLogger().writeErrorLine("Failed to write file " + snapshot.getFileName() + " to disk.");
				System.exit(1);
			}
		}*/
		
		try {
			// create record in local DB, getting hash and last-mod-date from metadata
			MiBoxFile fileDataRecord = new MiBoxFile(snapshot.getFileName(), hash, lastModDate, lastModDate, hash, new Date());
			Global.getFileMetadataDAO().createOrUpdate(fileDataRecord);
		} catch (Exception ex) {
			Global.getLogger().writeErrorLine(ex.getMessage());
			Global.getLogger().writeErrorLine("Failed to create record in local DB for file " + snapshot.getFileName());
			System.exit(1);
		}
	}

}
