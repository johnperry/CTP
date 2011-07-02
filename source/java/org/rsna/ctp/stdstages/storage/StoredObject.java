/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import org.apache.log4j.Logger;

/**
 * A class to encapsulate the File and URL of a stored object.
 */
public class StoredObject {

	static final Logger logger = Logger.getLogger(StoredObject.class);

	/** The File pointing to the StoredObject in the FileStorageService. */

	public File file = null;

	/**
	 * The URL by which the StoredObject can be obtained from the FileStorageService.
	 * Note that this URL points to a servlet, and the path does not correspond to the
	 * path on the server.
	*/
	public String url = null;

	/**
	 * Construct a StoredObject.
	 * @param file the File pointing to the StoredObject.
	 * @param url the URL by which the StoredObject can be obtained from the FileStorageService.
	 */
	public StoredObject(File file, String url) {
		this.file = file;
		this.url = url;
	}
}
