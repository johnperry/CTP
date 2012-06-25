/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMPixelAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.dicom.PixelScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.Regions;
import org.rsna.ctp.stdstages.anonymizer.dicom.Signature;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * The DicomPixelAnonymizer pipeline stage class.
 */
public class DicomPixelAnonymizer extends AbstractPipelineStage implements Processor, Scriptable {

	static final Logger logger = Logger.getLogger(DicomPixelAnonymizer.class);

	public File scriptFile = null;
	PixelScript script = null;
	long lastModified = 0;
	boolean log = false;

	/**
	 * Construct the DicomPixelAnonymizer PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomPixelAnonymizer(Element element) {
		super(element);
		log = element.getAttribute("log").equals("yes");
		scriptFile = FileUtil.getFile(element.getAttribute("script"), "examples/example-dicom-pixel-anonymizer.script");
	}

	/**
	 * Process a DicomObject, logging the filename and returning the processed object.
	 * If there is no script file, pass the object unmodified.
	 * If the object is not a DicomObject, pass the object unmodified.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();
		if ( (fileObject instanceof DicomObject)
				&& (scriptFile != null)
					&& ((DicomObject)fileObject).isImage() ) {
			File file = fileObject.getFile();
			getScript();
			if (script != null) {
				Signature signature = script.getMatchingSignature((DicomObject)fileObject);
				log(fileObject, signature);
				if (signature != null) {
					Regions regions = signature.regions;
					if ((regions != null) && (regions.size() > 0)) {
						AnonymizerStatus status = DICOMPixelAnonymizer.anonymize(file, file, regions);
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
			}
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	private void log(FileObject fileObject, Signature signature) {
		if (log) {
			if (signature != null)
				logger.info(name+": DicomObject "+fileObject.getUID()+" matched:\n"+signature.script);
			else
				logger.info(name+": DicomObject "+fileObject.getUID()+" did not match any signature.");
		}
	}

	/**
	 * Get the script file.
	 * @return the script file used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] { scriptFile };
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		return getStatusHTML("");
	}

	//Load the script if necessary
	private void getScript() {
		if ((scriptFile != null) && scriptFile.exists()) {
			if (scriptFile.lastModified() > lastModified) {
				script = new PixelScript(scriptFile);
			}
		}
		else script = null;
	}
}