/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.util.*;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.util.IPUtil;

/**
 * The ShutdownServlet.
 */
public class ShutdownServlet extends Servlet {

	static final Logger logger = Logger.getLogger(ShutdownServlet.class);

	/**
	 * Construct a ShutdownServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public ShutdownServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: if the request is received from a
	 * user with the proper privileges, shut the system down.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		res.disableCaching();
		res.setContentType("html");

		String ra = req.getRemoteAddress();
		User user = req.getUser();
		String serviceCommand = req.getHeader("servicemanager");
		boolean serviceManager = (serviceCommand != null);

		if ((serviceManager && req.isFromLocalHost()) ||
			((user != null) && (req.userHasRole("shutdown") || req.isFromLocalHost())) ) {

			//Log the shutdown request
			String username = serviceManager ? "ServiceManager" : user.getUsername();
			logger.info("Shutdown request received from "+username+" at "+ra);
			res.write("Shutdown request received from "+username+" at "+ra+".<br>");

			boolean clean = shutdown();
			res.write("Goodbye.");
			res.send();

			//Let the ServiceManager do the system shutdown; otherwise, do it here.
			if (!serviceManager || !serviceCommand.equals("stayalive")) System.exit(0);
		}
		else {
			logger.warn("Rejected shutdown request from "+ra);
			res.write("Request rejected.");
			res.send();
		}
	}

	/**
	 * The GET handler: do the same as a GET.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doPost(HttpRequest req, HttpResponse res) {
		doGet(req, res);
	}

	//Shut the system down and log the result
	private static boolean shutdown() {
		//Tell the Configuration to shut down the pipelines
		Configuration config = Configuration.getInstance();
		config.shutdownPipelines();

		//Now poll the Configuration to see if all the pipes stopped.
		boolean pipesClean = false;
		for (int k=0; k<20; k++) {
			try { Thread.sleep(2000); }
			catch (Exception quit) { break; }
			if ( pipesClean = config.pipelinesAreDown() ) break;
		}

		//Now shut down the plugins.
		//This is done after the pipes are down because some stages may depend on a plugin.
		config.shutdownPlugins();

		//Now poll the Configuration to see if all the plugins stopped.
		boolean pluginsClean = false;
		for (int k=0; k<20; k++) {
			try { Thread.sleep(2000); }
			catch (Exception quit) { break; }
			if ( pluginsClean = config.pluginsAreDown() ) break;
		}

		//Log the result
		boolean clean = pipesClean & pluginsClean;
		logger.info("The system " + (clean?"":"did not ") + "shut down normally.\n");
		return clean;
	}

}
