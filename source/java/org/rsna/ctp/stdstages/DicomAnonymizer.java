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
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * The DicomAnonymizer pipeline stage class.
 */
public class DicomAnonymizer extends AbstractPipelineStage implements Processor, ScriptableDicom, Scriptable {

	static final Logger logger = Logger.getLogger(DicomAnonymizer.class);

	public File scriptFile = null; //the anonymizer script instructions for the DICOM elements
	public File lookupTableFile = null;
	public IntegerTable intTable = null;
	File dicomScriptFile = null; //the DicomFilter script that determines whether to anonymize the object

	/**
	 * Construct the DicomAnonymizer PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomAnonymizer(Element element) {
		super(element);
		scriptFile = FileUtil.getFile(element.getAttribute("script"), "examples/example-dicom-anonymizer.script");

		String lookupTable = element.getAttribute("lookupTable").trim();
		if (!lookupTable.equals("")) {
			lookupTableFile = new File(lookupTable);
		}
		intTable = new IntegerTable(root);
		String dicomScript = element.getAttribute("dicomScript").trim();
		if (!dicomScript.equals("")) {
			dicomScriptFile = FileUtil.getFile(dicomScript, "examples/example-filter.script");
		}
	}

	//Implement the ScriptableDicom interface
	/**
	 * Get the script file.
	 */
	public File getScriptFile() {
		return scriptFile;
	}

	/**
	 * Get the lookup table file.
	 */
	public File getLookupTableFile() {
		return lookupTableFile;
	}

	/**
	 * Get the integer table object.
	 */
	public IntegerTable getIntegerTable() {
		return intTable;
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
	 * Process a DicomObject, anonymizing it and returning the processed object.
	 * If there is no script file, pass the object unmodified.
	 * If the object is not a DicomObject, pass the object unmodified.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if ( (fileObject instanceof DicomObject) && (scriptFile != null) ) {

			//If there is a dicomScriptFile, use it to determine whether to anonymize
			if ((dicomScriptFile == null) || ((DicomObject)fileObject).matches(dicomScriptFile).getResult()) {

				//Okay, anonymize the object
				File file = fileObject.getFile();
				DAScript dascript = DAScript.getInstance(scriptFile);
				Properties script = dascript.toProperties();
				Properties lookup = LookupTable.getProperties(lookupTableFile);
				AnonymizerStatus status =
							DICOMAnonymizer.anonymize(file, file, script, lookup, intTable, false, false);
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
	 * Stop the pipeline stage.
	 */
	public void shutdown() {
		intTable.close();
		stop = true;
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		return getStatusHTML("");
	}
}