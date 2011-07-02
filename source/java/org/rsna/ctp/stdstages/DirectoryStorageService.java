/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.stdstages.ObjectCache;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.StorageService;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A class to store objects in a directory system with no index.
 */
public class DirectoryStorageService extends AbstractPipelineStage implements StorageService {

	static final Logger logger = Logger.getLogger(DirectoryStorageService.class);

	File lastFileStored = null;
	long lastTime = 0;
	File lastFileIn;
    int totalCount = 0;
    int acceptedCount = 0;
    int storedCount = 0;
    boolean returnStoredFile = true;
    int[] tags = null;
    String cacheID = "";

	/**
	 * Construct a DirectoryStorageService for DicomObjects.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DirectoryStorageService(Element element) {
		super(element);
		returnStoredFile = !element.getAttribute("returnStoredFile").toLowerCase().equals("no");
		cacheID = element.getAttribute("cacheID").trim();
		String structure = element.getAttribute("structure").trim();
		if (!structure.equals("")) {
			String[] strings = structure.split("/");
			LinkedList<Integer> ints = new LinkedList<Integer>();
			for (String s : strings) {
				s = s.replaceAll("[\\[\\]\\(\\),\\s]", "");
				int tagInt = StringUtil.getHexInt(s);
				if (tagInt != 0) ints.add( new Integer(tagInt) );
				else logger.warn("Illegal tag value: \""+s+"\"");
			}
			Integer[] x = ints.toArray( new Integer[ ints.size() ] );
			tags = new int[ x.length ];
			for (int i=0; i<x.length; i++) tags[i] = x[i].intValue();
		}
		lastFileIn = null;
		if (root == null) logger.error(name+": No root directory was specified.");
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

		if (fileObject instanceof DicomObject) {
			DicomObject dob = (DicomObject)fileObject;

			//Count the accepted files
			acceptedCount++;

			//The object is acceptable; get a place to store it.
			File destDir = root;

			//If there is a tags array, get the storage hierarchy/
			//If there is a cache, get the cached object; otherwise,.
			//use the current object to obtain the hierarchy.
			//Store the object in a hierarchy based on the
			//values of the elements identified by the tags;
			//otherwise, store everything in the root directory.
			//Note: the current object is always the one being stored;
			//if the cached object is use, it is only used to define
			//the hierarchy.
			if (tags != null) {
				//If there is not cache, use the current object.
				if (!cacheID.equals("")) {
					PipelineStage cache = Configuration.getInstance().getRegisteredStage(cacheID);
					if ((cache != null) && (cache instanceof ObjectCache)) {
						FileObject fob = ((ObjectCache)cache).getCachedObject();
						if (fob instanceof DicomObject) dob = (DicomObject)fob;
					}
				}
				//Now construct the child directories under the root.
				for (int tag : tags) {
					String eValue = dob.getElementValue(tag);
					String value = eValue.replaceAll("[\\\\/\\s]", "").trim();
					if (value.equals("")) value = "UNKNOWN";
					destDir = new File(destDir, value);
				}
			}

			//At this point, destDir points to where the object is to be stored.
			boolean ok;
			File savedFile = new File(destDir, dob.getSOPInstanceUID());
			destDir.mkdirs();
			if (returnStoredFile)
				ok = fileObject.moveTo(savedFile);
			else
				ok = fileObject.copyTo(savedFile);

			//If the object was successfully saved, count it.
			if (ok) {
				storedCount++;
				lastFileStored = savedFile;
				lastTime = System.currentTimeMillis();
			}

			//If anything went wrong, quarantine the object and abort.
			//Note: we can't do this with an else because ok may
			//have been changed in the clause above.
			if (!ok) {
				if (quarantine != null) quarantine.insert(fileObject);
				return null;
			}
		}
		return fileObject;
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
