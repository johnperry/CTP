/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.ImportService;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.archive.FileSource;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An ImportService that monitors a directory. This is an import service
 * that allows a user to manually drop files into its directory. The
 * import service processes the files and either deletes or quarantines them
 * at the end, depending on whether there is a quarantine directory defined
 * in the configuration file element for the stage.
 */
public class DirectoryImportService extends AbstractPipelineStage implements ImportService {

	static final Logger logger = Logger.getLogger(DirectoryImportService.class);

	static final int defaultAge = 5000;
	static final int minAge = 1000;
	long age;
	String fsName = null;
	int fsNameTag = 0;
	int filenameTag = 0;
	FileSource source = null;
	FileTracker tracker = null;

	/**
	 * Class constructor; creates a new instance of the ImportService.
	 * @param element the configuration element.
	 */
	public DirectoryImportService(Element element) throws Exception {
		super(element);
		age = StringUtil.getInt(element.getAttribute("minAge").trim());
		if (age < minAge) age = defaultAge;

		//See if there is a FileSystem name
		fsName = element.getAttribute("fsName").trim();
		if (fsName == null) fsName = fsName.trim();
		if (fsName.equals("")) fsName = null;
		fsNameTag = DicomObject.getElementTag(element.getAttribute("fsNameTag"));

		//See if there is a filenameTag
		filenameTag = DicomObject.getElementTag(element.getAttribute("filenameTag"));

		//Initialize the FileSource
		source = FileSource.getInstance(root, null); //disable checkpointing

		//Initialize the FileTracker
		tracker = new FileTracker();
	}

	/**
	 * Get the size of the import directory.
	 * @return the number of objects in the import queue, or zero if the
	 * ImportService is not queued.
	 */
	public int getQueueSize() {
		return FileUtil.getFileCount(root);
	}

	/**
	 * Get the next object available for processing.
	 * @return the next object available, or null if no object is available.
	 */
	public FileObject getNextObject() {
		File file;
		long maxLM = System.currentTimeMillis() - age;
		while ((file = findFile(maxLM)) != null) {

			FileObject fileObject = FileObject.getInstance(file);
			if (acceptable(fileObject)) {
				fileObject = setNames(fileObject);
				fileObject.setStandardExtension();
				lastFileOut = fileObject.getFile();
				lastTimeOut = System.currentTimeMillis();
				return fileObject;
			}

			//If we get here, this import service does not accept
			//objects of this type. Try to quarantine the
			//object, and if that fails, delete it.
			if (quarantine != null)  quarantine.insert(fileObject);
			else fileObject.getFile().delete();
		}
		return null;
	}

	//Store the FileSystem name and/or the filename in the object if required.
	private FileObject setNames(FileObject fo) {
		try {
			if (fo instanceof DicomObject) {
				boolean doFileSystemName = (fsName != null) && (fsNameTag != 0);
				boolean doFilename = (filenameTag != 0);
				if ( doFileSystemName || doFilename ) {
					//Unfortunately, we have to parse the object again
					//in order to be able to save it once we modify it.
					DicomObject dob = new DicomObject(fo.getFile(), true); //leave the stream open
					File dobFile = dob.getFile();

					//See if we have to store the FileSystem name
					if (doFileSystemName) {
						//Modify the specified element.
						//If the fsName is "@filename", use the name of the file;
						//otherwise, use the value of the fsName attribute.
						if (fsName.equals("@filename")) {
							String name = dobFile.getName();
							dob.setElementValue(fsNameTag, name);
						}
						else dob.setElementValue(fsNameTag, fsName);
					}

					if (doFilename) {
						dob.setElementValue(filenameTag, fo.getFile().getName());
					}

					//Save the modified object
					File tFile = File.createTempFile("TMP-", ".dcm", dobFile.getParentFile());
					dob.saveAs(tFile, false);
					dob.close();
					dobFile.delete();

					//Okay, we have saved the modified file in the temp file
					//and deleted the original file; now rename the temp file
					//to the original name so nobody is the wiser.
					tFile.renameTo(dobFile);

					//And finally parse it again so we have a real object to process;
					return new DicomObject(dobFile);
				}
			}
		}
		catch (Exception unableToSetFSName) {
			logger.warn("Unable to set the FileSystem name: \""+fsName+"\"");
			logger.warn("                               in: "+fo.getFile());
		}
		return fo;
	}

	//Walk a directory tree until we find a file with a
	//last-modified-time earlier than a specified time.
	private File findFile(long maxLM) {
		File file;
		tracker.purge();
		while ((file=source.getNextFile()) != null) {
			if (file.exists()
					&& !file.isHidden()
						&& file.isFile()
							&& (file.lastModified() < maxLM)
								&& !file.getName().endsWith(".partial")
									&& !tracker.contains(file)) {
				logger.debug("Processing "+file);
				tracker.add(file);
				return file;
			}
		}
		//If we get here, we didn't get anything.
		//Re-instantiate the FileSource so we'll
		//start over on the next try.
		source = FileSource.getInstance(root, null);
		return null;
	}

	/**
	 * Release a file from the import directory. Note that other stages in the
	 * pipeline may have moved the file, so it is possible that the file will
	 * no longer exist. This method only deletes the file if it is still in the
	 * tree under the root directory.
	 * @param file the file to be released.
	 */
	public void release(File file) {
		if ((file != null) && file.exists()) {
			//Only delete if the path includes the root.
			if (file.getAbsolutePath().startsWith(root.getAbsolutePath())) {
				 file.delete();
			}
		}
	}

	/**
	 * Get HTML text displaying the active status of the stage.
	 * This method does not call the method in the parent class
	 * because there is no time associated with the last file
	 * that was received.
	 * @return HTML text displaying the active status of the stage.
	 */
	public String getStatusHTML() {
		StringBuffer sb = new StringBuffer();
		sb.append("<h3>"+name+"</h3>");
		sb.append("<table border=\"1\" width=\"100%\">");
		sb.append("<tr><td width=\"20%\">Queue size:</td>");
		sb.append("<td>" + FileUtil.getFileCount(root) + "</td></tr>");
		sb.append("<tr><td width=\"20%\">Last file supplied:</td>");
		if (lastTimeOut != 0) {
			sb.append("<td>"+lastFileOut+"</td></tr>");
			sb.append("<tr><td width=\"20%\">Last file supplied at:</td>");
			sb.append("<td>"+StringUtil.getDateTime(lastTimeOut,"&nbsp;&nbsp;&nbsp;")+"</td></tr>");
		}
		else sb.append("<td>No activity</td></tr>");
		sb.append("</table>");
		return sb.toString();
	}

	//This class tracks files that have been processed.
	//It is designed to solve the problem that occurs when the root directory is
	//in the cloud and the delete operation at the end of a pipeline may take a
	//while to complete. The idea is to prevent the stage from seeing the same file
	//again while the delete operation is proceeding.
	//
	//To use this class, only accept a file that is not in the tracker.
	//You should periodically call the purge method to remove files from
	//the tracker. This then allows the tracker to process a new file of the
	//same name if it is subsequently received.
	class FileTracker {
		HashSet<File> files;
		public FileTracker() {
			files = new HashSet<File>();
		}
		public void purge() {
			for (File file : files) {
				if (!file.exists()) {
					files.remove(file);
				}
			}
		}
		public void add(File file) {
			files.add(file);
		}
		public boolean contains(File file) {
			return files.contains(file);
		}
	}

}