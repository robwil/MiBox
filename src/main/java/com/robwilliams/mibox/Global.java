package com.robwilliams.mibox;

import java.sql.SQLException;

import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.security.AWSCredentials;
import org.jets3t.service.security.ProviderCredentials;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.robwilliams.mibox.dataObjects.MiBoxFile;


public class Global {
	private LogMaster logger;
	private ConfigMaster config;
    private RestS3Service s3;
	private AmazonSimpleDB sdb;
	private ConnectionSource connectionSource;
	private Dao<MiBoxFile, String> fileMetadataDao;
	
	// singleton object
	private static Global global = null;
		
	// private constructor
	private Global() {
		logger = LogMaster.getLogger();
		config = ConfigMaster.getConfig();
		try {
			ProviderCredentials credentials = new AWSCredentials(config.getAWSAccesskey(), config.getAWSSecretkey());
			s3 = new RestS3Service(credentials);
			BasicAWSCredentials oAWSCredentials = new BasicAWSCredentials(config.getAWSAccesskey(), config.getAWSSecretkey());
			sdb = new AmazonSimpleDBClient(oAWSCredentials);
		
			// create a connection source to database and instantiate the DAO
			connectionSource = new JdbcConnectionSource("jdbc:sqlite:file_index.db");
			fileMetadataDao =  DaoManager.createDao(connectionSource, MiBoxFile.class);
		} catch (Exception e) {
			// TODO: do something nicer to error handle this	
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	// implement singleton getInstance API
	public static Global getInstance() {
		if (global == null) {
			global = new Global();
		}
		return global;
	}
	
	public static RestS3Service getS3() {
		Global gl = getInstance();
		return gl.s3;
	}
	
	public static AmazonSimpleDB getSDB() {
		Global gl = getInstance();
		return gl.sdb;
	}
	
	public static LogMaster getLogger() {
		Global gl = getInstance();
		return gl.logger;
	}
	
	public static ConfigMaster getConfig() {
		Global gl = getInstance();
		return gl.config;
	}
	public static ConnectionSource getORMLiteConnection() {
		Global gl = getInstance();
		return gl.connectionSource;
	}
	public static Dao<MiBoxFile, String> getFileMetadataDAO() {
		Global gl = getInstance();
		return gl.fileMetadataDao;
	}

	public static void cleanUp() throws SQLException {
		if (global != null) {
			Global gl = getInstance();
			gl.connectionSource.close();
			global = null;
		}
	}
}
