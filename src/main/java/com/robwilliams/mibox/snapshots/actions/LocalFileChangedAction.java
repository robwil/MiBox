package com.robwilliams.mibox.snapshots.actions;

import com.robwilliams.mibox.snapshots.LocalFileSnapshot;


/**
 * If the local file change trumps the cloud file, this action is used.
 * It will make sure the local database is in order, and upload the updated
 * file to the cloud, making necessary cloud DB changes.
 * <br><br>
 * @author Rob Williams
 *
 */
public class LocalFileChangedAction implements FileSnapshotAction {

	private LocalFileSnapshot snapshot;
	
	public LocalFileChangedAction(LocalFileSnapshot snapshot) {
		this.snapshot = snapshot;
	}
	
	public void run() {
		// Even though conceptually it is different, the code to perform this action
		// is identical to LocalFileAddedAction. Therefore, we just instantiate one of those objects
		// and run() it.
		LocalFileAddedAction delegate = new LocalFileAddedAction(snapshot);
		delegate.run();
	}

}
