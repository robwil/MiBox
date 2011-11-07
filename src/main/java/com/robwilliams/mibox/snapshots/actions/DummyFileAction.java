package com.robwilliams.mibox.snapshots.actions;

/**
 * DummyFileAction doesn't perform any action whatsoever.
 * Its main purpose is to be used as the action on snapshots
 * which do not require any action. For example, if the local
 * and cloud snapshots are already perfectly in sync, nothing will
 * need to be done.
 *  
 * @author Rob Williams
 *
 */
public class DummyFileAction implements FileSnapshotAction {

	public void run() {		
		;
	}

}
