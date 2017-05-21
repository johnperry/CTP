/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.server.User;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A Processor stage that logs objects as they flow by.
 */
public class ObjectLogger extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(ObjectLogger.class);

	volatile boolean loggingEnabled = true;
	volatile int count = 0;
	int interval = 1;
	boolean verbose = false;
	volatile String lastLogEntry = null;
	final String spaces = "                              ";
	final String margin = "\n" + spaces;

	/**
	 * Construct the ObjectLogger PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public ObjectLogger(Element element) {
		super(element);
		verbose = element.getAttribute("verbose").trim().equals("yes");
		loggingEnabled = !element.getAttribute("log").trim().equals("no");
		interval = Math.max( 1, StringUtil.getInt(element.getAttribute("interval").trim(), 1) );
		count = 0;
	}

	/**
	 * Log objects as they are received by the stage.
	 * @param fileObject the object to log.
	 * @return the same FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();
		
		String verboseString = "";

		if (loggingEnabled && ((count % interval) == 0)) {
			DicomObject dob = null;
			if (fileObject instanceof DicomObject) dob = (DicomObject)fileObject;

			if (verbose) {
				verboseString =
					( (dob == null) ? ""
									: margin + "TransferSyntax   = " + dob.getTransferSyntaxName() + " (" + dob.getTransferSyntaxUID() + ")" ) +
					margin + "PatientID        = " + fileObject.getPatientID() +
					margin + "StudyInstanceUID = " + fileObject.getStudyInstanceUID() +
					margin + "SOPInstanceUID   = " + fileObject.getSOPInstanceUID() +
					( (dob == null) ? ""
									: margin + "InstanceNumber   = " + dob.getInstanceNumber() ) +
					margin + "Digest           = " + fileObject.getDigest();
			}
			
			lastLogEntry = 
				name
				+ margin + fileObject.getClassName()
				+ ": (" + (count+1) + ") "
				+ fileObject.getFile().getName()
				+ " @ " + StringUtil.getTime(":")
				+ verboseString;
			
			logger.info(lastLogEntry);
		}
		count++;

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}
	
	public synchronized boolean getLoggingEnabled() {
		return loggingEnabled;
	}

	public synchronized void setLoggingEnabled(boolean loggingEnabled) {
		this.loggingEnabled = loggingEnabled;
	}
	
	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		String logEntry = "";
		if (lastLogEntry != null) {
			logEntry = 
				"<tr>" +
					"<td width=\"20%\">LastLogEntry:</td>" +
					"<td>" + lastLogEntry.replace(margin, "<br/>") + "</td>" +
				"</tr>";
		}
		String stageUniqueStatus =
			"<tr>" + 
				"<td width=\"20%\">Files processed:</td>" +
				"<td>" + count + "</td>" +
				logEntry +
			"</tr>";
		return super.getStatusHTML(stageUniqueStatus);
	}

	/**
	 * Get the list of links for display on the summary page.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public LinkedList<SummaryLink> getLinks(User user) {
		LinkedList<SummaryLink> links = super.getLinks(user);
		if (allowsAdminBy(user)) {
			String qs = "?p="+pipeline.getPipelineIndex()+"&s="+stageIndex;
			if (loggingEnabled) {
				links.addFirst( new SummaryLink("/objectlogger"+qs+"&log=no", null, "Disable Logging", false) );
			}
			else {
				links.addFirst( new SummaryLink("/objectlogger"+qs+"&log=yes", null, "Enable Logging", false) );
			}
		}
		return links;
	}
}