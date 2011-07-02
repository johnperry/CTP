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
import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.archivers.tar.*;
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
import org.rsna.ctp.stdstages.archive.FileSource;
import org.rsna.util.FileUtil;
import org.rsna.util.SerializerUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An ImportService that copies files from a directory tree. This is
 * a specialized import service for processing files from a large archive
 * without modifying the archive itself. It checkpoints its progress,
 * allowing the program to stop and start without the import service
 * losing its place. It is designed to walk through the directory tree
 * once. It does not detect changes in the tree which occur in parts of
 * the tree that have already been walked.
 */
public class ArchiveImportService extends AbstractPipelineStage implements ImportService {

	static final Logger logger = Logger.getLogger(ArchiveImportService.class);

	static final int defaultAge = 5000;
	static final int minAge = 1000;
	long age;
	String fsName = null;
	int fsNameTag = 0;
	File treeRoot = null;
	boolean expandTARs;
	FileSource fileSource = null;
	File active = null;
	boolean setFileSystemName;
	File lastArchiveFileFound = null;

	/**
	 * Class constructor; creates a new instance of the ImportService.
	 * @param element the configuration element.
	 */
	public ArchiveImportService(Element element) throws Exception {
		super(element);
		age = StringUtil.getInt(element.getAttribute("minAge"));
		if (age < minAge) age = defaultAge;

		//Get the root of the archive tree
		String treePath = element.getAttribute("treeRoot");
		if (!treePath.trim().equals("")) treeRoot = new File(treePath);
		else logger.warn("treeRoot attribute is missing.");

		expandTARs = element.getAttribute("expandTARs").trim().equals("yes");

		//See if there is a FileSystem name
		fsName = element.getAttribute("fsName");
		if (fsName == null) fsName = fsName.trim();
		if (fsName.equals("")) fsName = null;
		fsNameTag = StringUtil.getHexInt(element.getAttribute("fsNameTag"),fsNameTag);
		setFileSystemName = (fsName != null) && (fsNameTag != 0);

		if ((root != null) && (treeRoot != null)) {
			active = new File(root, "active");
			active.mkdirs();
			fileSource = FileSource.getInstance(treeRoot, root);
			logger.info("FileSource instantiated: starting file count = "+fileSource.getFileCount());
		}
	}

	/**
	 * Get the next object available for processing.
	 * @return the next object available, or null if no object is available.
	 */
	public FileObject getNextObject() {
		File file;
		long maxLM = System.currentTimeMillis() - age;
		while ((file = findFile(maxLM)) != null) {

			FileObject fileObject = FileObject.getInstance(file);
			if (acceptable(fileObject)) {
				if (!fileObject.getFile().getName().toLowerCase().endsWith(".tar")) {
					//Only change the extension if it isn't a TAR file.
					fileObject.setStandardExtension();
				}
				if (setFileSystemName) fileObject = setFSName(fileObject);
				lastFileOut = fileObject.getFile();
				lastTimeOut = System.currentTimeMillis();
				return fileObject;
			}

			//If we get here, the import service does not accept
			//objects of this type. Try to quarantine the
			//object, and if that fails, delete it.
			if (quarantine != null) quarantine.insert(fileObject);
			else fileObject.getFile().delete();
		}
		return null;
	}

	//Set the FileSystem name in the object if we can.
	private FileObject setFSName(FileObject fo) {
		try {
			if ((fo instanceof DicomObject) && setFileSystemName) {
				//Unfortunately, we have to parse the object again
				//in order to be able to save it once we modify it.
				DicomObject dob = new DicomObject(fo.getFile(), true); //leave the stream open
				dob.setElementValue(fsNameTag, fsName);
				File dobFile = dob.getFile();
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
		catch (Exception unableToSetFSName) {
			logger.warn("Unable to set the FileSystem name: \""+fsName+"\"");
			logger.warn("                               in: "+fo.getFile());
		}
		return fo;
	}

	//Get files from the FileSource until we find one with a
	//last-modified-time earlier than a specified time.
	private File findFile(long maxLM) {
		File[] files = active.listFiles();
		if (files.length == 0) {
			//The active directory is empty; try to reload it from the FileSource.
			File file;
			while ((file = fileSource.getNextFile()) != null) {
				lastArchiveFileFound = file;
				if (file.lastModified() < maxLM) {
					if (!expandTARs || !file.getName().endsWith(".tar")) {
						//It isn't a TAR or we aren't expanding TARs;
						//just copy the file and return it.
						File dest = new File(active, file.getName());
						FileUtil.copy(file, dest);
						return dest;
					}
					else {
						//It's a TAR and we are expanding TARs;
						//expand the file into the active directory,
						//which right now is empty.
						expandTAR(file, active);
						files = active.listFiles();
						if (files.length == 0) return null;
						return files[0];
					}
				}
			}
			return null;
		}
		return files[0];
	}

	//Expand a tar, writing its files into a destination directory.
	private void expandTAR(File tar, File dir) {
        try {
        	TarArchiveInputStream tais =
        			new TarArchiveInputStream(
								new FileInputStream(tar));
			TarArchiveEntry tae;
			while ((tae =  tais.getNextTarEntry()) != null) {
				if (!tae.isDirectory()) {
					FileOutputStream fos =
							new FileOutputStream(
									new File(dir, tae.getName()));
					byte[] buf = new byte[4096];
					long count = tae.getSize();
					while (count > 0) {
						int n = tais.read(buf, 0, buf.length);
						fos.write(buf, 0, n);
						count -= n;
					}
					fos.flush();
					fos.close();
				}
			}
			tais.close();
		}
		catch (Exception ex) {
			logger.warn("Unable to expand: \""+tar+"\"", ex);
		}
	}

	/**
	 * Release a file from the active directory. Note that other stages in the
	 * pipeline may have moved the file, so it is possible that the file will
	 * no longer exist. This method only deletes the file if it is still in the
	 * tree under the active directory.
	 * @param file the file to be released.
	 */
	public void release(File file) {
		if ((file != null) && file.exists()) {
			//Only delete if the path includes the active directory.
			if (file.getAbsolutePath().startsWith(active.getAbsolutePath())) {
				 file.delete();
			}
		}
		//We have just released a file. Checkpoint the FileSource,
		//which now points to the next file to retrieve.
		SerializerUtil.serialize(new File(root, FileSource.checkpointName), fileSource);
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

		sb.append("<tr><td width=\"20%\">Archive traversal:</td>");
		if (fileSource.isDone())
			sb.append("<td>complete</td></tr>");
		else sb.append("<td>in process</td></tr>");

		sb.append("<tr><td width=\"20%\">Archive files supplied:</td>");
			sb.append("<td>"+fileSource.getFileCount()+"</td></tr>");

		sb.append("<tr><td width=\"20%\">Last archive file found:</td>");
		if (lastArchiveFileFound != null)
			sb.append("<td>"+lastArchiveFileFound+"</td></tr>");
		else sb.append("<td>No activity</td></tr>");

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