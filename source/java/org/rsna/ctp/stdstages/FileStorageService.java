/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
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
import org.rsna.ctp.servlets.SummaryLink;
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
	int[] fsNameTag;
	boolean autoCreateUser = false;
	int port = 0;
	boolean ssl = false;
	String type;
	int timeDepth = 0;
	boolean requireAuthentication = false;
	boolean setReadable = false;
	boolean setWritable = false;
	boolean acceptDuplicateUIDs = true;
	boolean skipNonImageObjects = false;
	List<ImageQualifiers> qualifiers = null;
	StorageMonitor storageMonitor = null;
	HttpServer httpServer = null;
	File exportDirectory = null;

	/**
	 * Construct a FileStorageService.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public FileStorageService(Element element) {
		super(element);
		type = element.getAttribute("type").trim().toLowerCase();
		timeDepth = StringUtil.getInt(element.getAttribute("timeDepth").trim());
		returnStoredFile = !element.getAttribute("returnStoredFile").trim().toLowerCase().equals("no");
		String expDirString = element.getAttribute("exportDirectory").trim();
		if (!expDirString.equals("")) exportDirectory = new File(expDirString);
		requireAuthentication = element.getAttribute("requireAuthentication").trim().toLowerCase().equals("yes");
		setReadable = element.getAttribute("setWorldReadable").trim().toLowerCase().equals("yes");
		setWritable = element.getAttribute("setWorldWritable").trim().toLowerCase().equals("yes");
		qualifiers = getJPEGQualifiers(element);
		fsNameTag = DicomObject.getTagArray(element.getAttribute("fsNameTag").trim());
		autoCreateUser = element.getAttribute("autoCreateUser").trim().toLowerCase().equals("yes");
		acceptDuplicateUIDs = !element.getAttribute("acceptDuplicateUIDs").trim().toLowerCase().equals("no");
		skipNonImageObjects = element.getAttribute("skipNonImageObjects").trim().toLowerCase().equals("yes");
		port = StringUtil.getInt(element.getAttribute("port").trim());
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
					exportDirectory,
					qualifiers);
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
	 * Start the pipeline stage. This method is called by the
	 * Pipeline after all the stages have been constructed.
	 */
	public synchronized void start() {
		logger.info(name+" root: "+root.getAbsolutePath());
		startServer();
		startStorageMonitor();
	}

	/**
	 * Stop the pipeline stage.
	 */
	public synchronized void shutdown() {
		if (httpServer != null) httpServer.shutdown();
		super.shutdown();
	}

	/**
	 * Get the export directory specified in the configuration.
	 * @return the export directory
	 */
	public File getExportDirectory() {
		return exportDirectory;
	}

	/**
	 * Get the server port specified in the configuration.
	 * @return the stage's server port, or zero if none is specified.
	 */
	public int getPort() {
		return port;
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
			//Get the FileSystem. Note: the second argument in the getFileSystem
			//call is false to prevent creating a FileSystem if the FileSystem for
			//this object doesn't exist.
			String fsName = getFSName(fileObject);
			FileSystem fs = fsm.getFileSystem(fsName, false);
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
	public synchronized FileObject store(FileObject fileObject) {

		//See if the StorageService is configured to accept the object type.
		if (acceptable(fileObject) && !skip(fileObject)) {
			//The object is acceptable.
			try {
				//Get the file system for the object.
				String fsName = getFSName(fileObject);
				FileSystem fs = fsm.getFileSystem(fsName);
				//Store the object
				File storedFile = fs.store(fileObject);
				if (returnStoredFile) fileObject = FileObject.getInstance(storedFile);
				if (autoCreateUser) createUserForFileSystem(fs);
			}
			catch (Exception ex) {
				logger.debug("Unable to store "+fileObject.getFile().getName(), ex);
				if (quarantine != null) quarantine.insert(fileObject);
				return null;
			}
			lastFileStored = fileObject.getFile();
			lastTime = System.currentTimeMillis();
		}
		
		lastFileOut = fileObject.getFile();
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	//Test whether to skip an object
	private boolean skip(FileObject fileObject) {
		return skipNonImageObjects
			&& (fileObject instanceof DicomObject)
				&& !((DicomObject)fileObject).isImage();		
	}

	//Get the file system name for the object.
	private String getFSName(FileObject fileObject) throws Exception {
		String fsName = "";
		if ((fileObject instanceof DicomObject) && (fsNameTag.length != 0)) {
			DicomObject dob = (DicomObject)fileObject;
			byte[] bytes = dob.getElementBytes(fsNameTag);
			fsName = new String(bytes);
			fsName = fsName.trim();
		}
		return fsName;
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
	 * Get the list of links for display on the summary page.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public LinkedList<SummaryLink> getLinks(User user) {
		LinkedList<SummaryLink> links = super.getLinks(user);
		if (port > 0) {
			boolean isAuthenticated = (user != null);
			boolean admin = allowsAdminBy(user);
			boolean canView = !requireAuthentication || (isAuthenticated && user.hasRole("read"));
			if (isAuthenticated) links.addFirst( new SummaryLink(":"+port+"/guests", null, "Manage the Guest List", false) );
			List<String> fsList = fsm.getFileSystemsFor(user);
			for (String fsName : fsList) {
				FileSystem fs = fsm.getFileSystem(fsName);
				if (fs.getNumberOfStudies() > 0) {
					links.addFirst( new SummaryLink(":"+port+"/storage/"+fsName, null, "View Stored Studies for "+fsName, false) );
				}
			}
		}
		return links;
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public synchronized String getStatusHTML() {
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
			try {
				httpServer = new HttpServer(ssl, port, 4, selector);
				httpServer.start();
			}
			catch (Exception ex) {
				httpServer = null;
				logger.error(
					"Unable to instantiate the HTTP Server for "
					+name
					+" on port "
					+port, ex);
			}
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