/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.archive;

import java.io.*;
import org.apache.log4j.Logger;

/**
 * A stack frame encapsulating a single directory in a tree.
 */
public class Frame implements Serializable {

	static final Logger logger = Logger.getLogger(Frame.class);

	File dir;
	File[] files;
	int next;

	/**
	 * Construct a Frame, encapsulating one directory.
	 * @param dir the directory.
	 */
	public Frame(File dir) {
		this.dir = dir;
		if (dir.exists() && dir.isDirectory()) files = dir.listFiles();
		else files = new File[0];
		next = 0;
	}

	/**
	 * Get the next file available in the directory.
	 * @return the next file available, or null if no file is available,
	 * indicating the end of the arboreal perambulation.
	 */
	public File getNextFile() {
		return (next < files.length) ? files[next++] : null;
	}

}