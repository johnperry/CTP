/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import org.apache.log4j.Logger;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.rsna.ctp.stdstages.dicom.DicomStorageSCU;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.pipeline.Status;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An ExportService that exports files via the DICOM protocol.
 */
public class DicomExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(DicomExportService.class);

	DicomStorageSCU dicomSender = null;

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
		String url = element.getAttribute("url");

		//See if we are to force a close of the
		//association on every transfer
		boolean forceClose = element.getAttribute("forceClose").equals("yes");

		//Get the calledAETTag, if any
		int calledAETTag = StringUtil.getHexInt(element.getAttribute("calledAETTag"));

		//Get the callingAETTag, if any
		int callingAETTag = StringUtil.getHexInt(element.getAttribute("callingAETTag"));

		//Get the DicomSender
		dicomSender = new DicomStorageSCU(url, forceClose, calledAETTag, callingAETTag);

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
		return status;
	}

}