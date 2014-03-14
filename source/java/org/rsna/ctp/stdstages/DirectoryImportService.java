/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.util.HashSet;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractImportService;
import org.rsna.ctp.pipeline.QueueManager;
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
public class DirectoryImportService extends AbstractImportService {

	static final Logger logger = Logger.getLogger(DirectoryImportService.class);

	String fsName = null;
	int fsNameTag = 0;
	int filenameTag = 0;
	Poller poller = null;
	long interval = 20000;
	long minInterval = 1000;
	long defInterval = 20000;
	File importDirectory = null;
	FileTracker tracker = null;

	/**
	 * Class constructor; creates a new instance of the ImportService.
	 * @param element the configuration element.
	 */
	public DirectoryImportService(Element element) throws Exception {
		super(element);

		//Get the import directory and quit if it is blank.
		//This action is to trap configurations that haven't been
		//updated to the new version of this import service.
		String directoryName = element.getAttribute("import").trim();
		if (!directoryName.equals("")) {
			importDirectory = new File(directoryName);
			importDirectory.mkdirs();
		}
		if ((importDirectory == null) || !importDirectory.exists()) {
			logger.error(name+": The import attribute was not specified.");
			throw new Exception(name+": The import attribute was not specified.");
		}

		//Get the interval
		interval = Math.max(StringUtil.getLong(element.getAttribute("interval"), defInterval), minInterval);

		//See if there is a FileSystem name
		fsName = element.getAttribute("fsName").trim();
		if (fsName == null) fsName = fsName.trim();
		if (fsName.equals("")) fsName = null;
		fsNameTag = DicomObject.getElementTag(element.getAttribute("fsNameTag"));

		//See if there is a filenameTag
		filenameTag = DicomObject.getElementTag(element.getAttribute("filenameTag"));

		//Initialize the FileTracker
		tracker = new FileTracker();
	}

	/**
	 * Start the service. This method can be overridden by stages
	 * which can use it to start subordinate threads created in their constructors.
	 * This method is called by the Pipeline after all the stages have been
	 * constructed.
	 */
	public synchronized void start() {
		poller = new Poller();
		poller.start();
	}

	/**
	 * Stop the service.
	 */
	public synchronized void shutdown() {
		if (poller != null) poller.interrupt();
		super.shutdown();
	}

	class Poller extends Thread {
		String prefix = "IS-";
		LinkedList<File> fileList = null;

		public Poller() {
			super("Poller");
		}

		//To ensure that files are not
		public void run() {
			while (!isInterrupted()) {

				//Queue all the files that were found last time.
				//This ensures that they are at least 'interval' old.
				if (fileList != null) {
					for (File file : fileList) {
						fileReceived(file);
						tracker.add(file);
					}
				}

				//Get ready for the next search
				fileList = new LinkedList<File>();
				tracker.purge();
				addFiles(importDirectory);
				if (!isInterrupted()) {
					try { sleep(interval); }
					catch (Exception ignore) { }
				}
			}
		}

		//List all the files currently in a directory and all its children
		private void addFiles(File dir) {
			File[] files = dir.listFiles();
			for (File file : files) {
				if (!file.isHidden()) {
					if (file.isFile()
							&& !file.getName().endsWith(".partial")
								&& !tracker.contains(file)) {
						fileList.add(file);
					}
					else if (file.isDirectory()) addFiles(file);
				}
			}
		}
	}

	/**
	 * Get the next object available for processing.
	 * @return the next object available, or null if no object is available.
	 */
	public FileObject getNextObject() {
		File file;
		QueueManager queueManager = getQueueManager();
		File active = getActiveDirectory();
		if (queueManager != null) {
			while ((file = queueManager.dequeue(active)) != null) {
				lastFileOut = file;
				lastTimeOut = System.currentTimeMillis();
				FileObject fileObject = FileObject.getInstance(lastFileOut);
				fileObject.setStandardExtension();

				//Make sure we accept objects of this type.
				if (acceptable(fileObject)) {
					fileObject = setNames(fileObject);
					fileObject.setStandardExtension();
					lastFileOut = fileObject.getFile();
					lastTimeOut = System.currentTimeMillis();
					return fileObject;
				}

				//If we get here, this import service does not accept
				//objects of the active type. Try to quarantine the
				//object, and if that fails, delete it.
				if (quarantine != null)  quarantine.insert(fileObject);
				else fileObject.getFile().delete();
			}
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
		public synchronized void purge() {
			File[] keys = new File[files.size()];
			keys = files.toArray(keys);
			for (File file : keys) {
				if (!file.exists()) {
					files.remove(file);
				}
			}
		}
		public synchronized void add(File file) {
			files.add(file);
		}
		public synchronized boolean contains(File file) {
			return files.contains(file);
		}
	}

}