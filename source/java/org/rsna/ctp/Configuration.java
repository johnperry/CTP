/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.log4j.Logger;
import org.rsna.ctp.pipeline.ExportService;
import org.rsna.ctp.pipeline.ImportService;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Quarantine;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.server.HttpServer;
import org.rsna.util.CipherUtil;
import org.rsna.util.ClasspathUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.ImageIOTools;
import org.rsna.util.IPUtil;
import org.rsna.util.JarUtil;
import org.rsna.util.ProxyServer;
import org.rsna.util.SSLConfiguration;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The class that encapsulates the configuration of the system.
 * The configuration is specified by the config.xml file, which
 * must be located in the root directory of the application. This
 * class is a singleton. Instances of this class must be obtained
 * through the static getInstance() method.
 */
public class Configuration {

	static final Logger logger = Logger.getLogger(Configuration.class);

    public static final String configFN	= "config.xml";
    static final String exconfigFN	= "examples/example-config.xml";

	static Configuration configuration = null;

	HttpServer httpServer = null;
	File configFile;
	Document configXML = null;
	int serverPort = 80;
	boolean ssl = false;
	int maxThreads = 20;
	String usersClassName = "org.rsna.server.UsersXmlFileImpl";
	boolean requireAuthentication = false;
	String ipAddress = getIPAddress();
	List<Pipeline> pipelines = null;
	List<Plugin> pluginsList = null;
	Hashtable<String,String> manifest = null;
	Hashtable<String,PipelineStage> stages = null;
	Hashtable<String,Plugin> plugins = null;
	Element serverElement = null;
	String ctpBuild = "";
	String ctpJava = "";
	boolean isMIRC = false;

	/**
	 * Load the singleton instance of the Configuration.
	 * @return the Configuration.
	 */
	public static synchronized Configuration load() {
		if (configuration == null) configuration = new Configuration();
		return configuration;
	}

	/**
	 * Get the singleton instance of the Configuration.
	 * @return the Configuration.
	 */
	public static synchronized Configuration getInstance() {
		return configuration;
	}

	//The protected constructor. This can only be called by
	//Configuration.getInstance(), thus ensuring that it is
	//only called once.
	protected Configuration() {
		try {
			//Log the environment
			String thisOS = System.getProperty("os.name");
			String thisJavaVersion = System.getProperty("java.version");
			String thisJavaVendor = System.getProperty("java.vendor");
			String thisJavaVMVendor = System.getProperty("java.vm.vendor");
			String thisJavaVMVersion = System.getProperty("java.vm.version");
			String thisJavaBits = System.getProperty("sun.arch.data.model") + " bits";
			String thisJavaMaxMemory = String.format("%,d bytes", Runtime.getRuntime().maxMemory());
			int thisJCEKeyLength = CipherUtil.getMaxAllowedKeyLength();
			int thisJCEMaxKeyLength = CipherUtil.getMaxAllowedKeyLength("AES");

			//Load the ImageIO Tools and get the version
			String thisImageIOVersion = ImageIOTools.load(new File("libraries/imageio"));

			logger.info("Operating system:    " + thisOS);
			logger.info("Java vendor:         " + thisJavaVendor);
			logger.info("Java version:        " + thisJavaVersion);
			logger.info("Java VM vendor:      " + thisJavaVMVendor);
			logger.info("Java VM version:     " + thisJavaVMVersion);
			logger.info("Java data model:     " + thisJavaBits);
			logger.info("Java max memory:     " + thisJavaMaxMemory);
			logger.info("CTP crypto strength: " + thisJCEKeyLength);
			logger.info("Max crypto strength: " + thisJCEMaxKeyLength);
			logger.info("ImageIO Tools:       " + thisImageIOVersion);

			//Get the jar manifest so we can get CTP attributes later
			manifest = JarUtil.getManifestAttributes(new File("libraries/CTP.jar"));
			ctpJava = manifest.get("Java-Version");

			//Log the application libraries
			ctpBuild = logManifestAttribute(new File("libraries/CTP.jar"),	"Date",    "CTP build:           ");
			logManifestAttribute(new File("libraries/util.jar"),			"Date",    "Util build:          ");
			logManifestAttribute(new File("libraries/MIRC.jar"),			"Date",    "MIRC build:          ");
			logManifestAttribute(new File("libraries/isn/ISN.jar"), 		"Date",    "ISN build:           ");
			logManifestAttribute(new File("libraries/MIRC.jar"), 			"Version", "MIRC version:        ");
			logManifestAttribute(new File("libraries/isn/ISN.jar"), 		"Version", "ISN version:         ");

			logger.info("Start time:          "+StringUtil.getDateTime(" at "));
			logger.info("user.dir:            "+System.getProperty("user.dir"));
			logger.info("java.ext.dirs:       "+System.getProperty("java.ext.dirs"));
			logger.info("IP Address:          "+IPUtil.getIPAddress() + "\n");

			//Instantiate the stages table
			stages = new Hashtable<String,PipelineStage>();

			//Instantiate the plugins table
			plugins = new Hashtable<String,Plugin>();

			//Get the configuration file.
			configFile = FileUtil.getFile(configFN, exconfigFN);

			//Parse the configuration file
			configXML = XmlUtil.getDocument(configFile);
			Element root = configXML.getDocumentElement();

			//Log the configuration.
			logger.info("Classpath:\n"+ClasspathUtil.listClasspath());
			logger.info("Configuration:\n" + XmlUtil.toPrettyString(root));
			logger.info("Available ImageIO Codecs:\n" + ImageIOTools.listAvailableCodecs());

			//Get the children and instantiate them.
			//Save the parameters of the Server element.
			//Add all Pipeline elements to the pipelines List.
			//Load all the Plugin elements
			pipelines = new ArrayList<Pipeline>();
			pluginsList = new ArrayList<Plugin>();
			Node child = root.getFirstChild();
			while (child != null) {
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					Element childElement = (Element)child;
					String tagName = childElement.getTagName();
					if (tagName.equals("Server")) {
						serverElement = childElement;

						serverPort = StringUtil.getInt(serverElement.getAttribute("port"), 80);
						ssl = serverElement.getAttribute("ssl").equals("yes");
						maxThreads = StringUtil.getInt(serverElement.getAttribute("maxThreads"), 4);
						String temp = serverElement.getAttribute("usersClassName").trim();
						if (!temp.equals("")) usersClassName = temp;
						requireAuthentication = serverElement.getAttribute("requireAuthentication").equals("yes");

						//Set the proxy parameters
						ProxyServer proxy = ProxyServer.getInstance(serverElement);
						proxy.setSystemParameters();

						//Set the SSL params
						SSLConfiguration sslConfig = SSLConfiguration.getInstance(serverElement);
						sslConfig.setSystemParameters();
					}
					else if (tagName.equals("Pipeline") && !childElement.getAttribute("enabled").equals("no")) {
						Pipeline pipe = new Pipeline(childElement, pipelines.size());
						pipelines.add(pipe);
						List<PipelineStage> list = pipe.getStages();
						for (PipelineStage stage : list) registerStage(stage);
					}
					else if (tagName.equals("Plugin")) {
						String className = childElement.getAttribute("class").trim();
						if (!className.equals("")) {
							try {
								Class theClass = Class.forName(className);
								Class[] signature = { Element.class };
								Constructor constructor = theClass.getConstructor(signature);
								Object[] args = { childElement };
								Plugin plugin = (Plugin)constructor.newInstance(args);
								registerPlugin(plugin);
								pluginsList.add(plugin);
								if (className.equals("mirc.MIRC")) isMIRC = true;
							}
							catch (Exception ex) { logger.error(childElement.getAttribute("name") + ": Unable to load "+className,ex); }
						}
						else logger.error(childElement.getAttribute("name") + ": Plugin with no class attribute");
					}
				}
				child = child.getNextSibling();
			}
			logDuplicateRoots();
		}
		catch (Exception ex) {
			logger.error("Error loading the configuration.", ex);
		}
	}

	private String logManifestAttribute(File jarFile, String name, String prefix) {
		String value = "";
		if (jarFile.exists()) {
			Hashtable<String,String> manifest = JarUtil.getManifestAttributes(jarFile);
			if (manifest != null) {
				value = manifest.get(name);
				if (value != null) logger.info(prefix + value);
			}
		}
		return value;
	}

	/**
	 * Start the system.
	 * @param httpServer the main admin server
	 */
	public void start(HttpServer httpServer) {
		this.httpServer = httpServer;

		//Start the plugins.
		for (Plugin plugin : pluginsList) {
			plugin.start();
		}

		//Start the pipelines.
		for (Pipeline pipe : pipelines) {
			pipe.start();
		}

		//Start the web server.
		if (httpServer != null) httpServer.start();

		//Start the quarantine purge thread
		Quarantine.startQuarantinePurge();
	}

	/**
	 * Initiate a shutdown for all the pipelines.
	 */
	public void shutdownPipelines() {
		for (Pipeline pipe : pipelines) pipe.shutdown();
	}

	/**
	 * Check whether all the pipelines have shut down.
	 * @return true if all pipelines have cleanly shut down; false otherwise.
	 */
	public boolean pipelinesAreDown() {
		for (Pipeline pipe : pipelines) {
			if (!pipe.isDown()) return false;
		}
		return true;
	}

	/**
	 * Initiate a shutdown for all the plugins, in reverse order.
	 */
	public void shutdownPlugins() {
		Plugin[] p = pluginsList.toArray( new Plugin[pluginsList.size()] );
		for (int i=p.length-1; i>=0; i--) {
			p[i].shutdown();
		}
	}

	/**
	 * Check whether all plugins have shut down.
	 * @return true if all plugins have cleanly shut down; false otherwise.
	 */
	public boolean pluginsAreDown() {
		for (Plugin plugin : pluginsList) {
			if (!plugin.isDown()) return false;
		}
		return true;
	}

	/**
	 * Get a value from the manifest.
	 * @param name the name of the attribute
	 * @return the value from the manifest, or the empty string if the
	 * requested value is missing.
	 */
	public String getManifestAttribute(String name) {
		String value = manifest.get(name);
		if (value == null) value = "";
		return value;
	}

	/**
	 * Get the CTP build version.
	 * @return the CTP build version.
	 */
	public String getCTPBuild() {
		return ctpBuild;
	}

	/**
	 * Get the CTP Java version.
	 * @return the CTP Java version.
	 */
	public String getCTPJava() {
		return ctpJava;
	}

	/**
	 * Get the Configuration document.
	 * @return the Configuration XML Document.
	 */
	public Document getConfigurationDocument() {
		return configXML;
	}

	/**
	 * Get the Server element.
	 * @return the Server element from the config file.
	 */
	public Element getServerElement() {
		return serverElement;
	}

	/**
	 * Get a boolean indicating whether the MIRC plugin
	 * is present in the configuration.
	 * @return true if the MIRC plugin is present; false otherwise;
	 */
	public boolean isMIRC() {
		return isMIRC;
	}

	/**
	 * Get the class name of the Users class. If the usersClass attribute of
	 * the Server element is not present, the default class name is supplied
	 * (org.rsna.server.UsersXmlFileImpl).
	 * @return the class name of the Users class.
	 */
	public String getUsersClassName() {
		return usersClassName;
	}

	/**
	 * Determine whether authentication is required for the admin web server.
	 * @return true if authentication is required; false otherwise.
	 */
	public boolean getRequireAuthentication() {
		return requireAuthentication;
	}

	/**
	 * Get the HTTP server instance.
	 * @return the HTTP server instance, or null if the system has not yet started.
	 */
	public HttpServer getServer() {
		return httpServer;
	}

	/**
	 * Get the port on which the HTTP server is configured to run.
	 * @return the port on which the HTTP server is configured to run.
	 */
	public int getServerPort() {
		return serverPort;
	}

	/**
	 * Get the protocol of the HTTP server.
	 * @return true if the HTTP server is to use SSL; false otherwise.
	 */
	public boolean getServerSSL() {
		return ssl;
	}

	/**
	 * Get the maximum number of threads allowed on the HTTP server.
	 * @return the maximum number of threads allowed on the HTTP server.
	 */
	public int getServerMaxThreads() {
		return maxThreads;
	}

	/**
	 * Get the computer's IP address from the OS.
	 * @return the computer's IP address.
	 */
	public String getIPAddress() {
		return IPUtil.getIPAddress();
	}

	/**
	 * Register a PipelineStage using its id attribute so that other
	 * components can access it. The stage is registered only if its
	 * id attribute is not null and has at least one non-whitespace
	 * character.
	 * @param stage the stage to be registered.
	 */
	public void registerStage(PipelineStage stage) {
		registerStage(stage, stage.getID());
	}

	/**
	 * Register a PipelineStage using a supplied id value.
	 * The stage is registered only if the supplied id is
	 * not null and has at least one non-whitespace character.
	 * @param stage the stage to be registered.
	 * @param id the id under which to register the stage.
	 */
	public void registerStage(PipelineStage stage, String id) {
		if (id != null) {
			id = id.trim();
			if (!id.equals("")) stages.put(id, stage);
		}
	}

	/**
	 * Get a registered PipelineStage from the stages table.
	 * @param id the ID of the PipelineStage to be returned.
	 * @return the registered PipelineStage with the specified ID,
	 * or null if no stage with that ID has been registered.
	 */
	public PipelineStage getRegisteredStage(String id) {
		if (id == null) return null;
		return stages.get(id);
	}

	/**
	 * Register a Plugin so that other components can access it.
	 * The plugin is registered only if its id is not null and has at least
	 * one non-whitespace character.
	 * @param plugin the plugin to be registered.
	 */
	public void registerPlugin(Plugin plugin) {
		String id = plugin.getID();
		if (id != null) {
			id = id.trim();
			if (!id.equals("")) plugins.put(id, plugin);
		}
	}

	/**
	 * Get a registered Plugin from the plugins table.
	 * @param id the ID of the Plugin to be returned.
	 * @return the registered Plugin with the specified ID,
	 * or null if no Plugin with that ID has been registered.
	 */
	public Plugin getRegisteredPlugin(String id) {
		if (id == null) return null;
		return plugins.get(id);
	}

	/**
	 * Get the list of Pipeline objects.
	 * @return the list of Pipeline objects.
	 */
	public List<Pipeline> getPipelines() {
		return pipelines;
	}

	/**
	 * Get the list of Plugin objects.
	 * @return the list of Plugin objects.
	 */
	public List<Plugin> getPlugins() {
		return pluginsList;
	}

	//Check the root directories of all the
	//pipeline stages and log any duplicates.
	private void logDuplicateRoots() {
		Hashtable<File,PipelineStage> roots = new Hashtable<File,PipelineStage>();
		for (Pipeline pipe : pipelines) {
			for (PipelineStage stage : pipe.getStages()) {
				File root = stage.getRoot();
				if (root != null) {
					PipelineStage ps = roots.get(root);
					if ((ps != null) && root.getAbsoluteFile().equals(ps.getRoot().getAbsoluteFile())) {
						logger.warn("Duplicate root directory: "+pipe.getPipelineName()+": "+stage.getName());
					}
					else roots.put(root, stage);
				}
			}
		}
	}

}