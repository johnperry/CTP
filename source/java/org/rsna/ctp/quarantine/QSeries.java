/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.quarantine;

import java.io.File;
import java.io.Serializable;
import java.util.HashSet;
import org.rsna.ctp.objects.*;

/**
  * A class to encapsulate a series in a quarantine.
  */
public class QSeries implements Serializable, Comparable<QSeries> {

	public final String seriesNumber;
	public final String seriesUID;
	public final String studyUID;
	private HashSet<String> filenames;

	/**
	 * Construct a QSeries from a File.
	 * @param file the quarantined file.
	 */
	public QSeries(File file) {
		this(FileObject.getInstance(file));
	}

	/**
	 * Construct a QSeries from a FileObject
	 * or one of its subclasses.
	 * @param fileObject the quarantined object.
	 */
	public QSeries(FileObject fileObject) {
		this.seriesNumber = getSeriesNumber(fileObject);
		this.seriesUID = getSeriesUID(fileObject);
		this.studyUID = QStudy.getStudyUID(fileObject);
		this.filenames = new HashSet<String>();
	}

	/**
	 * Get the seriesUID for a FileObject, or "unknown" if
	 * no seriesUID is available.
	 * @param fileObject the object.
	 * @return the seriesUID, or "unknown" if the series UID cannot be obtained
	 */
	public static String getSeriesUID(FileObject fileObject) {
		String seriesUID = fileObject.getSeriesUID();
		if ((seriesUID == null) || seriesUID.equals("")) seriesUID = "unknown";
		return seriesUID;
	}

	/**
	 * Get the seriesNumber for a FileObject, or "unknown" if
	 * no seriesNumber is available.
	 * @param fileObject the object.
	 * @return the seriesNumber, or "unknown" if the series number cannot be obtained
	 */
	public static String getSeriesNumber(FileObject fileObject) {
		String seriesNumber = "unknown";
		if (fileObject instanceof DicomObject) {
			seriesNumber = ((DicomObject)fileObject).getSeriesNumber();
		}
		return seriesNumber;
	}

	/**
	 * Get the studyUID for this series.
	 * @return the study UID
	 */
	public String getStudyUID() {
		return studyUID;
	}

	/**
	 * Get the seriesUID for this series.
	 * @return the series UID
	 */
	public String getSeriesUID() {
		return seriesUID;
	}

	/**
	 * Get whether this series has no files.
	 * @return true if the series has no files; false otherwise.
	 */
	public boolean isEmpty() {
		return filenames.isEmpty();
	}

	/**
	 * Get the number of files for this series.
	 * @return the number of files stored for this series.
	 */
	public int getNumberOfFiles() {
		return filenames.size();
	}

	/**
	 * Get the filenames for this series.
	 * @return the array of filenames stored for this series.
	 */
	public String[] getFilenames() {
		String[] names = new String[filenames.size()];
		return filenames.toArray(names);
	}

	/**
	 * Add a file to this series.
	 * @param file the file to be added to this series
	 */
	public void add(QFile file) {
		filenames.add(file.getName());
	}

	/**
	 * Remove a file from this series.
	 * @param file the file to be removed from this series
	 */
	public void remove(QFile file) {
		filenames.remove(file.getName());
	}

	/**
	 * Implement the Comparable interface.
	 */
	public int compareTo(QSeries series) {
		int c;
		if ( (c=this.seriesNumber.compareTo(series.seriesNumber)) != 0) return c;
		return 0;
	}

}

