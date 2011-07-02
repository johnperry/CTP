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
import org.rsna.ctp.plugin.Plugin;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.servlets.Servlet;
import org.rsna.util.HtmlUtil;

/**
 * The ConfigurationServlet. This implementation simply returns the
 * system configuration as an HTML page.
 */
public class ConfigurationServlet extends Servlet {

	static final Logger logger = Logger.getLogger(ConfigurationServlet.class);

	/**
	 * Construct a ConfigurationServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public ConfigurationServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page displaying the configuration of the system.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		boolean admin = req.isFromLocalHost() || req.userHasRole("admin");
		String home = req.getParameter("home", "/");
		Configuration config = Configuration.getInstance();
		String ipAddress = config.getIPAddress();
		int serverPort = config.getServerPort();

		StringBuffer sb = new StringBuffer();
		sb.append("<html>");
		sb.append("<head>");
		sb.append("<title>Configuration</title>");
		sb.append("<style>");
		sb.append("body {background-color:#c6d8f9; margin-top:0; margin-right:0; padding:0;}");
		sb.append("td {background-color:white;}");
		sb.append("h1 {margin-top:10; margin-bottom:0;}");
		sb.append("</style>");
		sb.append("</head><body>");
		sb.append(HtmlUtil.getCloseBox(home));
		sb.append("<center>");
		sb.append("<h1>Configuration</h1>");
		sb.append("Build " + config.getManifestAttribute("Date"));
		sb.append(" on Java " + config.getManifestAttribute("Java-Version"));
		sb.append("</center>");

		//Insert a table for the server parameters
		sb.append("<h2>Server</h2>");
		sb.append("<table border=\"1\" width=\"100%\">");
		sb.append("<tr><td width=\"20%\">IP Address:</td><td>"+ipAddress+"</td></tr>");
		sb.append("<tr><td>Server Port:</td><td>"+serverPort+"</td></tr>");
		sb.append("</table></center>");

		//Insert information for each plugin
		Iterator<Plugin> xit = config.getPlugins().iterator();
		while (xit.hasNext()) sb.append(xit.next().getConfigHTML(admin));

		//Insert information for each pipeline
		Iterator<Pipeline> pit = config.getPipelines().iterator();
		while (pit.hasNext()) sb.append(pit.next().getConfigHTML(admin));

		sb.append("</body></html>");

		//Send the response;
		res.disableCaching();
		res.write(sb.toString());
		res.setContentType("html");
		res.send();
	}

}

