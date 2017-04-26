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
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMPaletteImageConverter;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * The DicomPaletteImageConverter pipeline stage class. This stage converts
 * PALETTE COLOR images to RGB.
 */
public class DicomPaletteImageConverter extends AbstractPipelineStage implements Processor, Scriptable {

	static final Logger logger = Logger.getLogger(DicomPaletteImageConverter.class);
	File dicomScriptFile = null;

	/**
	 * Construct the DicomPaletteImageConverter PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomPaletteImageConverter(Element element) {
		super(element);

		String dicomScript = element.getAttribute("script").trim();
		if (!dicomScript.equals("")) {
			dicomScriptFile = FileUtil.getFile(dicomScript, "examples/example-filter.script");
		}
	}

	/**
	 * Get the script file.
	 * @return the script file used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] { dicomScriptFile };
	}
	
	/**
	 * Process a DicomObject, logging the filename and returning the processed object.
	 * If the object is not a DicomObject, pass the object unmodified.
	 * If the object is not an image, pass the object unmodified.
	 * If the object does not have PhotometricInterpretation PALETTE COLOR, pass the object unmodified.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();
		if (fileObject instanceof DicomObject) {
			DicomObject dob = (DicomObject)fileObject;
			if (dob.isImage()
					&& dob.getPhotometricInterpretation().trim().equals("PALETTE COLOR")
					&& ((dicomScriptFile == null) || dob.matches(FileUtil.getText(dicomScriptFile)))) {

				File file = fileObject.getFile();
				AnonymizerStatus status = DICOMPaletteImageConverter.convert(file, file);
				if (status.isOK()) {
					fileObject = FileObject.getInstance(file);
				}
				else if (status.isSKIP()) {
					logger.info(status.getMessage());
				}
				else if (status.isQUARANTINE()) {
					if (quarantine != null) quarantine.insert(fileObject);
					lastFileOut = null;
					lastTimeOut = System.currentTimeMillis();
					return null;
				}
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
			String qs = "?p="+pipeline.getPipelineIndex()+"&s="+stageIndex+"&f=0";
			if (dicomScriptFile != null) {
				links.addFirst( new SummaryLink("/script"+qs, null, "Edit the Script File", false) );
			}
		}
		return links;
	}
}