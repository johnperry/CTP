/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.Serializable;

/**
 * A class to encapsulate typesafe enum return status values for CTP services.
 * This class provides static final instances for each of the possible operational
 * results (OK, FAIL, RETRY).
 */
public class Status implements Serializable {

	private final String status;

	//Protected constructor to prevent anything but this class
	//from instantiating the class.
	protected Status(String status) { this.status = status; }

	/**
	 * Get the text string name of the status class.
	 * @return a text string describing the status instance (OK, FAIL, RETRY).
	 */
	public String toString() { return status; }

	/**
	 * Test whether this status text is the same as an another status text.
	 * @param s Status to test against
	 * @return true if the texts are the same; false otherwise.
	 */
	public boolean is(Status s) { return this.status.equals(s.status); }

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

	/**
	 * Status value indicating that an operation is pending.
	 */
	public static final Status PENDING = new Status("PENDING");

	/**
	 * Status value indicating no information.
	 */
	public static final Status NONE = new Status("NONE");

}