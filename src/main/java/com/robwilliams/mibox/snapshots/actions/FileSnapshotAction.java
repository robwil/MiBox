package com.robwilliams.mibox.snapshots.actions;

/**
 * Simple interface representing an action that must take place
 * with the data of a FileSnapshot. In practice, these actions
 * are used to reconcile differences between local and cloud files. 
 *  <br><br>
 * Note: This interface extends Runnable because these actions
 * 		 may eventually be done by threads. Even if they never are,
 * 		 it still kind of makes sense since actions need to run() anyway.  
 * @author Rob Williams
 *
 */
public interface FileSnapshotAction extends Runnable {
	public void run();
}
