/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
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
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMECGProcessor;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.dcm4che.dict.Tags;
import org.w3c.dom.Element;

/**
 * The DicomECGProcessor pipeline stage class. This class adds an image of the waveform
 * to a file that contains a waveform but no image.
 */
public class DicomECGProcessor extends AbstractPipelineStage implements Processor, Scriptable {

	static final Logger logger = Logger.getLogger(DicomECGProcessor.class);

	File dicomScriptFile = null; //the DicomFilter script that determines whether to process the object
	
	boolean synthesizeMissingLeads = true;
	String format = "portrait";

	/**
	 * Construct the DicomWaveformProcessor PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomECGProcessor(Element element) {
		super(element);
		dicomScriptFile = getFilterScriptFile(element.getAttribute("dicomScript"));
		synthesizeMissingLeads = element.getAttribute("synthesizeMissingLeads").equals("yes");
		format = element.getAttribute("format");
	}

	//Implement the Scriptable interface
	/**
	 * Get the script files.
	 * @return the script files used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] { dicomScriptFile, null, null };
	}

	/**
	 * Get the list of links for display on the summary page.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public synchronized LinkedList<SummaryLink> getLinks(User user) {
		LinkedList<SummaryLink> links = super.getLinks(user);
		if (allowsAdminBy(user)) {
			String qs = "?p="+pipeline.getPipelineIndex()+"&s="+stageIndex;
			if (dicomScriptFile != null) {
				links.addFirst( new SummaryLink("/script"+qs+"&f=0", null, "Edit the Stage Filter Script", false) );
			}
		}
		return links;
	}

	/**
	 * Process a DicomObject and return the processed object.
	 * If the object is not a DicomObject, pass the object unmodified.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if (fileObject instanceof DicomObject) {
			DicomObject dob = (DicomObject)fileObject;

			//If there is a dicomScriptFile, use it to determine whether to process
			if ((dicomScriptFile == null) || !dicomScriptFile.exists()
				|| (dicomScriptFile.exists() && dob.matches(dicomScriptFile))) {
				
				//Now see if the object contains a waveform but no image
				if (!dob.isImage() && dob.getDataset().contains(Tags.WaveformSeq)) {
					//Okay, try to process the object
					File file = fileObject.getFile();
					AnonymizerStatus status = DICOMECGProcessor.process(file, file, synthesizeMissingLeads, format);
					if (status.isOK()) {
						fileObject = FileObject.getInstance(file);
					}
					else if (status.isQUARANTINE()) {
						if (quarantine != null) quarantine.insert(fileObject);
						return null;
					}
					else if (status.isSKIP()) ; //keep the input object
				}
			}
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Stop the pipeline stage.
	 */
	public synchronized void shutdown() {
		super.shutdown();
	}
}