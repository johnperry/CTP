/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractImportService;
import org.rsna.ctp.pipeline.QueueManager;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * An ImportService that import data from GCP Healthcare DICOM Stores.
 * Import service pulls DICOM objects from selected projectID/DICOMStore
 * and transfers them to next stage of the pipeline.
 */
public class GoogleCloudImportService extends AbstractImportService {

	static final Logger logger = Logger.getLogger(GoogleCloudImportService.class);

	Poller poller = null;
	long interval = 20000;
	long minInterval = 1000;
	long defInterval = 20000;
	File importDirectory = null;
	FileTracker tracker = null;

	String fsName = "";
	int fsNameTag = 0;
	boolean setFileSystemName;

	int filePathTag = 0;
	boolean setFilePath;

	int fileNameTag = 0;
	boolean setFileName;
	String projectId = null;
	String dicomStoreName = null;
	String googleCredentialsPath = null;
	File googleCredentialsFile = null;



	/**
	 * Class constructor; creates a new instance of the ImportService.
	 * @param element the configuration element.
	 * @throws Exception on any error
	 */
	public GoogleCloudImportService(Element element) throws Exception {
		super(element);


		String directoryName = element.getAttribute("import").trim();
		importDirectory = getDirectory(directoryName);
		if ((importDirectory == null) || !importDirectory.exists()) {
			logger.error(name+": The import directory was not specified.");
			throw new Exception(name+": The import directory was not specified.");
		}


		//Get the interval
		interval = Math.max(StringUtil.getLong(element.getAttribute("interval"), defInterval), minInterval);

		//See if there is a FileSystem name
		fsName = element.getAttribute("fsName").trim();
		fsNameTag = DicomObject.getElementTag(element.getAttribute("fsNameTag"));
		setFileSystemName = (!fsName.equals("")) && (fsNameTag > 0);

		//See if there is a filePathTag
		filePathTag = DicomObject.getElementTag(element.getAttribute("filePathTag"));
		setFilePath = (filePathTag > 0);

		//See if there is a filenameTag
		fileNameTag = DicomObject.getElementTag(element.getAttribute("fileNameTag"));
		setFileName = (fileNameTag > 0);

		//Initialize the FileTracker
		tracker = new FileTracker();

		projectId =  element.getAttribute("projectId").trim();
		dicomStoreName =  element.getAttribute("dicomStoreName").trim();


/*		googleCredentialsPath = element.getAttribute("googleCredentialsPath").trim();
		googleCredentialsFile = getDirectory(googleCredentialsPath);
		if ((googleCredentialsFile == null) || !googleCredentialsFile.exists()) {
			logger.error(name+": The import directory was not specified.");
			throw new Exception(name+": Google Credentials location was not specified.");
		}


		GoogleCredentials credential = GoogleCredentials.getApplicationDefault()
				.createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));

		credential.refreshIfExpired();
		System.out.println(credential.getAccessToken().getTokenValue());*/
	}

	/**
	 * Start the service. This method can be overridden by stages
	 * which can use it to start subordinate threads created in their constructors.
	 * This method is called by the Pipeline after all the stages have been
	 * constructed.
	 */
	@Override
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

		@Override
		public void run() {
			while (!isInterrupted()) {
				//Queue all the files that were found last time.
				//This ensures that they are at least 'interval' old.
				if (fileList != null) {
					for (File file : fileList) {
						setNames(file);
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
	 * Get the import directory; this is the directory that the stage monitors for submissions.
	 * @return the import directory.
	 */
	public File getImportDirectory() {
		return importDirectory;
	}
		
	/**
	 * Get the next object available for processing.
	 * @return the next object available, or null if no object is available.
	 */
	@Override
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

	//Store the FileSystem name and/or the filename in the file if required.
	private void setNames(File file) {
		if ((setFileSystemName || setFilePath || setFileName)) {
			try {
				FileObject fo = FileObject.getInstance(file);
				if (fo instanceof DicomObject) {
					//Unfortunately, we have to parse the object again
					//in order to be able to save it once we modify it.
					DicomObject dob = new DicomObject(fo.getFile(), true); //leave the stream open
					File dobFile = dob.getFile();

					//See if we have to store the FileSystem name
					if (setFileSystemName) {
						//Modify the specified element.
						//If the fsName is "@filename", use the name of the file;
						//otherwise, use the value of the fsName attribute.
						if (fsName.equals("@filename")) {
							String name = dobFile.getName();
							dob.setElementValue(fsNameTag, name);
						}
						else dob.setElementValue(fsNameTag, fsName);
					}

					if (setFilePath) {
						String path = dobFile.getParentFile().getAbsolutePath();
						File treeRootParent = importDirectory.getAbsoluteFile().getParentFile();
						if (treeRootParent == null) treeRootParent = importDirectory;
						String treeRootPath =  treeRootParent.getAbsolutePath();
						if (treeRootPath.endsWith(File.separator)) {
							treeRootPath = treeRootPath.substring(0, treeRootPath.length() -1);
						}
						//logger.info("dobFile:  \""+ path + "\"");
						//logger.info("treeRoot: \""+ treeRootPath + "\"");
						path = path.substring( treeRootPath.length()+1 );
						path = path.replace("\\", "/");
						//logger.info("path stored: \""+path+"\"");
						dob.setElementValue(filePathTag, path);
					}
					
					if (setFileName) dob.setElementValue(fileNameTag, dobFile.getName());

					//Save the modified object
					File tFile = File.createTempFile("TMP-", ".dcm", dobFile.getParentFile());
					dob.saveAs(tFile, false);
					dob.close();
					dobFile.delete();

					//Okay, we have saved the modified file in the temp file
					//and deleted the original file; now rename the temp file
					//to the original name so nobody is the wiser.
					tFile.renameTo(dobFile);
				}
			}
			catch (Exception unableToSetName) {
				logger.warn("Unable to set the name(s) in "+file.getName(), unableToSetName);
			}
		}
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