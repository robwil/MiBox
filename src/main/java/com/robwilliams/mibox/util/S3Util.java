package com.robwilliams.mibox.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Date;

import org.jets3t.service.Constants;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.io.BytesProgressWatcher;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.EncryptionUtil;
import org.jets3t.service.utils.Mimetypes;
import org.jets3t.service.utils.ObjectUtils;
import org.jets3t.service.utils.ServiceUtils;

import com.robwilliams.mibox.Global;

public class S3Util {
	
	public static EncryptionUtil getEncryptionUtil() throws Exception {
		return new EncryptionUtil(Global.getConfig().getEncryptionKey(), "PBEWITHSHA256AND128BITAES-CBC-BC", "2");
	}
	
	/**
	 * Basically I just copied the ObjectUtils.createObjectForUpload from JetS3t's ObjectUtils, so that it could work
	 * with a pre-computed hash. In the case of MiBox, that hash comes from local DB record and can potentially
	 * save a lot of computation time.
	 * @param objectKey If null, it will be set to the hash.
	 * @param dataFile
	 * @param hash Precomputed hash. If null, the hash will be computed.
	 * @return
	 * @throws Exception
	 */
	public static S3Object createObjectForUploadWithPrecomputedHash(String objectKey, File dataFile, String hash) throws Exception {
	    S3Object s3Object = new S3Object(objectKey != null ? objectKey : "dummy"); // if key is null, set key to dummy and change to hash at end
	
	    // Set object explicitly to private access by default.
	    s3Object.setAcl(AccessControlList.REST_CANNED_PRIVATE);
	
	    s3Object.addMetadata(Constants.METADATA_JETS3T_LOCAL_FILE_DATE,
	        ServiceUtils.formatIso8601Date(new Date(dataFile.lastModified())));
	
	    if (dataFile.isDirectory()) {
	        s3Object.setContentLength(0);
	        s3Object.setContentType(Mimetypes.MIMETYPE_BINARY_OCTET_STREAM);
	    } else {
	        s3Object.setContentType(Mimetypes.getInstance().getMimetype(dataFile));
	        // hack: use reflection to call private method of JetS3t
	        Method transformMethod = ObjectUtils.class.getDeclaredMethod("transformUploadFile", File.class, S3Object.class, EncryptionUtil.class, boolean.class, BytesProgressWatcher.class);
	        transformMethod.setAccessible(true); // mark as non-private
	        File uploadFile = (File) transformMethod.invoke(null, dataFile, s3Object, getEncryptionUtil(), false, (BytesProgressWatcher) null);
	        s3Object.setContentLength(uploadFile.length());
	        s3Object.setDataInputFile(uploadFile);
	
	        // Compute the upload file's MD5 hash.
	        InputStream inputStream = new BufferedInputStream(new FileInputStream(uploadFile));
	        if (hash != null) {
	        	s3Object.setMd5Hash(ServiceUtils.fromHex(hash));
	        } else {
	        	s3Object.setMd5Hash(ServiceUtils.computeMD5Hash(inputStream));
	        }
	        
	        if (!uploadFile.equals(dataFile)) {
	            // Compute the MD5 hash of the *original* file, if upload file has been altered
	            // through encryption or gzipping.
	            inputStream = new BufferedInputStream(new FileInputStream(dataFile));
	
	            s3Object.addMetadata(
	                S3Object.METADATA_HEADER_ORIGINAL_HASH_MD5,
	                ServiceUtils.toBase64(ServiceUtils.computeMD5Hash(inputStream)));
	        }
	    }
	    s3Object.setKey(s3Object.getMd5HashAsHex());
	    return s3Object;
	}
	
	public static S3Object createObjectForUpload(File dataFile) throws Exception {
		return createObjectForUploadWithPrecomputedHash(null, dataFile, null);
	}
}
