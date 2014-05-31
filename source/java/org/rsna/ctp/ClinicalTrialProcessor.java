/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
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
import org.rsna.util.ProxyServer;

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
	 */
	public static void main(String[] args) {
		//Set the context classloader to allow dcm4che to load its classes
		Thread.currentThread().setContextClassLoader(ClinicalTrialProcessor.class.getClassLoader());

		//Instantiate the main class
		new ClinicalTrialProcessor();
	}

	/**
	 * The startup method of the ClinicalTrialProcessor program.
	 * This method is used when running CTP as a Windows service.
	 * It does not return until the stopService method is called
	 * independently by the service manager.
	 */
	public static void startService(String[] args) {
		System.out.println("Start [ServiceManager]");
		main(args);
		while (running) {
			try { Thread.sleep(2000); }
			catch (Exception ignore) { }
		}
		if (logger != null) logger.info("startService returned\n");
	}

	/**
	 * The shutdown method of the ClinicalTrialProcessor program.
	 * This method is used when running CTP as a Windows service.
	 * This method makes an HTTP connection to the ShutdownServlet
	 * to trigger the plugins and pipelines to close down gracefully.
	 */
	public static void stopService(String[] args) {
		try {
			Configuration config = Configuration.getInstance();
			boolean ssl = config.getServerSSL();
			int port = config.getServerPort();

			URL url = new URL( "http" + (ssl?"s":"") + "://127.0.0.1:" + port + "/shutdown" );
			HttpURLConnection conn = HttpUtil.getConnection(url);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("servicemanager", "stayalive");
			conn.connect();

			String result = FileUtil.getText( conn.getInputStream() );
			if (result.contains("Goodbye.")) {
				System.out.println("Normal shutdown [ServiceManager]");
				running = false;
			}
			else System.out.println("Unable to service the shutdown request from ServiceManager.");
		}
		catch (Exception keepRunning) { keepRunning.printStackTrace(); }
		if (logger != null) logger.info("stopService returned");
	}

	/**
	 * The constructor of the ClinicalTrialProcessor program.
	 * There is no UI presented by the program. All access to
	 * the configuration and status of the program is presented
	 * through the HTTP server.
	 */
	public ClinicalTrialProcessor() {

		//Set up the classpath
		ClasspathUtil.addJARs( new File("libraries") );

		//Initialize Log4J
		File logs = new File("logs");
		logs.mkdirs();
		File logProps = new File("log4j.properties");
		PropertyConfigurator.configure(logProps.getAbsolutePath());
		logger = Logger.getLogger(ClinicalTrialProcessor.class);

		//Instantiate the singleton Cache, clear it, and preload
		//files from the jars. Other files will be loaded as required..
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
		String[] roles = { "read", "delete", "import", "qadmin", "guest", "proxy" };
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
