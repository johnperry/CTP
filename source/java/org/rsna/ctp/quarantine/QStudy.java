/*---------------------------------------------------------------
*  Copyright 2014 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.quarantine;

import java.io.File;
import java.io.Serializable;
import java.util.HashSet;
import org.rsna.ctp.objects.*;

/**
  * A class to encapsulate an object in a quarantine.
  */
public class QStudy implements Serializable, Comparable<QStudy> {

	public final String patientName;
	public final String patientID;
	public final String studyDate;
	public final String studyUID;
	private HashSet<String> seriesUIDs;

	/**
	 * Construct a QStudy from a File.
	 * @param file the quarantined file.
	 */
	public QStudy(File file) {
		this(FileObject.getInstance(file));
	}

	/**
	 * Construct a QStudy from a FileObject
	 * or one of its subclasses.
	 * @param fileObject the quarantined object.
	 */
	public QStudy(FileObject fileObject) {
		this.patientName = fileObject.getPatientName();
		this.patientID = fileObject.getPatientID();
		this.studyDate = fileObject.getStudyDate();
		this.studyUID = getStudyUID(fileObject);
		this.seriesUIDs = new HashSet<String>();
	}

	/**
	 * Get the studyUID for a FileObject, or "unknown" if
	 * no studyUID is available.
	 * @param fileObject the object.
	 */
	public static String getStudyUID(FileObject fileObject) {
		String studyUID = fileObject.getStudyUID();
		if (studyUID.equals("")) studyUID = "unknown";
		return studyUID;
	}

	/**
	 * Get the studyUID for this study.
	 */
	public String getStudyUID() {
		return studyUID;
	}

	/**
	 * Get whether this study has no series.
	 */
	public boolean isEmpty() {
		return seriesUIDs.isEmpty();
	}

	/**
	 * Get the seriesUIDs for this study.
	 */
	public String[] getSeriesUIDs() {
		String[] uids = new String[seriesUIDs.size()];
		return seriesUIDs.toArray(uids);
	}

	/**
	 * Add a QSeries to this study.
	 */
	public void add(QSeries series) {
		seriesUIDs.add(series.getSeriesUID());
	}

	/**
	 * Remove a series from this study.
	 */
	public void removeSeries(String seriesUID) {
		seriesUIDs.remove(seriesUID);
	}

	/**
	 * Implement the Comparable interface.
	 * <br>
	 * Sort order:
	 * <ol>
	 * <li>PatientID
	 * <li>StudyDate
	 * <li>StudyUID
	 * </ol>
	 */
	public int compareTo(QStudy study) {
		int c;
		if ( (c=this.patientID.compareTo(study.patientID)) != 0) return c;
		if ( (c=this.studyDate.compareTo(study.studyDate)) != 0) return c;
		if ( (c=this.studyUID.compareTo(study.studyUID)) != 0) return c;
		return 0;
	}

}

