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
  * A class to encapsulate a file in a quarantine.
  */
public class QFile implements Serializable, Comparable<QFile> {

	public final String instanceNumber;
	public final String filename;
	public final String type;
	public final String seriesUID;
	public final String studyUID;

	/**
	 * Construct a QFile from a File.
	 * @param file the quarantined file.
	 */
	public QFile(File file) {
		this(FileObject.getInstance(file));
	}

	/**
	 * Construct a QFile from a FileObject
	 * or one of its subclasses.
	 * @param fileObject the quarantined object.
	 */
	public QFile(FileObject fileObject) {
		this.instanceNumber = getInstanceNumber(fileObject);
		this.seriesUID = QSeries.getSeriesUID(fileObject);
		this.studyUID = QStudy.getStudyUID(fileObject);
		this.filename = fileObject.getFile().getName();
		this.type = fileObject.getType();
	}


	/**
	 * Get the instanceNumber for a FileObject, or "unknown" if
	 * no instanceNumber is available.
	 * @param fileObject the object.
	 */
	public static String getInstanceNumber(FileObject fileObject) {
		String instanceNumber = "unknown";
		if (fileObject instanceof DicomObject) {
			instanceNumber = ((DicomObject)fileObject).getInstanceNumber();
		}
		return instanceNumber;
	}

	/**
	 * Get the filename for this file.
	 */
	public String getName() {
		return filename;
	}

	/**
	 * Get the studyUID for this file.
	 */
	public String getStudyUID() {
		return studyUID;
	}

	/**
	 * Get the seriesUID for this file.
	 */
	public String getSeriesUID() {
		return seriesUID;
	}

	/**
	 * Implement the Comparable interface.
	 * <br>
	 * Sort order:
	 * <ol>
	 * <li>SeriesNumber
	 * </ol>
	 */
	public int compareTo(QFile file) {
		int c;
		if ( (c=this.type.compareTo(file.type)) != 0) return c;
		if ( (c=this.instanceNumber.compareTo(file.instanceNumber)) != 0) return c;
		return 0;
	}

}

