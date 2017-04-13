/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.BufferedOutputStream;;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
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
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.stdstages.ObjectCache;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;

/**
 * A class to extract encapsulated PDFs from DicomObjects and store them in a directory system with no index.
 */
public class PDFStorageService extends AbstractPipelineStage implements StorageService, Scriptable {

	static final Logger logger = Logger.getLogger(PDFStorageService.class);

	File lastFileStored = null;
	long lastTime = 0;
	File lastFileIn;
    int totalCount = 0;
    int acceptedCount = 0;
    int storedCount = 0;
    String[] dirs = null;
    String cacheID = "";
	File dicomScriptFile = null;
	String defaultString = "UNKNOWN";
	String whitespaceReplacement = "_";
	int maxPathLength = 260;
	String filter = "[^a-zA-Z0-9\\[\\]\\(\\)\\^\\.\\-_,;]+";
	boolean logDuplicates = false;

	/**
	 * Construct a PDFStorageService for DicomObjects.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public PDFStorageService(Element element) {
		super(element);
		logDuplicates = element.getAttribute("logDuplicates").trim().toLowerCase().equals("yes");

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

		//Get the script file
		dicomScriptFile = FileUtil.getFile(element.getAttribute("dicomScript").trim(), "examples/example-filter.script");

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
	 * Store an object. If the storage attempt fails, quarantine the input object if a quarantine
	 * was defined in the configuration, and return null to stop further processing.
	 * @param fileObject the object to process.
	 * @return either the original FileObject, or null if the object could not be stored.
	 */
	public FileObject store(FileObject fileObject) {

		//Count all the files
		totalCount++;

		if ((fileObject instanceof DicomObject) && checkFilter(fileObject)) {
			DicomObject dob = (DicomObject)fileObject;
			String mimeType = dob.getElementValue("MIMETypeOfEncapsulatedDocument");
			if (mimeType.toLowerCase().equals("application/pdf")) {

				//Get a place to store the object.
				File destDir = root;
				String name = fileObject.getSOPInstanceUID();

				//If there is a dirs array, get the storage hierarchy.
				//If there is a cache, get the cached object; otherwise,
				//use the current object to obtain the hierarchy.
				//Store the object in a hierarchy based on the
				//values of the elements identified by the tags;
				//otherwise, store everything in the root directory.
				//Note: the current object is always the one being stored;
				//the cached object is only used to define the hierarchy.
				if (dirs != null) {
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
						if (dir.equals("")) dir = defaultString;
						destDir = new File(destDir, dir);
					}
				}

				name = dob.getElementValue("DocumentTitle", name);
				if ((name == null) || name.equals("")) name = fileObject.getClassName() + ".pdf";
				name = name.replaceAll("[\\\\/\\s]+", whitespaceReplacement).trim();
				name = name.replaceAll(filter, "");
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

				//Fix the filename if necessary
				name = getDuplicateName(destDir, name, ".pdf");

				//Store the file
				File tempFile = new File(destDir, name+".partial");
				File savedFile = new File(destDir, name);
				int pathLength = savedFile.getAbsolutePath().length();
				logger.debug("...absolute path length: "+pathLength);
				if (pathLength > maxPathLength) {
					logger.warn("File path is too long for storage:\n"+savedFile);
					return null;
				}
				else {
					if (extractPDF(dob, tempFile) && tempFile.renameTo(savedFile)) {
						//The object was successfully saved, count it.
						storedCount++;
						lastFileStored = savedFile;
						lastTime = System.currentTimeMillis();
						logger.debug("...file stored successfully: "+savedFile);
					}
					//If anything went wrong, quarantine the object and abort.
					else {
						logger.warn("Unable to save "+savedFile);
						tempFile.delete();
						if (quarantine != null) quarantine.insert(fileObject);
						return null;
					}
				}
			}
		}
		lastFileOut = lastFileStored;
		lastTimeOut = lastTime;
		return fileObject;
	}
	
	private boolean extractPDF(DicomObject dob, File file) {
		try {
			Dataset ds = dob.getDataset();
			DcmElement de = ds.get( dob.getElementTag("EncapsulatedDocument") );
			ByteBuffer bb = de.getByteBuffer();
			byte[] b = bb.array();
			BufferedOutputStream bos =
				new BufferedOutputStream(new FileOutputStream(file));
			bos.write(b, 0, b.length);
			bos.flush();
			bos.close();
			return true;
		}
		catch (Exception unable) { 
			logger.debug("Unable to write PDF", unable);
			return false;
		}
	}

	static final Pattern bracketPattern = Pattern.compile("\\[([0-9]+)\\]");
	private String getDuplicateName(File dir, String name, String ext) {
		boolean hasExtension = name.toLowerCase().endsWith(ext.toLowerCase());
		String nameNoExt = name;
		if (hasExtension) nameNoExt = name.substring( 0, name.length() - ext.length() );
		File[] files = dir.listFiles(new NameFilter(nameNoExt));
		if (files.length == 0) return name;
		if (logDuplicates) {
			logger.info("Found "+files.length+" duplicate files for: "+nameNoExt);
			for (File file: files) logger.debug("   "+file);
		}
		int n = 0;
		for (File file : files) {
			Matcher matcher = bracketPattern.matcher(file.getName());
			if (matcher.find()) {
				n = Math.max( n, StringUtil.getInt(matcher.group(1)) );
			}
		}
		return nameNoExt + "["+(n+1)+"]" + (hasExtension ? ext : "");
	}

	//An implementation of java.io.FileFilter to return
	//only files whose names begin with a specified string.
	class NameFilter implements FileFilter {
		String name;
		public NameFilter(String name) {
			this.name = name;
		}
		public boolean accept(File file) {
			if (file.isFile()) {
				String fn = file.getName();
				return fn.equals(name) || fn.startsWith(name + ".") || fn.startsWith(name + "[");
			}
			return false;
		}
	}

	private boolean checkFilter(FileObject fileObject) {
		if (fileObject instanceof DicomObject) {
			return (dicomScriptFile == null) || ((DicomObject)fileObject).matches(dicomScriptFile);
		}
		return false; //Don't accept other object types.
	}

	private String replace(String string, DicomObject dob) {
		try {
			String singleTag = "[\\[\\(][0-9a-fA-F]{0,4}[,]?[0-9a-fA-F]{1,4}[\\]\\)]";
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
		try {
			int[] tags = DicomObject.getTagArray(group);
			value = dob.getElementString(tags);
		}
		catch (Exception ex) { logger.debug("......exception processing: "+group); }
		return value;
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

	/**
	 * Get the list of links for display on the summary page.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public LinkedList<SummaryLink> getLinks(User user) {
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
