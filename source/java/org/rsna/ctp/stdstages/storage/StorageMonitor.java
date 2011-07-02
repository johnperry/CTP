/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * The Thread that automatically removes studies from
 * the storage service after they time out.
 */
public class StorageMonitor extends Thread {

	static final Logger logger = Logger.getLogger(StorageMonitor.class);

	File root;
	int timeDepth;
	static final long anHour = 60 * 60 * 1000;
	static final long aDay = 24 * anHour;

	/**
	 * Create a new SharedFileCabinetManager to remove files
	 * from the shared file cabinet after they time out.
	 */
	public StorageMonitor(File root, int timeDepth) {
		this.root = root;
		this.timeDepth = timeDepth;
	}

	/**
	 * Start the thread. Check for timed out files every hour.
	 */
	public void run() {
		try {
			while (true) {
				checkStudies();
				sleep(aDay);
			}
		}
		catch (Exception ex) { return; }
	}

	//Remove timed out studies.
	private void checkStudies() {
		long maxAge = timeDepth * aDay;
		if (maxAge == 0) return;
		long timeNow = System.currentTimeMillis();
		long earliestAllowed = timeNow - maxAge;

		//Get the FileSystemManager
		FileSystemManager fsm = FileSystemManager.getInstance(root);

		//Get the list of FileSystems
		List<String> fsList = fsm.getFileSystems();

		//Look at each FileSystem
		Iterator<String> lit = fsList.iterator();
		while (lit.hasNext()) {
			String fsName = lit.next();
			FileSystem fs = fsm.getFileSystem(fsName, false);

			//Get the studies in this FileSystem
			Study[] studies = fs.getStudies();
			for (int i=0; i<studies.length; i++) {
				Study study = studies[i];
				File dir = study.getDirectory();
				long lm = dir.lastModified();
				if (lm < earliestAllowed) {
					String sn = study.getStudyName();
					fs.deleteStudyByUID(sn);
					logger.info(sn + " deleted");
				}
			}
		}
	}

}
