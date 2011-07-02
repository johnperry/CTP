/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.archive;

import java.io.*;
import java.util.Stack;
import org.apache.log4j.Logger;
import org.rsna.util.SerializerUtil;

/**
 * A FileSource that walks a directory tree. It checkpoints its progress,
 * allowing the program to stop and start without the FileSource
 * losing its place. It is designed to walk through the directory tree
 * once. It does not detect changes in the tree which occur in parts of
 * the tree that have already been passed.
 */
public class FileSource implements Serializable {

	static final Logger logger = Logger.getLogger(FileSource.class);

	File treeRoot;
	Frame currentFrame;
	Stack<Frame> stack;
	int fileCount;

	public static final String checkpointName = "checkpoint.bin";

	/**
	 * Protected class constructor to ensure that the class is instantiated
	 * by calls to getInstance, allowing it to be deserialized from a checkpoint
	 * if one exists.
	 * @param treeRoot the root of the tree to be walked.
	 */
	protected FileSource(File treeRoot) {
		this.treeRoot = treeRoot;
		stack = new Stack<Frame>();
		currentFrame = new Frame(treeRoot);
		fileCount = 0;
	}

	/**
	 * Get the instance of the FileSource from the checkpoint (if available),
	 * or instantiate a new instance if no checkpoint exists.
	 * @param treeRoot the root of the tree to be walked.
	 * @param checkpointDir the directory in which to write checkpoints.
	 */
	public static FileSource getInstance(File treeRoot, File checkpointDir) {
		Object fileSource = SerializerUtil.deserialize( new File(checkpointDir, checkpointName) );
		return (fileSource != null) ? (FileSource)fileSource : new FileSource(treeRoot);
	}

	/**
	 * Check whether there is a current Frame. If there is, the FileSource
	 * has not yet completed traversing the tree. If there is not, the
	 * FileSource is finished, and no more files will be supplied.
	 * @return true if the FileSource is done, false otherwise.
	 */
	public boolean isDone() {
		return (currentFrame == null);
	}

	/**
	 * Get the number of files supplied by this FileSource since it was
	 * originally instantiated, including any files that were supplied
	 * by instances which were loaded by deserializing checkpoints.
	 * @return the number of files supplied by this FileSource since it was
	 * originally instantiated.
	 */
	public int getFileCount() {
		return fileCount;
	}

	/**
	 * Get the next file available in the tree.
	 * @return the next file available, or null if no file is available,
	 * indicating the end of the arboreal perambulation.
	 */
	public File getNextFile() {
		while (currentFrame != null) {
			File file = currentFrame.getNextFile();
			if (file == null) {
				try { currentFrame = stack.pop(); }
				catch (Exception empty) { currentFrame = null; }
			}
			else if (file.isDirectory()) {
				stack.push(currentFrame);
				currentFrame = new Frame(file);
			}
			else {
				fileCount++;
				return file;
			}
		}
		return null;
	}
}