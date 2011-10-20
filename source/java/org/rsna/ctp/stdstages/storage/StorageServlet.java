/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.Path;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
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
		if (fsm.getSize() == 1) {
			//Only one FileSystem, just list its studies.
			//First, make a Path to the FileSystem.
			Path path = new Path("/storage/" + fsList.get(0));
			listStudies(req, res, path, fsm);
		}
		else {
			//More than one FileSystem; list them.
			StringBuffer sb = new StringBuffer();
			sb.append("<html><head>");
			sb.append("<title>Storage Service</title>");
			sb.append("<style>");
			sb.append("body {background-color:#c6d8f9;}");
			sb.append("th,td{padding-left:10px; padding-right:10px;}");
			sb.append("td{background-color:white;}");
			sb.append("</style>");
			sb.append("</head><body>");
			sb.append("<center><h1>File System List</h1></center>");
			sb.append("<center>");
			if (fsList.size() > 0) {
				sb.append("<table border=\"1\">");
				//Insert a table row for each file system here
				Iterator<String> lit = fsList.iterator();
				while (lit.hasNext()) {
					String fsName = lit.next();
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
		String delete = req.userHasRole("delete") ? "yes" : "no";
		String key = req.getParameter("key");
		if (key == null) key = "storageDate";
		String fsName = path.element(1);
		FileSystem fs = fsm.getFileSystem(fsName, false);
		String page = "Access to the requested File System is not allowed.";
		if ((fs != null) && fs.allowsAccessBy(req.getUser())) {
			File xslFile = new File("pages/list-studies.xsl");
			if (fs != null) {
				String[] params = new String[] {
					"context", "/storage/"+fsName,
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
	File xslFile;
	private void listObjects(HttpRequest req, HttpResponse res, Path path, FileSystemManager fsm) {
		User user = req.getUser();
		String fsName = path.element(1);
		String studyUID = path.element(2);
		FileSystem fs = fsm.getFileSystem(fsName, false);
		Study study = null;
		String page = "Access to the requested Study is not allowed.";
		if ((fs != null) && fs.allowsAccessBy(user)) {
			study = fs.getStudyByUID(studyUID);
			if (study != null) {
				String format = req.getParameter("format");
				format = (format==null) ? "list" : format;
				if (!format.equals("zip") && !format.equals("delete")) {
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
					res.disableCaching();
					res.send();
					return;
				}
				else if (format.equals("zip")) {
					File zipFile = null;
					try {
						zipFile = File.createTempFile("ZIP-",".zip",root);
						File studyDir = study.getDirectory();
						if (FileUtil.zipDirectory(studyDir, zipFile)) {
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
					}
					catch (Exception ex) { logger.debug("Internal server error in zip export.", ex); }
					res.setResponseCode( res.servererror );
					res.send();
					return;
				}
				else if (format.equals("delete")) {
					if ((user != null) &&
						(user.getUsername().equals(fs.getName()) || user.hasRole("delete"))) {
						fs.deleteStudyByUID(studyUID);
						//Redirect to the level above.
						String subpath = path.subpath(0, path.length()-2);
						res.setResponseCode(302);
						res.setHeader("Location", subpath);
						res.send();
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
						&& ((fo = study.getObject(path.element(3))) != null) ) {

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

