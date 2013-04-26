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
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMPlanarConfigurationConverter;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * The DicomPlanarConfigurationConverter pipeline stage class. This stage converts
 * images from PlanarConfiguration 1 to 0.
 * <ul>
 * <li>PlanarConfiguration 1 is RRR...GGG...BBB...
 * <li>PlanarConfiguration 0 is RBGRBGRGB...
 * </ul>
 */
public class DicomPlanarConfigurationConverter extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(DicomPlanarConfigurationConverter.class);

	/**
	 * Construct the DicomPlanarConfigurationConverter PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomPlanarConfigurationConverter(Element element) {
		super(element);
	}

	/**
	 * Process a DicomObject, logging the filename and returning the processed object.
	 * If the object is not a DicomObject, pass the object unmodified.
	 * If the object is not an image, pass the object unmodified.
	 * If the object does not have PlanarConfiguration 1, pass the object unmodified.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();
		if (fileObject instanceof DicomObject) {
			DicomObject dob = (DicomObject)fileObject;
			if (dob.isImage()
					&& (dob.getPlanarConfiguration() == 1)
						&& (dob.getSamplesPerPixel() == 3)) {
				File file = fileObject.getFile();
				AnonymizerStatus status = DICOMPlanarConfigurationConverter.convert(file, file);
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

}