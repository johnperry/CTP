/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.stdstages.ObjectLogger;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.servlets.Servlet;

/**
 * A Servlet that provides control of the logging by an ObjectLogger pipeline stage.
 */
public class ObjectLoggerServlet extends CTPServlet {

	static final Logger logger = Logger.getLogger(ObjectLoggerServlet.class);

	/**
	 * Construct an ObjectLoggerServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public ObjectLoggerServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: set the loggingEnabled flag for a selected ObjectLogger.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		super.loadParameters(req);

		//Make sure the user is authorized to do this.
		if (!userIsAuthorized) {
			res.setResponseCode(res.forbidden);
			res.send();
			return;
		}

		//Get the selected stage, if possible.
		ObjectLogger objectLogger = null;
		if (stage instanceof ObjectLogger) objectLogger = (ObjectLogger)stage;

		//Now set the parameter.
		boolean log = !req.getParameter("log", "yes").equals("no");
		if (objectLogger != null) {
			objectLogger.setLoggingEnabled(log);
		}

		//Redirect to the status page
		res.redirect("/summary?p="+p+"&s="+s+"&suppress");
	}

}