package com.robwilliams.mibox.snapshots.actions;

import com.robwilliams.mibox.snapshots.CloudFileSnapshot;


/**
 * If the cloud file change trumps the local file, this action is used.
 * It will download the file from the cloud, replacing the local file
 * and updating the row in the local DB.  
 * <br><br>
 * @author Rob Williams
 *
 */
public class CloudFileChangedAction implements FileSnapshotAction {

	private CloudFileSnapshot snapshot;
	
	public CloudFileChangedAction(CloudFileSnapshot snapshot) {
		this.snapshot = snapshot;
	}
	
	public void run() {
		// Even though conceptually it is different, the code to perform this action
		// is identical to CloudFileAddedAction. Therefore, we just instantiate one of those objects
		// and run() it.
		CloudFileAddedAction delegate = new CloudFileAddedAction(snapshot);
		delegate.run();
	}

}
