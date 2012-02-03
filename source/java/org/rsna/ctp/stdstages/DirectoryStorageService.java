/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.StorageService;
import org.rsna.ctp.stdstages.ObjectCache;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A class to store objects in a directory system with no index.
 */
public class DirectoryStorageService extends AbstractPipelineStage implements StorageService, Scriptable {

	static final Logger logger = Logger.getLogger(DirectoryStorageService.class);

	File lastFileStored = null;
	long lastTime = 0;
	File lastFileIn;
    int totalCount = 0;
    int acceptedCount = 0;
    int storedCount = 0;
    boolean returnStoredFile = true;
    boolean setStandardExtensions = false;
    String[] dirs = null;
    String cacheID = "";
	File dicomScriptFile = null;
	File xmlScriptFile = null;
	File zipScriptFile = null;
	String defaultString = "UNKNOWN";
	String whitespaceReplacement = "_";

	/**
	 * Construct a DirectoryStorageService for DicomObjects.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DirectoryStorageService(Element element) {
		super(element);

		returnStoredFile = !element.getAttribute("returnStoredFile").toLowerCase().equals("no");

		//Set up for capturing the structure
		cacheID = element.getAttribute("cacheID").trim();
		String structure = element.getAttribute("structure").trim();
		if (!structure.equals("")) {
			dirs = structure.split("/");
		}

		//See if we are to apply extensions to the stored files.
		setStandardExtensions = element.getAttribute("setStandardExtensions").toLowerCase().equals("yes");

		String temp = element.getAttribute("defaultString").trim();;
		if (!temp.equals("")) defaultString = temp;

		temp = element.getAttribute("whitespaceReplacement").trim();
		if (!temp.equals("")) whitespaceReplacement = temp;

		//See if there are scripts, and if so, get the files
		dicomScriptFile = FileUtil.getFile(element.getAttribute("dicomScript"), "examples/example-filter.script");
		xmlScriptFile = FileUtil.getFile(element.getAttribute("xmlScript"), "examples/example-filter.script");
		zipScriptFile = FileUtil.getFile(element.getAttribute("zipScript"), "examples/example-filter.script");

		lastFileIn = null;
		if (root == null) logger.error(name+": No root directory was specified.");
	}

	/**
	 * Get the script files.
	 * @return the script files used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] { dicomScriptFile, xmlScriptFile, zipScriptFile };
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

		if (acceptable(fileObject) && checkFilter(fileObject)) {

			//Get a place to store the object.
			File destDir = root;

			if (fileObject instanceof DicomObject) {
				DicomObject dob = (DicomObject)fileObject;

				//If there is a dirs array, get the storage hierarchy.
				//If there is a cache, get the cached object; otherwise,
				//use the current object to obtain the hierarchy.
				//Store the object in a hierarchy based on the
				//values of the elements identified by the tags;
				//otherwise, store everything in the root directory.
				//Note: the current object is always the one being stored;
				//the cached object is only used to define the hierarchy.
				//Note: DICOMDIR objects are always stored in the root
				//directory because the structure can't apply to both
				//images and DICOMDIRs and the root is as convenient a
				//place as any for them.
				if ((dirs != null) && !dob.isDICOMDIR()) {
					//Get the object to be used to determine the storage hierarchy.

					//If there is no cached object, then use the current object.
					DicomObject xdob = dob;

					//If there is a cached object, then use that one instead.
					if (!cacheID.equals("")) {
						PipelineStage cache = Configuration.getInstance().getRegisteredStage(cacheID);
						if ((cache != null) && (cache instanceof ObjectCache)) {
							FileObject fob = ((ObjectCache)cache).getCachedObject();
							if (fob instanceof DicomObject) xdob = (DicomObject)fob;
						}
					}

					//Now construct the child directories under the root.
					for (String dir : dirs) {
						dir = replace(dir, xdob);
						dir = dir.replaceAll("[\\\\/\\s]", whitespaceReplacement).trim();
						if (dir.equals("")) dir = defaultString;
						destDir = new File(destDir, dir);
					}
				}
			}

			else {
				//All other object types have already passed the filter, and they are
				//always stored in the root, so we are now ready to store.
			}

			//Count the accepted objects
			acceptedCount++;

			//At this point, destDir points to where the object is to be stored.
			//Make a name for the stored object.
			String name = fileObject.getSOPInstanceUID();
			if (setStandardExtensions) name += fileObject.getStandardExtension();

			//Store the object.
			destDir.mkdirs();
			File savedFile = new File(destDir, name);
			if (fileObject.copyTo(savedFile)) {
				//The object was successfully saved, count it.
				storedCount++;
				if (returnStoredFile) fileObject = FileObject.getInstance(savedFile);
				lastFileStored = savedFile;
				lastTime = System.currentTimeMillis();
			}
			//If anything went wrong, quarantine the object and abort.
			else {
				if (quarantine != null) quarantine.insert(fileObject);
				return null;
			}
		}
		return fileObject;
	}

	private boolean checkFilter(FileObject fileObject) {
		if (fileObject instanceof DicomObject) {
			return (dicomScriptFile == null) || ((DicomObject)fileObject).matches(dicomScriptFile).getResult();
		}
		else if (fileObject instanceof XmlObject) {
			return (xmlScriptFile == null) || ((XmlObject)fileObject).matches(xmlScriptFile);
		}
		else if (fileObject instanceof ZipObject) {
			return (zipScriptFile == null) || ((ZipObject)fileObject).matches(zipScriptFile);
		}
		return true; //Don't filter on other object types.
	}

	private String replace(String string, DicomObject dob) {
		try {
			Pattern pattern = Pattern.compile("[\\[\\(][0-9a-fA-F]{0,4}[,]?[0-9a-fA-F]{1,4}[\\]\\)]");
			Matcher matcher = pattern.matcher(string);
			StringBuffer sb = new StringBuffer();
			while (matcher.find()) {
				String group = matcher.group();
				String key = group.substring(1, group.length()-1).replace(",","");
				int tag = Integer.parseInt(key, 16);
				String repl = dob.getElementValue(tag);
				if (repl.equals("")) repl = defaultString;
				matcher.appendReplacement(sb, repl);
			}
			matcher.appendTail(sb);
			return sb.toString();
		}
		catch (Exception ex) {
			logger.warn(ex);
			return string;
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
		sb.append("<tr><td width=\"20%\">Files actually stored:</td>"
			+ "<td>" + storedCount + "</td></tr>");
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
