/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.util.Iterator;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.servlets.Servlet;
import org.rsna.util.HtmlUtil;

/**
 * The StatusServlet. This implementation returns the
 * status of all pipelines as an HTML page.
 */
public class StatusServlet extends Servlet {

	static final Logger logger = Logger.getLogger(StatusServlet.class);

	/**
	 * Construct a StatusServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public StatusServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page displaying the status of the system.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		Configuration config = Configuration.getInstance();
		String home = req.getParameter("home", "/");

		StringBuffer sb = new StringBuffer();
		sb.append("<html>");
		sb.append("<head>");
		sb.append("<title>Status</title>");
		sb.append("<style>");
		sb.append("body {background-color:#c6d8f9; margin-top:0; margin-right:0; padding:0;}");
		sb.append("td {background-color:white;}");
		sb.append("h1 {margin-top:10; margin-bottom:0;}");
		sb.append("</style>");
		sb.append("</head><body>");
		sb.append(HtmlUtil.getCloseBox(home));
		sb.append("<center><h1>Status</h1></center>");

		//Insert information for each pipeline
		Iterator<Pipeline> pit = config.getPipelines().iterator();
		while (pit.hasNext()) sb.append(pit.next().getStatusHTML());

		sb.append("</body></html>");

		//Send the response;
		res.disableCaching();
		res.write(sb.toString());
		res.setContentType("html");
		res.send();
	}

}

