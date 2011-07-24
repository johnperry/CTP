/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.objects;

import java.io.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.rsna.util.DigestUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A generic XML object for clinical trials metadata, providing
 * parsing and access to certain common elements.
 */
public class XmlObject extends FileObject {

	Document document = null;

	/**
	 * Class constructor; opens and parses an XML file.
	 * @param file the file containing the XML object.
	 * @throws Exception if the object does not parse.
	 */
	public XmlObject(File file) throws Exception {
		super(file);
		//In order to try to avoid the fatal error from the parser
		//when parsing a non-XML file, get the first few bytes and
		//verify that the first non-whitespace characters are valid
		//for an XML document.
		byte[] bytes = FileUtil.getBytes(file,1024);
		String string = new String(bytes, "ISO-8859-1");
		string = string.trim();
		if ((string.length() > 3) && (string.charAt(0) == '<') &&
			((string.charAt(1) == '?') || Character.isLetter(string.charAt(1)))) {

			//It looks like the start of an XML document, parse it
			document = XmlUtil.getDocument(file);
		}
		else {
			//It doesn't look like the start of an XML document, throw an exception.
			throw new Exception("Not XML");
		}
	}

	/**
	 * Get the standard extension for an XmlObject (".xml").
	 * @return ".xml"
	 */
	public String getStandardExtension() {
		return ".xml";
	}

	/**
	 * Get a prefix for an XmlObject ("XML-").
	 * @return a prefix for a XmlObject.
	 */
	public String getTypePrefix() {
		return "XML-";
	}

	/**
	 * Get the parsed XML DOM object.
	 * @return the document.
	 */
	public Document getDocument() {
		return document;
	}

	/**
	 * Get the document element name (the root element) of this object.
	 * @return the document element name.
	 */
	public String getDocumentElementName() {
		if (document == null) return "";
		return document.getDocumentElement().getTagName();
	}

	/**
	 * Get the UID of this object. This method looks in
	 * several places it:
	 * <ol>
	 * <li>the uid attribute of the root element
	 * <li>the uniqueIdentifier attribute of the root element (for AIM compatibility)
	 * <li>the uid child element of the root element
	 * <li>the uid attribute of the first child element of the root element
	 * </ol>
	 * If the UID is missing, a UID is generated from the hash of the text of the XML.
	 * @return the UID.
	 */
	public String getUID() {
		if (document == null) return "";
		Element root = document.getDocumentElement();
		String uid = root.getAttribute("uid").replaceAll("\\s","");
		if (!uid.equals("")) return uid;

		//Not in the uid attribute of the root.
		//Try the uniqueIdentifier attribute
		uid = root.getAttribute("uniqueIdentifier").replaceAll("\\s","");
		if (!uid.equals("")) return uid;

		//No joy. Look for a first-generation child named uid.
		//If it is present, it will be the first node in the list.
		NodeList nl = root.getElementsByTagName("uid");
		if (nl.getLength() > 0) {
			Node child = nl.item(0);
			if (child.getParentNode().equals(root)) {
				uid = child.getTextContent().replaceAll("\\s","");
				if (!uid.equals("")) return uid;
			}
		}

		//Didn't find an acceptable uid child element.
		//Look for the first child element of the root,
		//and see if it has a uid attribute.
		Node child = root.getFirstChild();
		while ((child != null) && (child.getNodeType() != Node.ELEMENT_NODE)) {
			child = child.getNextSibling();
		}
		if (child != null) {
			uid = ((Element)child).getAttribute("uid").replaceAll("\\s","");
			if (!uid.equals("")) return uid;
		}
		//No luck, hash the text and return the resulting string.
		try { return DigestUtil.hash(XmlUtil.toString(document)); }
		catch (Exception ex) { return ""; }
	}

	/**
	 * Convenience method to get the contents of the best study date which
	 * is available for the file. This method looks in several places:
	 * <ol>
	 * <li>the date attribute of the root element
	 * <li>the study-date attribute of the root element
	 * </ol>
	 * If no date is available it returns the empty string.
	 */
	public String getStudyDate() {
		if (document == null) return "";
		Element root = document.getDocumentElement();
		String date = root.getAttribute("date").replaceAll("\\s","");
		if (!date.equals("")) return date;
		date = root.getAttribute("study-date").replaceAll("\\s","");
		return date;
	}

	/**
	 * Get the description for this object. This method looks in
	 * two places to find the description element:
	 * <ol>
	 * <li>the description child element of the root element
	 * <li>the description child of the first child of the root element.
	 * </ol>
	 * @return the description, or the file name if it cannot be obtained.
	 */
	public String getDescription() {
		if (document == null) return "";
		String desc;
		Element root = document.getDocumentElement();
		String rootName = root.getTagName();
		desc = getValue(rootName + "/description");
		if (!desc.trim().equals("")) return desc;

		Node child = root.getFirstChild();
		while ((child != null) && (child.getNodeType() != Node.ELEMENT_NODE))
			child = child.getNextSibling();
		if (child == null) return file.getName();
		String path = rootName + "/" + child.getNodeName() + "/description";
		desc = getValue(path);
		if (!desc.trim().equals("")) return desc;
		return file.getName();
	}

	/**
	 * Get the patient name. This method looks in several places:
	 * <ol>
	 * <li>the pt-name attribute of the root element
	 * <li>the pt-name child element of the root element
	 * <li>the pt-name child of the first child of the root element.
	 * </ol>
	 * @return the patient name, if available; otherwise the empty string.
	 */
	public String getPatientName() {
		if (document == null) return "";
		String ptName;
		Element root = document.getDocumentElement();
		String rootName = root.getTagName();
		ptName = getValue(rootName + "@pt-name");
		if (!ptName.equals("")) return ptName;
		ptName = getValue(rootName + "/pt-name");
		if (!ptName.equals("")) return ptName;

		Node child = root.getFirstChild();
		while ((child != null) && (child.getNodeType() != Node.ELEMENT_NODE))
			child = child.getNextSibling();
		if (child == null) return "";
		String path = rootName + "/" + child.getNodeName() + "/pt-name";
		return getValue(path);
	}

	/**
	 * Get the patient ID. This method looks in several places:
	 * <ol>
	 * <li>the pt-id attribute of the root element
	 * <li>the pt-id child element of the root element
	 * <li>the pt-id child of the first child of the root element.
	 * </ol>
	 * @return the patient ID, if available; otherwise the empty string.
	 */
	public String getPatientID() {
		if (document == null) return "";
		String ptID;
		Element root = document.getDocumentElement();
		String rootName = root.getTagName();
		ptID = getValue(rootName + "@pt-id");
		if (!ptID.equals("")) return ptID;
		ptID = getValue(rootName + "/pt-id");
		if (!ptID.equals("")) return ptID;

		Node child = root.getFirstChild();
		while ((child != null) && (child.getNodeType() != Node.ELEMENT_NODE))
			child = child.getNextSibling();
		if (child == null) return "";
		String path = rootName + "/" + child.getNodeName() + "/pt-id";
		return getValue(path);
	}

	/**
	 * Get the study's unique identifier. This method looks in
	 * several places to find the StudyUID:
	 * <ol>
	 * <li>the study-uid attribute of the root element
	 * <li>the StudyInstanceUID attribute of the root element
	 * <li>the study-uid child element of the root element
	 * <li>the StudyInstanceUID child element of the root element
	 * <li>the study-uid child of the first child of the root element
	 * <li>the StudyInstanceUID child of the first child of the root element.
	 * </ol>
	 * @return the study's unique identifier, if available; otherwise the empty string.
	 */
	public String getStudyUID() {
		if (document == null) return "";
		String siuid;
		Element root = document.getDocumentElement();
		String rootName = root.getTagName();
		siuid = getValue(rootName + "@study-uid");
		if (!siuid.equals("")) return siuid;
		siuid = getValue(rootName + "@StudyInstanceUID");
		if (!siuid.equals("")) return siuid;
		siuid = getValue(rootName + "/study-uid");
		if (!siuid.equals("")) return siuid;
		siuid = getValue(rootName + "/StudyInstanceUID");
		if (!siuid.equals("")) return siuid;

		Node child = root.getFirstChild();
		while ((child != null) && (child.getNodeType() != Node.ELEMENT_NODE))
			child = child.getNextSibling();
		if (child == null) return "";
		String path = rootName + "/" + child.getNodeName() + "/study-uid";
		siuid = getValue(path);
		if (!siuid.equals("")) return siuid;
		path = rootName + "/" + child.getNodeName() + "/StudyInstanceUID";
		return getValue(path);
	}

	/**
	 * Get the value of the node identified by a path. This method is
	 * a convenience method that can only be relied on if the path to the
	 * node can be found by taking the first element matching each step of
	 * the path.
	 * @param path the path from the root element to the node. Note that this
	 * method uses the XmlUtil.getValueViaPath(String) method, and the path
	 * syntax is slightly different from that of XPath. Specifically,
	 * attributes are identified as "node@attribute" rather than "node/@attribute".
	 * @return the value, or the empty string if the node identified by the path is missing.
	 */
	public String getValue(String path) {
		if (document == null) return "";
		return XmlUtil.getValueViaPath(document.getDocumentElement(), path);
	}

	/**
	 * Get the text of this object.
	 * @return the complete text of the file.
	 */
	public String getText() {
		if (document == null) return "";
		return FileUtil.getText(file);
	}

	/**
	 * Make a String from this object.
	 * @return the XML string for the object, including
	 * an XML declaration specifying an encoding of UTF-8.
	 */
	public String toString() {
		if (document == null) return "";
		return XmlUtil.toString(document);
	}

	/**
	 * Evaluate a boolean script for this XmlObject. See the RSNA
	 * CTP wiki article (The CTP XmlFilter) for information on the
	 * script language.
	 * @param scriptFile the text file containing the expression
	 * to compute based on the values in this XmlObject.
	 * @return the computed boolean value of the script.
	 */
	public boolean matches(File scriptFile) {
		String script = FileUtil.getText(scriptFile);
		return XmlUtil.matches(document, script);
	}

	/**
	 * Evaluate a boolean script for this XmlObject. See the RSNA
	 * CTP wiki article (The CTP XmlFilter) for information on the
	 * script language.
	 * @param script the expression to compute based on the values
	 * in this XmlObject.
	 * @return the computed boolean value of the script.
	 */
	public boolean matches(String script) {
		return XmlUtil.matches(document, script);
	}

}
