/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.objects;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.*;
import org.apache.log4j.Logger;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A generic Zip object for collections of clinical trials data, providing
 * parsing and access to the files. This object supports a manifest, the primary
 * purpose of which is to provide a unique identifier along the same lines as the
 * DICOM SOP Instance UID. The manifest is an XML file named "manifest.xml" stored
 * in the root of the zip directory structure. The root element of the manifest can
 * have any element name, but the UID must be an attribute of the root element and
 * have the name "uid".
 * <p>
 * This object can be used to access zip files not having a manifest, but in that
 * case, all the manifest access methods return either null Strings or null XML
 * documents (depending on the method).
 * <p>
 * The schema of the manifest is:
 * <pre>
 * &lt;manifest
 *        uid="UID of the ZipObject instance"
 *        study-uid="UID of the study instance"
 *        date="YYYYMMDD"
 *        version="manifest version identifier"
 *        type="type of ZipObject"
 *        description="description of the ZipObject"
 *        pt-name="Last^First^Middle"
 *        pt-id="..."&gt;
 *    ...child elements as required for the usage of the ZipObject...
 * &lt;/manifest&gt;
 * </pre>
 */
public class ZipObject extends FileObject {

	public static final String manifestName = "manifest.xml";
	String manifest = "";
	Document manifestXML = null;

	static Charset latin1 = Charset.forName("iso-8859-1");
	static Charset utf8 = Charset.forName("utf-8");

	static final Logger logger = Logger.getLogger(ZipObject.class);

	/**
	 * Class constructor; opens the zip file.
	 * @param file the zip file.
	 * @throws Exception if the zip file does not parse.
	 */
	public ZipObject(File file) throws Exception {
		super(file);
		ZipFile zipFile = null;
		//Get the manifest text and DOM document.
		try {
			zipFile = new ZipFile(file);
			ZipEntry manifestEntry = zipFile.getEntry(manifestName);
			if (manifestEntry != null) manifest = extractFileText(manifestEntry);
			if (!manifest.equals("")) {
				try { manifestXML = XmlUtil.getDocument(manifest); }
				catch (Exception ex) { manifestXML = null; }
			}
			zipFile.close();
		}
		catch (Exception ex) {
			if (zipFile != null) zipFile.close();
			throw ex;
		}
	}

	/**
	 * Get the standard extension for a ZipObject (".zip").
	 * @return ".zip"
	 */
	public String getStandardExtension() {
		return ".zip";
	}

	/**
	 * Get a prefix for a ZipObject ("ZIP-").
	 * @return a prefix for a ZipObject.
	 */
	public String getTypePrefix() {
		return "ZIP-";
	}

	/**
	 * Get the text of the manifest for this object. The manifest is
	 * a text file named "manifest.xml" in the zip file.
	 * @return the text of the manifest file or the empty string if
	 * the manifest cannot be obtained.
	 */
	public String getManifestText() {
		if (manifest == null) return "";
		return manifest;
	}

	/**
	 * Get the manifest XML DOM document for this object.
	 * @return the XML DOM document for the parsed manifest file or null if
	 * the manifest cannot be obtained and parsed.
	 */
	public Document getManifestDocument() {
		return manifestXML;
	}

	/**
	 * Get the document element name (the root element) of the
	 * manifest for this object.
	 * @return the document element name of the manifest.
	 */
	public String getManifestDocumentElementName() {
		if (manifestXML == null) return "";
		return manifestXML.getDocumentElement().getTagName();
	}

	/**
	 * Get the UID for this object from the manifest. This requires that the
	 * "uid" attribute of the root element be present in the XML file named
	 * "manifest.xml" in the zip file.
	 * @return the UID or the empty string if the uid cannot be obtained.
	 */
	public String getUID() {
		if (manifestXML == null) return "";
		return manifestXML.getDocumentElement().getAttribute("uid").replaceAll("\\s","");
	}

	/**
	 * Get the description element text from the manifest. The description element
	 * must be a first-generation child of the root element and be named "description".
	 * The text returned is the sum of the text nodes of the first description
	 * element found under the root.
	 * @return the value of the description element in the manifest,
	 * or the file name if the element cannot be obtained.
	 */
	public String getDescription() {
		if (manifestXML == null) return file.getName();
		Element root = manifestXML.getDocumentElement();
		String rootName = root.getTagName();
		String desc = XmlUtil.getValueViaPath(root,rootName+"/description");
		if (!desc.trim().equals("")) return desc;
		return file.getName();
	}

	/**
	 * Get the study's unique identifier from the manifest. This requires that the
	 * "study-uid" attribute of the root element be present in the XML file named
	 * "manifest.xml" in the zip file.
	 * @return the study's unique identifier, if available; otherwise the empty string.
	 */
	public String getStudyUID() {
		if (manifestXML == null) return "";
		return manifestXML.getDocumentElement().getAttribute("study-uid");
	}

	/**
	 * Get the patient name from the manifest. This requires that the
	 * "pt-name" attribute of the root element be present in the XML file named
	 * "manifest.xml" in the zip file.
	 * @return the patient name, if available; otherwise the empty string.
	 */
	public String getPatientName() {
		if (manifestXML == null) return "";
		String ptname = manifestXML.getDocumentElement().getAttribute("pt-name").trim();
		if (!ptname.equals("")) return ptname;
		return manifestXML.getDocumentElement().getAttribute("patientName").trim();
	}

	/**
	 * Get the patient ID from the manifest. This requires that the
	 * "pt-id" attribute of the root element be present in the XML file named
	 * "manifest.xml" in the zip file.
	 * @return the patient ID, if available; otherwise the empty string.
	 */
	public String getPatientID() {
		if (manifestXML == null) return "";
		String ptid = manifestXML.getDocumentElement().getAttribute("pt-id").trim();
		if (!ptid.equals("")) return ptid;
		String patientID = manifestXML.getDocumentElement().getAttribute("patientID").trim();
		return patientID;
	}

	/**
	 * Get the version identifier from the manifest. This requires that the
	 * "version" attribute of the root element be present in the XML file named
	 * "manifest.xml" in the zip file.
	 * @return the manifest version identifier, if available; otherwise the empty string.
	 */
	public String getVersion() {
		if (manifestXML == null) return "";
		return manifestXML.getDocumentElement().getAttribute("version");
	}

	/**
	 * Get the type identifier from the manifest. This requires that the
	 * "type" attribute of the root element be present in the XML file named
	 * "manifest.xml" in the zip file.
	 * @return the manifest type identifier, if available; otherwise the empty string.
	 */
	public String getType() {
		if (manifestXML == null) return "";
		return manifestXML.getDocumentElement().getAttribute("type");
	}

	/**
	 * Get the date from the manifest. This requires that the
	 * "date" attribute of the root element be present in the XML file named
	 * "manifest.xml" in the zip file.
	 * @return the manifest date, if available; otherwise the empty string.
	 */
	public String getStudyDate() {
		if (manifestXML == null) return "";
		return manifestXML.getDocumentElement().getAttribute("date").replaceAll("\\D","");
	}

	/**
	 * Get the value of the node identified by a path in the manifest. This method
	 * is a convenience method that can only be relied on if the path to the
	 * node can be found by taking the first element matching each step of
	 * the path.
	 * @param path the path from the root element to the node. Note that this
	 * method uses the XmlUtil.getValueViaPath(String) method, and the path
	 * syntax is slightly different from that of XPath. Specifically,
	 * attributes are identified as "node@attribute" rather than "node/@attribute".
	 * @return the value, or the empty string if the node identified by the path is missing.
	 */
	public String getValue(String path) {
		if (manifestXML == null) return "";
		return XmlUtil.getValueViaPath(manifestXML.getDocumentElement(),path);
	}

	/**
	 * Get an array of ZipEntry objects corresponding to the files
	 * in the ZipObject. This method does not return ZipEntry objects
	 * which correspond to directories.
	 * @return the array of ZipEntry objects corresponding to the files
	 * in the ZipObject, or null if the ZipObject cannot be read.
	 */
	public ZipEntry[] getEntries() {
		ZipEntry[] entries = null;
		ZipFile zipFile = null;
		try {
			zipFile = new ZipFile(file);
			ZipEntry ze;
			Enumeration<? extends ZipEntry> e = zipFile.entries();
			//Get the ZipEntries corresponding to files (not directories).
			ArrayList<ZipEntry> list = new ArrayList<ZipEntry>();
			while ( e.hasMoreElements() ) {
				ze = e.nextElement();
				if (!ze.isDirectory()) list.add(ze);
			}
			entries = new ZipEntry[list.size()];
			entries = list.toArray(entries);
		}
		catch (Exception ex) { return null; }
		finally { FileUtil.close(zipFile); }
		return entries;
	}

	/**
	 * Unpack the ZipObject using the directory structure of the zip file.
	 * @param dir the directory into which to unpack the zip file. This becomes
	 * root directory of the file tree of the object. Files with no path information
	 * are placed in this directory; files with path information are placed in child
	 * directories with this directory as the root. Directories are created when necessary.
	 * @return true if the operation succeeded; false otherwise.
	 */
	public boolean extractAll(File dir) {
		boolean ok = true;
		BufferedOutputStream out = null;
		BufferedInputStream in = null;
		ZipFile zipFile = null;
		if (!file.exists()) return false;
		if (!dir.isDirectory()) return false;
		String path = dir.getAbsolutePath();
		if (!path.endsWith(File.separator)) path += File.separator;
		try {
			zipFile = new ZipFile(file);
			Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
			while (zipEntries.hasMoreElements()) {
				ZipEntry entry = zipEntries.nextElement();
				String name = entry.getName().replace('/',File.separatorChar);
				File outFile = new File(path + name);
				if (!entry.isDirectory()) {
					outFile.getParentFile().mkdirs();
					out = new BufferedOutputStream(new FileOutputStream(outFile));
					in = new BufferedInputStream(zipFile.getInputStream(entry));
					FileUtil.copy(in, out, -1);
				}
				else outFile.mkdirs();
			}
		}
		catch (Exception e) { ok = false; }
		finally {
			FileUtil.close(zipFile);
			FileUtil.close(in);
			FileUtil.close(out);
		}
		return ok;
	}

	/**
	 * Unpack the file corresponding to a ZipEntry and place it in a specified directory.
	 * If the directory does not exist, it is created.
	 * @param entry the entry pointing to the desired file in the zip file. This can be
	 * one of the entries in the array returned by getEntries().
	 * @param dir the directory into which to unpack the zip file. This becomes
	 * root directory of the file tree of the object. Files with no path information
	 * are placed in this directory; files with path information are placed in child
	 * directories with this directory as the root. Directories are created when necessary.
	 * @return true if the operation succeeded; false otherwise.
	 */
	public boolean extractFile(ZipEntry entry, File dir) {
		boolean ok = true;
		BufferedOutputStream out = null;
		BufferedInputStream in = null;
		ZipFile zipFile = null;
		if (!file.exists()) return false;
		if (!dir.exists()) dir.mkdirs();
		try {
			zipFile = new ZipFile(file);
			File outFile = new File(entry.getName().replace('/',File.separatorChar));
			outFile = new File(dir,outFile.getName());
			if (!entry.isDirectory()) {
				outFile.getParentFile().mkdirs();
				out = new BufferedOutputStream(new FileOutputStream(outFile));
				in = new BufferedInputStream(zipFile.getInputStream(entry));
				FileUtil.copy(in, out, -1);
			}
		}
		catch (Exception e) { ok = false; }
		finally {
			FileUtil.close(in);
			FileUtil.close(out);
			FileUtil.close(zipFile);
		}
		return ok;
	}

	/**
	 * Unpack the file corresponding to a ZipEntry and place it in a specified directory
	 * with a specified file name. No path information from the Zip Entry is used.
	 * If the directory does not exist, it is created. If the ZipEntry is a directory,
	 * nothing is done and false is returned.
	 * @param entry the entry pointing to the desired file in the zip file. This can be
	 * one of the entries in the array returned by getEntries().
	 * @param dir the directory into which to unpack the zip file.
	 * @param name the file name for the unpacked file.
	 * @return true if the operation succeeded; false otherwise.
	 */
	public boolean extractFile(ZipEntry entry, File dir, String name) {
		boolean ok = true;
		BufferedOutputStream out = null;
		BufferedInputStream in = null;
		ZipFile zipFile = null;
		if (!file.exists()) return false;
		if (!dir.exists()) dir.mkdirs();
		try {
			zipFile = new ZipFile(file);
			File outFile = new File(dir, name);
			if (!entry.isDirectory()) {
				outFile.getParentFile().mkdirs();
				out = new BufferedOutputStream(new FileOutputStream(outFile));
				in = new BufferedInputStream(zipFile.getInputStream(entry));
				FileUtil.copy(in, out, -1);
			}
		}
		catch (Exception e) { ok = false; }
		finally {
			FileUtil.close(in);
			FileUtil.close(out);
			FileUtil.close(zipFile);
		}
		return ok;
	}

	/**
	 * Unpack the file corresponding to a ZipEntry and return its contents as a String,
	 * using the iso-8859-1 character set (Latin-1).
	 * Care should be taken to use this method only on known text files.
	 * @param entry the entry pointing to the desired file in the zip file. This can be
	 * one of the entries in the array returned by getEntries().
	 * @return the text of the file if the operation succeeded, or the empty string if it failed.
	 */
	public String extractFileText(ZipEntry entry) {
		return extractFileText(entry, latin1);
	}

	/**
	 * Unpack the file corresponding to a ZipEntry and return its contents as a String,
	 * using the specified character set.
	 * Care should be taken to use this method only on known text files.
	 * @param entry the entry pointing to the desired file in the zip file. This can be
	 * one of the entries in the array returned by getEntries().
	 * @param charset the character encoding to be used for the bytes in the file.
	 * @return the text of the file if the operation succeeded, or the empty string if it failed.
	 */
	public String extractFileText(ZipEntry entry, Charset charset) {
		StringWriter sw = new StringWriter();
		BufferedReader in = null;
		ZipFile zipFile = null;
		if (!file.exists()) return "";
		try {
			zipFile = new ZipFile(file);
			if (!entry.isDirectory()) {
				in = new BufferedReader(
							new InputStreamReader(zipFile.getInputStream(entry),charset));
				int size = 1024;
				int n = 0;
				char[] c = new char[size];
				while ((n = in.read(c,0,size)) != -1) sw.write(c,0,n);
				in.close();
			}
			zipFile.close();
			return sw.toString();
		}
		catch (Exception e) {
			FileUtil.close(zipFile);
			FileUtil.close(in);
			return "";
		}
	}

	/**
	 * Evaluate a boolean script for the manifest of this ZipObject.
	 * See the RSNA CTP wiki article (The CTP XmlFilter) for information
	 * on the script language.
	 * @param scriptFile the text file containing the expression to
	 * compute based on the values in this ZipObject.
	 * @return the computed boolean value of the script, or false if
	 * the object does not contain a manifest.
	 */
	public boolean matches(File scriptFile) {
		if (scriptFile != null) {
			if (manifestXML != null) {
				String script = FileUtil.getText(scriptFile);
				return XmlUtil.matches(manifestXML, script);
			}
			return false;
		}
		return true;
	}

	/**
	 * Evaluate a boolean script for the manifest of this ZipObject.
	 * See the RSNA CTP wiki article (The CTP XmlFilter) for information
	 * on the script language.
	 * @param script the expression to compute based on the values
	 * in this ZipObject.
	 * @return the computed boolean value of the script, or false if
	 * the object does not contain a manifest.
	 */
	public boolean matches(String script) {
		if (manifestXML != null) {
			return XmlUtil.matches(manifestXML, script);
		}
		return false;
	}

}
