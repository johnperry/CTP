/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

/**
 * A class to encapsulate typesafe enum return status values for CTP services.
 * This class provides static final instances for each of the possible operational
 * results (OK, FAIL, RETRY).
 */
public class Status {

	private final String status;

	//Private constructor to prevent anything but this class
	//from instantiating the class.
	private Status(String status) { this.status = status; }

	/**
	 * Get the text string name of the status class.
	 * @return a text string describing the status instance (OK, FAIL, RETRY).
	 */
	public String toString() { return status; }

	/**
	 * Status value indicating that an operation completed successfully.
	 */
	public static final Status OK = new Status("OK");

	/**
	 * Status value indicating that an operation failed and retrying
	 * will also fail.
	 */
	public static final Status FAIL = new Status("FAIL");

	/**
	 * Status value indicating that an operation failed but retrying
	 * may succeed.
	 */
	public static final Status RETRY = new Status("RETRY");

}