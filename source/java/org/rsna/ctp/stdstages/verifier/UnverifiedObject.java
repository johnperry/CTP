/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.verifier;

import java.io.Serializable;

/**
 * A class to encapsulate an unverified study for the purpose of verification..
 */
public class UnverifiedObject implements Serializable {

	static final long serialVersionUID = 1L;

	public long datetime;
	public String digest;

	public UnverifiedObject(long datetime, String digest) {
		this.datetime = datetime;
		this.digest = digest;
	}

}
