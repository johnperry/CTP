/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.io.*;
import java.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.rsna.util.FileUtil;
import org.rsna.util.JarUtil;
import org.rsna.util.XmlUtil;

public class Configuration {

	public String windowTitle = "";
	public String programName = "";
	public String browserButtonName = "";
	public String ctpDate = "";
	public String ctpJava = "";
	public String utilJava = "";
	public String utilDate = "";
	public String mircJava = "";
	public String mircDate = "";
	public String mircVersion = "";
	public String isnJava = "";
	public String isnDate = "";
	public String isnVersion = "";
	public String imageIOVersion  = "";
	public String thisJava = "";
	public String thisJavaBits = "";
	public boolean imageIOTools = false;

	public boolean isMIRC;
	public boolean isISN;
	public int port;
	public boolean ssl;
	public Properties props;

	public Document configXML;

	File configFile = new File("config.xml");
	File propsFile = new File("Launcher.properties");
	File ctp = new File("libraries/CTP.jar");

	static Configuration config = null;

	public static synchronized Configuration getInstance() {
		return config;
	}

	public static Configuration load() throws Exception {
		config = new Configuration();
		return config;
	}

	protected Configuration() throws Exception {

		//Get the configuration parameters from the CTP config file.
		configXML = XmlUtil.getDocument(configFile);

		isMIRC = Util.containsAttribute(configXML, "Plugin", "class", "mirc.MIRC");
		isISN = (new File("libraries/isn/ISN.jar")).exists();

		try { port = Integer.parseInt( Util.getAttribute(configXML, "Server", "port") ); }
		catch (Exception ex) { port = 0; }
		ssl = Util.getAttribute(configXML, "Server", "ssl").equals("yes");

		if (isMIRC) programName = "RSNA Teaching File System";
		else if (isISN) programName = "RSNA ISN";
		else programName = "RSNA CTP";

		browserButtonName = isMIRC ? "TFS" : (isISN ? "ISN" : "CTP");
		windowTitle = programName + " Launcher";

		//Get the installation information
		thisJava = System.getProperty("java.version");
		thisJavaBits = System.getProperty("sun.arch.data.model") + " bits";

		//Find the ImageIO Tools and get the version
		String javaHome = System.getProperty("java.home");
		File extDir = new File(javaHome);
		extDir = new File(extDir, "lib");
		extDir = new File(extDir, "ext");
		File clib = getFile(extDir, "clibwrapper_jiio", ".jar");
		File jai = getFile(extDir, "jai_imageio", ".jar");
		imageIOTools = (clib != null) && clib.exists() && (jai != null) && jai.exists();
		if (imageIOTools) {
			Hashtable<String,String> jaiManifest = JarUtil.getManifestAttributes(jai);
			imageIOVersion  = jaiManifest.get("Implementation-Version");
		}

		//Get the CTP.jar parameters
		Hashtable<String,String> manifest = JarUtil.getManifestAttributes(ctp);
		ctpDate = manifest.get("Date");
		ctpJava = manifest.get("Java-Version");

		//Get the util.jar parameters
		Hashtable<String,String> utilManifest = JarUtil.getManifestAttributes(new File("libraries/util.jar"));
		utilDate = utilManifest.get("Date");
		utilJava = utilManifest.get("Java-Version");

		//Get the MIRC.jar parameters (if the plugin is present)
		Hashtable<String,String> mircManifest = JarUtil.getManifestAttributes(new File("libraries/MIRC.jar"));
		if (mircManifest != null) {
			mircJava = mircManifest.get("Java-Version");
			mircDate = mircManifest.get("Date");
			mircVersion = mircManifest.get("Version");
		}

		//Get the ISN.jar parameters
		Hashtable<String,String> isnManifest = JarUtil.getManifestAttributes(new File("libraries/isn/ISN.jar"));
		if (isnManifest != null) {
			isnJava = isnManifest.get("Java-Version");
			isnDate = isnManifest.get("Date");
			isnVersion = isnManifest.get("Version");
		}

		//Set up the installation information for display
		if (imageIOVersion .equals("")) {
			imageIOVersion  = "<b><font color=\"red\">not installed</font></b>";
		}
		else if (imageIOVersion .startsWith("1.0")) {
			imageIOVersion  = "<b><font color=\"red\">"+imageIOVersion +"</font></b>";
		}
		if (thisJavaBits.startsWith("64")) {
			thisJavaBits = "<b><font color=\"red\">"+thisJavaBits+"</font></b>";
		}
		boolean javaOK = (thisJava.compareTo(ctpJava) >= 0);
		javaOK &= (thisJava.compareTo(utilJava) >= 0);
		if (isMIRC && (mircJava != null)) javaOK &= (thisJava.compareTo(mircJava) >= 0);
		if (!javaOK) {
			thisJava = "<b><font color=\"red\">"+thisJava+"</font></b>";
		}

		//Get the properties
		props = new Properties();
		try { if (propsFile.exists())  props.load( new FileInputStream( propsFile ) ); }
		catch (Exception useDefaults) { }
		String mx = props.getProperty("mx");
		if ((mx == null) || mx.trim().equals("")) props.setProperty("mx", "256");
		String ms = props.getProperty("ms");
		if ((ms == null) || ms.trim().equals("")) props.setProperty("ms", "128");
	}

	private File getFile(File dir, String nameStart, String nameEnd) {
		File[] files = dir.listFiles( new NameFilter(nameStart, nameEnd) );
		if (files.length == 0) return null;
		return files[0];
	}
	
	class NameFilter implements FileFilter {
		String nameStart;
		String nameEnd;
		public NameFilter(String nameStart, String nameEnd) {
			this.nameStart = nameStart;
			this.nameEnd = nameEnd;
		}
		public boolean accept(File file) {
			String name = file.getName();
			return name.startsWith(nameStart) && name.endsWith(nameEnd);
		}
	}

	public boolean hasPipelines() {
		if (configXML != null) {
			Element root = configXML.getDocumentElement();
			Node child = root.getFirstChild();
			while (child != null) {
				if ((child instanceof Element) && child.getNodeName().equals("Pipeline")) return true;
				child = child.getNextSibling();
			}
		}
		return false;
	}

	public void reloadXML() {
		try {
			configXML = XmlUtil.getDocument(configFile);
			try { port = Integer.parseInt( Util.getAttribute(configXML, "Server", "port") ); }
			catch (Exception ex) { port = 0; }
			ssl = Util.getAttribute(configXML, "Server", "ssl").equals("yes");
		}
		catch (Exception ignore) { }
	}

	public void save() {
		FileOutputStream stream = null;
		try {
			stream = new FileOutputStream( propsFile );
			props.store( stream, propsFile.getName() );
			stream.flush();
			stream.close();
		}
		catch (Exception e) {
			if (stream != null) {
				try { stream.close(); }
				catch (Exception ignore) { }
			}
		}
	}

	public void saveXML() throws Exception {
		FileUtil.setText( configFile, XmlUtil.toPrettyString( configXML ) );
	}

}
