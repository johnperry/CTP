/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.FileObject;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class to encapsulate Study objects in a single FileSystem.
 */
public class FileSystem {

	static final Logger logger = Logger.getLogger(FileSystem.class);

	String name;
	File dir;
	String type;
	boolean requireAuthentication;
	boolean acceptDuplicateUIDs;
	boolean setReadable;
	boolean setWritable;
	List<ImageQualifiers> qualifiers;
	int dirNameLength = 0;
	File indexFile = null;
	Document indexDoc = null;
	Hashtable<String,Study> uidTable = null;
	GuestList guestList = null;

	/**
	 * Create a FileSystem within a specific root directory.
	 * @param root the base directory of all FileSystems.
	 * @param name the name of this FileSystem. This name is filtered
	 * by the makeFSName method before use.
	 * @param type the way the directory tree is to be structured:
	 * <ul>
	 * <li>"none" - all study directories appear as first
	 * generation children of the root directory (e.g., root/studyName).
	 * <li>"year" - all study directories appear as children of a year
	 * directory (e.g., root/year/studyName).
	 * <li>"month" - all study directories appear as children of a year/month
	 * directory (e.g., root/year/month/studyName).
	 * <li>"week" - all study directories appear as children of a year/week
	 * directory (e.g., root/year/week/studyName).
	 * <li>"day" - all study directories appear as children of a year/day
	 * directory (e.g., root/year/day/studyName).
	 * </ul>
	 * @param requireAuthentication true if accesses to this FileSystem require authentication;
	 * false otherwise.
	 * @param setReadable true if files and directories in this FileSystem are to be world readable.
	 * @param setWritable true if files and directories in this FileSystem are to be world writable.
	 * @param qualifiers the list of qualifiers for the creation of JPEG images when a
	 * DICOM image is stored.
	 */
	public FileSystem(File root,
					  String name,
					  String type,
					  boolean requireAuthentication,
					  boolean acceptDuplicateUIDs,
					  boolean setReadable,
					  boolean setWritable,
					  List<ImageQualifiers> qualifiers) {
		this.name = makeFSName(name);
		this.dir = new File(root, this.name);
		this.type = type.toLowerCase();
		this.requireAuthentication = requireAuthentication;
		this.acceptDuplicateUIDs = acceptDuplicateUIDs;
		this.setReadable = setReadable;
		this.setWritable = setWritable;
		this.qualifiers = qualifiers;
		dir.mkdirs();
		dirNameLength = dir.getAbsolutePath().length();
		indexFile = new File(dir,"__index.xml");
//		if (setReadable) dir.setReadable(true,false); //Java 1.6
//		if (setWritable) dir.setWritable(true,false); //Java 1.6
		guestList = new GuestList(dir);
	}

	/**
	 * Determine whether a specific user can access this FileSystem.
	 * The rules are applied in this order, with the first matching rule winning:
	 * <ol>
	 * <li>If authentication is not required, all users are granted access, even ones
	 * who are not authenticated (e.g. user == null).
	 * <li>If authentication is required:
	 * <ul>
	 * <li>If the user is not authenticated (user == null) access is denied.
	 * <li>If the FileSystem name is "__default", access is granted.
	 * <li>If the username is the same as the name of the FileSystem, access is granted.
	 * <li>If the user has the "read" privilege, access is granted.
	 * <li>If the username appears in the guest list, access is granted.
	 * <li>In all other cases, access is denied.
	 * </ul>
	 * </ol>
	 * @param user the User requesting access.
	 * @return true if this FileSystem can be accessed by the specified user; false otherwise.
	 */
	public boolean allowsAccessBy(User user) {
		if (!requireAuthentication) return true;
		if (user == null) return false;
		if (name.equals("__default")) return true;
		if (user.getUsername().equals(name)) return true;
		if (user.hasRole("read")) return true;
		if (guestList.includes(user)) return true;
		return false;
	}

	/**
	 * Get the name of this FileSystem.
	 * @return the name of this FileSystem.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the root directory for this FileSystem.
	 * @return the root directory.
	 */
	public File getRootDirectory() {
		return dir;
	}

	/**
	 * Get the GuestList for this FileSystem.
	 * @return the GuestList for this FileSystem.
	 */
	public GuestList getGuestList() {
		return guestList;
	}

	/**
	 * Get the setReadable parameter for this FileSystem.
	 * @return the setReadable parameter for this FileSystem.
	 */
	public boolean getSetReadable() {
		return setReadable;
	}

	/**
	 * Get the setWritable parameter for this FileSystem.
	 * @return the setWritable parameter for this FileSystem.
	 */
	public boolean getSetWritable() {
		return setWritable;
	}

	/**
	 * Get the ImageQualifiers list for this FileSystem.
	 * @return the ImageQualifiers list for this FileSystem.
	 */
	public List<ImageQualifiers> getImageQualifiersList() {
		return qualifiers;
	}

	/**
	 * Filter a name to create one which is acceptable for use as a FileSystem name.
	 * The filter removes all characters except underscore, a-z, A-Z, and 0-9.
	 * If the result is an empty string, it assigns the name "__default".
	 * @param name a proposed name for a FileSystem.
	 * @return the filtered name.
	 */
	public static String makeFSName(String name) {
		if (name == null) name = "";
		else name = name.replace(".","_").replaceAll("[^_\\w-]","");
		if (name.equals("")) name = "__default";
		return name;
	}

	/**
	 * Store a FileObject in this FileSystem. If the Study for the FileObject does
	 * not exist, create it and add it to the index of the FileSystem.
	 * @param fileObject the object to store.
	 * @return the file that was stored.
	 */
	public synchronized File store(FileObject fileObject) throws Exception {
		getIndex();
		String studyName = Study.makeStudyName(fileObject.getStudyUID());
		Study study = uidTable.get(studyName);
		if (study == null) {
			File studyDir = getStudyDirectory(studyName);
			study = new Study(this, studyName, studyDir);
			uidTable.put(studyName,study);
			Element s = indexDoc.createElement("study");
			append(s, "dir", studyDir.getAbsolutePath().substring(dirNameLength+1));
			append(s, "patientName", fileObject.getPatientName());
			append(s, "patientID", fileObject.getPatientID());
			append(s, "storageDate", StringUtil.getDate(System.currentTimeMillis(),""));
			append(s, "studyDate", fileObject.getStudyDate());
			append(s, "studyName", studyName);
			append(s, "studyUID", fileObject.getStudyUID());
			indexDoc.getDocumentElement().appendChild(s);
			FileUtil.setText(indexFile, XmlUtil.toString(indexDoc));
		}
		return study.store(fileObject, acceptDuplicateUIDs);
	}

	/**
	 * Get a study by the Study UID.
	 * @param studyUID the UID of the study to get.
	 * @return the study if it exists; otherwise null.
	 */
	public Study getStudyByUID(String studyUID) {
		try {
			getIndex();
			return uidTable.get(studyUID);
		}
		catch (Exception notThere) {
			return null;
		}
	}

	/**
	 * Delete a study from the FileSystem.
	 * @param studyUID the UID of the study to delete.
	 */
	public synchronized void deleteStudyByUID(String studyUID) {
		try {
			getIndex();
			Study study = uidTable.get(studyUID);
			if (study != null) {
				File studyDir = study.getDirectory();
				Element root = indexDoc.getDocumentElement();
				Node child = root.getFirstChild();
				while (child != null) {
					if ((child.getNodeType()==Node.ELEMENT_NODE)
						&& child.getNodeName().equals("study")) {

						//We have a study element; see if it is the right one.
						Node stChild = child.getFirstChild();
						while (stChild != null) {
							if ((stChild.getNodeType()==Node.ELEMENT_NODE)
								&& stChild.getNodeName().equals("studyUID")
								&& stChild.getTextContent().equals(studyUID)) {

								//This is the one. Remove it from the index.
								child.getParentNode().removeChild(child);
								//Save the index.
								FileUtil.setText(indexFile, XmlUtil.toString(indexDoc));
								//Remove it from the table.
								uidTable.remove(studyUID);
								//Delete the study directory.
								FileUtil.deleteAll(studyDir);
								return;

							}
							stChild = stChild.getNextSibling();
						}
					}
					child = child.getNextSibling();
				}
			}
		}
		catch (Exception notThere) { logger.debug(notThere); }
	}

	/**
	 * Get an array of the studies in the index.
	 * @return the studies in order as determined by the compareTo
	 * method in the Study class.
	 */
	public Study[] getStudies() {
		try {
			getIndex();
			Study[] studies = new Study[uidTable.size()];
			studies = uidTable.values().toArray(studies);
			Arrays.sort(studies);
			return studies;
		}
		catch (Exception ex) {
			logger.warn("Unable to get the studies", ex);
			return new Study[0]; }
	}

	//Get a directory in which to store the study.
	//The directory placement is determined by the type parameter.
	//The root of the directory tree is the base directory provided to the constructor.
	//On return, all the directories above the study directory will have been created,
	//but the study directory itself may not exist.
	private File getStudyDirectory(String studyName) throws Exception {
		Calendar now = Calendar.getInstance();
		//Set up the structure.
		File studyDir = null;
		if (type.equals("year")) {
			//root/year/studyName
			studyDir = new File(dir, StringUtil.intToString(now.get(Calendar.YEAR), 4));
		}
		else if (type.equals("month")) {
			//root/year/month/studyName
			studyDir = new File(dir, StringUtil.intToString(now.get(Calendar.YEAR), 4));
			studyDir = new File(studyDir, StringUtil.intToString(now.get(Calendar.MONTH) + 1, 2));
		}
		else if (type.equals("week")) {
			//root/year/week/studyName
			studyDir = new File(dir, StringUtil.intToString(now.get(Calendar.YEAR), 4));
			studyDir = new File(studyDir, StringUtil.intToString(now.get(Calendar.WEEK_OF_YEAR), 2));
		}
		else if (type.equals("day")) {
			//root/year/day/studyName
			studyDir = new File(dir, StringUtil.intToString(now.get(Calendar.YEAR), 4));
			studyDir = new File(studyDir, StringUtil.intToString(now.get(Calendar.DAY_OF_YEAR), 3));
		}
		else studyDir = dir;
		studyDir.mkdirs();
		setProtections(studyDir);
		if (studyName.startsWith("__")) return new File(studyDir, studyName);
		File newFile = File.createTempFile("ST-","",studyDir);
		newFile.delete();
		return newFile;
	}

	//Set the protections on all the intermediate directories associated with a study.
	private void setProtections(File d) {
		/* disable this to make it build on Java 1.5
		while ((d != null) && d.isDirectory() && !d.equals(dir)) {
			if (setReadable) d.setReadable(true, false); //Java 1.6
			if (setWritable) d.setWritable(true, false); //Java 1.6
			d = d.getParentFile();
		}
		*/
	}

	//Create a child element and append it to a parent.
	private void append(Element parent, String name, String content) {
		Element child = indexDoc.createElement(name);
		child.setTextContent(content);
		parent.appendChild(child);
	}

	/**
	 * Get the index document for this FileSystem.
	 * @return the index document.
	 * @throws Exception if the document cannot be found and parsed
	 */
	public Document getIndex() throws Exception {
		if (indexDoc == null) {
			uidTable = new Hashtable<String,Study>();
			if (indexFile.exists()) {
				indexDoc = XmlUtil.getDocument(indexFile);
/**/			upgradeSchema();
				Element root = indexDoc.getDocumentElement();
				Node n = root.getFirstChild();
				while (n != null) {
					if (n.getNodeType() == Node.ELEMENT_NODE) {
						String studyName = "";
						String studyDir = "";
						Node c = n.getFirstChild();
						while (c != null) {
							if (c.getNodeType() == Node.ELEMENT_NODE) {
								if (c.getNodeName().equals("dir")) studyDir = c.getTextContent();
								else if (c.getNodeName().equals("studyName")) studyName = c.getTextContent();
							}
							c = c.getNextSibling();
						}
						Study study = new Study(this, studyName, new File(dir, studyDir));
						uidTable.put(studyName, study);
					}
					n = n.getNextSibling();
				}
			}
			else {
				indexDoc = XmlUtil.getDocument();
				Element root = indexDoc.createElement("index");
				root.setAttribute("fileSystemName",name);
				indexDoc.appendChild(root);
				FileUtil.setText(indexFile, XmlUtil.toString(indexDoc));
			}
		}
		return indexDoc;
	}

	//Upgrade the index if necessary
	private void upgradeSchema() {
		boolean change = false;
		Element root = indexDoc.getDocumentElement();
		Node n = root.getFirstChild();
		while (n != null) {
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element)n;
				Element date = null;
				Element studyDate = null;
				Node child = e.getFirstChild();
				while (child != null) {
					if (child.getNodeType() == Node.ELEMENT_NODE) {
						Element c = (Element)child;
						String tag = c.getTagName();
						if (tag.equals("date")) date = c;
						else if (tag.equals("studyDate")) studyDate = c;
					}
					child = child.getNextSibling();
				}
				if (date != null) {
					if (studyDate == null) {
						studyDate = indexDoc.createElement("studyDate");
						studyDate.appendChild(indexDoc.createTextNode(date.getTextContent()));
						e.appendChild(studyDate);
						e.removeChild(date);
						change = true;
					}
				}
			}
			n = n.getNextSibling();
		}
		if (change) FileUtil.setText(indexFile, XmlUtil.toString(indexDoc));
	}

}

