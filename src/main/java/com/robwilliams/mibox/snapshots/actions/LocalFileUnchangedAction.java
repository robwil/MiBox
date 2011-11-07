package com.robwilliams.mibox.snapshots.actions;

import com.robwilliams.mibox.Global;
import com.robwilliams.mibox.dataObjects.MiBoxFile;
import com.robwilliams.mibox.snapshots.LocalFileSnapshot;

/**
 * This action handles the case where there is a local file
 * that is unchanged, meaning it matches perfectly what is on
 * the cloud. This requires an action because it is possible
 * that the local DB does not yet know about the file. Therefore,
 * the local DB is consulted and a row is inserted/updated if it
 * is missing or invalid.
 * <br><br>
 * For example, this could happen if the user manually copies
 * their existing MiBox directory to a new computer via USB,
 * then launched MiBox for the first time on that computer. All
 * files would match perfectly with cloud and be considered
 * unchanged, but the local database would be empty. This ensures
 * the local DB gets properly populated.
 * <br><br>
 * @author Rob Williams
 *
 */
public class LocalFileUnchangedAction implements FileSnapshotAction {

	private LocalFileSnapshot snapshot;
	
	public LocalFileUnchangedAction(LocalFileSnapshot snapshot) {
		this.snapshot = snapshot;
	}
	
	public void run() {
		// add or update local DB with file information
		try {
			MiBoxFile fileDataRecord = new MiBoxFile(snapshot);	
			Global.getFileMetadataDAO().createOrUpdate(fileDataRecord);
		} catch (Exception ex) {
			// TODO: clean up error handling
			ex.printStackTrace();
			System.exit(1);
		}
	}

}
