/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.StorageService;
import org.rsna.ctp.servlets.DecipherServlet;
import org.rsna.ctp.stdstages.storage.AjaxServlet;
import org.rsna.ctp.stdstages.storage.FileSystem;
import org.rsna.ctp.stdstages.storage.FileSystemManager;
import org.rsna.ctp.stdstages.storage.GuestListServlet;
import org.rsna.ctp.stdstages.storage.ImageQualifiers;
import org.rsna.ctp.stdstages.storage.StorageMonitor;
import org.rsna.ctp.stdstages.storage.StorageServlet;
import org.rsna.ctp.stdstages.storage.StoredObject;
import org.rsna.ctp.stdstages.storage.Study;
import org.rsna.server.HttpServer;
import org.rsna.server.ServletSelector;
import org.rsna.server.User;
import org.rsna.server.Users;
import org.rsna.server.UsersXmlFileImpl;
import org.rsna.servlets.LoginServlet;
import org.rsna.servlets.Servlet;
import org.rsna.servlets.UserServlet;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class to store objects in a file system.
 */
public class FileStorageService extends AbstractPipelineStage implements StorageService {

	static final Logger logger = Logger.getLogger(FileStorageService.class);

	File lastFileStored = null;
	long lastTime = 0;
	boolean returnStoredFile = true;
	FileSystemManager fsm = null;
	int fsNameTag = 0;
	boolean autoCreateUser = false;
	int port = 0;
	boolean ssl = false;
	String type;
	int timeDepth = 0;
	boolean requireAuthentication = false;
	boolean setReadable = false;
	boolean setWritable = false;
	boolean acceptDuplicateUIDs = true;
	List<ImageQualifiers> qualifiers = null;
	StorageMonitor storageMonitor = null;
	HttpServer httpServer = null;

	/**
	 * Construct a FileStorageService.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public FileStorageService(Element element) {
		super(element);
		type = element.getAttribute("type").toLowerCase();
		timeDepth = StringUtil.getInt(element.getAttribute("timeDepth"));
		returnStoredFile = !element.getAttribute("returnStoredFile").toLowerCase().equals("no");
		requireAuthentication = element.getAttribute("requireAuthentication").toLowerCase().equals("yes");
		setReadable = element.getAttribute("setWorldReadable").toLowerCase().equals("yes");
		setWritable = element.getAttribute("setWorldWritable").toLowerCase().equals("yes");
		qualifiers = getJPEGQualifiers(element);
		fsNameTag = StringUtil.getHexInt(element.getAttribute("fsNameTag"));
		autoCreateUser = element.getAttribute("auto-create-user").toLowerCase().equals("yes");
		acceptDuplicateUIDs = !element.getAttribute("acceptDuplicateUIDs").toLowerCase().equals("no");
		port = StringUtil.getInt(element.getAttribute("port"));
		ssl = element.getAttribute("ssl").equals("yes");
		if (root == null) logger.error(name+": No root directory was specified.");
		fsm = FileSystemManager
				.getInstance(
					root,
					type,
					requireAuthentication,
					acceptDuplicateUIDs,
					setReadable,
					setWritable,
					qualifiers);
		startServer();
		startStorageMonitor();
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
	 * Stop the pipeline stage.
	 */
	public void shutdown() {
		if (httpServer != null) httpServer.stopServer();
		stop = true;
	}

	/**
	 * Get the value of the fsNameTag, which may be zero, indicating that that no tag has been specified.
	 * @return fsNameTag
	 */
	public int getFSNameTag() {
		return fsNameTag;
	}

	/**
	 * Get the URL corresponding to a stored object. This method is used to find the URL of
	 * a stored object corresponding to a queued object.
	 * @param fileObject the object.
	 * @param filename the name by which the object is stored in the FileStorage Service.
	 * This name may be different from the name contained in the File of the FileObject
	 * because the supplied FileObject may be a copy of the object which was queued under
	 * another name.
	 * @return the URL corresponding to the stored object, or null if no object corresponding
	 * to the supplied object is stored.
	 */
	public StoredObject getStoredObject(FileObject fileObject, String filename) {
		try {
			//Get the file system name for the object.
			String fsName = "";
			if ((fileObject instanceof DicomObject) && (fsNameTag != 0)) {
				byte[] bytes = ((DicomObject)fileObject).getElementBytes(fsNameTag);
				fsName = new String(bytes);
				fsName = fsName.trim();
			}
			//Now get the FileSystem. Note: the second argument in the getFileSystem
			//call is false to prevent creating a FileSystem if the FileSystem for
			//this object doesn't exist.
			FileSystem fs = fsm.getFileSystem(fsName,false);
			if (fs == null) return null;
			//Get the Study
			String studyName = Study.makeStudyName(fileObject.getStudyUID());
			Study study = fs.getStudyByUID(studyName);
			if (study == null) return null;
			if (study.contains(filename)) {
				//The object exists in the study. Get the context for the URL.
				String context = "http://"
								+ Configuration.getInstance().getIPAddress()
									+ ":" + port
										+ "/storage"
											+ "/" + fs.getName();
				//Construct the URL of the object.
				String url = study.getStudyURL(context) + "/" + filename;
				//Get the File pointing to the stored object.
				File file = study.getFile(filename);
				return new StoredObject(file, url);
			}
			else return null;
		}
		catch (Exception ex) { return null;}
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

		//See if the StorageService is configured to accept the object type.
		if (!acceptable(fileObject)) return fileObject;

		//The object is acceptable.
		try {
			//Get the file system for the object.
			String fsName = "";
			if ((fileObject instanceof DicomObject) && (fsNameTag != 0)) {
				byte[] bytes = ((DicomObject)fileObject).getElementBytes(fsNameTag);
				fsName = new String(bytes);
				fsName = fsName.trim();
			}
			FileSystem fs = fsm.getFileSystem(fsName);
			//Store the object
			File storedFile = fs.store(fileObject);
			if (returnStoredFile) fileObject = FileObject.getInstance(storedFile);
			if (autoCreateUser) createUserForFileSystem(fs);
		}
		catch (Exception ex) {
			if (quarantine != null) quarantine.insert(fileObject);
			return null;
		}

		lastFileStored = fileObject.getFile();
		lastTime = System.currentTimeMillis();
		return fileObject;
	}

	private void createUserForFileSystem(FileSystem fs) {
		String name = fs.getName();
		if (!name.startsWith("__")) {
			Users users = Users.getInstance();
			if ((users != null) && (users instanceof UsersXmlFileImpl)) {
				User user = users.getUser(name);
				if (user == null) {
					user = new User(name, users.convertPassword(name));
					((UsersXmlFileImpl)users).addUser(user);
				}
			}
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

	//Start a web server on the root if the port is non-zero.
	private void startServer() {
		if (port > 0) {
			//Install the home page if necessary
			File index = new File(root, "index.html");
			if (!index.exists()) {
				File exampleIndex = new File("examples/example-storage-index.html");
				FileUtil.copy(exampleIndex, index);
			}

			//Create the ServletSelector
			ServletSelector selector = new ServletSelector(root, requireAuthentication);
			selector.addServlet("login",	LoginServlet.class);
			selector.addServlet("user",		UserServlet.class);
			selector.addServlet("storage",	StorageServlet.class);
			selector.addServlet("guests",	GuestListServlet.class);
			selector.addServlet("ajax",		AjaxServlet.class);
			selector.addServlet("decipher",	DecipherServlet.class);

			//Instantiate the server
			httpServer = null;
			try { httpServer = new HttpServer(ssl, port, selector); }
			catch (Exception ex) {
				logger.error(
					"Unable to instantiate the HTTP Server for "
					+name
					+" on port "
					+port, ex);
			}

			//Start it if possible
			if (httpServer != null) httpServer.start();
		}
	}

	//Start a thread to delete studies older than the specified timeDepth..
	private void startStorageMonitor() {
		if (timeDepth > 0) {
			storageMonitor = new StorageMonitor(root, timeDepth);
			storageMonitor.start();
		}
	}

}