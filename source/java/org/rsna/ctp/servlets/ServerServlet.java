/*---------------------------------------------------------------
*  Copyright 2013 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.servlets.Servlet;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.Scriptable;
import org.rsna.ctp.stdstages.ScriptableDicom;

/**
 * The ServerServlet. This servlet is intended for use by Ajax
 * calls on web pages which need to know key parameters of the server
 * (IP, port, CTP build)..
 */
public class ServerServlet extends Servlet {

	static final Logger logger = Logger.getLogger(ServerServlet.class);

	/**
	 * Construct a ServerServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public ServerServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return an XML structure containing
	 * information about the server and the configuration.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		res.setContentType("xml");
		res.disableCaching();
		Configuration config = Configuration.getInstance();
		if (req.hasParameter("type")) {
			String type = req.getParameter("type");
			try {
				Class c = Class.forName(type);
				for (Pipeline pipe : config.getPipelines()) {
					if (pipe.isEnabled()) {
						for (PipelineStage stage : pipe.getStages()) {
							if (c.isAssignableFrom(stage.getClass())) {
								res.write("<true/>");
								res.send();
								return;
							}
						}
					}
				}
			}
			catch (Exception ex) { }
			res.write("<false/>");
		}
		else {
			try {
				String ip = config.getIPAddress();
				String port = Integer.toString(config.getServerPort());
				String build = config.getCTPBuild();
				String java = config.getCTPJava();
				Document configXML = config.getConfigurationDocument();
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement("CTP");
				doc.appendChild(root);
				root.setAttribute("ip", ip);
				root.setAttribute("port", port);
				root.setAttribute("build", build);
				root.setAttribute("java", java);
				root.appendChild( doc.importNode( configXML.getDocumentElement(), true ) );
				res.write(XmlUtil.toPrettyString(doc));
			}
			catch (Exception ex) { res.write("<Server/>"); }
		}
		res.send();
	}

}
