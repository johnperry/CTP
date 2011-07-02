/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.HashSet;
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
import org.rsna.util.JdbmUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An indexing stage for objects which have been processed, providing a web interface.
 */
public class ObjectTracker extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(ObjectTracker.class);

    RecordManager recman = null;
    public HTree dateIndex = null;
    public HTree patientIndex = null;
    public HTree studyIndex = null;
    public HTree seriesIndex = null;

	/**
	 * Construct the ObjectTracker PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public ObjectTracker(Element element) {
		super(element);
		if (root != null) {
			File indexFile = new File(root, "__tracker");
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
				logger.warn("Unable to commit and close the database.");
			}
		}
		//Set stop so the isDown method will return the correct value.
		stop = true;
	}

	/**
	 * Update the indexes for this object.
	 * This stage tracks the following data:
	 * <ul>
	 * <li>Processing date (today)
	 * <li>PatientID
	 * <li>StudyInstanceUID
	 * <li>SeriesInstanceUID
	 * <li>SOPInstanceUID
	 * </ul>
	 * It creates table entries for the values in this object.
	 * IDs which are not unique may be overwritten by subsequent
	 * objects (e.g. duplicates). Thus, the tables contain only
	 * records of unique objects that have been processed.
	 * @param fileObject the object to process.
	 * @return the same FileObject if the result is true; otherwise null.
	 */
	public FileObject process(FileObject fileObject) {

		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		try {
			if (fileObject instanceof DicomObject) {
				DicomObject dob = (DicomObject)fileObject;

				String date = StringUtil.getDate("");
				String patientID = dob.getPatientID();
				String studyInstanceUID = dob.getStudyInstanceUID();
				String seriesInstanceUID = dob.getSeriesInstanceUID();
				String sopInstanceUID = dob.getSOPInstanceUID();

				index(dateIndex, date, patientID);
				index(patientIndex, patientID, studyInstanceUID);
				index(studyIndex, studyInstanceUID, seriesInstanceUID);
				index(seriesIndex, seriesInstanceUID, sopInstanceUID);

				recman.commit();
			}
		}
		catch (Exception skip) {
			logger.debug("Unable to process "+fileObject.getFile());
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	//Update the index for a key/value pair.
	//Note that the values in all indexes are HashSets,
	//so the update consists of adding the value to
	//the HashSet.
	private void index(HTree index, String key, String value) {
		if ((key == null) || (value == null)) return;
		key = key.trim();
		value = value.trim();
		if (key.equals("") || value.equals("")) return;
		try {
			HashSet<String> values = (HashSet<String>)index.get(key);
			if (values == null) {
				values = new HashSet<String>();
			}
			values.add(value);
			index.put(key, values);
		}
		catch (Exception ignore) {
			logger.debug("Unable to update the index for:");
			logger.debug("   key   = "+key);
			logger.debug("   value = "+value);
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
		recman		= JdbmUtil.getRecordManager( indexPath );
		dateIndex	= JdbmUtil.getHTree(recman, "dateIndex");
		patientIndex= JdbmUtil.getHTree(recman, "patientIndex");
		studyIndex	= JdbmUtil.getHTree(recman, "studyIndex");
		seriesIndex	= JdbmUtil.getHTree(recman, "seriesIndex");
	}

}