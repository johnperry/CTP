/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.ctp.stdplugins.AuditLog;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A Processor stage that logs the differences between two DicomObjects,
 * the current object and an object cached by an earlier ObjectCache stage.
 */
public class DicomAuditLogger extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(DicomAuditLogger.class);

	String verbosity;
	String objectCacheID;
	String auditLogID;
	AuditLog auditLog = null;
	ObjectCache objectCache = null;

	/**
	 * Construct the DicomDifferenceLogger PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomAuditLogger(Element element) {
		super(element);
		verbosity = element.getAttribute("verbosity");
		objectCacheID = element.getAttribute("cacheID");
		auditLogID = element.getAttribute("auditLogID");
	}

	/**
	 * Start the pipeline stage. When this method is called, all the
	 * stages have been instantiated. We have to get the ObjectCache
	 * and AuditLog stages here to ensure that the Configuration
	 * has been instantiated. (Note: The Configuration constructor has
	 * not finished when the stages are constructed.)
	 */
	public void start() {
		Configuration config = Configuration.getInstance();

		if (!verbosity.equals("")) {
			PipelineStage stage = config.getRegisteredStage(objectCacheID);
			if ((stage != null) && (stage instanceof ObjectCache)) {
				objectCache = (ObjectCache)stage;
			}
			else logger.warn("Unable to obtain the ObjectCache");
		}

		Plugin plugin = config.getRegisteredPlugin(auditLogID);
		if ((plugin != null) && (plugin instanceof AuditLog)) {
			auditLog = (AuditLog)plugin;
		}
		else logger.warn("Unable to obtain the AuditLog");
	}

	/**
	 * Log objects as they are received by the stage.
	 * @param fileObject the object to log.
	 * @return the same FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if ((auditLog != null) && (fileObject instanceof DicomObject)) {
			DicomObject dob = (DicomObject)fileObject;
			String patientID = dob.getPatientID();
			String studyUID = dob.getStudyInstanceUID();
			String sopiUID = dob.getSOPInstanceUID();
			String entry = "PatientID:  " + patientID + "\n"
						 + "Study UID:  " + studyUID + "\n"
						 + "Object UID: " + sopiUID;

			try { auditLog.addEntry( entry, "text", patientID, studyUID, sopiUID ); }
			catch (Exception ex) { logger.warn("Unable to add audit log entry for "+sopiUID, ex); }
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		return super.getStatusHTML("");
	}

}