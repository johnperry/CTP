/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMDecompressor;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * The DicomAnonymizer pipeline stage class.
 */
public class DicomDecompressor extends AbstractPipelineStage implements Processor, Scriptable  {

	static final Logger logger = Logger.getLogger(DicomDecompressor.class);

	public File scriptFile = null;

	/**
	 * Construct the DicomDecompressor PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomDecompressor(Element element) {
		super(element);
		scriptFile = FileUtil.getFile(element.getAttribute("script"), "examples/example-filter.script");
	}

	/**
	 * Process a DicomObject, decompressing encapsulated pixel data images
	 * and returning the processed object with EVRLE transfer syntax.
	 * If there is no script file, process all images. If there is a
	 * script file, process only those images which match the script.
	 * If the object is not a DicomObject, pass the object unmodified.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if (fileObject instanceof DicomObject) {
			DicomObject dob = (DicomObject)fileObject;
			if (dob.isImage() && dob.getDcmDecodeParam().encapsulated) {
				if ((scriptFile == null) || dob.matches(FileUtil.getText(scriptFile)).getResult()) {
					File file = dob.getFile();
					AnonymizerStatus status = DICOMDecompressor.decompress(file,file);
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
	 * Get the script file.
	 * @return the script file used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] {scriptFile};
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		return getStatusHTML("");
	}

}