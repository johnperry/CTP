/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.ctp.stdplugins.AuditLog;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A Servlet which provides web access to the indexed data stored by an AudirLog plugin.
 */
public class AuditLogServlet extends Servlet {

	static final Logger logger = Logger.getLogger(AuditLogServlet.class);

	/**
	 * Construct an AuditLogServlet. Note: the AuditLogServlet
	 * is added to the ServletSelector by the AuditLog Plugin.
	 * The context is defined by the Plugin to be the Plugin's
	 * ID. This provides the linkage between the AuditLogServlet
	 * and the AuditLog Plugin.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public AuditLogServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page displaying either the list of
	 * AuditLog plugins or the search form for a selected AuditLog plugin.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) throws Exception {

		//Make sure the user is authorized to do this.
		String home = req.getParameter("home", "/");
		if (!req.userHasRole("admin")) { res.redirect(home); return; }

		//Get the plugin, if possible.
		Plugin plugin = Configuration.getInstance().getRegisteredPlugin(context);

		if ((plugin != null) && (plugin instanceof AuditLog)) {
			//Make the search page.
			AuditLog auditLog = (AuditLog)plugin;
			Document doc = XmlUtil.getDocument();
			Element root = (Element)doc.importNode(auditLog.getConfigElement(), true);
			doc.appendChild(root);
			Document xsl = XmlUtil.getDocument( FileUtil.getStream( "/AuditLogServlet.xsl" ) );
			Object[] params = { "context", context };
			res.write( XmlUtil.getTransformedText( doc, xsl, params ) );
		}
		else {
			//Since the servlet was installed by the AuditLog, this should never happen.
			//Protect against it anyway in case somebody does something funny with the ID.
			res.setResponseCode(res.notfound);
		}

		//Return the page
		res.disableCaching();
		res.setContentType("html");
		res.send();
	}

	/**
	 * The POST handler
	 * This method interprets the posted parameters as search criteria
	 * for the selected IdMap. It performs the search and then
	 * returns the results in the selected format.
	 * @param req The HttpRequest provided by the servlet container.
	 * @param res The HttpResponse provided by the servlet container.
	 */
	public void doPost(HttpRequest req, HttpResponse res) {

		//Set up the response
		res.setContentType("xml");
		res.disableCaching();

		//Get the AuditLog plugin.
		Plugin plugin = Configuration.getInstance().getRegisteredPlugin(context);

		if (req.userHasRole("admin") && (plugin != null) && (plugin instanceof AuditLog)) {
			AuditLog auditLog = (AuditLog)plugin;

			String entry = req.getParameter("entry");
			String type = req.getParameter("type");
			String text = req.getParameter("text");

			try {
				Document doc = XmlUtil.getDocument();
				Element result = doc.createElement("result");
				doc.appendChild(result);

				if (entry != null) {

					//This is a request for a specific entry
					Integer id = new Integer(entry);
					String entryText = auditLog.getText(id);
					String entryTime = auditLog.getTime(id);
					String entryContentType = auditLog.getContentType(id);
					Element el = doc.createElement("entry");
					result.appendChild(el);
					el.setAttribute("id", id.toString());
					el.setAttribute("time", entryTime);
					el.setAttribute("contentType", entryContentType);
					el.appendChild( doc.createCDATASection(entryText) );
				}

				else if ( (type != null) && (text != null) ) {

					//This is a request for a list of entries for
					//a specific patient, study, or object.
					LinkedList<Integer> ids = new LinkedList<Integer>();
					if (type.equals("ptid")) ids = auditLog.getEntriesForPatientID(text);
					else if (type.equals("study")) ids = auditLog.getEntriesForStudyUID(text);
					else if (type.equals("object")) ids = auditLog.getEntriesForObjectUID(text);

					for (Integer id : ids) {
						String entryTime = auditLog.getTime(id);
						Element el = doc.createElement("entry");
						result.appendChild(el);
						el.setAttribute("id", id.toString());
						el.setAttribute("time", entryTime);
					}
				}
				res.write( XmlUtil.toPrettyString( result ) );
				res.send();
			}
			catch (Exception returnEmptyResult) { }
		}

		//If we get here, we couldn't service the request.
		//Return an empty result.
		res.write("<result/>");
		res.send();
	}
}

