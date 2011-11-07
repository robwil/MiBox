package com.robwilliams.mibox.snapshots.actions;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jets3t.service.impl.rest.httpclient.RestS3Service;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.robwilliams.mibox.Global;
import com.robwilliams.mibox.dataObjects.MiBoxFile;
import com.robwilliams.mibox.snapshots.CloudFileSnapshot;
import com.robwilliams.mibox.util.DateUtil;
import com.robwilliams.mibox.util.SimpleDBUtil;

/**
 * If it is determined that the cloud file should be deleted, this action is run.
 * It will remove the row from the local DB if existent, then delete the
 * cloud file->hash map for the file.
 * <br><br>
 * @author Rob Williams
 *
 */
public class CloudFileDeletedAction implements FileSnapshotAction {

	private CloudFileSnapshot snapshot;
	
	public CloudFileDeletedAction(CloudFileSnapshot snapshot) {
		this.snapshot = snapshot;
	}
	
	public void run() {
		try {
			// clean row from local database
			MiBoxFile existingFileRecord = Global.getFileMetadataDAO().queryForId(snapshot.getFileName());
			if (existingFileRecord != null) {
				Global.getFileMetadataDAO().delete(existingFileRecord);
			}
			
			// delete remote file
			RestS3Service s3 = Global.getS3();
			s3.deleteObject(Global.getConfig().getFilenameHashMapBucket(), snapshot.getFileName());
			
			// now we need to handle deletion on the cloud, which is actually quite complex
			
			// simple case is that cloud already knows about this delete (aka "pending delete")
			
			// 1) we need to know how many MiBox hosts the cloud knows about
			// so query LastSyncDates to determine this
			AmazonSimpleDB sdb = Global.getSDB();
			String selectExpression = "select count(*) from `" + Global.getConfig().getLastSyncDatesDomain() + "`";
			int hostCount = Integer.parseInt(SimpleDBUtil.selectAttributeFromSingleRow(sdb, selectExpression, "Count", "0"));
			
			// so hostCount represents how many MiBox hosts are syncing with cloud
			// we will use this number to represent how many hosts need to have this file deleted
			
			// 1.1) subtract 1 since it has already been deleted on the local host
			hostCount--;
			
			// 1.2) get the addDate for this deleted file
			selectExpression = "select * from `" + Global.getConfig().getCloudFilesDomain() + "`";
			selectExpression += " where itemName() = '" + SimpleDBUtil.escapeSingleQuotedString(snapshot.getFileName()) + "'";
			Date addDate = DateUtil.parse(SimpleDBUtil.selectAttributeFromSingleRow(sdb, selectExpression, "addDate", "1970-01-01 00:00:00"));
			
			// 1.3) loop through lastSyncDates of all hosts and determine if they even know about this file yet
			selectExpression = "select * from `" + Global.getConfig().getLastSyncDatesDomain() + "`";
			SelectRequest selectRequest = new SelectRequest(selectExpression);
			List<Item> items = sdb.select(selectRequest).getItems();		 
			for (Item item : items) {
				// skip the current host since we've already dealt with it
				if (item.getName().equals(Global.getConfig().getMiBoxHostName())) continue;
				
				// otherwise, check the lastSyncDate of the host
				// if it is < the addDate of the deleted file,
				// then it doesn't know about the file and so doesn't need to be deleted from that host
				List<Attribute> attributes = item.getAttributes();
				for (Attribute attribute : attributes) {
					if (attribute.getName().equals("lastSyncDate")) {
						Date lastSyncDate = DateUtil.parse(attribute.getValue());
						if (lastSyncDate.before(addDate)) {
							hostCount--;
						}
					}
				}
			}
			
			// finally, hostCount is (hopefully) accurate number of hosts which still need the file to be deleted
			// call method to update pending deletes on the cloud DB
			// (this is extracted method because it is used in LocalFileDeleted as well)
			updatePendingDeletes(sdb, snapshot.getFileName(), hostCount);
		} catch (Exception ex) {
			// TODO: Better error handling here
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

	/**
	 * Encapsulate all logic needed to update pendingDeletes on cloud.
	 * @param sdb
	 * @param cloudFileName
	 * @param pendingDeletes
	 */
	public static void updatePendingDeletes(AmazonSimpleDB sdb, String cloudFileName, int pendingDeletes) {
		// if pending deletes <= 0, then there are no pending deletes so we can actually delete row from CloudFiles
		if (pendingDeletes <= 0) {
			sdb.deleteAttributes(new DeleteAttributesRequest(Global.getConfig().getCloudFilesDomain(), cloudFileName));
		}
		// otherwise, we must save this hostCount info to the pendingDeletes column of the CloudFiles row
		else {
			List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>();
			attributes.add(new ReplaceableAttribute("pendingDeletes", Integer.toString(pendingDeletes), true));
			PutAttributesRequest request =
					new PutAttributesRequest(Global.getConfig().getCloudFilesDomain(), cloudFileName, attributes);
			sdb.putAttributes(request);
		}
	}

}
