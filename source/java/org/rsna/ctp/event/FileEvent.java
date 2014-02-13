/*---------------------------------------------------------------
*  Copyright 2014 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.event;

import java.io.File;

/**
 * The event that passes the result of a file operation to FileListeners.
 */
public class FileEvent {

	File file;

	/**
	 * Class constructor capturing a file event.
	 * @param file the file on which the event occurred.
	 */
	public FileEvent(File file) {
		this.file = file;
	}

	public File getFile() {
		return file;
	}

}
