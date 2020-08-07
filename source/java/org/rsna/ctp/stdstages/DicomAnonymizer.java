/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedList;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * The DicomAnonymizer pipeline stage class.
 */
public class DicomAnonymizer extends AbstractPipelineStage implements Processor, Scriptable, ScriptableDicom, SupportsLookup {

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

		//Note: The script is obtained in the start method so that
		//different default scripts can be used for teaching files
		//and clinical trials.

		String lookupTable = element.getAttribute("lookupTable").trim();
		if (!lookupTable.equals("")) {
			lookupTableFile = new File(lookupTable);
		}

		try { intTable = new IntegerTable(root); }
		catch (Exception ex) { logger.warn(name+": "+ex.getMessage()); }

		dicomScriptFile = getFilterScriptFile(element.getAttribute("dicomScript"));
	}

	/**
	 * Start the pipeline stage. This method obtains the script file. This must be done
	 * in the start method because the Configuration object is not available in the constructor,
	 * and the Configuration object is used to distinguish teaching file installations from
	 * clinical trial installations so different default scripts can be used.
	 */
	public synchronized void start() {
		String defaultScript = "examples/example-ctp-dicom-anonymizer.script";
		if (Configuration.getInstance().isMIRC()) {
			defaultScript = "examples/example-tfs-dicom-anonymizer.script";
		}
		String script = element.getAttribute("script").trim();
		scriptFile = FileUtil.getFile(script, defaultScript);
	}

	//Implement the ScriptableDicom interface
	/**
	 * Get the script file.
	 */
	public File getDAScriptFile() {
		return scriptFile;
	}

	//Implement the SupportsLookup interface
	/**
	 * Get the lookup table file.
	 */
	public File getLookupTableFile() {
		return lookupTableFile;
	}

	/**
	 * Get the integer table object.
	 * @return the integer table
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
	 * Get the list of links for display on the summary page.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public synchronized LinkedList<SummaryLink> getLinks(User user) {
		LinkedList<SummaryLink> links = super.getLinks(user);
		if (allowsAdminBy(user)) {
			String qs = "?p="+pipeline.getPipelineIndex()+"&s="+stageIndex;
			if (lookupTableFile != null) {
				links.addFirst( new SummaryLink("/lookup"+qs, null, "Edit the Lookup Table", false) );
			}
			links.addFirst( new SummaryLink("/daconfig"+qs, null, "Edit the Anonymizer Script", false) );
			if (dicomScriptFile != null) {
				links.addFirst( new SummaryLink("/script"+qs+"&f=0", null, "Edit the Stage Filter Script", false) );
			}
		}
		return links;
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
			if (((DicomObject)fileObject).matches(dicomScriptFile)) {

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
	public synchronized void shutdown() {
		intTable.close();
		super.shutdown();
	}
}