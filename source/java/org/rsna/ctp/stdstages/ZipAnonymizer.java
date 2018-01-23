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
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.zip.ZIPAnonymizer;
import org.rsna.ctp.stdstages.SupportsLookup;
import org.rsna.util.FileUtil;
import org.rsna.server.User;
import org.w3c.dom.Element;

/**
 * The XmlAnonymizer pipeline stage class.
 */
public class ZipAnonymizer extends AbstractPipelineStage implements Processor, Scriptable, SupportsLookup {

	static final Logger logger = Logger.getLogger(ZipAnonymizer.class);

	public File scriptFile = null;
	public File lookupTableFile = null;

	/**
	 * Construct the ZipAnonymizer PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public ZipAnonymizer(Element element) {
		super(element);
		scriptFile = getFilterScriptFile(element.getAttribute("script"));
		String lookupTable = element.getAttribute("lookupTable").trim();
		if (!lookupTable.equals("")) {
			lookupTableFile = new File(lookupTable);
		}
	}

	/**
	 * Process a ZipObject, logging the filename and returning the processed object.
	 * If there is no script file, pass the object unmodified.
	 * If the object is not a ZipObject, pass the object unmodified.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if ( (fileObject instanceof ZipObject) && (scriptFile != null) ) {
			File file = fileObject.getFile();
			Properties lookup = LookupTable.getProperties(lookupTableFile);
			AnonymizerStatus status = ZIPAnonymizer.anonymize(file,file,scriptFile,lookup);
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

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Get the script file.
	 * @return the script file used by this stage.
	 */
	public File getScriptFile() {
		return scriptFile;
	}

	/**
	 * Get the script file.
	 * @return the script file used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] { scriptFile };
	}

	/**
	 * Get the lookup table file.
	 */
	public File getLookupTableFile() {
		return lookupTableFile;
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
			if (scriptFile != null) {
				links.addFirst( new SummaryLink("/script"+qs+"&f=0", null, "Edit the Anonymizer Script", false) );
			}
		}
		return links;
	}

}