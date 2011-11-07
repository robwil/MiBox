package com.robwilliams.mibox.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.Vector;

import com.robwilliams.mibox.Global;

public class FileUtil {
	
	/**
	 * Given a relative file path (the kind used in the DBs and on the cloud)
	 * return a string representing the local absolute file path, using correct
	 * separators for the host OS.
	 * 
	 * @param relativePath
	 * @return
	 */
	public static String getLocalFilePath(String relativePath) {
		// TODO: make this convert UNIX style relativePath to system-dependent path of local system
		return Global.getConfig().getBoxPath() + File.separator + relativePath;
	}
	
	// source: http://snippets.dzone.com/posts/show/1875
	// Given a directory File object, return a Collection of File's in that directory
	// (and sub-directories if 'recurse' parameter is set to 'true')
	public static Collection<File> listFiles(File directory, FilenameFilter filter, boolean recurse) {
		// List of files / directories
		Vector<File> files = new Vector<File>();

		// Get files / directories in the directory
		File[] entries = directory.listFiles();
		
		// TODO _make sure this uses UNIX style relative paths
		
		// Go over entries
		for (File entry : entries) {
			// If there is no filter or the filter accepts the
			// file / directory, add it to the list
			if (filter == null || filter.accept(directory, entry.getName())) {
				files.add(entry);
			}			

			// If the file is a directory and the recurse flag
			// is set, recurse into the directory
			if (recurse && entry.isDirectory()) {
				files.addAll(listFiles(entry, filter, recurse));
			}
		}

		// Return collection of files
		return files;
	}
	
	// given a file path, return the SHA1 hash of the file's contents
	// NOW done by JetS3t, so deprecated
	@Deprecated
	public static String hash(String fileName) throws IOException {
		FileInputStream fis = null;
		
		try {
			fis = new FileInputStream(fileName);
			
			// create digest using SHA2 algorithm, reading file a chunk at a time
		    byte[] buffer = new byte[1024];
		    MessageDigest digest = MessageDigest.getInstance("SHA1");
		    int bytesRead;
		    do {
		      bytesRead = fis.read(buffer);
		      if (bytesRead > 0) {
		        digest.update(buffer, 0, bytesRead);
		      }
		    } while (bytesRead != -1);	    
		    
		    // convert digest to string and return it
		    byte[] b = digest.digest();
		    String result = "";
		    for (int i = 0; i < b.length; i++) {
		      result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		    }
		    return result;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		} finally {
			if (fis != null)
				fis.close();
		}
		
		return "";
	}

	public static void writeInputStreamToFileAndSetLastModDate(InputStream in, String fileName, Date lastModDate) throws IOException {
		File file = new File(fileName);
		file.getParentFile().mkdirs(); // creates parent directories of file, if needed
		OutputStream out=new FileOutputStream(file);
		byte buf[]=new byte[1024];
		int len;
		while((len=in.read(buf))>0)
			out.write(buf,0,len);
		out.close();
		in.close();
		file.setLastModified(lastModDate.getTime());
	}
}
