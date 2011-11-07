package com.robwilliams.mibox;

import java.io.File;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.robwilliams.mibox.dataObjects.MiBoxFile;
import com.robwilliams.mibox.exceptions.IntegrityError;
import com.robwilliams.mibox.snapshots.CloudFileSnapshot;
import com.robwilliams.mibox.snapshots.LocalFileSnapshot;
import com.robwilliams.mibox.snapshots.actions.CloudFileAddedAction;
import com.robwilliams.mibox.snapshots.actions.CloudFileChangedAction;
import com.robwilliams.mibox.snapshots.actions.CloudFileDeletedAction;
import com.robwilliams.mibox.snapshots.actions.DummyFileAction;
import com.robwilliams.mibox.snapshots.actions.LocalFileAddedAction;
import com.robwilliams.mibox.snapshots.actions.LocalFileChangedAction;
import com.robwilliams.mibox.snapshots.actions.LocalFileConflictAction;
import com.robwilliams.mibox.snapshots.actions.LocalFileDeletedAction;
import com.robwilliams.mibox.snapshots.actions.LocalFileUnchangedAction;
import com.robwilliams.mibox.util.DateUtil;
import com.robwilliams.mibox.util.FileUtil;
import com.robwilliams.mibox.util.SimpleDBUtil;

/**
 * SyncMaster's job is to handle all tasks associated with syncing the cloud
 * and local databases. This includes the initial sync that occurs when the program
 * launches, as well as syncs that happen as a result of JNotify events or remote
 * data changes.
 * <br><br>
 * @author Rob Williams
 * @since July 16, 2011
 *
 */
public class SyncMaster {
	
	private LogMaster logger;
	
	public SyncMaster() {
		logger = Global.getLogger();
	}
		 
	 /**
	  * Retrieve a collection of LocalFileSnapshot objects representing the local
	  * files in the user's MiBox.
	  * @return collection of LocalFileSnapshot's
	  */
	 public Map<String, LocalFileSnapshot> getLocalFileSnapshots(Date lastSyncDate) {
		 
		 Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>();
		 
		 try {
			 // get all file information from local DB
			 List<MiBoxFile> filesFromDB = Global.getFileMetadataDAO().queryForAll();
			 for (MiBoxFile fileFromDB : filesFromDB) {
				 File file = new File(FileUtil.getLocalFilePath(fileFromDB.getName()));
				 LocalFileSnapshot snapshot = new LocalFileSnapshot(fileFromDB, file);
				 localFileSnapshots.put(fileFromDB.getName(), snapshot);
			 }
			 
			 // get list of all files in box directory
			 File boxDirectory = new File(Global.getConfig().getBoxPath());
			 Collection<File> files = FileUtil.listFiles(boxDirectory, null, true);
			 
			 // loop through each file
			 // compare it to what we know from the DB and act accordingly (details inline below)
			 for (File file : files) {
				 if (file.isDirectory()) continue; // skip directories
				 String relativePath = boxDirectory.toURI().relativize(file.toURI()).getPath();
				 Date fileModifiedTime = new Date(file.lastModified());
				 LocalFileSnapshot snapshot = localFileSnapshots.get(relativePath);
				 // compare existing snapshot (from DB) to local file on disk
				 if (snapshot != null && snapshot.getLastModifiedDate().equals(fileModifiedTime)) {
					 // database has record with same last modified date
					 // if the database's lastSyncTime is older than lastSyncDate for this host, we can safely remove this snapshot from the map
					 // because it means it hasn't changed
					 if (snapshot.getLastSyncTime().compareTo(lastSyncDate) <= 0) {
						 localFileSnapshots.remove(relativePath);
					 }
				 } else {
					 // If snapshot is not null but LMD's are not equal, it implies the DB record is out-dated.
					 // We want to keep the "last sync" data fields from DB, but calculate the file hash over again.
					 if (snapshot != null) {
						 snapshot = new LocalFileSnapshot(relativePath, fileModifiedTime, snapshot, file);
					 }
					 // Otherwise, there is no DB record, so we create a new snapshot straight from the local file.
					 else {
						 snapshot = new LocalFileSnapshot(relativePath, fileModifiedTime, file);
					 }
					 localFileSnapshots.put(relativePath, snapshot);
				 }
			 }
		 } catch (Exception ex) {
			 // TODO: handle error better
			 ex.printStackTrace();
			 System.exit(1);
		 }
		 return localFileSnapshots;
	 }
	 
	 /**
	  * Retrieve a collection of CloudFileSnapshot objects representing the remote
	  * files stored in the user's AWS account.
	  * @return collection of CloudFileSnapshot's
	  */
	 public Map<String, CloudFileSnapshot> getCloudFileSnapshots(String lastSyncDate) {
		 Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>();
		 
		 try {
			 AmazonSimpleDB sdb = Global.getSDB();
			
			 // now, we only select cloud files which were synced after our lastSyncDate, or has pending deletes
			 String selectExpression = "select * from `" + Global.getConfig().getCloudFilesDomain() + "`";
			 selectExpression += " where lastSyncDate > '" + SimpleDBUtil.escapeSingleQuotedString(lastSyncDate) + "'";
			 selectExpression += " OR pendingDeletes > '0'";
			 SelectRequest selectRequest = new SelectRequest(selectExpression);
	         for (Item item : sdb.select(selectRequest).getItems()) {
	        	 // construct snapshot and add to result Map
	        	 cloudFileSnapshots.put(item.getName(), new CloudFileSnapshot(item));
	         }
		 } catch (Exception ex) {
			 //TODO: handle error better
			 ex.printStackTrace();
			 System.exit(1);
		 }
		 
		 return cloudFileSnapshots;
	 }

	/**
	  * This is usually called when the daemon is first launched to sync all local files with all cloud files.
	  * @throws IntegrityError indicates something is very wrong with the integrity of the metadata
	  */
	 public void performInitialSync() throws IntegrityError {
		 // query SimpleDB for the last successful sync date for this MiBox host
		 String selectExpression = "select * from `" + Global.getConfig().getLastSyncDatesDomain() + "`";
		 selectExpression += " where itemName() = '" + SimpleDBUtil.escapeSingleQuotedString(Global.getConfig().getMiBoxHostName()) + "'";
		 String lastSyncDate = SimpleDBUtil.selectAttributeFromSingleRow(Global.getSDB(), selectExpression, "lastSyncDate", "1970-01-01 00:00:00");
		 
		 // get snapshots for cloud and local files that have changed since last sync date
		 Map<String, LocalFileSnapshot> localFileSnapshots;
		 Map<String, CloudFileSnapshot> cloudFileSnapshots;
		 try {
			 localFileSnapshots = getLocalFileSnapshots(DateUtil.parse(lastSyncDate));
			 cloudFileSnapshots = getCloudFileSnapshots(lastSyncDate);
		 } catch (ParseException e) {
			 // if the parsing of the date string failed, just be safe and do a full sync by setting lastSyncDate to epoch time
			 localFileSnapshots = getLocalFileSnapshots(new Date(0));
			 cloudFileSnapshots = getCloudFileSnapshots("1970-01-01 00:00:00");
		 }
		 
		 // perform partial sync using this information
		 performSync(localFileSnapshots, cloudFileSnapshots);
	 }
	 
	 /**
	  * Perform synchronization between local files and cloud files.
	  * @param localFileSnapshots Snapshots of the local files to sync.
	  * @param cloudFileSnapshots Snapshots of the cloud files to sync.
	  * @throws IntegrityError indicates something is very wrong with the integrity of the metadata
	  */
	public void performSync(Map<String, LocalFileSnapshot> localFileSnapshots,
				Map<String, CloudFileSnapshot> cloudFileSnapshots) throws IntegrityError {
		// determine what merge actions to take
		prepareMerge(localFileSnapshots, cloudFileSnapshots);
		
		// executes the actions for merging
		merge(localFileSnapshots, cloudFileSnapshots);
		
		// inform cloud of a successful sync
		persistLastSyncDate();
	 }

	/**
	  * By comparing the local and cloud file snapshots, this method determines
	  * the actions necessary to merge the results. It annotates the snapshot objects
	  * with the FileSnapshotStatus representing an action to be performed.
	  * <br><br>
	  * The code may look confusing because it's a ton of IF statements, but it is
	  * actually a very simple decision tree. Maybe I'll make a diagram at some point
	  * to explain it clearly.
	  * <br><br>
	  * This method is usually called before the merge(...) method.
	  * 
	  * @param localFileSnapshots
	  * @param cloudFileSnapshots
	  * @throws IntegrityError indicates something is very wrong with the integrity of the metadata
	  */
	private void prepareMerge(Map<String, LocalFileSnapshot> localFileSnapshots,
			Map<String, CloudFileSnapshot> cloudFileSnapshots) throws IntegrityError {
		
		// loop through all local file snapshots
		for (LocalFileSnapshot localFileSnapshot : localFileSnapshots.values()) {
			// retrieve the cloud file snapshot corresponding to this local file, if any exists
			CloudFileSnapshot relatedCloudFileSnapshot = cloudFileSnapshots.get(localFileSnapshot.getFileName());
			// if there is no cloud snapshot, relatedCloudFileSnapshot will be null
			if (relatedCloudFileSnapshot == null) {
				// file not existing on cloud means one of two cases
				// 1) file was deleted locally, in which case the snapshot will be marked as existsLocally = false
				if (localFileSnapshot.existsLocally() == false) {
					// there are two sub-cases. Either the cloud knows about this file already or it doesn't.
					// query CloudFiles to determine which case it is
					AmazonSimpleDB sdb = Global.getSDB();
					String selectExpression = "select * from `" + Global.getConfig().getCloudFilesDomain() + 
							"` where itemName() = '" + SimpleDBUtil.escapeSingleQuotedString(localFileSnapshot.getFileName()) + "'";
			        SelectRequest selectRequest = new SelectRequest(selectExpression);
			        List<Item> items = sdb.select(selectRequest).getItems();
			        // only care about DB entry if there is exactly one, because otherwise something weird is up
			        if (items != null && items.size() == 1) {
			        	// case 1.1: cloud knows about file, so delete from cloud
			        	Item item = items.get(0);
			        	try {
							relatedCloudFileSnapshot = new CloudFileSnapshot(item);
							// put in map since it's a new snapshot
							cloudFileSnapshots.put(relatedCloudFileSnapshot.getFileName(), relatedCloudFileSnapshot);
							relatedCloudFileSnapshot.setAction(new CloudFileDeletedAction(relatedCloudFileSnapshot));
							localFileSnapshot.setAction(new DummyFileAction());
							logger.writeDebugLine("Deleted action chosen for cloud file: " + relatedCloudFileSnapshot.getFileName());
			        	} catch (Exception e) {
							; // swallow Exception, probably just case 1.2
						}
			        }
			        // case 1.2: cloud doesn't have row for file, which means it has already been deleted or never synced.
			        // either way, nothing needs to be done
			        else {
			        	localFileSnapshot.setAction(new DummyFileAction());
			        }
				}
				// 2) it is a local addition or change (which are both handled by LocalFileAddedAction anyway)	
				else {
					localFileSnapshot.setAction(new LocalFileAddedAction(localFileSnapshot));	
					logger.writeDebugLine("Added action chosen for local file: " + localFileSnapshot.getFileName());
				}
			} else { // we have cloud snapshot, so file was changed on cloud since last sync
				// next compare last modified dates
				if (localFileSnapshot.getLastModifiedDate().equals(relatedCloudFileSnapshot.getLastModifiedDate())) {
					// last modified dates are equal, so next compare hashes
					if (localFileSnapshot.getHash().equals(relatedCloudFileSnapshot.getHash())) {
						// filename, last modified date, and hash are all equal
						// therefore, there was no change to this file
						// (which in all honesty is a super rare case, since it means the user modified file locally,
						//  then manually copied to another computer and sync'd to cloud there.)
						localFileSnapshot.setAction(new LocalFileUnchangedAction(localFileSnapshot));
						relatedCloudFileSnapshot.setAction(new DummyFileAction());
						logger.writeDebugLine("Unchanged action chosen for local file: " + localFileSnapshot.getFileName());
					} else { // hashes not equal
						// if hashes are not equal, then it is a conflict
						// not to mention, also a quite rare occurrence:
						// (the file was updated separately on two computers but modified at same second)
						localFileSnapshot.setAction(new LocalFileConflictAction(localFileSnapshot, relatedCloudFileSnapshot));
						// all heavy lifting for conflicts happens in Local, so Cloud snapshot will do nothing
						// This was an arbitrary design decision;
						// it just as easily could have been Cloud doing all work and Local dummy'ing.
						relatedCloudFileSnapshot.setAction(new DummyFileAction()); 
						logger.writeDebugLine("Conflict due to diff hash/same LMD: " + localFileSnapshot.getFileName());
					}
				} else { // last modified dates are not equal
					// Check for a very rare case
					// (file was touch'd but not changed)
					if (localFileSnapshot.getHash().equals(relatedCloudFileSnapshot.getHash())) {
						// Since the file didn't change, the most recently modified version will trump the other.
						// Even though the LocalFileChanged and CloudFileChanged will call the heavy Added actions,
						// it shouldn't involve network upload/download of file due to hash matching.
						if (localFileSnapshot.getLastModifiedDate().compareTo(relatedCloudFileSnapshot.getLastModifiedDate()) > 0) {
							// local file is more recently modified, so local change trumps
							localFileSnapshot.setAction(new LocalFileChangedAction(localFileSnapshot));
							relatedCloudFileSnapshot.setAction(new DummyFileAction());
							logger.writeDebugLine("Changed action chosen for local file: " + localFileSnapshot.getFileName());
						} else {
							// cloud file must be more recently modified (or they're equal, so take cloud anyway)
							localFileSnapshot.setAction(new DummyFileAction());
							relatedCloudFileSnapshot.setAction(new CloudFileChangedAction(relatedCloudFileSnapshot));
							logger.writeDebugLine("Changed action chosen for cloud file: " + relatedCloudFileSnapshot.getFileName());
						}
					}
					// next, check for a horrible situation that should never happen
					// if it does happen, it means time travel has been invented, or the local DB is corrupted, or file metadata was tampered with
					else if (localFileSnapshot.getLastSyncedLastModifiedDate().compareTo(relatedCloudFileSnapshot.getLastModifiedDate()) > 0
							|| localFileSnapshot.getLastSyncedLastModifiedDate().compareTo(localFileSnapshot.getLastModifiedDate()) > 0) {
						throw new IntegrityError("Local file or cloud file was modified before a modified date previously recorded for them!");
					}
					// next, check for more common conflict case
					else if (!localFileSnapshot.getLastSyncedLastModifiedDate().equals(new Date(0)) // this only applies if there is a last sync'd last-mod-date
							&& localFileSnapshot.getLastSyncedLastModifiedDate().compareTo(relatedCloudFileSnapshot.getLastModifiedDate()) < 0
							&& localFileSnapshot.getLastSyncedLastModifiedDate().compareTo(localFileSnapshot.getLastModifiedDate()) < 0) {
						
						// One final very rare case that we need to check for before declaring this is a conflict.
						// Make sure that the local file actually changed. There is no sense in making a conflict just
						// because someone touch'd the local file after syncing, when the cloud has a real change.
						if (localFileSnapshot.getHash().equals(localFileSnapshot.getLastSyncedHash())) {
							// we know the cloud was actually changed, because if cloud hash == local hash we would be in branch above
							localFileSnapshot.setAction(new DummyFileAction());
							relatedCloudFileSnapshot.setAction(new CloudFileChangedAction(relatedCloudFileSnapshot));
							logger.writeDebugLine("Changed action chosen for cloud file: " + relatedCloudFileSnapshot.getFileName());
						} else {
							// Finally, we know it's a conflict.
							// This is a conflict because it means the file changed both locally and on the cloud in the time
							// since the last successful sync of this file. (Remember cloud mod date and local mod date are not equal
							// AND hashes are not equal, else we would have been in a different branch.)
							// Therefore, there is no way to merge files or determine which one should be kept.
							localFileSnapshot.setAction(new LocalFileConflictAction(localFileSnapshot, relatedCloudFileSnapshot));
							relatedCloudFileSnapshot.setAction(new DummyFileAction());
							logger.writeDebugLine("Conflict due to last sync'd LMD: " + localFileSnapshot.getFileName());
						}
					}
					// next, finally handle the usual case where cloud or local file is more recent and trumps the other
					// (this is actually quite rare now that we are partial sync'ing)
					else if (localFileSnapshot.getLastModifiedDate().compareTo(relatedCloudFileSnapshot.getLastModifiedDate()) > 0) {
						// local file is more recently modified, so local change trumps
						localFileSnapshot.setAction(new LocalFileChangedAction(localFileSnapshot));
						relatedCloudFileSnapshot.setAction(new DummyFileAction());
						logger.writeDebugLine("Changed action chosen for local file: " + localFileSnapshot.getFileName());
					} else if (relatedCloudFileSnapshot.getLastModifiedDate().compareTo(localFileSnapshot.getLastModifiedDate()) > 0) {
						// cloud file is more recently modified, so cloud change trumps
						localFileSnapshot.setAction(new DummyFileAction());
						relatedCloudFileSnapshot.setAction(new CloudFileChangedAction(relatedCloudFileSnapshot));
						logger.writeDebugLine("Changed action chosen for cloud file: " + relatedCloudFileSnapshot.getFileName());
					}
					// if it gets this far, something is wrong
					// I'm not convinced this is even reachable code, but better safe than sorry.
					else {
						throw new IntegrityError("File metadata is messed up");
					}
				}
			}
			
		}
		
		// loop through all cloud file snapshots
		for (CloudFileSnapshot cloudFileSnapshot : cloudFileSnapshots.values()) {
			// first check if action is non-null.
			// This means we already processed this file in local snapshot loop,
			// so skip this snapshot.
			if (cloudFileSnapshot.getAction() != null) {
				continue;
			}
			
			// retrieve the local file snapshot corresponding to this cloud file, if any exists
			// (and in fact, we know one won't exist because if it did then we would have processed it in above loop)
			LocalFileSnapshot relatedLocalFileSnapshot = localFileSnapshots.get(cloudFileSnapshot.getFileName());
			if (relatedLocalFileSnapshot == null) {
				// file exists on cloud but we have no local snapshot
				// this means one of two cases
				
				// 1) cloud snapshot is for a file with pending deletes
				// this has two subcases
				if (cloudFileSnapshot.getPendingDeletes() > 0) {
					try {
						File file = new File(FileUtil.getLocalFilePath(cloudFileSnapshot.getFileName()));
						// 1.1) local file exists on file system, so delete it
						if (file.exists()) {
							// get file data from DB.
							MiBoxFile existingFileRecord = Global.getFileMetadataDAO().queryForId(cloudFileSnapshot.getFileName());
							// it's impossible for it to not exist in DB and be in this code path, due to how local file snapshots are created
							if (existingFileRecord == null) {
								throw new IntegrityError("File exists on filesystem without DB entry and yet no local snapshot was made for it");
							}
							try {
								relatedLocalFileSnapshot = new LocalFileSnapshot(existingFileRecord, file);
							} catch (Exception ex) {
								// TODO: More bad error handling. PLz fix
								ex.printStackTrace();
								System.exit(1);
							}
							// put in map since it's a new snapshot
							localFileSnapshots.put(relatedLocalFileSnapshot.getFileName(), relatedLocalFileSnapshot);
							relatedLocalFileSnapshot.setAction(new LocalFileDeletedAction(relatedLocalFileSnapshot));
							cloudFileSnapshot.setAction(new DummyFileAction());
							logger.writeDebugLine("Deleted action chosen for local file: " + relatedLocalFileSnapshot.getFileName());
						}
						// 1.2) local file doesn't exist, in which case this host never downloaded this file or it has already been deleted
						//      either way, nothing needs to be done
						else {
							cloudFileSnapshot.setAction(new DummyFileAction());
						}
					} catch (SQLException e) {
						// TODO: make sure this makes sense
						; // swallow exception, and continue to #2 below
					}
				} else {
					// TODO: make sure when I implement JNotify handlers that a local deletion only removes from local DB if it removes from cloud successfully
					// 2) file was added or modified on cloud and should be downloaded to local
					cloudFileSnapshot.setAction(new CloudFileAddedAction(cloudFileSnapshot));
					logger.writeDebugLine("Added action chosen for cloud file: " + cloudFileSnapshot.getFileName());
				}
			}
		}
	}
	
	/**
	 * After this method is finished running, the local and cloud file data and metadata should
	 * be identical. This is accomplished by making any necessary file and database changes, both
	 * locally and remotely.
	 * 
	 * @param localFileSnapshots
	 * @param cloudFileSnapshots
	 * @throws IntegrityError indicates something is very wrong with the integrity of the metadata
	 */
	private void merge(Map<String, LocalFileSnapshot> localFileSnapshots,
			Map<String, CloudFileSnapshot> cloudFileSnapshots) throws IntegrityError {
				
		for (LocalFileSnapshot localFileSnapshot : localFileSnapshots.values()) {
			localFileSnapshot.getAction().run();
		}
		
		for (CloudFileSnapshot cloudFileSnapshot : cloudFileSnapshots.values()) {
			cloudFileSnapshot.getAction().run();
		}		
	}
	
	/**
	 * Write the date of a successful sync to the cloud's LastSyncDates DB domain.
	 * This enables partial syncing to only look at changed files on subsequent syncs.
	 */
	@SuppressWarnings("serial")
	private void persistLastSyncDate() {
		AmazonSimpleDB sdb = Global.getSDB();
		List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>() {{			
			add(new ReplaceableAttribute("lastSyncDate", DateUtil.dateToString(new Date()), true));
		}};
		PutAttributesRequest request =
				new PutAttributesRequest(Global.getConfig().getLastSyncDatesDomain(), Global.getConfig().getMiBoxHostName(), attributes);
		sdb.putAttributes(request);
	}
	 
}
