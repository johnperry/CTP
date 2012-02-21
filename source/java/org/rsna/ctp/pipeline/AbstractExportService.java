/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An abstract class implementing the ExportService interface.
 * This class provides the queue management and status functions so
 * normal ExportServices only have to receive files and add them
 * to the queue directory.
 */
public abstract class AbstractExportService extends AbstractQueuedExportService {

	static final Logger logger = Logger.getLogger(AbstractExportService.class);

	static final int defaultInterval = 5000;
	static final int minInterval = 1000;
	static final int maxInterval = 2 * defaultInterval;
	static final int maxThrottle = 5000;

	int successCount = 0;
	int retryCount = 0;

	int throttle = 0;
	int interval = defaultInterval;
	Exporter exporter = null;
	boolean enableExport = true;

	/**
	 * Construct an ExportService.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public AbstractExportService(Element element) {
		super(element);
		if (root != null) {
			throttle = StringUtil.getInt(element.getAttribute("throttle"));
			if (throttle < 0) throttle = 0;
			if (throttle > maxThrottle) throttle = maxThrottle;
			interval = StringUtil.getInt(element.getAttribute("interval"));
			if ((interval < minInterval) || (interval > maxInterval)) interval = defaultInterval;
			enableExport = !element.getAttribute("enableExport").trim().equals("no");
			exporter = new Exporter();
		}
	}

	/**
	 * Start the export thread. This method is called by the subclass
	 * that does the actual exporting after it has had time to set up.
	 */
	public void startExportThread() {
		if (enableExport && (exporter != null)) exporter.start();
	}

	/**
	 * Determine whether the pipeline stage has shut down.
	 */
	public synchronized boolean isDown() {
		if (enableExport
				&& (exporter != null)
						&& !exporter.getState().equals(Thread.State.TERMINATED))
								return false;
		return stop;
	}

	/**
	 * Abstract method to export a file.
	 * @param file the file to export.
	 * @return the status of the attempt to export the file.
	 */
	public abstract Status export(File file);

	/**
	 * Dummy method which receives notification from the subordinate Exporter Thread
	 * that it is about to export files. ExportServices that need to connect to
	 * external databases or other systems should override this method.
	 * @return indication whether the connection could be made. This method always
	 * returns Status.OK, but methods that override it should return the correct
	 * result.
	 */
	public synchronized Status connect() {
		return Status.OK;
	}

	/**
	 * Dummy method which receives notification from the subordinate Exporter Thread
	 * that it is temporarily done exporting files. ExportServices that need to commit
	 * changes to external databases or other systems should override this method.
	 * @return indication whether the disconnect processing was successful.
	 * This method always returns Status.OK, but methods that override it should
	 * return the correct result.
	 */
	public synchronized Status disconnect() {
		return Status.OK;
	}

	class Exporter extends Thread {
		public Exporter() {
			super(name + " Exporter");
		}
		public void run() {
			logger.info(name+": Exporter Thread: Started");
			File file = null;
			while (!stop && !interrupted()) {
				try {
					if ((getQueueSize()>0) && connect().equals(Status.OK)) {
						while (!stop && ((file = getNextFile()) != null)) {
							Status result = export(file);
							if (result.equals(Status.FAIL)) {
								//Something is wrong with the file.
								//Log a warning and quarantine the file.
								logger.warn(name+": Unable to export "+file);
								if (quarantine != null) quarantine.insert(file);
								else file.delete();
							}
							else if (result.equals(Status.RETRY)) {
								//Something is wrong, but probably not with the file.
								//Note that the file has been removed from the queue,
								//so it is necessary to requeue it. This has the
								//effect of moving it to the end of the queue.
								getQueueManager().enqueue(file);
								//Note that enqueuing a file does not delete it
								//from the source location, so we must delete it now.
								file.delete();
								logger.debug("Status.RETRY received: successCount = "+successCount+"; retryCount = "+retryCount);
								successCount = 0;
								//Only break if we have had a string of failures
								//in a row; otherwise, move on to the next file.
								if (retryCount++ > 5) break;
							}
							else {
								if (throttle > 0) {
									try { Thread.sleep(throttle); }
									catch (Exception ignore) { }
								}
								release(file);
								successCount++;
								retryCount = 0;
							}
						}
						disconnect();
					}
					if (!stop) sleep(interval);
					//Recount the queue in case it has been corrupted by
					//someone copying files into the queue directories by hand.
					//To keep from doing this when it doesn't really matter and
					//it might take a long time, only do it when the remaining
					//queue is small.
					if (!stop && (getQueueSize() < 20)) recount();
				}
				catch (Exception e) {
					logger.warn(name+" Exporter Thread: Exception received",e);
					stop = true;
				}
			}
			logger.info(name+" Thread: Interrupt received; thread stopped");
		}
	}

}