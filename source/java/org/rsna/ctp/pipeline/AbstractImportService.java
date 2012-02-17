/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An abstract class implementing the ImportService interface.
 * This class provides the queue management and status functions so
 * normal ImportServices only have to receive files and add them
 * to the queue directory.
 */
public abstract class AbstractImportService extends AbstractPipelineStage implements ImportService {

	static final Logger logger = Logger.getLogger(AbstractImportService.class);

	File active = null;
	String activePath = "";
	File temp = null;
	QueueManager queueManager = null;
	int count = 0;
	public boolean logDuplicates = false;

/**/LinkedList<String> recentUIDs = new LinkedList<String>();
/**/LinkedList<Long> recentTimes = new LinkedList<Long>();
/**/static final int maxQueueSize = 10;

	/**
	 * Construct an ImportService.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public AbstractImportService(Element element) {
		super(element);
		if (root == null)
			logger.error(name+": No root directory was specified.");
		else {
			logDuplicates = element.getAttribute("logDuplicates").equals("yes");
			temp = new File(root, "temp");
			temp.mkdirs();
			File queue = new File(root, "queue");
			queueManager = new QueueManager(queue, 0, 0); //use default settings
			active = new File(root, "active");
			active.mkdirs();
			activePath = active.getAbsolutePath();
			queueManager.enqueueDir(active); //requeue any files that are left from an ungraceful shutdown.
		}
	}

	/**
	 * Get the temp directory
	 * @return the temp directory to use while receiving objects.
	 */
	public synchronized File getTempDirectory() {
		return temp;
	}

	/**
	 * Get the QueueManager.
	 * @return the QueueManager.
	 */
	public synchronized QueueManager getQueueManager() {
		return queueManager;
	}

	/**
	 * Enqueue a file and log it.
	 * @param file the file that was received.
	 */
	public synchronized void fileReceived(File file) {
		count++; //Count the file
		//The received file is in the temp directory.
		File qFile = getQueueManager().enqueue(file);
		//Enqueuing the file does not delete it
		//from the source directory, so we have to
		//delete it here.
		file.delete();
		//Now log the file. Here, we're logging the enqueued
		//file instead of the version in the temp directory.
		lastFileIn = qFile;
		lastTimeIn = System.currentTimeMillis();
	}

	/**
	 * Get the next object available for processing.
	 * @return the next object available, or null if no object is available.
	 */
	public synchronized FileObject getNextObject() {
		File file;
		if (queueManager != null) {
			while ((file = queueManager.dequeue(active)) != null) {
				lastFileOut = file;
				lastTimeOut = System.currentTimeMillis();
				FileObject fileObject = FileObject.getInstance(lastFileOut);
				fileObject.setStandardExtension();

				if (logDuplicates) {
					//*********************************************************************************************
					//See if this object has the same UID as a recent one.
					String currentUID = fileObject.getUID();
					if (recentUIDs.contains(currentUID)) {
						logger.warn("----------------------------------------------------------------");
						logger.warn(name);
						logger.warn("Duplicate UID in last "+maxQueueSize+" objects: "+currentUID);
						String s = "";
						long time = 0;
						for (int i=0; i<recentUIDs.size(); i++) {
							String uid = recentUIDs.get(i);
							s += uid.equals(currentUID) ? "!" : "*";
							time = recentTimes.get(i).longValue();
						}
						long deltaT = System.currentTimeMillis() - time;
						logger.warn("[oldest] "+s+"! [newest]  deltaT = "+deltaT+"ms");
						logger.warn("----------------------------------------------------------------");
					}
					recentUIDs.add(currentUID);
					recentTimes.add( new Long( System.currentTimeMillis() ) );
					if (recentUIDs.size() > maxQueueSize) { recentUIDs.remove(); recentTimes.remove(); }
					//*********************************************************************************************
				}

				//Make sure we accept objects of this type.
				if (acceptable(fileObject)) return fileObject;

				//If we get here, this import service does not accept
				//objects of the active type. Try to quarantine the
				//object, and if that fails, delete it.
				if (quarantine != null)  quarantine.insert(fileObject);
				else fileObject.getFile().delete();
			}
		}
		return null;
	}

	/**
	 * Release a file from the active directory. Note that other stages in the
	 * pipeline may have moved the file, so it is possible that the file will
	 * no longer exist. This method only deletes the file if it is still in the
	 * active directory.
	 * @param file the file to be released, which must be the original file
	 * supplied by the ImportService.
	 */
	public synchronized void release(File file) {
		if ((file != null)
				&& file.exists()
					&& file.getParentFile().getAbsolutePath().equals(activePath)) {
			if (!file.delete()) {
				logger.warn("Unable to release the processed file from the active directory:");
				logger.warn("    file: "+file.getAbsolutePath());
			}
		}
	}

	/**
	 * Get HTML text displaying the active status of the stage.
	 * @return HTML text displaying the active status of the stage.
	 */
	public synchronized String getStatusHTML() {
		String stageUniqueStatus =
			"<tr><td width=\"20%\">Files received:</td><td>" + count + "</td></tr>"
			+ "<tr><td width=\"20%\">Queue size:</td>"
			+ "<td>"
			+ ((queueManager!=null) ? queueManager.size() : "???")
			+ "</td></tr>";
		return super.getStatusHTML(stageUniqueStatus);
	}

}