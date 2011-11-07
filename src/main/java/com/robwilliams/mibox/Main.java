package com.robwilliams.mibox;

import java.io.File;
import java.security.Provider;
import java.security.Security;
import java.sql.SQLException;
import java.util.Arrays;

import org.jets3t.service.S3ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.security.EncryptionUtil;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.j256.ormlite.table.TableUtils;
import com.robwilliams.mibox.dataObjects.MiBoxFile;
import com.robwilliams.mibox.exceptions.IntegrityError;

public class Main {
	public static void main(String[] args) throws Exception { // TODO: ABSOLUTELY DO NOT KEEP THIS THROWS EXCEPTION
		Global.getInstance(); // will cause Global singleton to be initialized; probably not necessary to explicitly do this

		// initialize box
		String boxPath = Global.getConfig().getBoxPath();
		LogMaster logger = Global.getLogger();
		File boxDirectory = new File(boxPath);
		File boxDirectoryParent = boxDirectory.getParentFile();
		if (!boxDirectoryParent.exists()) {
			if (!boxDirectoryParent.mkdirs()) {
				Global.getLogger().writeFatalLine("Box Directory's parent does not exist, and it could not be created at path: "
												   + boxDirectoryParent.getAbsolutePath());
				System.exit(1);
			}
			logger.writeDebugLine("Parent Directory was created at path: " + boxDirectoryParent.getAbsolutePath());
		}
		if (!boxDirectory.exists()) {
			if (!boxDirectory.mkdir()) {
				Global.getLogger().writeFatalLine("Box Directory does not exist, and it could not be created at path: "	+ boxPath);
				System.exit(1);
			}
			logger.writeDebugLine("Box Directory was created at path: "	+ boxPath);
		}
		
        // initialize file index database	        
        try {
			TableUtils.createTableIfNotExists(Global.getORMLiteConnection(), MiBoxFile.class);
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}
        
        // initialize S3
        RestS3Service s3 = Global.getS3();
        // create Filename -> Hash map bucket, and enable versioning
        s3.createBucket(Global.getConfig().getFilenameHashMapBucket());
		s3.enableBucketVersioning(Global.getConfig().getFilenameHashMapBucket());
		// create Hash -> Data map bucket
		s3.createBucket(Global.getConfig().getHashDataMapBucket());
		
		// initialize SimpleDB
		AmazonSimpleDB sdb = Global.getSDB();
		sdb.createDomain(new CreateDomainRequest(Global.getConfig().getCloudFilesDomain()));
		sdb.createDomain(new CreateDomainRequest(Global.getConfig().getCloudVersionsDomain()));
		sdb.createDomain(new CreateDomainRequest(Global.getConfig().getLastSyncDatesDomain()));
        
		// perform initial sync
		SyncMaster sm = new SyncMaster();
		try {
			sm.performInitialSync();
		} catch (IntegrityError e1) {
			System.out.println(e1.getMessage());
			System.exit(1);
		}
		
        // clean up
        try {
			Global.cleanUp();
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}
}
