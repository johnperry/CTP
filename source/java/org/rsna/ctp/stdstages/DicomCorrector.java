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
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMCorrector;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * The DicomCorrector pipeline stage class.
 */
public class DicomCorrector extends AbstractPipelineStage implements Processor, Scriptable {

	static final Logger logger = Logger.getLogger(DicomCorrector.class);

	File dicomScriptFile = null; //the DicomFilter script that determines whether to correct the object
	boolean quarantineUncorrectedMismatches = false;
	boolean logUncorrectedMismatches = false;
	boolean fixPrivateElements = false;

	/**
	 * Construct the DicomCorrector PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomCorrector(Element element) {
		super(element);
		dicomScriptFile = getFilterScriptFile(element.getAttribute("dicomScript"));
		fixPrivateElements = element.getAttribute("fixPrivateElements").trim().toLowerCase().equals("yes");
		quarantineUncorrectedMismatches = element.getAttribute("quarantineUncorrectedMismatches").trim().toLowerCase().equals("yes");
		logUncorrectedMismatches = element.getAttribute("logUncorrectedMismatches").trim().toLowerCase().equals("yes");
	}

	//Implement the Scriptable interface
	/**
	 * Get the script files.
	 * @return the script files used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] { dicomScriptFile };
	}

	/**
	 * Process a DicomObject, correcting what can be corrected
	 * and returning the processed object.
	 * If the object is not a DicomObject, pass the object unmodified.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if (fileObject instanceof DicomObject) {

			//If there is a dicomScriptFile, use it to determine whether to anonymize
			if (((DicomObject)fileObject).matches(dicomScriptFile)) {

				//Okay, correct the object
				File file = fileObject.getFile();
				AnonymizerStatus status =
							DICOMCorrector.correct(file, file, 
												   fixPrivateElements,
												   quarantineUncorrectedMismatches, 
												   logUncorrectedMismatches);
				if (status.isOK()) {
					fileObject = FileObject.getInstance(file);
				}
				else if (status.isQUARANTINE()) {
					if (quarantine != null) quarantine.insert(fileObject);
					lastFileOut = null;
					lastTimeOut = System.currentTimeMillis();
					return null;
				}
				else if (status.isSKIP()) ; //keep the input object
			}
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
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
			if (dicomScriptFile != null) {
				links.addFirst( new SummaryLink("/script"+qs+"&f=0", null, "Edit the Stage Filter Script", false) );
			}
		}
		return links;
	}

}