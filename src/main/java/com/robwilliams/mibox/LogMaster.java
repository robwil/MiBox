package com.robwilliams.mibox;

public class LogMaster {

	// constants
	
	// singleton object
	private static LogMaster logger = null;
	
	// private constructor
	private LogMaster() {
		// TODO: open file?
	}
	
	// singleton getInstance method
	public static LogMaster getLogger() {
		if (logger == null) {
			logger = new LogMaster();
		}
		return logger;
	}
	
	//
	// getters and setters
	//
	
	public void writeFatalLine(String message) {
		writeLine("[FATAL] " + message);
	}
	
	public void writeErrorLine(String message) {
		writeLine("[ERROR] " + message);
	}
	
	public void writeWarningLine(String message) {
		writeLine("[WARNING] " + message);
	}
	
	public void writeDebugLine(String message) {
		writeLine("[DEBUG] " + message);
	}
	
	private void writeLine(String message) {
		// TODO: make this write to file
		System.out.println(message);
	}
	
}
