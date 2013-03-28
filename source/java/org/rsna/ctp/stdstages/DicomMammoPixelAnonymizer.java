/*---------------------------------------------------------------
*  Copyright 2013 by the Radiological Society of North America
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
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMMammoPixelAnonymizer;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * The DicomMammoPixelAnonymizer pipeline stage class. This stage attempts to find
 * PHI burned into mammography images and blanks it out. This stage only works
 * on DICOM mammography images that have PHI burned into a rectangle on either
 * left or right side.
 */
public class DicomMammoPixelAnonymizer extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(DicomMammoPixelAnonymizer.class);

	/**
	 * Construct the DicomMammoPixelAnonymizer PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomMammoPixelAnonymizer(Element element) {
		super(element);
	}

	/**
	 * Process a DicomObject, logging the filename and returning the processed object.
	 * If the object is not a DicomObject, pass the object unmodified.
	 * If the object is not an image, pass the object unmodified.
	 * If the object is not a mammorgaphy image, pass the object unmodified.
	 * Otherwise, find the block of pixels containing the PHI and blank it out.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();
		if (fileObject instanceof DicomObject) {
			DicomObject dob = (DicomObject)fileObject;
			String modality = dob.getModality().toLowerCase();
			boolean notMammo = modality.equals("CT") || modality.equals("MR") || modality.equals("US");
			if ( !notMammo && dob.isImage()
					&& !dob.isEncapsulated()
						&& (dob.getRows() > 1500)
							&& (dob.getColumns() > 1500) ) {

				logger.debug("Processing "+dob.getSOPInstanceUID());

				File file = dob.getFile();
				AnonymizerStatus status = DICOMMammoPixelAnonymizer.anonymize(file, file);
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
				else if (status.isSKIP()) ; //keep the input object
			}
		}
		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

}