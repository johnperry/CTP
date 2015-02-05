/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
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
import org.rsna.ctp.stdstages.Scriptable;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An abstract class implementing the ExportService interface.
 * This class provides the queue management and status functions so
 * ExportServices only have to receive files and add them
 * to the queue directory.
 */
public abstract class AbstractQueuedExportService
							extends AbstractPipelineStage
							implements ExportService, Scriptable {

	static final Logger logger = Logger.getLogger(AbstractQueuedExportService.class);

	protected File active = null;
	protected String activePath = "";
	protected File temp = null;
	protected QueueManager cacheManager = null;
	protected QueueManager queueManager = null;
	protected File dicomScriptFile = null;
	protected File xmlScriptFile = null;
	protected File zipScriptFile = null;
	protected volatile File lastFileDequeued = null;
	protected volatile long lastTimeDequeued = 0;

	/**
	 * Construct an ExportService.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public AbstractQueuedExportService(Element element) {
		super(element);
		if (root == null)
			logger.error(name+": No root directory was specified.");
		else {
			dicomScriptFile = FileUtil.getFile(element.getAttribute("dicomScript").trim(), "examples/example-filter.script");
			xmlScriptFile = FileUtil.getFile(element.getAttribute("xmlScript").trim(), "examples/example-filter.script");
			zipScriptFile = FileUtil.getFile(element.getAttribute("zipScript").trim(), "examples/example-filter.script");

			temp = new File(root, "temp");
			temp.mkdirs();
			
			File queue = new File(root, "queue");
			queueManager = new QueueManager(queue, 0, 0); //use default settings
			active = new File(root, "active");
			activePath = active.getAbsolutePath();
			queueManager.enqueueDir(active); //requeue any files that are left from an ungraceful shutdown.

			cacheManager = queueManager;
			int cacheSize = StringUtil.getInt(element.getAttribute("cacheSize").trim());
			if (cacheSize > 0) {
				File cache = new File(root, "cache");
				cacheManager = new QueueManager(cache, 0, 0);
			}
		}
	}

	/**
	 * Get the script files.
	 * @return the script files used by this stage.
	 */
	public synchronized File[] getScriptFiles() {
		return new File[] { dicomScriptFile, xmlScriptFile, zipScriptFile };
	}

	/**
	 * Get the temp directory
	 * @return the temp directory to use while receiving objects.
	 */
	public synchronized File getTempDirectory() {
		return temp;
	}

	/**
	 * Get the QueueManager of the export queue.
	 * @return the QueueManager.
	 */
	public synchronized QueueManager getQueueManager() {
		return queueManager;
	}

	/**
	 * Get the QueueManager of the cache.
	 * @return the QueueManager.
	 */
	public synchronized QueueManager getCacheManager() {
		return cacheManager;
	}

	/**
	 * Add a FileObject to the export queue if the ExportService is
	 * configured to accept the FileObject class or subclass.
	 * If a script file corresponding to the object is defined,
	 * compute its value and enqueue the object if the result is true.
	 * This method enqueues the object and returns immediately.
	 * @param fileObject the object to be exported.
	 */
	public synchronized void export(FileObject fileObject) {
		lastFileIn = fileObject.getFile();
		lastTimeIn = System.currentTimeMillis();
		if (fileObject instanceof DicomObject) {
			if (acceptDicomObjects) {
				if ((dicomScriptFile == null)
					|| ((DicomObject)fileObject).matches(dicomScriptFile))
							enqueue(fileObject);
			}
		}
		else if (fileObject instanceof XmlObject) {
			if (acceptXmlObjects) {
				if ((xmlScriptFile == null)
					|| ((XmlObject)fileObject).matches(xmlScriptFile))
							enqueue(fileObject);
			}
		}
		else if (fileObject instanceof ZipObject) {
			if (acceptZipObjects) {
				if ((zipScriptFile == null)
					|| ((ZipObject)fileObject).matches(zipScriptFile))
							enqueue(fileObject);
			}
		}
		else if (acceptFileObjects) enqueue(fileObject);
		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
	}

	//Queue a fileObject.
	//Note: if caching is enabled, this puts the object in the cache;
	//if caching is not enabled, it puts the object directly in the export queue.
	private void enqueue(FileObject fileObject) {
		if (cacheManager.enqueue(lastFileIn) == null) {
			if (quarantine != null) quarantine.insertCopy(fileObject);
		}
	}

	/**
	 * Get the size of the export queue.
	 * return the size of the export queue, or 0 if no QueueManager exists.
	 */
	public synchronized int getQueueSize() {
		if (queueManager != null) return queueManager.size();
		return 0;
	}

	/**
	 * Force a recount of the export queue.
	 * return the size of the export queue, or 0 if no QueueManager exists.
	 */
	protected synchronized int recount() {
		if (queueManager != null) return queueManager.recount();
		return 0;
	}

	/**
	 * Get the next file in the queue for exporting. This method moves the file to
	 * the active directory. Note that this means that if the export fails in such
	 * a way that a retry is appropriate, the file must be requeued.
	 */
	protected synchronized File getNextFile() {
		if (queueManager != null) {
			File file = queueManager.dequeue(active);
			if (file != null) {
				lastFileDequeued = file;
				lastTimeDequeued = System.currentTimeMillis();
			}
			return file;
		}
		return null;
	}

	/**
	 * Release a file from the active directory. Note that the
	 * file may have moved, so it is possible that the file will
	 * no longer exist. This method only deletes the file if it
	 * is still in the active directory.
	 * @param file the file to be released, which must be the original file
	 * supplied by the ExportService.
	 */
	protected synchronized boolean release(File file) {
		boolean ok = false;
		if ((file != null)
				&& file.exists()
					&& file.getParentFile().getAbsolutePath().equals(activePath)) {
			ok = file.delete();
		}
		return ok;
	}

	/**
	 * Get HTML text displaying the active status of the stage.
	 * @param childUniqueStatus the status of the stage of which
	 * this class is the parent.
	 * @return HTML text displaying the active status of the stage.
	 */
	public synchronized String getStatusHTML(String childUniqueStatus) {
		StringBuffer sb = new StringBuffer();
		sb.append("<tr><td width=\"20%\">Queue size:</td>");
		sb.append("<td>" + ((queueManager!=null) ? queueManager.size() : "???") + "</td></tr>");
		sb.append("<tr><td width=\"20%\">Last file dequeued:</td>");
		if (lastTimeDequeued != 0) {
			sb.append("<td>"+lastFileDequeued+"</td></tr>");
			sb.append("<tr><td width=\"20%\">Last file dequeued at:</td>");
			sb.append("<td>"+StringUtil.getDateTime(lastTimeDequeued,"&nbsp;&nbsp;&nbsp;")+"</td></tr>");
		}
		else sb.append("<td>No activity</td></tr>");
		return super.getStatusHTML(childUniqueStatus + sb.toString());
	}

}


