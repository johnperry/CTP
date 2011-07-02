/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.database;

/**
 * A class to encapsulate typesafe enum return values
 * for the DatabaseAdapter uidQuery method.
 */
public class UIDResult {

	private boolean present;
	private long datetime;
	private String digest;

	//Private constructor to prevent anything but this class
	//from instantiating the class.
	private UIDResult(boolean present, long datetime, String digest) {
		this.present = present;
		this.datetime = datetime;
		this.digest = digest;
	}

	/**
	 * See if the UID was present in the database.
	 * @return true if the UID was present; false otherwise.
	 */
	public boolean isPRESENT() { return present; }

	/**
	 * See if the UID was missing from the database.
	 * @return true if the UID was missing; false otherwise.
	 */
	public boolean isMISSING() { return !present; }

	/**
	 * See if the UID was missing from the database.
	 * @return true if the UID was missing; false otherwise.
	 */
	public boolean matches(String digest) {
		if (present && (digest != null) && (this.digest != null)) {
			return this.digest.equals(digest);
		}
		return false;
	}

	/**
	 * Get the datetime at which the database stored the object.
	 * @return the datetime for when the object was stored in the database,
	 * or zero if the datetime is unknown.
	 */
	public long getDateTime() { return datetime; }

	/**
	 * Get the digest string for the object in the database.
	 * @return the digest string for the object in the database.
	 */
	public String getDigest() { return digest; }

	/**
	 * Get the UIDResult as a string.
	 * @return the status message.
	 */
	public String toString() {
		return "("+(present?"PRESENT, " + datetime:"MISSING")+")";
	}

	/**
	 * Get a UIDResult indicating that the object was in the database.
	 * @param datetime the System time at which the object was
	 * stored in the database (in milliseconds since 1970).
	 * @param digest the MD5 digest of the object that is
	 * stored in the database.
	 */
	public static UIDResult PRESENT(long datetime, String digest) {
		return new UIDResult(true, datetime, digest);
	}

	/**
	 * UIDResult value indicating that the object was not n the database.
	 */
	public static UIDResult MISSING() {
		return new UIDResult(false, 0L, "");
	}
}