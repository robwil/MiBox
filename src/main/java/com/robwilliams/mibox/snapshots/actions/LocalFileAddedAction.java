package com.robwilliams.mibox.snapshots.actions;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.multi.ThreadedStorageService;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.UpdateCondition;
import com.robwilliams.mibox.Global;
import com.robwilliams.mibox.dataObjects.MiBoxFile;
import com.robwilliams.mibox.snapshots.LocalFileSnapshot;
import com.robwilliams.mibox.util.DateUtil;

/**
 * If the local file does not exist on cloud, this action adds it to the cloud.
 * It will make sure the local database is in order, and upload the new
 * file to the cloud, making necessary cloud DB changes.
 * <br><br>
 * @author Rob Williams
 *
 */
public class LocalFileAddedAction implements FileSnapshotAction {

	private LocalFileSnapshot snapshot;
	
	public LocalFileAddedAction(LocalFileSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	
	@SuppressWarnings("serial")
	public void run() {
		try {
			// upload file data to S3 HashData bucket
			RestS3Service s3 = Global.getS3();
			try {
				s3.getObjectDetails(Global.getConfig().getHashDataMapBucket(), snapshot.getHash());
			} catch (Exception ex) {
				// eat exception
				// it was most likely caused by hash not already existing on server, so let's upload it
				s3.putObject(Global.getConfig().getHashDataMapBucket(), snapshot.getS3Object());				
			}
			
			// add file to S3 File Hash bucket
			S3Object metadataObject = new S3Object(snapshot.getFileName(), "");
			metadataObject.addMetadata("hash", snapshot.getHash());
			metadataObject.addMetadata("lastmodifieddate", DateUtil.dateToString(snapshot.getLastModifiedDate()));
			metadataObject.addMetadata("source", Global.getConfig().getMiBoxHostName());
			s3.putObject(Global.getConfig().getFilenameHashMapBucket(), metadataObject);
			
			// add row to local DB
			MiBoxFile fileDataRecord = new MiBoxFile(snapshot);
			Global.getFileMetadataDAO().createOrUpdate(fileDataRecord);
			
			// add row to cloud files DB
			AmazonSimpleDB sdb = Global.getSDB();
			List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>() {{
				add(new ReplaceableAttribute("hash", snapshot.getHash(), true));
				add(new ReplaceableAttribute("lastModifiedDate", DateUtil.dateToString(snapshot.getLastModifiedDate()), true));
				add(new ReplaceableAttribute("source", Global.getConfig().getMiBoxHostName(), true));
				add(new ReplaceableAttribute("lastSyncDate", DateUtil.dateToString(new Date()), true));
			}};
			PutAttributesRequest request =
					new PutAttributesRequest(Global.getConfig().getCloudFilesDomain(), snapshot.getFileName(), attributes);
			sdb.putAttributes(request);
			
			// keep track of when the file was added. Update condition is set to false so that it doesn't get updated if already exists
			attributes.clear();
			attributes.add(new ReplaceableAttribute("addDate", DateUtil.dateToString(new Date()), true));
			UpdateCondition condition = new UpdateCondition("addDate", null, false);
			request = new PutAttributesRequest(Global.getConfig().getCloudFilesDomain(), snapshot.getFileName(), attributes, condition);
			try {
				sdb.putAttributes(request);
			} catch (AmazonServiceException ex) {
				; // eat exception, since it just means the file already had an Add Date (i.e. the UpdateCondition failed)
			}
				
			// add row to cloud version DB
			// the version database has a key of hash+lmd+filename, then source as its one attribute
			attributes.clear();
			attributes.add(new ReplaceableAttribute("source", Global.getConfig().getMiBoxHostName(), true));
			String itemName = snapshot.getHash() + DateUtil.dateToString(snapshot.getLastModifiedDate()) + snapshot.getFileName();
			request = new PutAttributesRequest(Global.getConfig().getCloudVersionsDomain(), itemName, attributes);
			sdb.putAttributes(request);
			
		} catch (Exception e) {
			// TODO: handle cleaner
			e.printStackTrace();
			System.exit(1);
		}
		
		
	}

}
