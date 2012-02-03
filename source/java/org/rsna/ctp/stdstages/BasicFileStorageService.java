/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import jdbm.RecordManager;
import jdbm.htree.HTree;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.StorageService;
import org.rsna.ctp.stdstages.storage.ImageQualifiers;
import org.rsna.util.FileUtil;
import org.rsna.util.JdbmUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class to store objects in a file system.
 */
public class BasicFileStorageService extends AbstractPipelineStage implements StorageService {

	static final Logger logger = Logger.getLogger(BasicFileStorageService.class);

	File lastFileStored = null;
	long lastTime = 0;
	boolean returnStoredFile = true;
	boolean logDuplicates = false;
	File lastFileIn;
	FileFilter dirsOnly;
	FileFilter filesOnly;
	int nLevels = 0;
	int maxSize = 0;
	List<ImageQualifiers> qualifiers = null;
	int subNameLength;
	int topNameLength = 10;
	String zeroes = "0000000000000000";
    RecordManager recman = null;
    HTree index = null;
    int totalCount = 0;
    int acceptedCount = 0;
    int duplicateCount = 0;

	/**
	 * Construct a BasicFileStorageService.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public BasicFileStorageService(Element element) {
		super(element);
		returnStoredFile = !element.getAttribute("returnStoredFile").toLowerCase().equals("no");
		logDuplicates = element.getAttribute("logDuplicates").toLowerCase().equals("yes");
		nLevels = StringUtil.getInt(element.getAttribute("nLevels"));
		maxSize = StringUtil.getInt(element.getAttribute("maxSize"));
		nLevels = Math.max(nLevels, 3);
		maxSize = Math.max(maxSize, 200);
		qualifiers = getJPEGQualifiers(element);
		lastFileIn = null;
		dirsOnly = new NumericFileFilter(true,false);
		filesOnly = new NumericFileFilter(false,true);
		subNameLength = Integer.toString(this.maxSize).length();
		if (root == null) logger.error(name+": No root directory was specified.");
		String indexPath = element.getAttribute("index").trim();
		if (indexPath.equals(""))
			logger.error(name+": No index directory was specified.");
		else {
			File indexDir = new File(indexPath);
			indexDir.mkdirs();
			File indexFile = new File(indexDir, "__index");
			getIndex(indexFile.getPath());
		}
	}

	/**
	 * Stop the stage.
	 */
	public void shutdown() {
		//Commit and close the database
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
			}
			catch (Exception failed) {
				logger.warn("Unable to commit and close the index.");
			}
		}
		//Set stop so the isDown method will return the correct value.
		stop = true;
	}

	private void getIndex(String indexPath) {
		recman = JdbmUtil.getRecordManager( indexPath );
		index = JdbmUtil.getHTree( recman, "index" );
		if (index == null) logger.warn("Unable to load the index.");
	}

	//Get the list of qualifiers for jpeg child elements.
	private List<ImageQualifiers> getJPEGQualifiers(Element el) {
		LinkedList<ImageQualifiers> list = new LinkedList<ImageQualifiers>();
		Node child = el.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE)
					&& child.getNodeName().equals("jpeg")) {
				list.add(new ImageQualifiers((Element)child));
			}
			child = child.getNextSibling();
		}
		return list;
	}

	/**
	 * Get the File corresponding to a UID.
	 * @param uid the UID of the object to find.
	 * @return the File corresponding to the stored object with the requested UID,
	 * or null if no object corresponding to the UID is stored.
	 */
	public File getFileForUID(String uid) {
		try {
			String path = (String)index.get(uid);
			File file = new File(path);
			return file.getAbsoluteFile();
		}
		catch (Exception noPath) {
			logger.debug("Unable to find UID ("+uid+") in the index.");
			return null;
		}
	}

	/**
	 * Store an object if the object is of a type that the StorageService is
	 * configured to accept. If the StorageService is not configured to accept
	 * the object type, return the original object; otherwise, return either
	 * the passed object or the stored object depending on whether the
	 * returnStoredFile attribute was "no" or "yes".
	 * If the storage attempt fails, quarantine the input object if a quarantine
	 * was defined in the configuration, and return null to stop further processing.
	 * @param fileObject the object to process.
	 * @return either the original FileObject or the stored FileObject, or null
	 * if the object could not be stored.
	 */
	public FileObject store(FileObject fileObject) {

		//Count all the files
		totalCount++;

		//See if the StorageService is configured to accept the object type.
		if (!acceptable(fileObject)) return fileObject;

		//Count the accepted files
		acceptedCount++;

		//The object is acceptable; get a place to store it.
		//First, see if the object is already in the store;
		File savedFile;
		String uid = fileObject.getUID();
		String path = null;
		try { path = (String)index.get(uid); }
		catch (Exception notThere) { path = null; }

		if (path != null) {
			//The file already exists; overwrite it.
			savedFile = new File(path);
			duplicateCount++;
			if (logDuplicates) {
				String margin = "                                         ";
				String warning = "Duplicate SOPInstanceUID: "+uid + "\n";
				FileObject prevObject = FileObject.getInstance(savedFile);
				warning += margin + "Previous StudyUID:  "+prevObject.getStudyInstanceUID() + "\n";
				warning += margin + "Current StudyUID:   "+fileObject.getStudyInstanceUID() + "\n";
				if ((fileObject instanceof DicomObject) && (prevObject instanceof DicomObject)) {
					warning += margin + "Previous SeriesUID: "+((DicomObject)prevObject).getSeriesInstanceUID() + "\n";
					warning += margin + "Current SeriesUID:  "+((DicomObject)fileObject).getSeriesInstanceUID() + "\n";
				}
				warning += margin + "Storage location:   "+path;
				logger.warn(warning);
			}
		}
		else {
			//This is a new file, get the next open location and remember it in lastFileIn;
			lastFileIn = getNextFileIn(fileObject.getStandardExtension());
			savedFile = lastFileIn;
		}

		//At this point, savedFile points to where the file is to be stored.
		//Make sure the parent directory exists.
		File parent = savedFile.getAbsoluteFile().getParentFile();
		parent.mkdirs();

		//Store the object
		if (fileObject.copyTo(savedFile)) {
			//The store worked; update the index
			try {
				index.put(uid, savedFile.getPath());
				recman.commit();
			}
			catch (Exception ex) {
				logger.warn("Unable to update the index for "+uid+" ("+savedFile.getAbsolutePath()+")", ex);
			}
			if (returnStoredFile) fileObject = FileObject.getInstance(savedFile);
			makeJPEGs(fileObject, savedFile);
		}
		else {
			if (quarantine != null) quarantine.insert(fileObject);
			return null;
		}

		lastFileStored = fileObject.getFile();
		lastTime = System.currentTimeMillis();
		return fileObject;
	}

	private void makeJPEGs(FileObject fileObject, File storedFile) {
		//Create any required JPEG images
		if ((fileObject instanceof DicomObject) && (qualifiers.size() > 0)) {
			DicomObject dob = (DicomObject)fileObject;
			int nFrames = Math.max(dob.getNumberOfFrames(), 1);
			if (dob.isImage()) {
				for (ImageQualifiers q : qualifiers) {
					int frame = q.getSelectedFrames(nFrames);
					//There are two fundamental situations:
					//1. If an individual frame has been specified in the qualifiers,
					//   then just make that JPEG, but don't include the frame
					//   index in the name. The reason for not including the frame
					//   index is that it wouldn't be possible for an external
					//   system (like NBIA) to guess the name.
					//2. If the qualifiers require all the frames to be saved,
					//   then make all the JPEGs and include the index in the name.
					//   To make it easier to sequence through the names, don't
					//   put leading zeroes in the frame index part of the name.
					if (frame < 0) {
						//Save all the frames
						for (frame=0; frame<nFrames; frame++) {
							String jpegName = storedFile.getName() + q.toString(frame) + ".jpeg";
							File jpegFile = new File(storedFile.getParentFile(), jpegName);
							dob.saveAsJPEG(jpegFile, frame, q.maxWidth, q.minWidth, q.quality);
						}
					}
					else {
						//Just save the specified frame
						String jpegName = storedFile.getName() + q.toString() + ".jpeg";
						File jpegFile = new File(storedFile.getParentFile(), jpegName);
						dob.saveAsJPEG(jpegFile, frame, q.maxWidth, q.minWidth, q.quality);
					}
				}
			}
		}
	}

	//Get the next File into which to store a file.
	private File getNextFileIn(String suffix) {
		if (lastFileIn == null) lastFileIn = findLastFile(root, 0);
		int[] levels = new int[nLevels];
		if (lastFileIn == null) {
			//If lastFileIn is still null, then the store must
			//be empty, so we should start over with all zeroes.
			for (int i=0; i<nLevels; i++) levels[i] = 0;
		}
		else {
			//lastFileIn was not null. Make the next file.
			//Note: lastFileIn is guaranteed to be nLevels deep
			//and start with a numeric sequence.
			File file = lastFileIn;
			for (int i=nLevels-1; i>=0; i--) {
				String name = file.getName();
				int k = name.indexOf(".");
				if (k != -1) name = name.substring(0,k);
				try { levels[i] = Integer.parseInt(name); }
				catch (Exception ex) {
					logger.warn("Unparsable file name - this should never happen.", ex);
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
		File nextFile = makeFileForLevels(levels, suffix);
		//Update the index
		try { index.put("__lastFile", nextFile.getPath()); }
		catch (Exception ex) {
			logger.warn("Unable to update the __lastFile key for "+nextFile.getPath());
		}
		return nextFile;
	}

	//Make a file out of a set of levels, starting at the root directory.
	private File makeFileForLevels(int[] levels, String suffix) {
		//First make the directories
		File file = root;
		for (int i=0; i<nLevels-1; i++) {
			file = new File(file, makeName(levels[i], i));
		}
		//Now make the actual file
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

	//Find the last file in the store.
	//This method requires
	//that the file be located at the bottom level of the tree
	//and that the files and their directories are named numerically.
	private File findLastFile(File dir, int level) {

		//Start by looking in the index for the "__lastFile" key.
		//If the key is present, then return a File for it.
		try {
			String path = (String)index.get("__lastFile");
			if (path != null) return new File(path);
		}
		catch (Exception notThere) { }

		//If we get here, there was no __lastFile key in the index.
		//Search the directory tree to find the last entry.
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
				String name = file.getName();
				int k = name.indexOf(".");
				if (k >= 0) name = name.substring(0,k);
				return (name.replaceAll("[\\d\\.]","").length() == 0);
			}
			return false;
		}
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		StringBuffer sb = new StringBuffer();
		sb.append("<h3>"+name+"</h3>");
		sb.append("<table border=\"1\" width=\"100%\">");

		sb.append("<tr><td width=\"20%\">Files received for storage:</td>"
			+ "<td>" + totalCount + "</td></tr>");
		sb.append("<tr><td width=\"20%\">Files accepted for storage:</td>"
			+ "<td>" + acceptedCount + "</td></tr>");
		sb.append("<tr><td width=\"20%\">Duplicate files:</td>"
			+ "<td>" + duplicateCount + "</td></tr>");

		sb.append("<tr><td width=\"20%\">Last file stored:</td>");
		if (lastTime != 0) {
			sb.append("<td>"+lastFileStored+"</td></tr>");
			sb.append("<tr><td width=\"20%\">Last file stored at:</td>");
			sb.append("<td>"+StringUtil.getDateTime(lastTime,"&nbsp;&nbsp;&nbsp;")+"</td></tr>");
		}
		else sb.append("<td>No activity</td></tr>");
		sb.append("</table>");
		return sb.toString();
	}

}