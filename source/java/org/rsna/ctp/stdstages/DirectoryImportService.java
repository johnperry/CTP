/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.ImportService;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An ImportService that monitors a directory. This is an import service
 * which allows a user to manually drop files into its directory. The
 * import service processes the files and either deletes them or quarantines
 * at the end, depending onwhether there is a quarantine directory defined
 * in the configuration file element for the stage.
 */
public class DirectoryImportService extends AbstractPipelineStage implements ImportService {

	static final Logger logger = Logger.getLogger(DirectoryImportService.class);

	static final int defaultAge = 5000;
	static final int minAge = 1000;
	long age;
	String fsName = null;
	int fsNameTag = 0;

	/**
	 * Class constructor; creates a new instance of the ImportService.
	 * @param element the configuration element.
	 */
	public DirectoryImportService(Element element) throws Exception {
		super(element);
		age = StringUtil.getInt(element.getAttribute("minAge"));
		if (age < minAge) age = defaultAge;

		//See if there is a FileSystem name
		fsName = element.getAttribute("fsName");
		if (fsName == null) fsName = fsName.trim();
		if (fsName.equals("")) fsName = null;
		fsNameTag = StringUtil.getHexInt(element.getAttribute("fsNameTag"),fsNameTag);
	}

	/**
	 * Get the next object available for processing.
	 * @return the next object available, or null if no object is available.
	 */
	public FileObject getNextObject() {
		File file;
		long maxLM = System.currentTimeMillis() - age;
		while ((file = findFile(root, maxLM)) != null) {

			FileObject fileObject = FileObject.getInstance(file);
			if (acceptable(fileObject)) {
				fileObject.setStandardExtension();
				fileObject = setFSName(fileObject);
				lastFileOut = fileObject.getFile();
				lastTimeOut = System.currentTimeMillis();
				return fileObject;
			}

			//If we get here, this import service does not accept
			//objects of this type. Try to quarantine the
			//object, and if that fails, delete it.
			if (quarantine != null)  quarantine.insert(fileObject);
			else fileObject.getFile().delete();
		}
		return null;
	}

	//Set the FileSystem name in the object if we can.
	private FileObject setFSName(FileObject fo) {
		try {
			if (fo instanceof DicomObject) {
				if ((fsName != null) && (fsNameTag != 0)) {
					//Unfortunately, we have to parse the object again
					//in order to be able to save it once we modify it.
					DicomObject dob = new DicomObject(fo.getFile(), true); //leave the stream open
					File dobFile = dob.getFile();

					//Modify the specified element.
					//If the fsName is "@filename", use the name of the file;
					//otherwise, use the value of the fsName attribute.
					if (fsName.equals("@filename")) {
						String name = dobFile.getName();
						name = name.substring(0, name.length()-4).trim();
						dob.setElementValue(fsNameTag, name);
					}
					else dob.setElementValue(fsNameTag, fsName);

					//Save the modified object
					File tFile = File.createTempFile("TMP-",".dcm",dobFile.getParentFile());
					dob.saveAs(tFile, false);
					dob.close();
					dob.getFile().delete();

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

	//Walk a directory tree until we find a file with a
	//last-modified-time earlier than a specified time.
	private File findFile(File dir, long maxLM) {
		File[] files = dir.listFiles();
		if (files.length == 0) {
			//The directory is empty.
			//Delete it if it is not the root.
			if (!dir.equals(root)) dir.delete();
			return null;
		}
		//Something is in the directory; check it out.
		for (int i=0; i<files.length; i++) {
			if (files[i].isFile() && (files[i].lastModified() < maxLM)) return files[i];
			if (files[i].isDirectory()) return findFile(files[i], maxLM);
		}
		return null;
	}

	/**
	 * Release a file from the import directory. Note that other stages in the
	 * pipeline may have moved the file, so it is possible that the file will
	 * no longer exist. This method only deletes the file if it is still in the
	 * tree under the root directory.
	 * @param file the file to be released.
	 */
	public void release(File file) {
		if ((file != null) && file.exists()) {
			//Only delete if the path includes the root.
			if (file.getAbsolutePath().startsWith(root.getAbsolutePath())) {
				 file.delete();
			}
		}
	}

	/**
	 * Get HTML text displaying the active status of the stage.
	 * This method does not call the method in the parent class
	 * because there is no time associated with the last file
	 * that was received.
	 * @return HTML text displaying the active status of the stage.
	 */
	public String getStatusHTML() {
		StringBuffer sb = new StringBuffer();
		sb.append("<h3>"+name+"</h3>");
		sb.append("<table border=\"1\" width=\"100%\">");
		sb.append("<tr><td width=\"20%\">Queue size:</td>");
		sb.append("<td>" + FileUtil.getFileCount(root) + "</td></tr>");
		sb.append("<tr><td width=\"20%\">Last file supplied:</td>");
		if (lastTimeOut != 0) {
			sb.append("<td>"+lastFileOut+"</td></tr>");
			sb.append("<tr><td width=\"20%\">Last file supplied at:</td>");
			sb.append("<td>"+StringUtil.getDateTime(lastTimeOut,"&nbsp;&nbsp;&nbsp;")+"</td></tr>");
		}
		else sb.append("<td>No activity</td></tr>");
		sb.append("</table>");
		return sb.toString();
	}

}