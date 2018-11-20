/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.net.*;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import org.apache.log4j.Logger;
import org.rsna.ctp.pipeline.AbstractQueuedExportService;
import org.rsna.ctp.servlets.PolledServlet;
import org.rsna.server.*;
import org.rsna.servlets.Servlet;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An ExportService that serves files via the HTTP protocol.
 */
public class PolledHttpExportService extends AbstractQueuedExportService {

	static final Logger logger = Logger.getLogger(PolledHttpExportService.class);

	HttpServer server = null;
	ServletSelector selector;
	int port = 9100;
	boolean ssl;
	WhiteList ipWhiteList = null;
	BlackList ipBlackList = null;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 * @throws Exception on any error
	 */
	public PolledHttpExportService(Element element) throws Exception {
		super(element);

		//Get the port
		try { port = Integer.parseInt(element.getAttribute("port").trim()); }
		catch (Exception ex) { logger.error(name+": Unparseable port value"); }

		//Get the protocol
		ssl = element.getAttribute("ssl").trim().equals("yes");

		//Get the whitelist and blacklist
		ipWhiteList = new WhiteList(element, "ip");
		ipBlackList = new BlackList(element, "ip");

		//Create the HttpServer
		try {
			selector = new ServletSelector(new File("ROOT"), false);
			selector.addServlet(id, PolledServlet.class);
			server = new HttpServer(ssl, port, 1, selector);
		}			
		catch (Exception ex) {
			logger.warn("Unable to instantiate the HttpServer", ex);
			throw ex;
		}
	}

	/**
	 * Stop the stage.
	 */
	public synchronized void shutdown() {
		stop = true;
		if (server != null) server.shutdown();
		super.shutdown();
	}

	/**
	 * Determine whether the pipeline stage has shut down.
	 */
	public synchronized boolean isDown() {
		return stop;
	}
	
	/**
	 * Start the stage.
	 */
	public void start() {
		if (server != null) server.start();
	}
	
	//Give the PolledServlet access
	public WhiteList getWhiteList() {
		return ipWhiteList;
	}

	public BlackList getBlackList() {
		return ipBlackList;
	}
	
	public File getNextFile() {
		return super.getNextFile();
	}

	public boolean release(File file) {
		return super.release(file);
	}

}