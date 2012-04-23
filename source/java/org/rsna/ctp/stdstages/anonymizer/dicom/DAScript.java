/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.util.Hashtable;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A DICOM Anonymizer script.
 */
public class DAScript {

	static final Logger logger = Logger.getLogger(DAScript.class);

	static Hashtable<String,DAScript> scripts = new Hashtable<String,DAScript>();

	public File file;
	public String script;
	public boolean scriptIsXML = false;
	public String xmlScript = null;
	public Document xml = null;
	public Properties properties = null;
	public long lastVersionLoaded = 0;

	/**
	 * Get the singleton instance of a DAScript, loading a new instance
	 * if the script file has changed.
	 * @param file the file containing the script.
	 */
	public static synchronized DAScript getInstance(File file) {
		//First see if we already have an instance in the table
		String path = file.getAbsolutePath().replaceAll("\\\\","/");
		DAScript das = scripts.get(path);
		if ( (das != null) && das.isCurrent() ) return das;

		//We didn't get a current instance from the table; create one.
		das = new DAScript(file);

		//Put this instance in the table and then return it.
		scripts.put(path, das);
		return das;
	}

	/**
	 * Protected constructor; create a DAScript from either a properties file or an XML file.
	 * @param file the file containing the script.
	 */
	protected DAScript(File file) {
		this.file = file;
		this.script = FileUtil.getText(file, FileUtil.utf8);
		this.lastVersionLoaded = file.lastModified();

		//See if this might be an xml document.
		Pattern xmlPattern = Pattern.compile("^\\s*<", Pattern.DOTALL | Pattern.MULTILINE);
		Matcher xmlMatcher = xmlPattern.matcher(script);
		scriptIsXML = xmlMatcher.find();
	}

	/**
	 * Determine whether the script file has changed since it was last loaded.
	 * If the script file has changed in the last 5 seconds, it is ignored
	 * to ensure that we don't jump on a file that is still being modified.
	 * @return true if this DAScript instance is up-to-date; false if the file has changed.
	 */
	public boolean isCurrent() {
		long lastModified = file.lastModified();
		long age = System.currentTimeMillis() - lastModified;
		return ( (lastVersionLoaded >= lastModified) || (age < 5000) );
	}

	/**
	 * Get the script as an XML string.
	 * @return the script as an XML string
	 */
	public String toXMLString() {
		if (scriptIsXML) return script;

		if (xmlScript != null) return xmlScript;

		if (xml == null) toXML();

		if (xml != null) {
			xmlScript = XmlUtil.toString(xml);
			return xmlScript;
		}
		xmlScript = "<script/>";
		return xmlScript;
	}

	public Properties toProperties() {
		if (properties != null) return properties;

		if (!scriptIsXML) {
			try {
				//This is a kludge to make it compile in Java 1.5 so the folks at NCI can
				//build it. The props.load(Reader) method is not supported in 1.5; only 1.6.
/*Java1.5*/		ByteArrayInputStream sr = new ByteArrayInputStream(script.getBytes("ISO-8859-1"));
/*Java1.6*/		/*StringReader sr = new StringReader(script);*/

				properties = new Properties();
				properties.load(sr);
			}
			catch (Exception ex) {
				logger.warn("Unable to read the properties stream.");
			}
			return properties;
		}
		return (properties = makeProperties());
	}

	//This method must only be called when the script is XML.
	//If it is called with an XML script, it returns an empty
	//Properties object.
	private Properties makeProperties() {
		Properties props = new Properties();
		if (toXML() == null) return props;
		Element root = xml.getDocumentElement();
		Node child = root.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				Element eChild = (Element)child;
				String tag = eChild.getTagName();
				if (tag.equals("p")) addParam(props, eChild);
				else if (tag.equals("e")) addElement(props, eChild);
				else if (tag.equals("k")) addKeep(props, eChild);
				else if (tag.equals("r")) addRemove(props, eChild);
			}
			child = child.getNextSibling();
		}
		properties = props;
		return props;
	}

	private void addParam(Properties props, Element x) {
		String key = "param." + x.getAttribute("t");
		String value = x.getTextContent();
		props.setProperty(key, value);
	}

	private void addElement(Properties props, Element x) {
		String sel = (x.getAttribute("en").equals("T") ? "" : "#");
		String t = x.getAttribute("t");
		String elem = "[" + t.substring(0,4) + "," + t.substring(4) + "]";
		String name = x.getAttribute("n");
		String key = sel + "set." + elem + name;
		String value = x.getTextContent().trim();
		props.setProperty(key, value);
	}

	private void addKeep(Properties props, Element x) {
		String sel = (x.getAttribute("en").equals("T") ? "" : "#");
		String t = x.getAttribute("t");
		String group = "group" + t.substring(2,4);
		String key = sel + "keep." + group;
		props.setProperty(key, "");
	}

	private void addRemove(Properties props, Element x) {
		String sel = (x.getAttribute("en").equals("T") ? "" : "#");
		String t = x.getAttribute("t");
		String key = sel + "remove." + t;
		props.setProperty(key, "");
	}

	public Document toXML() {
		if (xml == null) {
			if (scriptIsXML) {
				//This is an XML file; parse it.
				try { xml = XmlUtil.getDocument(script); }
				catch (Exception ex) { logger.warn(ex); }
			}
			else {
				//This must be a properties file; convert
				//it to XML, save the result, and return it;
				try {
					Document doc = XmlUtil.getDocument();
					Element root = doc.createElement("script");
					doc.appendChild(root);
					BufferedReader br = new BufferedReader(new StringReader(script));
					String line;
					while ( (line=br.readLine()) != null ) {
						line = line.trim();
						int k = line.indexOf("=");
						if (k != -1) {
							String left = line.substring(0,k);
							String right = line.substring(k+1);
							if (left.startsWith("param.")) {
								root.appendChild(createParam(doc, left, right));
							}
							else if (left.startsWith("set.") || left.startsWith("#set.")) {
								root.appendChild(createElement(doc, left, right));
							}
							else if (left.startsWith("keep.") || left.startsWith("#keep.")) {
								root.appendChild(createKeep(doc, left, right));
							}
							else if (left.startsWith("remove.") || left.startsWith("#remove.")) {
								root.appendChild(createRemove(doc, left, right));
							}
						}
					}
					xml = doc;
				}
				catch (Exception ex) { logger.warn(ex); }
			}
		}
		return xml;
	}

	private Element createParam(Document doc, String left, String right) {
		Element p = doc.createElement("p");
		p.setAttribute("t", left.substring(6).trim());
		p.appendChild(doc.createTextNode(right));
		return p;
	}

	private Element createElement(Document doc, String left, String right) {
		Element e = doc.createElement("e");
		String en;
		if (left.startsWith("#")) {
			en = "F";
			left = left.substring(1);
		}
		else en = "T";
		e.setAttribute("en", en);
		String t = "00000000";
		String n = "";
		int q = left.indexOf("[");
		if (q != -1) {
			int qq = left.indexOf("]", q);
			if (qq != -1) {
				t = left.substring(q, qq);
				t = t.replaceAll("[^0-9a-fA-f]", "");
				n = left.substring(qq+1).trim();
			}
		}
		e.setAttribute("t", t);
		e.setAttribute("n", n);
		e.appendChild(doc.createTextNode(right));
		return e;
	}

	private Element createKeep(Document doc, String left, String right) {
		Element k = doc.createElement("k");
		String en;
		if (left.startsWith("#")) {
			en = "F";
			left = left.substring(1);
		}
		else en = "T";
		k.setAttribute("en", en);
		String t = left.substring(10).trim();
		while (t.length() < 4) t = "0" + t;
		k.setAttribute("t", t);
		k.setAttribute("n", right.trim());
		return k;
	}

	private Element createRemove(Document doc, String left, String right) {
		Element r = doc.createElement("r");
		String en;
		if (left.startsWith("#")) {
			en = "F";
			left = left.substring(1);
		}
		else en = "T";
		r.setAttribute("en", en);
		String t = left.substring(7).trim();
		r.setAttribute("t", t);
		String n = "";
		if (t.equals("privategroups")) n="Remove private groups [recommended]";
		else if (t.equals("unspecifiedelements")) n="Remove unchecked elements";
		else if (t.equals("curves")) n="Remove curves";
		else if (t.equals("overlays")) n="Remove overlays";
		r.setAttribute("n", n);
		return r;
	}
}
