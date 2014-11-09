/*---------------------------------------------------------------
*  Copyright 2014 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import org.rsna.server.*;
import org.rsna.servlets.Servlet;
import org.rsna.ctp.Configuration;
import org.rsna.util.Attack;
import org.rsna.util.AttackLog;

/**
 * The ServerStatusServlet. This servlet returns the status of the
 * server, including the number of active threads currently
 * servicing requests, the size of the server thread pool, and
 * the number of requests waiting in the queue.
 */
public class ServerStatusServlet extends Servlet {

	/**
	 * Construct a ServerStatusServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public ServerStatusServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return the date/time.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		HttpServer server = Configuration.getInstance().getServer();
		int maxThreads = server.getMaxThreads();
		int activeThreads = server.getActiveThreads();
		int queuedThreads = server.getQueuedThreads();

		String response = activeThreads + " of " + maxThreads + " server threads are currently active.\n"
							+ queuedThreads + " thread"
							+ ((queuedThreads == 1) ? " is" : "s are")
							+ " waiting in the queue.";

		res.write(response);
		res.setContentType("txt");
		res.disableCaching();
		res.send();
	}
}
