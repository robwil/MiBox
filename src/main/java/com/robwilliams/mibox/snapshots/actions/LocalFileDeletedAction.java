package com.robwilliams.mibox.snapshots.actions;

import java.io.File;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.robwilliams.mibox.Global;
import com.robwilliams.mibox.dataObjects.MiBoxFile;
import com.robwilliams.mibox.snapshots.LocalFileSnapshot;
import com.robwilliams.mibox.util.FileUtil;
import com.robwilliams.mibox.util.SimpleDBUtil;

/**
 * If it is determined that the local file should be deleted, this action is run.
 * It will remove the row from the local DB if existent, then delete the actual file.
 * It also deals with pendingDeletes attribute of remote DB.
 * 
 * <br><br>
 * @author Rob Williams
 *
 */
public class LocalFileDeletedAction implements FileSnapshotAction {

	private LocalFileSnapshot snapshot;
	
	public LocalFileDeletedAction(LocalFileSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	public void run() {
		try {
			// clean row from local database
			MiBoxFile existingFileRecord;		
			existingFileRecord = Global.getFileMetadataDAO().queryForId(snapshot.getFileName());
			if (existingFileRecord != null) {
				Global.getFileMetadataDAO().delete(existingFileRecord);
			}
			
			// actually delete the local file
			File file = new File(FileUtil.getLocalFilePath(snapshot.getFileName()));
			if (file.exists() && !file.delete())
				throw new Exception("Failed to delete " + snapshot.getFileName());
			
			// handle pending deletes on remote DB
			
			// query current pendingDeletes for cloud file
			AmazonSimpleDB sdb = Global.getSDB();
			String selectExpression = "select * from `" + Global.getConfig().getCloudFilesDomain() + "`";
			selectExpression += " where itemName() = '" + SimpleDBUtil.escapeSingleQuotedString(snapshot.getFileName()) + "'";
			int pendingDeletes = Integer.parseInt(SimpleDBUtil.selectAttributeFromSingleRow(sdb, selectExpression, "pendingDeletes", "-1"));
			
			if (pendingDeletes >= 0) { // no sense in doing anything if it returned -1 to signify not found or error
				// subtract 1 representing this local host having deleted the file
				pendingDeletes--;
				
				// call method to update pending deletes on the cloud DB
				CloudFileDeletedAction.updatePendingDeletes(sdb, snapshot.getFileName(), pendingDeletes);
			}
		} catch (Exception e) {
			// TODO: error handle better
			e.printStackTrace();
			System.exit(1);
		}
	}

}
