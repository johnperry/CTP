/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import org.apache.log4j.Logger;
import org.rsna.util.FileUtil;

/**
 * A class to manage a queue directory and multiple subdirectories to
 * prevent any subdirectory from growing larger than a specified size.
 * The files stored in the queue are placed at the deepest level of the tree,
 * with higher-level directories being used just to store directories.
 */
public class QueueManager {

	static final Logger logger = Logger.getLogger(QueueManager.class);

	private File root;
	private int nLevels;
	private int maxSize;
	private ActiveDirectory outDir;
	private File lastFileIn;
	private FileFilter dirsOnly;
	private FileFilter filesOnly;
	private int size;
	private int subNameLength;
	private int topNameLength = 10;
	private String zeroes = "0000000000000000";

	/**
	 * Create a QueueManager for a root directory.
	 * @param root the root directory of the queue tree.
	 * If the root directory does not exist, it is created.
	 * @param nLevels number of levels in the queue tree.
	 * If specified to be less than 3, it is set to 3.
	 * @param maxSize the maximum number of files allowed
	 * in any directory (except for the root directory).
	 * If specified to be less than 200, it is set to 200.
	 */
	public QueueManager(File root, int nLevels, int maxSize) {
		this.root = root;
		this.nLevels = Math.max(nLevels, 3);
		this.maxSize = Math.max(maxSize, 200);
		root.mkdirs();
		outDir = null;
		lastFileIn = null;
		size = countFiles(root);
		dirsOnly = new NumericFileFilter(true,false);
		filesOnly = new NumericFileFilter(false,true);
		subNameLength = Integer.toString(this.maxSize).length();
	}

	/**
	 * Get the size of the queue.
	 * @return the number of objects in the queue..
	 */
	public synchronized int size() {
		return size;
	}

	/**
	 * Insert a file into the queue directory tree,
	 * preserving the embedded filename, if present.
	 * @param file the file to be inserted.
	 * @return a File pointing to the object in the queue.
	 */
	public synchronized File enqueue(File file) {
		String filename = getEmbeddedFilename(file.getName(), true);
		lastFileIn = getNextFileIn(filename);
		lastFileIn = copyFile(file, lastFileIn);
		if (lastFileIn != null) size++;
		return lastFileIn;
	}

	/**
	 * Insert all the files in a directory into the queue
	 * directory tree, preserving the embedded filename, if present.
	 * this method does not walk a deep tree; it only takes the
	 * first-generation children of the specified directory,
	 * ignoring any child directories.
	 * @param dir the directory whose contents are to be inserted.
	 */
	public synchronized void enqueueDir(File dir) {
		if ((dir != null) && dir.exists()) {
			File[] files = dir.listFiles();
			for (int i=0; i<files.length; i++) {
				if (files[i].isFile()) {
					enqueue(files[i]);
					files[i].delete();
				}
			}
		}
	}

	/**
	 * Get a filename which is either the string embedded most deeply
	 * in square brackets in a filename, the full filename itself, or the
	 * empty string. For example, the filename
	 * "XXX-1234[yyy-5678[abc-9876.dcm].dcm].dcm" will return "abc-9876.dcm".
	 * @param filename the starting filename to be searched for the most
	 * deeply embedded bracketed string.
	 * @param returnName true if the original filename is to be returned if
	 * no embedded name is found; false if the empty string is to be returned
	 * if no embedded name is found.
	 * @return the contents of the most deeply bracketed string (without the brackets),
	 * or if there are no brackets, then either the full filename or the empty string,
	 * depending on the value of returnName.
	 */
	public static String getEmbeddedFilename(String filename, boolean returnName) {
		String returnString = returnName ? filename : "";
		int k1 = filename.indexOf("[");
		if (k1 == -1) return returnString;
		int k2 = filename.lastIndexOf("]");
		if (k2 == -1) return returnString;
		if (k2 < k1) return returnString;
		return getEmbeddedFilename(filename.substring(k1+1, k2).trim(), true);
	}

	/**
	 * Retrieve a file from the queue directory tree. The file
	 * is removed from the queue and placed into a directory
	 * supplied by the calling method. Files are returned in
	 * FIFO order unless files have been placed into queue directories
	 * by hand, in which case those files are likely to be served first.
	 * @param dir the directory into which to place the file to be retrieved.
	 * @return a File pointing to the file in the supplied directory. If
	 * no file is available in the queue, null is returned. If the supplied
	 * directory file is null or not a directory, null is returned.
	 */
	public synchronized File dequeue(File dir) {
		//Return null if there is no directory into which to put the
		//retrieved file or if the supplied file exists but is not a
		//directory.
		if (dir == null) return null;
		if (dir.exists() && !dir.isDirectory()) return null;
		//Ensure that the directory and its parents exist.
		dir.mkdirs();

		//If there is an active directory, try to get a file from it.
		File qFile = null;
		if (outDir != null) {
			qFile = outDir.getNextFile();
			//If we didn't get a file, then there must be none left
			//in the active directory. Set the outDir to null so the
			//next code will find a new directory containing files.
			if (qFile == null) outDir = null;
		}
		if (outDir == null) {
			//If we get here, then either there was not an active directory
			//or the current active directory contained no remaining files.
			//Walk the tree starting at the root and find one containing a
			//file. Note that this method will find a file even if it
			//is not at the lowest level. The reason for this approach is that
			//somebody might copy files into a directory in the tree by hand
			//(even though they aren't supposed to do that), and we need to
			//be sure we catch those files.
			File file = findFirstFile(root);
			//If we didn't get a file, then there must be none anywhere
			//in the queue, so we have to return null.
			if (file == null) return null;
			//Okay, there is a file, so we can create an active directory
			//for its parent.
			outDir = new ActiveDirectory(file.getParentFile());
			//Get a file from the directory. This file will be the same
			//one that we got from findFirstFile, but we need to get it
			//from the outDir object so its index is properly updated.
			qFile = outDir.getNextFile();
		}
		//We have a qFile. Just in case somebody has done something untoward
		//behind our back, make a last check, and if all is well, move the
		//file to the output directory and return it.
		if ((qFile != null) && qFile.exists()) {
			qFile = moveFile(qFile, dir);
			if (qFile != null) size--;
			return qFile;
		}
		return null;
	}

	/**
	 * Re-count all the files in the queue.
	 * @return the number of files in the queue.
	 */
	public synchronized int recount() {
		size = countFiles(root);
		return size;
	}

	//Count all the files in a directory and its subdirectories.
	//Note: only files are counted, not directories.
	private int countFiles(File dir) {
		if ((dir == null) || !dir.exists() || !dir.isDirectory()) return 0;
		int n = 0;
		File[] files = dir.listFiles();
		for (int i=0; i<files.length; i++) {
			if (files[i].isFile())
				n++;
			else
				n += countFiles(files[i]);
		}
		return n;
	}

	//Find the first file in the queue, no matter where
	//it is located in the tree.
	private File findFirstFile(File dir) {
		if ((dir == null) || !dir.exists() || !dir.isDirectory()) return null;
		File[] files = dir.listFiles();
		Arrays.sort(files);
		if ((files.length == 0) && !dir.equals(root)) {
			dir.delete();
		}
		else {
			for (int i=0; i<files.length; i++) {
				if (files[i].isFile()) return files[i];
				else {
					File returnedFile = findFirstFile(files[i]);
					if (returnedFile != null) return returnedFile;
				}
			}
		}
		return null;
	}

	//Find the last file in the queue. This method requires
	//that the file be located at the bottom level of the tree
	//and that the files and their directories are named numerically.
	private File findLastFile(File dir, int level) {
		//For this method, dir is the top of the directory tree below which
		//to search. level is the level in the tree at which dir occurs.
		//Start by qualifying dir.
		if ((dir == null) || !dir.exists() || !dir.isDirectory()) return null;
		//Okay, dir exists and is a directory.
		if (level < nLevels-1) {
			//We're not yet to the bottom; look at the directories
			//in this directory and walk them backward, seeking the last
			//one containing a file.
			File[] files = dir.listFiles(dirsOnly);
			if (files.length == 0) return null;
			Arrays.sort(files);
			for (int i=files.length-1; i>=0; i--) {
				File returnedFile = findLastFile(files[i], level+1);
				if (returnedFile != null) return returnedFile;
			}
			//Didn't find one; all we can do is return null
			return null;
		}
		else {
			//We're at the bottom of the tree; see if there is a file,
			//and if so, return the last one.
			File[] files = dir.listFiles(filesOnly);
			if (files.length == 0) return null;
			Arrays.sort(files);
			return files[files.length - 1];
		}
	}

	//Copy a file. Return null if the operation does not succeed. If the
	//operation succeeds, return a file pointing to the location of the copy.
	private File copyFile(File file, File copy) {
		try {
			copy.getParentFile().mkdirs();
			boolean ok = FileUtil.copy(file, copy);
			if (ok) return copy;
			else return null;
		}
		catch (Exception ex) { return null; }
	}

	//Move a file to a directory, using a generated name.
	//Return null if the operation does not succeed. If the
	//operation succeeds, remove the source file and return
	//a file pointing to the new location.
	private File moveFile(File file, File dir) {
		try {
			dir.mkdirs();
			//Preserve the embedded filename, if present.
			String suffix = getEmbeddedFilename(file.getName(), false);
			if (!suffix.equals("")) suffix = "[" + suffix + "]";
			File dest = File.createTempFile("QF-", suffix, dir);

			//First try a rename
			boolean ok = file.renameTo(dest);
			if (!ok) {
				//That didn't work; try to do a copy.
				ok = FileUtil.copy(file,dest);
				//If that worked, then delete the original.
				if (ok) file.delete();
			}
			if (ok) return dest;
			return null;
		}
		catch (Exception ex) { return null; }
	}

	//Get the next File into which to enqueue a file.
	private File getNextFileIn(String filename) {
		if (lastFileIn == null) lastFileIn = findLastFile(root, 0);
		int[] levels = new int[nLevels];
		if (lastFileIn == null) {
			//If lastFileIn is still null, then the queue must
			//be empty, so we can start over with all zeroes.
			for (int i=0; i<nLevels; i++) levels[i] = 0;
		}
		else {
			//lastFileIn was not null. Make the next file.
			//Note: lastFileIn is guaranteed to be nLevels deep
			//and start with a numeric sequence.
			File file = lastFileIn;
			for (int i=nLevels-1; i>=0; i--) {
				String name = file.getName();
				int k = name.indexOf("[");
				if (k != -1) name = name.substring(0,k);
				try { levels[i] = Integer.parseInt(name); }
				catch (Exception ex) {
					logger.debug("Found filename which cannot be parsed as an integer: "+name, ex);
				}
				file = file.getParentFile();
			}
			//levels now contains the integer names at each level.
			//Now move to the next file integers.
			for (int i=nLevels-1; i>=0; i--) {
				levels[i]++;
				if (levels[i] < maxSize) break;
				if (i > 0) levels[i] = 0;
			}
		}
		return makeFileForLevels(levels, filename);
	}

	//Make a file out of a set of levels, starting at the root directory.
	private File makeFileForLevels(int[] levels, String filename) {
		//First make the directories
		File file = root;
		for (int i=0; i<nLevels-1; i++) {
			file = new File(file, makeName(levels[i], i));
		}
		//Now make the actual file
		String suffix = "";
		filename = filename.trim();
		if (!filename.equals("")) suffix = "["+filename+"]";
		return new File(file, makeName(levels[nLevels-1], nLevels-1) + suffix);
	}

	//Make a filename at a specific level, choosing a length
	//for the name based on the level.
	private String makeName(int k, int level) {
		int len = (level==0) ? topNameLength : subNameLength;
		String name = Integer.toString(k);
		if (name.length() >= len) return name;
		return zeroes.substring(0, len - name.length()) + name;
	}

	//A class to encapsulate a directory that contains queued data files.
	//This class is used for selecting output files from the queue.
	class ActiveDirectory {
		File dir;
		File[] files;
		int index;
		public ActiveDirectory(File dir) {
			this.dir = dir;
			files = dir.listFiles();
			Arrays.sort(files);
			index = 0;
		}
		public File getNextFile() {
			while (index < files.length) {
				if (files[index].isDirectory() || !files[index].exists()) index++;
				else return files[index++];
			}
			return null;
		}
	}

	//An implementation of java.io.FileFilter to return
	//only files which have numeric names.
	class NumericFileFilter implements FileFilter {
		boolean dirs;
		boolean files;
		public NumericFileFilter(boolean dirs, boolean files) {
			this.dirs = dirs;
			this.files = files;
		}
		public boolean accept(File file) {
			if ((files && file.isFile()) || (dirs && file.isDirectory())) {
				return (file.getName().replaceAll("[\\d\\.]","").length() == 0);
			}
			return false;
		}
	}

}