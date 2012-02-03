/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class to manage the FileObjects in a single study.
 */
public class Study implements Comparable {

	static final Logger logger = Logger.getLogger(Study.class);

	FileSystem fileSystem;
	String studyName = "";
	String patientName = "";
	String patientID = "";
	String studyDate = "";
	File dir = null;
	File indexFile = null;
	Document indexDoc = null;

	/**
	 * Create a Study in an assigned directory.
	 * @param dir the directory assigned to the Study by the parent FileSystem.
	 * @param fileSystem the FileSystem which manages this Study.
	 * @param studyName the name of the Study as assigned by the FileSystem.
	 */
	public Study(FileSystem fileSystem, String studyName, File dir) {
		this.dir = dir;
		this.fileSystem = fileSystem;
		this.studyName = studyName;
		dir.mkdirs();
//		if (fileSystem.getSetReadable()) dir.setReadable(true,false); //Java 1.6
//		if (fileSystem.getSetWritable()) dir.setWritable(true,false); //Java 1.6
		indexFile = new File(dir,"__index.xml");
		try {
			getIndex();
			Element root = indexDoc.getDocumentElement();
			patientName = getStringDefault(root.getAttribute("patientName"), "");
			patientID = getStringDefault(root.getAttribute("patientID"), "");
			studyDate = getStringDefault(root.getAttribute("studyDate"), "");
		}
		catch (Exception ex) {
			logger.warn("Unable to load the index for "+studyName);
		}
	}

	/**
	 * Get the directory containing this Study.
	 * @return the directory containing this Study.
	 */
	public File getDirectory() {
		return dir;
	}

	/**
	 * Get the name of this Study.
	 * @return the name of this Study.
	 */
	public String getStudyName() {
		return studyName;
	}

	/**
	 * Get the name of patient in this Study.
	 * @return the name of patient in this Study.
	 */
	public String getPatientName() {
		return patientName;
	}

	/**
	 * Get the ID of patient in this Study.
	 * @return the ID of patient in this Study.
	 */
	public String getPatientID() {
		return patientID;
	}

	/**
	 * Get the date of this Study.
	 * @return the date of this Study.
	 */
	public String getStudyDate() {
		return studyDate;
	}

	/**
	 * Get a parsed object from this Study.
	 * @param fileName the name of the file stored in the directory for this study.
	 * @return the parsed object for a file stored in this study.
	 */
	public FileObject getObject(String fileName) {
		return FileObject.getInstance(new File(dir,fileName));
	}

	/**
	 * The Comparable interface: return an order based on the date of the Study.
	 * @return the order of this Study compared to the supplied Study, in reverse
	 * date order.
	 */
	public int compareTo(Object otherObject) {
		if (otherObject instanceof Study) {
			Study otherStudy = (Study)otherObject;
			String otherDate = otherStudy.getStudyDate();
			return otherDate.compareTo(studyDate);
		}
		else return 0;
	}

	/**
	 * Filter a name to create one which is acceptable for use as a Study name.
	 * The filter removes all characters except underscore, period, a-z, A-Z, and 0-9.
	 * If the result is an empty string, it assigns the name "__bullpen".
	 * @param name a proposed name for a Study.
	 * @return the filtered name.
	 */
	public static String makeStudyName(String name) {
		if (name == null) name = "";
		else name = name.replaceAll("[^_\\w\\.]","");
		if (name.equals("")) name = "__bullpen";
		return name;
	}

	/**
	 * Get the URL of this study.
	 * @param context the context identifying the servlet and the file system within which
	 * this study is located (example: "/storage/__default").
	 * @return the URL of this study.
	 */
	public String getStudyURL(String context) {
		return context + "/" + studyName;
	}

	/**
	 * Get a File pointing to a filename in the study.
	 * @param filename the name of the file.
	 * @return the File pointing to the requested filename, or null if the filename
	 * does not exist in this study.
	 */
	public File getFile(String filename) {
		File file = new File(dir, filename);
		if (file.exists()) return file;
		return null;
	}

	/**
	 * Determine whether this study contains a file with a specified name.
	 * @param filename the name of the file
	 * @return true if the file is contained in this study; false otherwise.
	 */
	public boolean contains(String filename) {
		return (getFile(filename) != null);
	}

	/**
	 * Get a list of files in the index corresponding to a specified UID.
	 * @param uid the UID on which to select.
	 * @return a list containing the File objects of files which have
	 * the specified UID.
	 */
	public List<File> listFilesForUID(String uid) {
		List<File> list = new LinkedList<File>();
		try {
			getIndex();
			Element studyRoot = indexDoc.getDocumentElement();
			Node child = studyRoot.getFirstChild();
			while (child != null) {
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					if (getValue(child, "uid").equals(uid)) {
						String filename = getValue(child, "file");
						File file = getFile(filename);
						if (file != null) list.add(file);
					}
				}
				child = child.getNextSibling();
			}
		}
		catch (Exception noIndex) { logger.debug("Unable to get the study index"); }
		return list;
	}

	//Get the text content of a named child of a node.
	private String getValue(Node node, String name) {
		Node child = node.getFirstChild();
		while (child != null) {
			if (child.getNodeName().equals(name)) {
				return child.getTextContent();
			}
			child = child.getNextSibling();
		}
		return "";
	}

	/**
	 * Get the DOM Element in the index corresponding to a filename. The element
	 * returned will be a class name element (DicomObject, XmlObject, ZipObject, or FileObject);
	 * @return the DOM Element corresponding to the specified filename, or null if
	 * no such element exists.
	 */
	public Element getIndexElementForFilename(String filename) {
		try {
			getIndex();
			Element studyRoot = indexDoc.getDocumentElement();
			Node child = studyRoot.getFirstChild();
			while (child != null) {
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					if (getValue(child, "file").equals(filename)) {
						return (Element)child;
					}
				}
				child = child.getNextSibling();
			}
		}
		catch (Exception noIndex) { logger.debug("Unable to get the study index."); }
		return null;
	}

	/**
	 * Store a FileObject in this Study.
	 * @param fileObject the object to store.
	 * @return the file that was stored.
	 */
	public File store(FileObject fileObject, boolean acceptDuplicateUIDs) throws Exception {

		getIndex();

		//Make a file to save the object.
		//Handle the duplicate problem.
		File newFile = null;
		if (!acceptDuplicateUIDs) {
			List<File> list = listFilesForUID(fileObject.getUID());
			if (list.size() != 0) {
				File oldFile = list.get(0);
				newFile = oldFile;
				//Find and remove the index entry for the oldFile
				Element oldIndexElement = getIndexElementForFilename(oldFile.getName());
				if (oldIndexElement != null) {
					oldIndexElement.getParentNode().removeChild(oldIndexElement);
				}
			}
		}
		if (newFile == null) newFile = File.createTempFile("FO-",fileObject.getExtension(),dir);
		newFile.delete();

		//Save the object
		if (!fileObject.copyTo(newFile)) {
			throw new Exception("Unable to copy the FileObject to study directory");
		}
//		if (fileSystem.getSetReadable()) newFile.setReadable(true,false); //Java 1.6
//		if (fileSystem.getSetWritable()) newFile.setWritable(true,false); //Java 1.6

		//Update the index.
		String className = fileObject.getClass().getName();
		className = className.substring(className.lastIndexOf(".")+1);
		Element o = indexDoc.createElement(className);
		append(o, "file", newFile.getName());
		String uid = fileObject.getUID();
		if (uid.equals("")) uid = newFile.getName();
		append(o, "uid", uid);
		if (fileObject instanceof DicomObject) {
			DicomObject dob = (DicomObject)fileObject;
			append(o, "series", fixNumber(dob.getSeriesNumber()));
			append(o, "acquisition", fixNumber(dob.getAcquisitionNumber()));
			append(o, "instance", fixNumber(dob.getInstanceNumber()));
			if (dob.isImage()) {
				o.setAttribute("type","image");
				append(o, "rows", ""+dob.getRows());
				append(o, "columns", ""+dob.getColumns());
			}
		}
		Element root = indexDoc.getDocumentElement();
		root.appendChild(o);

		//Set the study attributes in case they haven't been set before.
		//Set the patientName if we don't have one.
		if (patientName.equals("")) {
			patientName = getStringDefault(fileObject.getPatientName(), "");
			root.setAttribute("patientName", patientName);
		}
		//Set the patientID if we don't have one.
		if (patientID.equals("")) {
			patientID = getStringDefault(fileObject.getPatientID(), "");
			root.setAttribute("patientID", patientID);
		}
		//Set the date if we don't have one.
		if (studyDate.equals("")) {
			studyDate = getStringDefault(fileObject.getStudyDate(), "");
			root.setAttribute("studyDate", studyDate);
		}
		//Set the studyName.
		root.setAttribute("studyName", studyName);
		//Set the fileSystemName.
		root.setAttribute("fileSystemName", fileSystem.getName());

		//Save the index in the indexFile.
		FileUtil.setText(indexFile, XmlUtil.toString(indexDoc));

		//Create any required JPEG images
		//Note: this method always saves the first frame.
		if (fileObject instanceof DicomObject) {
			//int nFrames = dob.getNumberOfFrames();
			List<ImageQualifiers> qList = fileSystem.getImageQualifiersList();
			if (qList.size() > 0) {
				DicomObject dob = (DicomObject)fileObject;
				for (ImageQualifiers q : qList) {
					int frame = 0; //q.getSelectedFrames(nFrames);
					//Make the name of the jpeg
					String jpegName = newFile.getName() + q.toString() + ".jpeg";
					File jpegFile = new File(dir, jpegName);
					dob.saveAsJPEG(jpegFile, frame, q.maxWidth, q.minWidth, q.quality);
				}
			}
		}
		return newFile;
	}

	//Fix a text string that must be numeric.
	//If it's not, then return "-1"
	private String fixNumber(String text) {
		try { int k = Integer.parseInt(text.trim()); }
		catch (Exception ex) { return "-1"; }
		return text;
	}

	//Create a child element and append it to a parent.
	private void append(Element parent, String name, String content) {
		Element child = indexDoc.createElement(name);
		child.setTextContent(content);
		parent.appendChild(child);
	}

	/**
	 * Load the index, creating it if it doesn't exist.
	 * @return the index of this Study.
	 * @throws Exception if the index cannot be found or parsed.
	 */
	public Document getIndex() throws Exception {
		if (indexDoc == null) {
			if (indexFile.exists()) {
				indexDoc = XmlUtil.getDocument(indexFile);
/**/			upgradeSchema();
			}
			else {
				indexDoc = XmlUtil.getDocument();
				Element root = indexDoc.createElement("index");
				root.setAttribute("fileSystemName", fileSystem.getName());
				indexDoc.appendChild(root);
				FileUtil.setText(indexFile, XmlUtil.toString(indexDoc));
			}
		}
		return indexDoc;
	}

	//Upgrade the index if necessary
	private void upgradeSchema() {
		Element root = indexDoc.getDocumentElement();
		String date = root.getAttribute("date");
		if (!date.equals("")) {
			if (studyDate.equals("")) studyDate = date;
			root.removeAttribute("date");
			FileUtil.setText(indexFile, XmlUtil.toString(indexDoc));
		}
	}

	//Get the content of an child element
	private String getContent(Node node, String childName) {
		if (node == null) return "";
		if (node instanceof Element) {
			Node child = node.getFirstChild();
			while (child != null) {
				if (child.getNodeName().equals(childName)) {
					return child.getTextContent();
				}
				child = child.getNextSibling();
			}
		}
		return "";
	}

	//Return a default String if a String is null; else return the String.
	private String getStringDefault(String string, String defaultString) {
		return (string != null) ? string : defaultString;
	}

}
