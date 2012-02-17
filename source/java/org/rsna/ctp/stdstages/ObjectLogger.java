/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A Processor stage that logs objects as they flow by.
 */
public class ObjectLogger extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(ObjectLogger.class);

	int count = 0;
	int interval = 1;
	boolean verbose = false;
	final String margin = "\n                              ";

	/**
	 * Construct the ObjectLogger PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public ObjectLogger(Element element) {
		super(element);
		verbose = element.getAttribute("verbose").trim().equals("yes");
		interval = Math.max( 1, StringUtil.getInt(element.getAttribute("interval"), 1) );
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

		if ((count % interval) == 0) {
			DicomObject dob = null;
			if (fileObject instanceof DicomObject) dob = (DicomObject)fileObject;

			String verboseString = "";
			if (verbose) {
				verboseString =
					margin + "PatientID        = " + fileObject.getPatientID() +
					margin + "StudyInstanceUID = " + fileObject.getStudyInstanceUID() +
					margin + "SOPInstanceUID   = " + fileObject.getSOPInstanceUID() +
					( (dob == null) ? ""
									: margin + "InstanceNumber   = " + dob.getInstanceNumber() ) +
					margin + "Digest           = " + fileObject.getDigest();
			}

			logger.info(
				name
				+ margin + fileObject.getClassName()
				+ ": (" + (count+1) + ") "
				+ fileObject.getFile().getName()
				+ " @ " + StringUtil.getTime(":")
				+ verboseString
			);
		}
		count++;

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		String stageUniqueStatus =
			"<tr><td width=\"20%\">Files processed:</td>"
			+ "<td>" + count + "</td></tr>";
		return super.getStatusHTML(stageUniqueStatus);
	}

}