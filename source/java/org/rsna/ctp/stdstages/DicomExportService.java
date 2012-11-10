/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.rsna.ctp.stdplugins.AuditLog;
import org.rsna.ctp.stdstages.dicom.DicomStorageSCU;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.pipeline.Status;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * An ExportService that exports files via the DICOM protocol.
 */
public class DicomExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(DicomExportService.class);

	DicomStorageSCU dicomSender = null;
	AuditLog auditLog = null;
	String auditLogID = null;
	LinkedList<Integer> auditLogTags = null;
	String url = "";

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 */
	public DicomExportService(Element element) throws Exception {
		super(element);
		acceptXmlObjects = false;
		acceptZipObjects = false;
		acceptFileObjects = false;

		//Get the destination url
		String url = element.getAttribute("url").trim();

		//See if we are to force a close of the
		//association on every transfer
		boolean forceClose = element.getAttribute("forceClose").equals("yes");

		//Get the calledAETTag, if any
		int calledAETTag = StringUtil.getHexInt(element.getAttribute("calledAETTag"));

		//Get the callingAETTag, if any
		int callingAETTag = StringUtil.getHexInt(element.getAttribute("callingAETTag"));

		//Get the DicomSender
		dicomSender = new DicomStorageSCU(url, forceClose, calledAETTag, callingAETTag);

		//Get the AuditLog parameters
		auditLogID = element.getAttribute("auditLogID").trim();
		String[] alts = element.getAttribute("auditLogTags").split("/");
		auditLogTags = new LinkedList<Integer>();
		for (String alt :alts) {
			int tag = DicomObject.getElementTag(alt);
			if (tag == 0) auditLogTags.add(new Integer(tag));
			else logger.warn(name+": Unknown DICOM element tag: "+alt);
		}
	}

	/**
	 * Start the pipeline stage. This method starts the export thread.
	 * It is called by the Pipeline after all the stages have been constructed.
	 */
	public synchronized void start() {
		//Get the AuditLog plugin, if there is one.
		auditLog = (AuditLog)Configuration.getInstance().getRegisteredPlugin(auditLogID);

		//Now that everything is set up, start the thread that
		//will make calls to the export method.
		startExportThread();
	}

	/**
	 * Export a file.
	 * @param fileToExport the file to export.
	 * @return the status of the attempt to export the file.
	 */
	public Status export(File fileToExport) {
		DicomObject dicomObject = null;

		//Get a DicomObject for the file.
		//Leave the file open so it can be used by the sender.
		try { dicomObject = new DicomObject(fileToExport, true); }
		catch (Exception ex) { return Status.FAIL; }

		//Got the object; send it.
		Status status = dicomSender.send(dicomObject);
		dicomObject.close();

		//Make an AuditLog entry if required
		if (status.equals(Status.OK) && (auditLog != null)) {
			String patientID = dicomObject.getPatientID();
			String studyInstanceUID = dicomObject.getStudyInstanceUID();
			String sopInstanceUID = dicomObject.getSOPInstanceUID();
			String sopClassName = dicomObject.getSOPClassName();
			String entry;
			try {
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement("DicomExportService");
				root.setAttribute("URL", url);
				root.setAttribute("SOPClassName", sopClassName);

				for (Integer tag : auditLogTags) {
					int tagint = tag.intValue();
					String elementName = DicomObject.getElementName(tagint);
					if (elementName == null) {
						int g = (tagint >> 16) & 0xFFFF;
						int e = tagint &0xFFFF;
						elementName = String.format("g%04Xe&04X", g, e);
					}
					root.setAttribute(elementName, dicomObject.getElementValue(tagint, ""));
				}
				entry = XmlUtil.toPrettyString(root);
			}
			catch (Exception ex) { entry = "<DicomExportService/>"; }

			try { auditLog.addEntry(entry, "xml", patientID, studyInstanceUID, sopInstanceUID); }
			catch (Exception ex) { logger.warn("Unable to make AuditLog entry"); }
		}
		return status;
	}

}