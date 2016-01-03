/*---------------------------------------------------------------
*  Copyright 2016 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.ExportService;
import org.rsna.ctp.pipeline.QueueManager;
import org.rsna.ctp.pipeline.Status;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.stdstages.logger.*;
import org.rsna.server.User;
import org.rsna.util.SerializerUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A pipeline stage for exporting differences in DicomObjects to external databases.
 */
public class DicomDifferenceLogger extends AbstractPipelineStage implements ExportService {

	static final Logger logger = Logger.getLogger(DicomDifferenceLogger.class);

	String objectCacheID;
	ObjectCache objectCache = null;
	LinkedList<Integer> tags = null;
	LogAdapter logAdapter = null;

	static final int defaultInterval = 5000;
	static final int minInterval = 1000;
	static final int maxInterval = 2 * defaultInterval;
	static final int maxThrottle = 5000;

	int throttle = 0;
	int interval = defaultInterval;
	Exporter exporter = null;
	public boolean enableExport = true;

	protected QueueManager queueManager = null;
	protected File active = null;
	protected String activePath = "";
	protected File temp = null;

	volatile long lastElapsedTime = -1;

	/**
	 * Construct the AbstractDicomDifferenceLogger PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomDifferenceLogger(Element element) {
		super(element);
		logAdapter = getLogAdapter(element.getAttribute("adapterClass"));
		objectCacheID = element.getAttribute("cacheID").trim();
		String[] tagNames = element.getAttribute("tags").split(";");
		tags = new LinkedList<Integer>();
		for (String tagName : tagNames) {
			tagName = tagName.trim();
			if (!tagName.equals("")) {
				int tag = DicomObject.getElementTag(tagName);
				if (tag != 0) tags.add(new Integer(tag));
				else logger.warn(name+": Unknown DICOM element tag: \""+tagName+"\"");
			}
		}

		//Set up the queue directories
		temp = new File(root, "temp");
		temp.mkdirs();
		File queue = new File(root, "queue");
		queueManager = new QueueManager(queue, 0, 0); //use default settings
		active = new File(root, "active");
		activePath = active.getAbsolutePath();
		queueManager.enqueueDir(active); //requeue any files that are left from an ungraceful shutdown.

		//Set up the exporter
		throttle = StringUtil.getInt(element.getAttribute("throttle").trim());
		if (throttle < 0) throttle = 0;
		if (throttle > maxThrottle) throttle = maxThrottle;
		interval = StringUtil.getInt(element.getAttribute("interval").trim());
		if ((interval < minInterval) || (interval > maxInterval)) interval = defaultInterval;
		enableExport = !element.getAttribute("enableExport").trim().equals("no");
		exporter = new Exporter();
	}
	
	private LogAdapter getLogAdapter(String adapterClassName) {
		try {
			Class adapterClass = Class.forName(adapterClassName);
			Class[] signature = { Element.class };
			Object[] args = { element };
			return (LogAdapter)adapterClass.getConstructor(signature).newInstance(args);
		}
		catch (Exception unable) {
			logger.error(name+": Unable to load the LogAdapter class: " + adapterClassName);
			return null;
		}
	}

	/**
	 * Start the pipeline stage. When this method is called, the Configuration object
	 * and all the stages have been instantiated.
	 */
	public void start() {
		Configuration config = Configuration.getInstance();
		if (!objectCacheID.equals("")) {
			PipelineStage stage = config.getRegisteredStage(objectCacheID);
			if (stage != null) {
				if (stage instanceof ObjectCache) {
					objectCache = (ObjectCache)stage;
				}
				else logger.warn(name+": cacheID \""+objectCacheID+"\" does not reference an ObjectCache");
			}
			else logger.warn(name+": cacheID \""+objectCacheID+"\" does not reference any PipelineStage");
		}
		if ((logAdapter != null) && (objectCache != null) && (exporter != null)) {
			exporter.start();
		}
		else logger.warn(name+": logging is disabled");
	}

	/**
	 * Stop the pipeline stage.
	 */
	public synchronized void shutdown() {
		super.shutdown();
		exporter.interrupt();
	}

	/**
	 * Determine whether the pipeline stage has shut down.
	 */
	public synchronized boolean isDown() {
		if ((exporter != null) && !exporter.getState().equals(Thread.State.TERMINATED))
			return false;
		return stop;
	}

	/**
	 * Log objects as they are received by the stage.
	 * @param fileObject the object to log.
	 */
	public void export(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if ((objectCache != null) && (fileObject instanceof DicomObject)) {

			//Make a DicomObject for the current object
			DicomObject currentObject = (DicomObject)fileObject;

			//Get the cached object
			DicomObject cachedObject = null;
			FileObject fob = objectCache.getCachedObject();
			if ( (fob instanceof DicomObject) ) {
				cachedObject = (DicomObject)fob;
				
				//Okay, we have a current object and a cached object,
				//and both are DicomObjects. First, get the cohort name.
				String cohortName = logAdapter.getCohortName(currentObject, cachedObject);
				
				//Now make the list of elements
				LinkedList<LoggedElement> list = new LinkedList<LoggedElement>();
				for (Integer tag : tags) {
					list.add(new LoggedElement(tag, currentObject, cachedObject));
				}
				
				//Finally, enqueue the data
				QueueEntry entry = new QueueEntry(cohortName, list);
				try {
					File entryFile = File.createTempFile("QF-", ".bin", temp);
					SerializerUtil.serialize(entryFile, entry);
					if (queueManager.enqueue(entryFile) == null) {
						if (quarantine != null) quarantine.insertCopy(fileObject);
						logger.warn(name+": Unable to enter difference object in the export queue");
					}
				}
				catch (Exception ex) {
					logger.warn(name+": Unable to enter difference object in the export queue");
				}
			}
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
	}
	
	/**
	 * Get the size of the export queue.
	 * return the size of the export queue, or 0 if no QueueManager exists.
	 */
	public synchronized int getQueueSize() {
		if (queueManager != null) return queueManager.size();
		return 0;
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
					if ((queueManager.size()>0) && logAdapter.connect().equals(Status.OK)) {
						while (!stop && ((file = queueManager.dequeue(active)) != null)) {
							QueueEntry entry = (QueueEntry)SerializerUtil.deserialize(file);
							long startTime = System.nanoTime();
							Status result = logAdapter.export(entry);
							lastElapsedTime = System.nanoTime() - startTime;
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
								queueManager.enqueue(file);
								//Note that enqueuing a file does not delete it
								//from the source location, so we must delete it now.
								file.delete();
							}
							else {
								if (throttle > 0) {
									try { Thread.sleep(throttle); }
									catch (Exception ignore) { }
								}
								if ((file != null)
										&& file.exists()
											&& file.getParentFile().getAbsolutePath().equals(activePath)) {
									file.delete();
								}
							}
						}
						logAdapter.disconnect();
					}
					if (!stop) sleep(interval);
					//Recount the queue in case it has been corrupted by
					//someone copying files into the queue directories by hand.
					//To keep from doing this when it doesn't really matter and
					//it might take a long time, only do it when the remaining
					//queue is small.
					if (!stop && (queueManager.size() < 20)) queueManager.recount();
				}
				catch (Exception e) {
					logger.debug(name+" Exporter Thread: Exception received", e);
					stop = true;
				}
			}
			logger.info(name+" Thread: Interrupt received; exporter thread stopped");
		}
	}

	/**
	 * Get HTML text displaying the active status of the stage.
	 * @param childUniqueStatus the status of the stage of which
	 * this class is the parent.
	 * @return HTML text displaying the active status of the stage.
	 */
	public synchronized String getStatusHTML(String childUniqueStatus) {
		StringBuffer sb = new StringBuffer();
		sb.append("<tr><td width=\"20%\">Export queue size:</td>");
		sb.append("<td>" + ((queueManager!=null) ? queueManager.size() : "???") + "</td></tr>");
		sb.append(
				  "<tr><td width=\"20%\">Export enabled:</td>"
				+ "<td>"
				+ (enableExport ? "yes" : "no")
				+ "</td></tr>");
		if (lastElapsedTime >= 0) {
			long et = lastElapsedTime / 1000000;
			sb.append(
				  "<tr><td width=\"20%\">Last export elapsed time:</td>"
				+ "<td>"
				+ String.format("%d msec", et)
				+ "</td></tr>");
		}
		return super.getStatusHTML(childUniqueStatus + sb.toString());
	}

}