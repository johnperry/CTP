/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp;

import java.io.File;
import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.ctp.servlets.*;
import org.rsna.server.Authenticator;
import org.rsna.server.HttpServer;
import org.rsna.server.ServletSelector;
import org.rsna.server.Users;
import org.rsna.servlets.*;
import org.rsna.util.AcceptAllHostnameVerifier;
import org.rsna.util.Cache;
import org.rsna.util.ClasspathUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.ImageIOTools;
import org.rsna.util.JarClassLoader;
import org.rsna.util.ProxyServer;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The ClinicalTrialProcessor program.
 */
public class ClinicalTrialProcessor {

	static final File libraries = new File("libraries");
	static final String mainClassName = "org.rsna.ctp.ClinicalTrialProcessor";
	static boolean running = true;
	static Logger logger = null;

	/**
	 * The main method of the ClinicalTrialProcessor program.
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		//Make sure the libraries directory is present
		libraries.mkdirs();
		
		//Get a JarClassLoader pointing to the libraries directory
		JarClassLoader cl = 
			JarClassLoader.getInstance(new File[] { libraries });
			
		//Set the context classloader to allow dcm4che to load its classes
		Thread.currentThread().setContextClassLoader(cl);

		//Load the class and instantiate it
		try {
			Class ctpClass = cl.loadClass(mainClassName);
			ctpClass.getConstructor( new Class[0] ).newInstance( new Object[0] );
		}
		catch (Exception unable) { unable.printStackTrace(); }
	}

	/**
	 * The startup method of the ClinicalTrialProcessor program.
	 * This method is used when running CTP as a Windows service.
	 * It does not return until the stopService method is called
	 * independently by the service manager.
	 * @param args the command line arguments
	 */
	public static void startService(String[] args) {
		System.out.println("Start [ServiceManager]");
		main(args);
		while (running) {
			try { Thread.sleep(2000); }
			catch (Exception ignore) { }
		}
		if (logger != null) logger.info("startService returned\n");
		System.out.println("Stop [ServiceManager]");
		Runnable r = new Runnable() {
			public void run() {
				try { Thread.sleep(1000); }
				catch (Exception ignore) { }
				System.exit(0);
			}
		};
		new Thread(r).start();
	}

	/**
	 * The shutdown method of the ClinicalTrialProcessor program.
	 * This method is used when running CTP as a Windows service.
	 * This method makes an HTTP connection to the ShutdownServlet
	 * to trigger the plugins and pipelines to close down gracefully.
	 * @param args the command line arguments
	 */
	public static void stopService(String[] args) {
		try {
			Document doc = XmlUtil.getDocument(new File("config.xml"));
			Element root = doc.getDocumentElement();
			Element server = XmlUtil.getElementViaPath(root, "Configuration/Server");
			int port = StringUtil.getInt(server.getAttribute("port").trim());
			boolean ssl = server.getAttribute("ssl").trim().equals("yes");
			
			URL url = new URL( "http" + (ssl?"s":"") + "://127.0.0.1:" + port + "/shutdown" );
			HttpURLConnection conn = HttpUtil.getConnection(url);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("servicemanager", "stayalive"); // was: "exit"
			conn.connect();

			String result = FileUtil.getText( conn.getInputStream() );
			if (result.contains("Goodbye.")) {
				System.out.println("Normal shutdown [ServiceManager]");
				running = false;
				System.exit(0);
			}
			else System.out.println("Unable to service the shutdown request from ServiceManager.");
		}
		catch (Exception keepRunning) { keepRunning.printStackTrace(); }
	}

	/**
	 * The constructor of the ClinicalTrialProcessor program.
	 * There is no UI presented by the program. All access to
	 * the configuration and status of the program is presented
	 * through the HTTP server.
	 */
	public ClinicalTrialProcessor() {

		//Initialize Log4J
		File logs = new File("logs");
		logs.mkdirs();
		File logProps = new File("log4j.properties");
		String propsPath = logProps.getAbsolutePath();
		if (!logProps.exists()) {
			System.out.println("Logger configuration file: "+propsPath);
			System.out.println("Logger configuration file not found.");
		}
		PropertyConfigurator.configure(propsPath);
		logger = Logger.getLogger(ClinicalTrialProcessor.class);
		
		//Instantiate the singleton Cache, clear it, and preload
		//files from the jars. Other files will be loaded as required.
		Cache cache = Cache.getInstance(new File("CACHE"));
		cache.clear();
		logger.info("Cache cleared");
		File libraries = new File("libraries");
		cache.load(new File(libraries, "util.jar"));
		cache.load(new File(libraries, "CTP.jar"));

		//Get the configuration
		Configuration config = Configuration.load();

		//Instantiate the singleton Users class
		Users users = Users.getInstance(config.getUsersClassName(), config.getServerElement());

		//Add the CTP roles
		String[] roles = { "read", "delete", "import", "export", "qadmin", "guest", "proxy" };
		for (String role : roles) users.addRole(role);

		//Disable session timeouts for the server
		Authenticator.getInstance().setSessionTimeout( 0L );

		//Create the ServletSelector for the HttpServer
		ServletSelector selector =
				new ServletSelector(
						new File("ROOT"),
						config.getRequireAuthentication());

		//Add in the servlets
		selector.addServlet("login",		LoginServlet.class);
		selector.addServlet("users",		UserManagerServlet.class);
		selector.addServlet("user",			UserServlet.class);
		selector.addServlet("logs",			LogServlet.class);
		selector.addServlet("configuration",ConfigurationServlet.class);
		selector.addServlet("status",		StatusServlet.class);
		selector.addServlet("quarantines",	QuarantineServlet.class);
		selector.addServlet("idmap",		IDMapServlet.class);
		selector.addServlet("objectlogger",	ObjectLoggerServlet.class);
		selector.addServlet("objecttracker",ObjectTrackerServlet.class);
		selector.addServlet("databaseverifier",DBVerifierServlet.class);
		selector.addServlet("decipher",		DecipherServlet.class);
		selector.addServlet("system",		SysPropsServlet.class);
		selector.addServlet("environment",	EnvironmentServlet.class);
		selector.addServlet("daconfig",		DicomAnonymizerServlet.class);
		selector.addServlet("script",		ScriptServlet.class);
		selector.addServlet("lookup",		LookupServlet.class);
		selector.addServlet("webstart",		ApplicationServer.class);
		selector.addServlet("level",		LoggerLevelServlet.class);
		selector.addServlet("shutdown",		ShutdownServlet.class);
		selector.addServlet("server",		ServerServlet.class);
		selector.addServlet("summary",		SummaryServlet.class);
		selector.addServlet("ping",			PingServlet.class);
		selector.addServlet("svrsts",		ServerStatusServlet.class);
		selector.addServlet("attacklog",	AttackLogServlet.class);

		//Instantiate the server.
		int port = config.getServerPort();
		boolean ssl = config.getServerSSL();
		int maxThreads = config.getServerMaxThreads();
		HttpServer httpServer = null;
		try { httpServer = new HttpServer(ssl, port, maxThreads, selector); }
		catch (Exception ex) {
			logger.error("Unable to instantiate the HTTP Server on port "+port, ex);
			System.exit(0);
		}

		//Start the system
		config.start(httpServer);
	}
}
