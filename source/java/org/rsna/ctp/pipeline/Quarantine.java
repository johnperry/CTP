/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import jdbm.htree.HTree;
import jdbm.helper.FastIterator;
import jdbm.RecordManager;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.*;
import org.rsna.ctp.quarantine.*;
import org.rsna.util.FileUtil;
import org.rsna.util.JdbmUtil;
import org.rsna.util.XmlUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 * A class representing a quarantine directory and providing
 * methods for inserting FileObjects.
 */
public class Quarantine {

	static final Logger logger = Logger.getLogger(Quarantine.class);

	private static Hashtable<File,Quarantine> quarantines = new Hashtable<File,Quarantine>();

	File directory = null;
	File indexDir = null;

	private RecordManager recman;
	private static final String databaseName = "QuarantineIndex";
	private static final String versionTableName = "VERSION";
	private static final String studyTableName = "STUDY";
	private static final String seriesTableName = "SERIES";
	private static final String instanceTableName = "INSTANCE";
	private static final String versionKey = "version";
	private static final String versionID = "1";

	private HTree versionTable = null;
	private HTree studyTable = null; 	//Map from StudyInstanceUID to QStudy
	private HTree seriesTable = null; 	//Map from SeriesInstanceUID to QSeries
	private HTree instanceTable = null; //Map from filename to QInstance

	/**
	 * Get the Quarantine object for a directory.
	 * @param directoryPath the path to the base directory of the quarantine.
	 * @return the Quarantine object for the directory, or null if the
	 * Quarantine object cannot be created.
	 */
	public static synchronized Quarantine getInstance(String directoryPath) {
		Quarantine q = null;
		directoryPath = directoryPath.trim();
		if (!directoryPath.equals("")) {
			File dir = new File(directoryPath);
			try {
				dir = dir.getCanonicalFile();
				q = quarantines.get(dir);
				if (q == null) {
					q = new Quarantine(dir);
					quarantines.put(dir, q);
				}
			}
			catch (Exception ex) { }
		}
		return q;
	}

	/**
	 * Create a Quarantine object for a directory, creating
	 * the directory and the index if necessary.
	 */
	protected Quarantine(File directory) throws Exception {
		this.directory = directory;
		indexDir = new File(directory, "..index");
		indexDir.mkdirs();
		try {
			openIndex();
			String v = (String)versionTable.get(versionKey);
			if ((v == null) || !v.equals(versionID)) {
				logger.info("Rebuilding quarantine index: "+directory);
				closeIndex();
				deleteIndex();
				openIndex();
				loadIndex();
			}
		}
		catch (Exception ex) {
			logger.warn("Unable to create the quarantine index for "+directory);
			throw ex;
		}
	}

	/**
	 * Close the quarantine.
	 */
	public synchronized void close() {
		closeIndex();
	}

	/**
	 * Open the index.
	 */
	 private synchronized void openIndex() throws Exception {
		File dbFile = new File(indexDir, databaseName);
		recman = JdbmUtil.getRecordManager(dbFile.getCanonicalPath());
		versionTable = JdbmUtil.getHTree(recman, versionTableName);
		studyTable = JdbmUtil.getHTree(recman, studyTableName);
		seriesTable = JdbmUtil.getHTree(recman, seriesTableName);
		instanceTable = JdbmUtil.getHTree(recman, instanceTableName);
	}

	// Commit the index.
	private synchronized void commitIndex() {
		if (recman != null) {
			try { recman.commit(); }
			catch (Exception ignore) { }
		}
	}

	// Close the index.
	private synchronized void closeIndex() {
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
				recman = null;
			}
			catch (Exception ignore) { }
		}
	}

	// Load the index for this Quarantine by parsing the files in the directory.
	private synchronized void loadIndex() {
		try {
			File[] files = directory.listFiles();
			for (File file : files) {
				if (file.isFile()) {
					index(file);
				}
			}
			versionTable.put(versionKey, versionID);
			commitIndex();
		}
		catch (Exception ex) { }
	}

	//Delete the index.
	private synchronized void deleteIndex() {
		File dbFile = new File(indexDir, databaseName+".db");
		File lgFile = new File(indexDir, databaseName+".lg");
		dbFile.delete();
		lgFile.delete();
	}

	/**
	 * Get the number of files in the quarantine.
	 * @return the number of files in the instanceTable.
	 */
	public synchronized int getSize() {
		Object object;
		int count = 0;
		try {
			FastIterator fit = instanceTable.values();
			while ( (object=fit.next()) != null ) count++;
		}
		catch (Exception ignore) { }
		return count;
	}

	//Add a file to the quarantine index
	private synchronized void index(File file) {
		index(FileObject.getInstance(file));
	}

	//Add a FileObject to the quarantine index
	private synchronized boolean index(FileObject fileObject) {
		File file = fileObject.getFile();
		if (file.isFile() && file.getParentFile().equals(directory)) {
			try {
				String studyUID = QStudy.getStudyUID(fileObject);
				QStudy qstudy = (QStudy)studyTable.get(studyUID);
				if (qstudy == null) {
					qstudy = new QStudy(fileObject);
				}
				String seriesUID = QSeries.getSeriesUID(fileObject);
				QSeries qseries = (QSeries)seriesTable.get(seriesUID);
				if (qseries == null) {
					qseries = new QSeries(fileObject);
				}
				QFile qfile = new QFile(fileObject);
				qseries.add(qfile);
				qstudy.add(qseries);
				instanceTable.put(qfile.getName(), qfile);
				seriesTable.put(seriesUID, qseries);
				studyTable.put(studyUID, qstudy);
				return true;
			}
			catch (Exception unable) { logger.warn("index", unable); }
		}
		return false;
	}

	//Remove a file from the quarantine index
	private synchronized void deindex(File file) {
		if (file.isFile() && file.getParentFile().equals(directory)) {
			try {
				String name = file.getName();
				QFile qfile = (QFile)instanceTable.get(name);
				String seriesUID = qfile.getSeriesUID();
				QSeries qseries = (QSeries)seriesTable.get(seriesUID);
				String studyUID = qseries.getStudyUID();
				QStudy qstudy = (QStudy)studyTable.get(studyUID);
				instanceTable.remove(name);
				qseries.remove(qfile);
				if (!qseries.isEmpty()) {
					seriesTable.put(seriesUID, qseries);
				}
				else {
					seriesTable.remove(seriesUID);
					qstudy.removeSeries(seriesUID);
					if (qstudy.isEmpty()) {
						studyTable.remove(studyUID);
					}
				}
				commitIndex();
			}
			catch (Exception unable) { }
		}
	}

	/**
	 * Delete all the files in the Quarantine.
	 */
	public void deleteAll() {
		File[] files = directory.listFiles();
		for (File file: files) {
			deleteFile(file);
		}
	}

	/**
	 * Delete all the files in in a study.
	 * @param studyUID the UID of the study to delete.
	 */
	public void deleteStudy(String studyUID) {
		try {
			QStudy study = (QStudy)studyTable.get(studyUID);
			if (study != null) {
				for (String seriesUID : study.getSeriesUIDs()) {
					deleteSeries(seriesUID);
				}
			}
		}
		catch (Exception unable) { }
	}

	/**
	 * Delete all the files in in a series.
	 * @param seriesUID the UID of the series to delete.
	 */
	public void deleteSeries(String seriesUID) {
		try {
			QSeries series = (QSeries)seriesTable.get(seriesUID);
			if (series != null) {
				for (String filename : series.getFilenames()) {
					deleteFile( getFile(filename) );
				}
			}
		}
		catch (Exception unable) { }
	}

	/**
	 * Delete a file from the Quarantine.
	 * @param filename the filename to delete
	 */
	public void deleteFile(String filename) {
		if (filename != null) {
			File file = new File(directory, filename);
			deleteFile(file);
		}
	}

	/**
	 * Delete a file from the Quarantine.
	 * @param file the file to delete
	 */
	public void deleteFile(File file) {
		if ((file != null) && file.isFile() && file.getParentFile().equals(directory)) {
			deindex(file);
			file.delete();
		}
	}

	/**
	 * Queue all the files in the Quarantine to a QueueManager.
	 * @param queueManager the QueueManager to receive the files.
	 */
	public void queueAll(QueueManager queueManager) {
		File[] files = directory.listFiles();
		for (File file : files) {
			queueFile(file, queueManager);
		}
	}

	/**
	 * Queue all the files in in a study to a QueueManager.
	 * @param studyUID the UID of the study to queue.
	 * @param queueManager the QueueManager to receive the files.
	 */
	public void queueStudy(String studyUID, QueueManager queueManager) {
		try {
			QStudy study = (QStudy)studyTable.get(studyUID);
			if (study != null) {
				for (String seriesUID : study.getSeriesUIDs()) {
					queueSeries(seriesUID, queueManager);
				}
			}
		}
		catch (Exception unable) { }
	}

	/**
	 * Queue all the files in in a series to a QueueManager.
	 * @param seriesUID the UID of the series to queue.
	 * @param queueManager the QueueManager to receive the files.
	 */
	public void queueSeries(String seriesUID, QueueManager queueManager) {
		try {
			QSeries series = (QSeries)seriesTable.get(seriesUID);
			if (series != null) {
				for (String filename : series.getFilenames()) {
					queueFile( getFile(filename), queueManager);
				}
			}
		}
		catch (Exception unable) { }
	}

	/**
	 * Queue a file in the Quarantine to a QueueManager.
	 * @param file the file to queue
	 * @param queueManager the QueueManager to receive the file.
	 */
	public void queueFile(File file, QueueManager queueManager) {
		if ((file != null) && file.isFile() && file.getParentFile().equals(directory)) {
			try {
				deindex(file);
				queueManager.enqueue(file);
				file.delete();
				recman.commit();
			}
			catch (Exception unable) { }
		}
	}

	/**
	 * Move a File to the quarantine directory.
	 * @param file the file to quarantine.
	 * @return true if the move was successful, false otherwise.
	 */
	public boolean insert(File file) {
		FileObject fileObject = FileObject.getInstance(file);
		return insert(fileObject);
	}

	/**
	 * Move a FileObject to the quarantine directory, modifying
	 * the FileObject itself to point to the new file's new location.
	 * @param fileObject the object to quarantine.
	 * @return true if the move was successful, false otherwise.
	 */
	public boolean insert(FileObject fileObject) {
		File qfile = getTempFile(fileObject.getExtension());
		boolean ok = fileObject.moveTo(qfile);
		if (ok) {
			try { index(fileObject); }
			catch (Exception ignore) { }
		}
		return ok;
	}

	private File getTempFile(String ext) {
		File file = null;
		try {
			file = File.createTempFile("TQ-", ext, directory);
			file.delete();
		}
		catch (Exception ignore) { }
		return file;
	}

	/**
	 * Move a copy of a FileObject to the quarantine directory, leaving
	 * the FileObject pointing to the object's original location.
	 * @param fileObject the object to quarantine.
	 * @return true if the move was successful, false otherwise.
	 */
	public boolean insertCopy(FileObject fileObject) {
		File qfile = getTempFile(fileObject.getExtension());
		boolean ok = fileObject.copyTo(qfile);
		if (ok) {
			fileObject = FileObject.getInstance(qfile);
			try { index(fileObject); }
			catch (Exception ignore) { }
		}
		return ok;
	}

	/**
	 * Get the array of QStudy objects, sorted in natural order..
	 */
	public synchronized QStudy[] getStudies() {
		LinkedList<QStudy> studyList = new LinkedList<QStudy>();
		try {
			FastIterator fit = studyTable.values();
			QStudy study;
			while ((study=(QStudy)fit.next()) != null) {
				studyList.add(study);
			}
		}
		catch (Exception ex) { }
		QStudy[] studies = new QStudy[studyList.size()];
		studies = studyList.toArray(studies);
		Arrays.sort(studies);
		return studies;
	}

	/**
	 * Get the QStudy corresponding to a studyUID..
	 * @param studyUID the UID of the study (for a DICOM study, the StudyInstanceUID).
	 * @return the QStudy, or null if the QStudy is not in the quarantine.
	 */
	public synchronized QStudy getStudy(String studyUID) {
		try { return (QStudy)studyTable.get(studyUID); }
		catch (Exception ex) { return null; }
	}

	/**
	 * Get the array of QSeries objects in a QStudy, sorted in natural order.
	 * @param study the QStudy.
	 * @return the array of QSeries objects in the QStudy, or the
	 * empty array if the QStudy has no QSeries.
	 */
	public synchronized QSeries[] getSeries(QStudy study) {
		LinkedList<QSeries> seriesList = new LinkedList<QSeries>();
		try {
			if (study != null) {
				String[] uids = study.getSeriesUIDs();
				for (String uid : uids) {
					QSeries series = (QSeries)seriesTable.get(uid);
					if (series != null) seriesList.add(series);
				}
			}
		}
		catch (Exception ex) { }
		QSeries[] seriesArray = new QSeries[seriesList.size()];
		seriesArray = seriesList.toArray(seriesArray);
		Arrays.sort(seriesArray);
		return seriesArray;
	}

	/**
	 * Get the QSeries corresponding to a seriesUID..
	 * @param seriesUID the UID of the QSeries (for a DICOM study, the SeriesInstanceUID).
	 * @return the QSeries, or null if the QSeries is not in the quarantine.
	 */
	public synchronized QSeries getSeries(String seriesUID) {
		try { return (QSeries)seriesTable.get(seriesUID); }
		catch (Exception ex) { return null; }
	}

	/**
	 * Get the array of QFile objects in a QSseries, sorted in natural order.
	 * @param series the QSseries.
	 * @return the array of QFile objects in the QSseries, or the
	 * empty array if the QSseries has no QFiles.
	 */
	public synchronized QFile[] getFiles(QSeries series) {
		LinkedList<QFile> fileList = new LinkedList<QFile>();
		try {
			if (series != null) {
				String[] names = series.getFilenames();
				for (String name : names) {
					QFile file = (QFile)instanceTable.get(name);
					if (file != null) fileList.add(file);
				}
			}
		}
		catch (Exception ex) { }
		QFile[] fileArray = new QFile[fileList.size()];
		fileArray = fileList.toArray(fileArray);
		Arrays.sort(fileArray);
		return fileArray;
	}

	/**
	 * Get an array of all the files in the quarantine index,
	 * sorted in natural order.
	 */
	public File[] getFiles() {
		LinkedList<File> fileList = new LinkedList<File>();
		for (QStudy study : getStudies()) {
			for (QSeries series : getSeries(study)) {
				for (QFile file : getFiles(series)) {
					fileList.add( new File(directory, file.getName()) );
				}
			}
		}
		File[] files = new File[fileList.size()];
		files = fileList.toArray(files);
		return files;
	}

	/**
	 * Get the file in the quarantine corresponding to a filename.
	 * This method only finds files in the quarantine directory.
	 * @param filename the name of the file.
	 */
	public File getFile(String filename) {
		if (filename == null) return null;
		File file = new File(filename);
		String name = file.getName();
		return new File(directory, name);
	}

	/**
	 * Get an XML document listing the studies in the quarantine.
	 */
	public Document getStudiesXML() throws Exception {
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("Studies");
		doc.appendChild(root);
		for (QStudy q : getStudies()) {
			Element study = doc.createElement("Study");
			root.appendChild(study);
			study.setAttribute("patientName", q.patientName);
			study.setAttribute("patientID", q.patientID);
			study.setAttribute("studyDate", q.studyDate);
			study.setAttribute("studyUID", q.studyUID);
			study.setAttribute("nSeries", Integer.toString(q.getNumberOfSeries()));
		}
		return doc;
	}

	/**
	 * Get an XML document listing the series in a study.
	 * @param studyUID the studyUID of the QStudy
	 */
	public Document getSeriesXML(String studyUID) throws Exception {
		QStudy study = getStudy(studyUID);
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("Study");
		root.setAttribute("studyUID", studyUID);
		doc.appendChild(root);
		for (QSeries q : getSeries(study)) {
			Element series = doc.createElement("Series");
			root.appendChild(series);
			series.setAttribute("seriesUID", q.seriesUID);
			series.setAttribute("seriesNumber", q.seriesNumber);
			series.setAttribute("nFiles", Integer.toString(q.getNumberOfFiles()));
		}
		return doc;
	}

	/**
	 * Get an XML document listing the files in a series.
	 * @param seriesUID the seriesUID of the QSeries
	 */
	public Document getFilesXML(String seriesUID) throws Exception {
		QSeries series = getSeries(seriesUID);
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("Files");
		root.setAttribute("seriesUID", seriesUID);
		doc.appendChild(root);
		for (QFile q : getFiles(series)) {
			Element file = doc.createElement("File");
			root.appendChild(file);
			file.setAttribute("type", q.type);
			file.setAttribute("instanceNumber", q.instanceNumber);
			file.setAttribute("filename", q.filename);
		}
		return doc;
	}
}