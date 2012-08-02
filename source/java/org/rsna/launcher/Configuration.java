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

	public static Configuration getInstance() {
		return config;
	}

	public static Configuration load() throws Exception {
		config = new Configuration();
		return config;
	}

	protected Configuration() throws Exception {

		//Get the configuration parameters from the CTP config file.
		configXML = Util.getDocument(configFile);
		if (configXML == null) throw new Exception("The config file is missing or does not parse.");

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
		File clib = new File(extDir, "clibwrapper_jiio.jar");
		File jai = new File(extDir, "jai_imageio.jar");
		imageIOTools = clib.exists() && jai.exists();
		if (imageIOTools) {
			Hashtable<String,String> jaiManifest = Util.getManifestAttributes(jai);
			imageIOVersion  = jaiManifest.get("Implementation-Version");
		}

		//Get the CTP.jar parameters
		Hashtable<String,String> manifest = Util.getManifestAttributes(ctp);
		ctpDate = manifest.get("Date");
		ctpJava = manifest.get("Java-Version");

		//Get the util.jar parameters
		Hashtable<String,String> utilManifest = Util.getManifestAttributes("libraries/util.jar");
		utilDate = utilManifest.get("Date");
		utilJava = utilManifest.get("Java-Version");

		//Get the MIRC.jar parameters (if the plugin is present)
		Hashtable<String,String> mircManifest = Util.getManifestAttributes("libraries/MIRC.jar");
		if (mircManifest != null) {
			mircJava = mircManifest.get("Java-Version");
			mircDate = mircManifest.get("Date");
			mircVersion = mircManifest.get("Version");
		}

		//Get the ISN.jar parameters
		Hashtable<String,String> isnManifest = Util.getManifestAttributes("libraries/isn/ISN.jar");
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
		if (isMIRC) javaOK &= (thisJava.compareTo(mircJava) >= 0);
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
		Util.setText( configFile, Util.toPrettyString( configXML ) );
	}

}
