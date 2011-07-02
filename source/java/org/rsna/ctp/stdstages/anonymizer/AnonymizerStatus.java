/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer;

import java.io.File;

/**
 * A class to encapsulate typesafe enum return status values for CTP services.
 * This class provides static final instances for each of the possible operational
 * results (OK, SKIP, QUARANTINE).
 */
public class AnonymizerStatus {

	private String status;
	private File file;
	private String message;

	//Private constructor to prevent anything but this class
	//from instantiating the class.
	private AnonymizerStatus(String status, File file, String message) {
		this.status = status;
		this.file = file;
		this.message = message;
	}

	/**
	 * Get the text string name of the status.
	 * @return true if the status indicates OK.
	 */
	public boolean isOK() { return status.equals("OK"); }

	/**
	 * Get the text string name of the status.
	 * @return true if the status indicates SKIP.
	 */
	public boolean isSKIP() { return status.equals("SKIP"); }

	/**
	 * Get the text string name of the status.
	 * @return true if the status indicates QUARANTINE.
	 */
	public boolean isQUARANTINE() { return status.equals("QUARANTINE"); }

	/**
	 * Get the text string name of the status.
	 * @return a text string describing the status instance (OK, SKIP, QUARANTINE).
	 */
	public String getStatus() { return status; }

	/**
	 * Get the text string name of the message.
	 * @return the status message.
	 */
	public File getFile() { return file; }

	/**
	 * Get the text string name of the message.
	 * @return the status message.
	 */
	public String getMessage() { return message; }

	/**
	 * Get the status as a string.
	 * @return the status message.
	 */
	public String toString() {
		return "("+status+","+file+","+message+")";
	}

	/**
	 * Status value indicating that the anonymization succeeded.
	 */
	public static AnonymizerStatus OK(File file, String message) {
		return new AnonymizerStatus("OK", file, message);
	}

	/**
	 * Status value indicating that a skip() function call was encountered.
	 */
	public static AnonymizerStatus SKIP(File file, String message) {
		return new AnonymizerStatus("SKIP", file, message);
	}

	/**
	 * Status value indicating that a quarantine() function call was encountered.
	 */
	public static AnonymizerStatus QUARANTINE(File file, String message) {
		return new AnonymizerStatus("QUARANTINE", file, message);
	}

}