/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import org.rsna.ctp.objects.FileObject;
import org.rsna.util.FileUtil;

/**
 * A class representing a quarantine directory and providing
 * methods for inserting FileObjects.
 */
public class Quarantine {

	File directory = null;

	/**
	 * Create a Quarantine object for a directory, creating
	 * the directory if necessary.
	 */
	public Quarantine(String directoryPath) {
		directory = new File(directoryPath);
		directory.mkdirs();
	}

	/**
	 * Get the File for this Quarantine instance.
	 * @return the File pointing to the quarantine directory.
	 */
	public File getDirectory() {
		return directory;
	}

	/**
	 * Get the number of files in the Quarantine.
	 * @return the number of files in the directory.
	 */
	public int getSize() {
		return FileUtil.getFileCount(directory);
	}

	/**
	 * Get the files in the Quarantine.
	 * @return the files in the directory.
	 */
	public File[] getFiles() {
		return directory.listFiles();
	}

	/**
	 * Delete all the files in the Quarantine.
	 */
	public void deleteAll() {
		File[] files = getFiles();
		for (int i=0; i<files.length; i++) files[i].delete();
	}

	/**
	 * Delete a file from the Quarantine.
	 * @param filename the filename to delete
	 */
	public void deleteFile(String filename) {
		File file = getFile(filename);
		if (file.exists()) file.delete();
	}

	/**
	 * Queue all the files in the Quarantine to a QueueManager.
	 * @param queueManager the QueueManager to receive the files.
	 */
	public void queueAll(QueueManager queueManager) {
		File[] files = getFiles();
		for (int i=0; i<files.length; i++) {
			queueManager.enqueue(files[i]);
			files[i].delete();
		}
	}

	/**
	 * Queue a file in the Quarantine to a QueueManager.
	 * @param filename the filename to queue
	 * @param queueManager the QueueManager to receive the file.
	 */
	public void queueFile(String filename, QueueManager queueManager) {
		File file = getFile(filename);
		if (file.exists()) {
			queueManager.enqueue(file);
			file.delete();
		}
	}

	/**
	 * Get a file in the Quarantine.
	 * @param fileName the name of the file to get.
	 * @return the files in the directory.
	 */
	public File getFile(String fileName) {
		fileName = (new File(fileName)).getName();
		return new File(directory, fileName);
	}

	/**
	 * Move a FileObject to the quarantine directory, modifying
	 * the FileObject itself to point to the new file's new location.
	 * @param fileObject the object to quarantine.
	 * @return true if the move was successful, false otherwise.
	 */
	public boolean insert(FileObject fileObject) {
		return fileObject.moveToDirectory(directory, false);
	}

	/**
	 * Move a File to the quarantine directory.
	 * @param file the file to quarantine.
	 * @return true if the move was successful, false otherwise.
	 */
	public boolean insert(File file) {
		FileObject fileObject = new FileObject(file);
		return fileObject.moveToDirectory(directory, false);
	}

	/**
	 * Move a copy of a FileObject to the quarantine directory, leaving
	 * the FileObject pointing to the object's original location.
	 * @param fileObject the object to quarantine.
	 * @return true if the move was successful, false otherwise.
	 */
	public File insertCopy(FileObject fileObject) {
		return fileObject.copyToDirectory(directory);
	}

}