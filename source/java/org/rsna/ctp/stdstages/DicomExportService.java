/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.rsna.ctp.stdstages.dicom.DicomStorageSCU;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.pipeline.Status;
import org.rsna.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * An ExportService that exports files via the DICOM protocol.
 */
public class DicomExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(DicomExportService.class);

	DicomStorageSCU dicomSender = null;
	String url = "";

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 * @throws Exception if the URL does not parse
	 */
	public DicomExportService(Element element) throws Exception {
		super(element);
		acceptDicomObjects = true;
		acceptXmlObjects = false;
		acceptZipObjects = false;
		acceptFileObjects = false;

		//Get the destination url
		url = element.getAttribute("url").trim();

		//Get the association timeout
		int timeout = StringUtil.getInt(element.getAttribute("associationTimeout"))*1000;

		//See if we are to force a close of the association on every transfer
		boolean forceClose = element.getAttribute("forceClose").trim().equals("yes");

		//Get the hostTag, if any
		int hostTag = DicomObject.getElementTag(element.getAttribute("hostTag").trim());

		//Get the portTag, if any
		int portTag = DicomObject.getElementTag(element.getAttribute("portTag").trim());

		//Get the calledAETTag, if any
		int calledAETTag = DicomObject.getElementTag(element.getAttribute("calledAETTag").trim());

		//Get the callingAETTag, if any
		int callingAETTag = DicomObject.getElementTag(element.getAttribute("callingAETTag").trim());

		//Get the DicomSender
		dicomSender = new DicomStorageSCU(url, timeout, forceClose, hostTag, portTag, calledAETTag, callingAETTag);
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
		catch (Exception ex) { 
			logger.debug("Unable to parse "+fileToExport+" as a DicomObject");
			return Status.FAIL; }

		//Got the object; send it.
		Status status = dicomSender.send(dicomObject);
		dicomObject.close();

		//Make an AuditLog entry if required
		makeAuditLogEntry(dicomObject, status, getName(), url);

		return status;
	}

	/**
	 * Stop the pipeline stage.
	 */
	public synchronized void shutdown() {
		if (dicomSender != null) dicomSender.interrupt();
		super.shutdown();
	}

}