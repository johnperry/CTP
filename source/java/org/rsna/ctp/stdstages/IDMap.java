/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Properties;
import jdbm.RecordManager;
import jdbm.htree.HTree;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.rsna.ctp.stdstages.XmlAnonymizer;
import org.rsna.ctp.stdstages.ZipAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizerContext;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.JdbmUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * An indexing stage for PHI and replacement values, providing a web interface.
 */
public class IDMap extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(IDMap.class);

    RecordManager recman = null;
    public HTree uidIndex = null;
    public HTree ptIDIndex = null;
    public HTree anIndex = null;
    public HTree uidInverseIndex = null;
    public HTree ptIDInverseIndex = null;
    public HTree anInverseIndex = null;

    boolean scripts = false;
    File dcmScript = null;
    File dcmLUT = null;
    IntegerTable dcmIntTab = null;
    File xmlScript = null;
    File zipScript = null;

	static final int defaultInterval = 600000; //10 minutes
	static final int minInterval = 60000;	   //1 minute
	int interval = defaultInterval;

	/**
	 * Construct the IDMap PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public IDMap(Element element) {
		super(element);
		if (root != null) {
			File indexFile = new File(root, "__map");
			getIndex(indexFile.getPath());
		}
		else logger.error(name+": No root directory was specified.");
	}

	/**
	 * Stop the stage.
	 */
	public void shutdown() {
		//Commit and close the database
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
			}
			catch (Exception ex) {
				logger.debug("Unable to commit and close the database");
			}
		}
		//Set stop so the isDown method will return the correct value.
		stop = true;
	}

	/**
	 * Update the indexes for the IDs contained in this object.
	 * This stage finds the following IDs:
	 * <ul>
	 * <li>SOPInstanceUID
	 * <li>StudyInstanceUID
	 * <li>SeriesInstanceUID
	 * <li>PatientID
	 * <li>AccessionNumber
	 * </ul>
	 * It creates table entries for the values in this object and the
	 * values which will be produced by the next anonymizer for the
	 * current object type in the pipeline. Each ID has two tables,
	 * one indexed on the current value and one indexed on the
	 * anonymized value, allowing for mapping in both directions.
	 * IDs which are not unique may be overwritten by subsequent
	 * objects.
	 * @param fileObject the object to process.
	 * @return the same FileObject if the result is true; otherwise null.
	 */
	public FileObject process(FileObject fileObject) {
		String cmd;

		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		//Find the scripts if we haven't found them already.
		//Important note: this cannot be done in the constructor because
		//at the time the constructor is called, the subsequent stages in
		//the pipeline have not yet been instantiated.
		getScripts();

		//Now process the object.
		try {
			if (fileObject instanceof DicomObject) {
				DicomObject dob = (DicomObject)fileObject;
				if (dcmScript != null) {
					DAScript daScript = DAScript.getInstance(dcmScript);
					Properties cmds = daScript.toProperties();
					Properties lkup = LookupTable.getProperties(dcmLUT);
					DICOMAnonymizerContext context =
								new DICOMAnonymizerContext(cmds, lkup, dcmIntTab, dob.getDataset(), null);

					int sopiUIDtag		= 0x00080018;
					String sopiUID		= dob.getSOPInstanceUID();
					String sopiUIDscript= context.getScriptFor(sopiUIDtag);
					String sopiUIDrepl	= DICOMAnonymizer.makeReplacement(sopiUIDscript, context, sopiUIDtag);

					int siUIDtag		= 0x0020000d;
					String siUID		= dob.getStudyInstanceUID();
					String siUIDscript	= context.getScriptFor(siUIDtag);
					String siUIDrepl	= DICOMAnonymizer.makeReplacement(siUIDscript, context, siUIDtag);

					int ptIDtag			= 0x00100020;
					String ptID			= dob.getPatientID();
					String ptIDscript	= context.getScriptFor(ptIDtag);
					String ptIDrepl		= DICOMAnonymizer.makeReplacement(ptIDscript, context, ptIDtag);

					int seriesUIDtag	= 0x0020000e;
					String seriesUID	= dob.getSeriesInstanceUID();
					String seriesUIDscript= context.getScriptFor(seriesUIDtag);
					String seriesUIDrepl= DICOMAnonymizer.makeReplacement(seriesUIDscript, context, seriesUIDtag);

					int accNumbertag	= 0x0080050;
					String accNumber	= dob.getAccessionNumber();
					String accNumberscript= context.getScriptFor(accNumbertag);
					String accNumberrepl= DICOMAnonymizer.makeReplacement(accNumberscript, context, accNumbertag);

					index(sopiUID, sopiUIDrepl, uidIndex, uidInverseIndex);
					index(siUID, siUIDrepl, uidIndex, uidInverseIndex);
					index(ptID, ptIDrepl, ptIDIndex, ptIDInverseIndex);
					index(seriesUID, seriesUIDrepl, uidIndex, uidInverseIndex);
					index(accNumber, accNumberrepl, anIndex, anInverseIndex);

					//Now commit everything
					recman.commit();
				}
			}
		}
		catch (Exception skip) {
			if (skip.getMessage().contains("quarantine")) {
				logger.debug("Skip the object because the anonymizer will quarantine it.");
			}
			else logger.debug("Unable to process the object "+fileObject.getFile(), skip);
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	//Update forward and inverse indexes for a phi/replacement pair
	private void index(String phi, String rep, HTree forward, HTree inverse) {
		//System.out.println("index call: phi:\""+phi+"\"; rep:\""+rep+"\"");
		if ((phi == null) || (rep == null)) return;
		phi = phi.trim();
		rep = rep.trim();
		if (phi.equals("") || rep.equals("") || rep.equals("@remove()")) return;
		if (rep.equals("@keep()")) rep = phi;
		try {
			forward.put(phi, rep);
			inverse.put(rep, phi);
		}
		catch (Exception ignore) {
			logger.debug("Unable to update the indexes for:");
			logger.debug("   phi = "+phi);
			logger.debug("   rep = "+rep);
		}
	}

	//Find the next anonymizer stage of each type and get its script.
	private void getScripts() {
		if (!scripts) {
			Pipeline pipe = Configuration.getInstance().getPipeline(this);
			if (pipe != null) {
				List<PipelineStage> stageList = pipe.getStages();
				int thisStage = stageList.indexOf(this);
				if (thisStage != -1) {
					PipelineStage[] stages = new PipelineStage[stageList.size()];
					stages = stageList.toArray(stages);
					//Find the next DicomAnonymizer
					for (int i=thisStage+1; i<stages.length; i++) {
						if (stages[i] instanceof DicomAnonymizer) {
							DicomAnonymizer da = ((DicomAnonymizer)stages[i]);
							dcmScript = da.scriptFile;
							dcmLUT = da.lookupTableFile;
							dcmIntTab = da.intTable;
							break;
						}
					}
					//Find the next XmlAnonymizer
					for (int i=thisStage+1; i<stages.length; i++) {
						if (stages[i] instanceof XmlAnonymizer) {
							xmlScript = ((XmlAnonymizer)stages[i]).scriptFile;
							break;
						}
					}
					//Find the next ZipAnonymizer
					for (int i=thisStage+1; i<stages.length; i++) {
						if (stages[i] instanceof ZipAnonymizer) {
							zipScript = ((ZipAnonymizer)stages[i]).scriptFile;
							break;
						}
					}
				}
			}
			scripts = true;
		}
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		return getStatusHTML("");
	}

	//Load the index HTrees
	private void getIndex(String indexPath) {
		try {
			recman			= JdbmUtil.getRecordManager( indexPath );
			uidIndex		= getHTree(recman, "uidIndex", "Original UID", "Trial UID");
			ptIDIndex		= getHTree(recman, "ptIDIndex", "Original PatientID", "Trial PatientID");
			anIndex			= getHTree(recman, "anIndex", "Original AccessionNumber", "Trial AccessionNumber");
			uidInverseIndex	= getHTree(recman, "uidInverseIndex", "Trial UID", "Original UID");
			ptIDInverseIndex= getHTree(recman, "ptIDInverseIndex", "Trial PatientID", "Original PatientID");
			anInverseIndex	= getHTree(recman, "anInverseIndex", "Trial AccessionNumber", "Original AccessionNumber");
		}
		catch (Exception ex) {
			recman = null;
			logger.warn("Unable to load the indexes.");
		}
	}

	//Get a named HTree, or create it if it doesn't exist.
	private HTree getHTree(RecordManager recman, String name, String keyTitle, String valueTitle) throws Exception {
		HTree index = JdbmUtil.getHTree( recman, name );
		if ((keyTitle != null) && (valueTitle != null)) {
			index.put("__keyTitle", keyTitle);
			index.put("__valueTitle", valueTitle);
		}
		recman.commit();
		return index;
	}

}