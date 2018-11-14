/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.zip.*;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.Path;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;

/**
 * A Servlet which provides web access to the studies stored in a FileStorageService.
 */
public class StorageServlet extends Servlet {

	static final Logger logger = Logger.getLogger(StorageServlet.class);

	/**
	 * Construct a StorageServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public StorageServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page displaying the list of studies
	 * stored in the FileStorageService.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {

		//Get the FileSystemManager.
		FileSystemManager fsm = FileSystemManager.getInstance(root);
		if (fsm == null) {
			//There is no FileSystemManager for this root.
			res.setResponseCode( res.notfound );
			res.send();
		}

		//Figure out what was requested from the length of the path.
		Path path = new Path(req.path);
		switch (path.length()) {

			case 1: listFileSystems(req, res, fsm);
					return;

			case 2:	listStudies(req, res, path, fsm);
					return;

			case 3: listObjects(req, res, path, fsm);
					return;

			case 4: getObject(req, res, path, fsm);
					return;
		}

		//Not one of those; return NotFound
		res.setResponseCode( res.notfound );
		res.send();
	}

	//List all the file systems.
	private void listFileSystems(HttpRequest req, HttpResponse res, FileSystemManager fsm) {
		List<String> fsList = fsm.getFileSystemsFor(req.getUser());
		if (fsList.size() == 1) {
			//Only one FileSystem, just list its studies.
			//First, make a Path to the FileSystem.
			Path path = new Path("/storage/" + fsList.get(0));
			listStudies(req, res, path, fsm);
		}
		else {
			//Zero or more than one FileSystem; list them.
			StringBuffer sb = new StringBuffer();
			sb.append("<html><head>");
			sb.append("<title>Storage Service</title>");
			sb.append("<link rel=\"stylesheet\" href=\"/BaseStyles.css\" type=\"text/css\"/>");
			sb.append("<style>");
			sb.append("th,td{padding-left:10px; padding-right:10px;}");
			sb.append("td{background-color:white;}");
			sb.append("</style>");
			sb.append("</head><body>");
			sb.append("<center><h1>File System List</h1></center>");
			sb.append("<center>");
			if (fsList.size() > 0) {
				sb.append("<table border=\"1\">");
				//Insert a table row for each file system here
				for (String fsName : fsList) {
					sb.append("<tr>");
					sb.append("<td>");
					sb.append("<a href=\"/storage/"+fsName+"\">"+fsName+"</a>");
					sb.append("</td>");
					sb.append("</tr>");
				}
				sb.append("</table>");
			}
			else sb.append("<p>The storage service is empty.</p>");
			sb.append("</center>");
			sb.append("</body></html>");
			res.write(sb.toString());
			res.setContentType("html");
			res.disableCaching();
			res.send();
		}
	}

	//List all studies in one file system.
	private void listStudies(HttpRequest req, HttpResponse res, Path path, FileSystemManager fsm) {
		String admin = req.userHasRole("admin") ? "yes" : "no";
		String dir = (fsm.getExportDirectory() != null) ? "yes" : "no";
		String delete = req.userHasRole("delete") ? "yes" : "no";
		String key = req.getParameter("key");
		if (key == null) key = "storageDate";
		String fsName = path.element(1);
		FileSystem fs = fsm.getFileSystem(fsName, false);

		if (req.userHasRole("delete") && req.hasParameter("deleteAll")) {
			fs.deleteAll();
		}

		String page = "Access to the requested File System is not allowed.";
		if ((fs != null) && fs.allowsAccessBy(req.getUser())) {
			File xslFile = new File("pages/list-studies.xsl");
			if (fs != null) {
				String[] params = new String[] {
					"context", "/storage/"+fsName,
					"dir",		dir,
					"delete",	delete,
					"key",		key
				};
				try { page = XmlUtil.getTransformedText(fs.getIndex(), xslFile, params); }
				catch (Exception e) {
					logger.warn("Unable to get the study list page.");
				}
			}
		}
		res.write(page);
		res.setContentType("html");
		res.disableCaching();
		res.send();
	}

	//List all the objects in one study.
	private void listObjects(HttpRequest req, HttpResponse res, Path path, FileSystemManager fsm) {
		File xslFile;
		User user = req.getUser();
		String fsName = path.element(1);
		String studyUID = path.element(2);
		FileSystem fs = fsm.getFileSystem(fsName, false);
		Study study = null;
		String page = "Access to the requested Study is not allowed.";
		res.disableCaching();
		if ((fs != null) && fs.allowsAccessBy(user)) {
			study = fs.getStudyByUID(studyUID);
			if (study != null) {
				String format = req.getParameter("format", "list");
				if (!format.equals("zip") && !format.equals("delete") & !format.equals("dir")) {
					if (format.equals("list"))
						xslFile = new File("pages/list-objects.xsl");
					else
						xslFile = new File("pages/view-objects.xsl");
					String[] params = new String[] {
						"context",	"/storage/"+fsName
					};
					try { page = XmlUtil.getTransformedText(study.getIndex(), xslFile, params); }
					catch (Exception e) { logger.debug("Unable to get the object listing page."); }
					res.write(page);
					res.setContentType("html");
					res.send();
					return;
				}
				else if (format.equals("zip")) {
					File zipFile = null;
					try {
						String accNumber = lastN(study.getAccessionNumber(), 4);
						if (!accNumber.equals("")) accNumber = "-" + accNumber;
						String ptName = study.getPatientName().replaceAll("[^0-9a-zA-Z\\-.]+","_");
						String ptID = lastN(study.getPatientID(), 8);
						String studyDate = study.getStudyDate();
						String studyName = ptID+"-"+studyDate+accNumber;
						String folderPath = studyName+"/";
						zipFile = new File(root, studyName+".zip");
						File studyDir = study.getDirectory();
						if (zipDirectory(studyDir, zipFile, studyName, folderPath)) {
							res.write(zipFile);
							res.setContentType("zip");
							res.setContentDisposition(zipFile);
							res.send();
							//Handle the delete if present
							String delete = req.getParameter("delete");
							if ((delete != null) && (delete.equals("yes")) &&
								(user != null) &&
								(user.getUsername().equals(fs.getName()) || user.hasRole("delete"))) {
								fs.deleteStudyByUID(studyUID);
							}
							zipFile.delete();
							return;
						}
						else logger.warn("Unable to create the zip file for export ("+zipFile+")");
					}
					catch (Exception ex) { logger.warn("Internal server error in zip export.", ex); }
					res.setResponseCode( res.servererror );
					res.send();
					return;
				}
				else if (format.equals("dir")) {
					File expdir = fsm.getExportDirectory();
					if ((expdir != null) && (user.hasRole("admin") || user.hasRole("export"))) {
						try {
							expdir.mkdirs();
							File studyDir = study.getDirectory();
							File[] files = studyDir.listFiles();
							for (File file : files) {
								String name = file.getName();
								if (!name.startsWith("__")) {
									File outfile = new File(expdir, name);
									FileUtil.copy(file, outfile);
								}
							}
							//Redirect to the level above.
							String subpath = path.subpath(0, path.length()-2);
							res.redirect(subpath);
							return;
						}
						catch (Exception ex) {
							logger.warn("Internal server error in directory export.", ex);
							res.setResponseCode( res.servererror );
							res.send();
						}
					}
					res.setResponseCode( res.forbidden ); //Not authorized
					res.send();
					return;
				}
				else if (format.equals("delete")) {
					if ((user != null) &&
						(user.getUsername().equals(fs.getName()) || user.hasRole("delete"))) {
						logger.debug("Delete request received from "+user.getUsername());
						logger.debug("...FileSystem: "+fs.getName());
						logger.debug("...StudyUID:   "+studyUID);
						fs.deleteStudyByUID(studyUID);
						//Redirect to the level above.
						String subpath = path.subpath(0, path.length()-2);
						res.redirect(subpath);
						return;
					}
					res.setResponseCode( res.forbidden ); //Not authorized
					res.send();
					return;
				}
			}
		}
		res.write(page);
		res.send();
	}
	
	private String lastN(String s, int n) {
		if (s.length() > n) s = s.substring(s.length() - n);
		return s;
	}
	
	//Zip a study directory
	private boolean zipDirectory(File studyDir, File zipFile, String studyName, String folderPath) {
		int k;
		try {
			//Get the streams
			FileOutputStream fout = new FileOutputStream(zipFile);
			ZipOutputStream zout = new ZipOutputStream(fout);
			
			//Make tables to track entries
			Hashtable<String,Integer> entryNames = new Hashtable<String,Integer>();
			Hashtable<String,String> dcmEntryNames = new Hashtable<String,String>();

			//Put in the files, skipping .db, .lg, and __index.xml files
			File[] files = studyDir.listFiles();
			Arrays.sort(files, new FileComparator());
			for (File file : files) {
				String fn = file.getName();
				if (file.exists() && file.isFile() && !fn.endsWith(".db") && !fn.endsWith(".lg") && !fn.equals("__index.xml")) {
					
					//Get the file name without the extension
					String origName = file.getName();
					k = origName.lastIndexOf(".");
					if (k >= 0) origName = origName.substring(0, k);
					
					//Figure out what kind of object the file is and make an entry name for it.
					FileObject fob = FileObject.getInstance(file);
					String entryName = "";
					boolean isJPEG = false;
					if (fob instanceof DicomObject) {
						DicomObject dob = (DicomObject)fob;
						int sNumber = StringUtil.getInt(dob.getSeriesNumber());
						int aNumber = StringUtil.getInt(dob.getAcquisitionNumber());
						int iNumber = StringUtil.getInt(dob.getInstanceNumber());
						if (dob.isImage()) {
							entryName = String.format("S%d-A%d-%04d", sNumber, aNumber, iNumber);
							dcmEntryNames.put(origName, entryName);
						}
						else if (dob.isKIN()) entryName = "KOS";
						else if (dob.isSR()) entryName = "SR";
						else if (dob.isPS()) entryName = "PS";
						else entryName = "OTHER";
					}
					else if (fob instanceof XmlObject) entryName = "XML";
					else if (fob instanceof ZipObject) entryName = "ZIP";
					else if (fob.getFile().getName().endsWith(".jpeg")) {
						if (origName.endsWith(".dcm")) {
							origName = origName.substring(0, origName.length()-4);
							entryName = dcmEntryNames.get(origName);
							if (entryName == null) entryName = "JPEG";
						}
						else entryName = "JPEG";
						isJPEG = true;
					}
					else entryName = "FILE";
					
					//Add the extension
					if (!isJPEG) entryName += fob.getStandardExtension();
					else entryName += ".jpeg";

					//Put in the folder path
					entryName = folderPath + entryName;
					
					//Add an index if this entry already exists
					Integer n = entryNames.get(entryName);
					if (n == null) entryNames.put(entryName, new Integer(0));
					else {
						int nint = n.intValue() + 1;
						entryNames.put(entryName, new Integer(nint));
						k = entryName.lastIndexOf(".");
						if (k != -1) entryName = entryName.substring(0,k)+"["+nint+"]"+entryName.substring(k);
						else entryName += "["+nint+"]";
					}
						
					//Now add it to the zip file
					byte[] buffer = new byte[10000];
					int bytesread;
					FileInputStream fin = new FileInputStream(file);
					ZipEntry ze = new ZipEntry(entryName);
					zout.putNextEntry(ze);
					while ((bytesread = fin.read(buffer)) > 0) zout.write(buffer,0,bytesread);
					zout.closeEntry();
					fin.close();
				}
			}
			zout.close();
			return true;
		}
		catch (Exception ex) { 
			logger.warn("Unable to zip directory "+studyDir, ex);
			return false;
		}
	}
	
	class FileComparator implements Comparator<File> {
		public FileComparator() { }
		public int compare(File f1, File f2) {
			return ext(f1.getName()).compareTo(ext(f2.getName()));
		}
		private String ext(String name) {
			int k = name.lastIndexOf(".") + 1;
			if (k > 0) return name.substring(k);
			return "";
		}
	}

	//Get an object.
	private void getObject(HttpRequest req, HttpResponse res, Path path, FileSystemManager fsm) {
		boolean admin = req.userHasRole("admin");
		String fsName = path.element(1);
		String studyUID = path.element(2);
		FileSystem fs = fsm.getFileSystem(fsName, false);
		Study study;
		FileObject fo;
		if ( (fs != null)
				&& fs.allowsAccessBy(req.getUser())
					&& ((study=fs.getStudyByUID(studyUID)) != null)
						&& ((fo=study.getObject(path.element(3))) != null) ) {

			if (fo instanceof DicomObject) {
				//This is a DicomObject; see how we are to return it.
				//There are three options:
				//  1. as a DICOM element list (triggered by format=list)
				//  2. as a JPEG image (triggered by format=jpeg)
				//  3. as a file download (if no format parameter is present)
				String format = req.getParameter("format", "");
				DicomObject dob = (DicomObject)fo;

				if (format.equals("list")) {
					//Return an element listing page
					res.write( dob.getElementTablePage( req.userHasRole("admin") ) );
					res.setContentType("html");
				}
				else if (format.equals("jpeg")) {
					//Return a JPEG image
					//Get the qualifiers.
					ImageQualifiers q = new ImageQualifiers(req);

					//Make the name of the jpeg
					String jpegName = fo.getFile().getName() + q.toString() + ".jpeg";

					//See if the jpeg file already exists.
					File jpegFile = new File(fo.getFile().getParentFile(), jpegName);

					if (!jpegFile.exists()) {
						//No, create it
						q.maxWidth = Math.min(q.maxWidth, 1024);
						if (dob.saveAsJPEG(jpegFile, 0, q.maxWidth, q.minWidth, q.quality) == null) {
							//Error, return a code
							res.setResponseCode( res.servererror );
							res.send();
							return;
						}
					}
					res.write(jpegFile);
					res.setContentType("jpeg");
					res.disableCaching();
				}
				else {
					res.write(fo.getFile());
					res.setContentType("dcm");
				}
			}
			else {
				File file = fo.getFile();
				res.write(file);
				res.setContentType(file);
			}
		}
		else res.setResponseCode( res.notfound );
		res.send();
	}
}

