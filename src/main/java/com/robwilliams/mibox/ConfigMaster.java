package com.robwilliams.mibox;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigMaster {
	
	// local constants
	private final String boxPath; // TODO: get away from Windows-specific filenames
	private final String miboxHostName;
	private final String encryptionKey;
	
	// AWS constants
	private final String filenameHashMapBucket;
	private final String hashDataMapBucket;
	private final String cloudFilesDomain;
	private final String cloudVersionsDomain;
	private final String lastSyncDatesDomain;
	private final String AWS_secretKey;
	private final String AWS_accessKey;
	private final int serviceCallRetries;
	
	// singleton object
	private static ConfigMaster config = null;
	
	// private constructor
	private ConfigMaster() {
		// open properties file
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream("mibox.properties"));
		} catch (IOException e) {
			Global.getLogger().writeFatalLine("Could not open properties file for configuration data");
			System.exit(1);
		}
		// initialize configuration values from properties file
		// TODO: Throw error if required attributes are missing or non-empty
		boxPath = properties.getProperty("boxPath");
		miboxHostName = properties.getProperty("miboxHostName");
		encryptionKey = properties.getProperty("encryptionKey"); // TODO: PLEASE DO SOMETHING MORE SECURE WITH THIS!!
		filenameHashMapBucket = properties.getProperty("filenameHashMapBucket");
		hashDataMapBucket = properties.getProperty("hashDataMapBucket");
		AWS_secretKey = properties.getProperty("AWS_secretKey");
		AWS_accessKey = properties.getProperty("AWS_accessKey");
		// initialize optional settings, by specifying default values
		cloudFilesDomain = properties.getProperty("cloudFilesDomain", "CloudFiles");
		cloudVersionsDomain = properties.getProperty("cloudVersionsDomain", "CloudVersions");
		lastSyncDatesDomain = properties.getProperty("lastSyncDatesDomain", "LastSyncDates");
		serviceCallRetries = Integer.parseInt(properties.getProperty("serviceCallRetries", "3"));
	}
	
	// singleton getInstance method
	public static ConfigMaster getConfig() {
		if (config == null) {
			config = new ConfigMaster();
		}
		return config;
	}
	
	//
	// getters and setters
	//
	
	public String getBoxPath() {		
		return boxPath;
	}

	public String getFilenameHashMapBucket() {
		return filenameHashMapBucket;
	}

	public String getAWSSecretkey() {
		return AWS_secretKey;
	}

	public String getAWSAccesskey() {
		return AWS_accessKey;
	}

	public String getHashDataMapBucket() {
		return hashDataMapBucket;
	}

	public String getCloudFilesDomain() {
		return cloudFilesDomain;
	}

	public String getCloudVersionsDomain() {
		return cloudVersionsDomain;
	}

	public String getLastSyncDatesDomain() {
		return lastSyncDatesDomain;
	}

	public String getMiBoxHostName() {
		return miboxHostName;
	}

	public int getServiceCallRetries() {
		return serviceCallRetries;
	}

	public String getEncryptionKey() {
		return encryptionKey;
	}
}
