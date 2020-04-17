/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.awt.image.*;
import javax.imageio.*;
import javax.imageio.stream.*;
import java.io.*;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.StorageService;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.stdstages.ObjectCache;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A class to store images from DicomObjects in a directory system with no index.
 */
public class PictureStorageService extends AbstractPipelineStage implements StorageService, Scriptable {

	static final Logger logger = Logger.getLogger(PictureStorageService.class);

	File lastFileStored = null;
	long lastTime = 0;
	File lastFileIn;
    volatile int totalCount = 0;
    volatile int acceptedCount = 0;
    volatile int storedCount = 0;
    int filenameTag = 0;
    String[] dirs = null;
    String cacheID = "";
	File dicomScriptFile = null;
	String defaultString = "UNKNOWN";
	String whitespaceReplacement = "_";
	String format = "jpeg";
	int maxWidth = 1024;
	int maxPathLength = 260;
	String filter = "[^a-zA-Z0-9\\[\\]\\(\\)\\^\\.\\-_,;]+";
	boolean logDuplicates = false;

	/**
	 * Construct a PictureStorageService for DicomObjects.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public PictureStorageService(Element element) {
		super(element);

		filenameTag = DicomObject.getElementTag(element.getAttribute("filenameTag"));

		//Set up for capturing the structure
		cacheID = element.getAttribute("cacheID").trim();
		String structure = element.getAttribute("structure").trim();
		if (!structure.equals("")) {
			dirs = structure.split("/");
		}

		String temp = element.getAttribute("defaultString").trim();;
		if (!temp.equals("")) defaultString = temp;

		temp = element.getAttribute("whitespaceReplacement").trim();
		if (!temp.equals("")) whitespaceReplacement = temp;

		temp = element.getAttribute("format").trim();
		if (!temp.equals("")) format = temp;
		
		maxWidth = StringUtil.getInt(element.getAttribute("maxWidth").trim(), 0);
		if (maxWidth <= 0) maxWidth = Integer.MAX_VALUE;
		
		//Get the script files
		dicomScriptFile = getFilterScriptFile(element.getAttribute("dicomScript"));

		lastFileIn = null;
		if (root == null) logger.error(name+": No root directory was specified.");

		maxPathLength = System.getProperty("os.name", "").toLowerCase().contains("windows") ? 260 : 255;
	}

	/**
	 * Get the script files.
	 * @return the script files used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] { dicomScriptFile, null, null };
	}

	/**
	 * Store an image from a DicomObject. 
	 * If the storage attempt fails, log the error but allow the object to continue down the pipe.
	 * @param fileObject the object to process.
	 * @return either the original FileObject.
	 */
	public synchronized FileObject store(FileObject fileObject) {
		logger.debug("File received for storage: "+fileObject.getFile());

		//Count all the files
		totalCount++;

		if (acceptable(fileObject) && checkFilter(fileObject)) {
			logger.debug("Object received for storage: "+fileObject.getClass().getName());

			//Get a place to store the object.
			File destDir = root;
			String name = fileObject.getSOPInstanceUID() + "." + format;

			if (fileObject instanceof DicomObject) {
				DicomObject dob = (DicomObject)fileObject;
				logger.debug("name: "+name);
				if (dob.isDICOMDIR() && (name == null)) name = dob.getSOPInstanceUID();

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
						dir = dir.replaceAll("[\\\\/\\s]+", whitespaceReplacement).trim();
						dir = dir.replaceAll(filter, "");
						while (dir.endsWith(".")) dir = dir.substring(0, dir.length()-1);
						dir = dir.trim();
						if (dir.equals("")) dir = defaultString;
						destDir = new File(destDir, dir);
					}
				}
				//See if we are to use a filename from the object
				if (filenameTag != 0) {
					name = dob.getElementValue(filenameTag, name) + "." + format;
				}
				if ((name == null) || name.equals("")) name = fileObject.getClassName();
				name = name.replaceAll("[\\\\/\\s]+", whitespaceReplacement).trim();
				name = name.replaceAll(filter, "");
				while (name.endsWith(".")) name = name.substring(0, name.length()-1);
				name = name.trim();
				logger.debug("...filtered filename: "+name);

				//Count the accepted objects
				acceptedCount++;

				//At this point, destDir points to where the object is to be stored.
				if (destDir.getAbsolutePath().length() > maxPathLength) {
					logger.warn("File path is too long for directory creation:\n"+destDir);
					return null;
				}

				//Make the directory and all its parents
				destDir.mkdirs();

				//Store the file
				File savedFile = new File(destDir, name);
				int pathLength = savedFile.getAbsolutePath().length();
				logger.debug("...absolute path length: "+pathLength);
				if (pathLength > maxPathLength) {
					logger.warn("File path is too long for storage:\n"+savedFile);
					return null;
				}
				else {
					savedFile.delete();
					try {
						BufferedImage bi = dob.getScaledBufferedImage(0, maxWidth, 128);
						saveAs(savedFile, format, bi, 100);
						storedCount++;
					}
					catch (Exception unable) {
						logger.info("Unable to save picture file: " + savedFile, unable);
					}
				}
			}
		}
		lastFileOut = lastFileStored;
		lastTimeOut = lastTime;
		return fileObject;
	}
	
	public void saveAs(File file, String format, BufferedImage rgbImage, int quality) {
		FileImageOutputStream out = null;
		ImageWriter writer = null;
		try {
			// Encode the image and write it in the specified file.
			writer = ImageIO.getImageWritersByFormatName(format).next();
			ImageWriteParam iwp = writer.getDefaultWriteParam();
			if (quality >= 0) {
				quality = Math.min(quality, 100);
				float fQuality = ((float)quality) / 100.0F;
				iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				iwp.setCompressionQuality(fQuality);
			}
			out = new FileImageOutputStream(file);
			writer.setOutput(out);
			IIOImage image = new IIOImage(rgbImage, null, null);
			writer.write(null, image, iwp);
		}
		catch (Exception ex) { logger.warn("Unable to save the image as a " + format, ex); }
		finally {
			if (out != null) {
				try { out.flush(); out.close(); }
				catch (Exception ignore) { }
			}
			if (writer != null) writer.dispose();
		}
	}

	private boolean checkFilter(FileObject fileObject) {
		if (fileObject instanceof DicomObject) {
			return ((DicomObject)fileObject).matches(dicomScriptFile);
		}
		return true; //Don't filter on other object types.
	}

	private String replace(String string, DicomObject dob) {
		try {
			
			String singleHexTag = "[\\[\\(][0-9a-fA-F]{1,4}(\\[[^\\]]*\\])??[,]?[0-9a-fA-F]{1,4}[\\]\\)]";
			String singleKeywordTag = "\\{[A-Z][^\\}]*\\}";
			String singleTag = "(("+singleHexTag+")|("+singleKeywordTag+"))";
			Pattern pattern = Pattern.compile( singleTag + "(::"+singleTag+")*" );

			Matcher matcher = pattern.matcher(string);
			StringBuffer sb = new StringBuffer();
			while (matcher.find()) {
				String group = matcher.group();
				String repl = getElementValue(dob, group);
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

	private String getElementValue(DicomObject dob, String group) {
		String value = "";
		try { value = dob.getElementString(group); }
		catch (Exception ex) { logger.debug("......exception processing: "+group); }
		return value;
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public synchronized String getStatusHTML() {
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

	/**
	 * Get the list of links for display on the summary page.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public synchronized LinkedList<SummaryLink> getLinks(User user) {
		LinkedList<SummaryLink> links = super.getLinks(user);
		if (allowsAdminBy(user)) {
			String qs = "?p="+pipeline.getPipelineIndex()+"&s="+stageIndex;
			if (dicomScriptFile != null) {
				links.addFirst( new SummaryLink("/script"+qs+"&f=0", null, "Edit the Stage DICOM Filter Script File", false) );
			}
		}
		return links;
	}

}
