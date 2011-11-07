package com.robwilliams.mibox.snapshots.actions;

import java.io.File;

import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.utils.Mimetypes;
import org.jets3t.service.utils.ServiceUtils;

import com.robwilliams.mibox.dataObjects.MiBoxFile;
import com.robwilliams.mibox.snapshots.LocalFileSnapshot;
import com.robwilliams.mibox.snapshots.CloudFileSnapshot;
import com.robwilliams.mibox.util.FileUtil;
import com.robwilliams.mibox.util.DateUtil;
import com.robwilliams.mibox.util.S3Util;
import com.robwilliams.mibox.Global;

/**
 * This is currently the most complex action. It is required
 * when both the local and cloud file have been changed since the last sync,
 * or when it is impossible to tell which one is more recent. Therefore,
 * we keep both files and rename them to indicate a conflict. Since a rename
 * is really a delete/add pair, and we are doing four logical operations
 * <ol>
 * <li>delete conflicting file from local</li>
 * <li>delete conflicting file from cloud</li>
 * <li>add local conflicted file to cloud under new name (append _<local mibox hostname>)</li>
 * <li>add cloud conflicted file to local under new name (append _<cloud file's source>)</li>
 * </ol>
 * And of course each of these logical operations may involve many database, network,
 * and file operations. This action is not cheap!
 * <br><br>
 * @author Rob Williams
 *
 */
public class LocalFileConflictAction implements FileSnapshotAction {

	private LocalFileSnapshot localSnapshot;
	private CloudFileSnapshot cloudSnapshot;
	
	public LocalFileConflictAction(LocalFileSnapshot localSnapshot, CloudFileSnapshot cloudSnapshot) {
		this.localSnapshot = localSnapshot;
		this.cloudSnapshot = cloudSnapshot;
	}
	
	public void run() {
		try {
			// rename file on filesystem before we do anything
			String newLocalFileName = localSnapshot.getFileName() + " (" + Global.getConfig().getMiBoxHostName() + "'s conflicted copy "
									  + DateUtil.dateToSimplerString(localSnapshot.getLastModifiedDate()) + ")";
			File existingLocalFile = new File(FileUtil.getLocalFilePath(localSnapshot.getFileName()));
			File newLocalFile = new File(FileUtil.getLocalFilePath(newLocalFileName));
			existingLocalFile.renameTo(newLocalFile);
			
			// next rename file on cloud filesystem, which is in fact a delete + add
			String newCloudFileName = cloudSnapshot.getFileName() + " (" + cloudSnapshot.getSource() + "'s conflicted copy "
					                  + DateUtil.dateToSimplerString(cloudSnapshot.getLastModifiedDate()) + ")";
			RestS3Service s3 = Global.getS3();
			S3Object metadataObject = new S3Object(newCloudFileName, "");
			metadataObject.addMetadata("hash", cloudSnapshot.getHash());
			metadataObject.addMetadata("lastmodifieddate", DateUtil.dateToString(cloudSnapshot.getLastModifiedDate()));
			metadataObject.addMetadata("source", cloudSnapshot.getSource());
			s3.renameObject(Global.getConfig().getFilenameHashMapBucket(), cloudSnapshot.getFileName(), metadataObject);
			
			// initiate delete of old local file
			LocalFileDeletedAction localFileDeletedAction = new LocalFileDeletedAction(localSnapshot);
			localFileDeletedAction.run();
			
			// initiate delete of old cloud file
			CloudFileDeletedAction cloudFileDeletedAction = new CloudFileDeletedAction(cloudSnapshot);
			cloudFileDeletedAction.run();
			
			// initiate add of new local file
			// requires modifying local snapshot with new filename and new S3Object pointing to the new file
			localSnapshot.setFileName(newLocalFileName);
			S3Object s3Object = S3Util.createObjectForUploadWithPrecomputedHash(localSnapshot.getHash(), newLocalFile, localSnapshot.getHash());
			localSnapshot.setS3Object(s3Object);
			LocalFileAddedAction localFileAddedAction = new LocalFileAddedAction(localSnapshot);
			localFileAddedAction.run();
			
			// initiate add of new cloud file
			cloudSnapshot.setFileName(newCloudFileName);
			CloudFileAddedAction cloudFileAddedAction = new CloudFileAddedAction(cloudSnapshot);
			cloudFileAddedAction.run();
			
			// initiate local add of the cloud file
			// (this gets it into the CloudFiles DB)
			File newCloudFile = new File(newCloudFileName);
			MiBoxFile newCloudFileDataRecord = Global.getFileMetadataDAO().queryForId(newCloudFileName);
			LocalFileSnapshot cloudConflictedSnapshot = new LocalFileSnapshot(newCloudFileDataRecord, newCloudFile);
			localFileAddedAction = new LocalFileAddedAction(cloudConflictedSnapshot);
			localFileAddedAction.run();
		} catch (Exception ex) {
			// TODO: do something better here, redrive?
			ex.printStackTrace();
			System.exit(1);
		}
	}

}
