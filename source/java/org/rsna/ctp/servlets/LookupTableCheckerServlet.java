/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.util.List;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.rsna.ctp.stdstages.LookupTableChecker;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The Lookup Table Checker servlet.
 * This servlet provides a browser-accessible user interface for
 * resolving missing entries in an anonymizer lookup table file.
 * This sevlet is installed by the LookupTableChecker stage when it is
 * started. The context is the id of the LookupTableChecker stage.
 * This servlet responds to both HTTP GET and POST.
 */
public class LookupTableCheckerServlet extends CTPServlet {

	static final Logger logger = Logger.getLogger(LookupTableCheckerServlet.class);

	/**
	 * Construct a LookupTableCheckerServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public LookupTableCheckerServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * This method returns an HTML page containing a form for
	 * entering values for keys that are in the LookupTableChecker database.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		super.loadParameters(req);

		//Make sure the user is authorized to do this.
		if (!userIsAuthorized) {
			res.setResponseCode(res.forbidden);
			res.send();
			return;
		}

		//Make a page containing a form for updating the lookup table.
		res.write(getEditorPage());
		res.setContentType("html");
		res.disableCaching();
		res.send();
	}

	//Create an HTML page containing the form for resolving the missing keys.
	private String getEditorPage() {
		try {
			Configuration config = Configuration.getInstance();
			PipelineStage stage = config.getRegisteredStage(context);
			LookupTableChecker ltcStage = (LookupTableChecker)stage;
			Document doc = ltcStage.getIndexDocument();

			//logger.info("LUTDoc:\n"+XmlUtil.toPrettyString(doc));

			Document xsl = XmlUtil.getDocument( FileUtil.getStream( "/LookupTableCheckerServlet.xsl" ) );
			Object[] params = {
				"context", context,
				"home", home,
				"pipelineName", ltcStage.getPipeline().getPipelineName(),
				"stageName", ltcStage.getName(),
				"lutFile", ltcStage.getLookupTableFile().getAbsolutePath()
			};
			return XmlUtil.getTransformedText( doc, xsl, params );
		}
		catch (Exception unable) { }
		return "Unable to create the Editor page";
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * This method interprets the posted parameters as a new addition
	 * to the file and updates it accordingly.
	 * It then returns an HTML page containing a new form constructed
	 * from the new contents of the file.
	 * @param req The HttpRequest provided by the servlet container.
	 * @param res The HttpResponse provided by the servlet container.
	 */
	public void doPost(HttpRequest req, HttpResponse res) {
		super.loadParameters(req);

		//Make sure the user is authorized to do this.
		if (!userIsAuthorized || !req.isReferredFrom(context)) {
			res.setResponseCode(res.forbidden);
			res.send();
			return;
		}

		//logger.info("POST:\n"+req.toString()+"\nParameters\n"+req.listParameters("  "));

		if (req.hasParameter("suppress")) home = "";

		try {
			Configuration config = Configuration.getInstance();
			PipelineStage stage = config.getRegisteredStage(context);
			LookupTableChecker ltcStage = (LookupTableChecker)stage;
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("Terms");
			doc.appendChild(root);

			boolean update = false;
			int k = 1;
			while ( true ) {
				String index = "[" + k + "]";
				String key = req.getParameter( "key"+index );
				if (key == null) break;
				key = key.trim();
				if (!key.equals("")) {
					String keyType = req.getParameter("keyType"+index).trim();
					String value = req.getParameter("value"+index).trim();
					if (!value.equals("")) {
						Element term = doc.createElement("Term");
						root.appendChild(term);
						term.setAttribute("key", keyType + key);
						term.setAttribute("value", value);
						update = true;
					}
				}
				k++;
			}
			if (update) ltcStage.update(doc);
		}
		catch (Exception ex) { }
		res.write(getEditorPage());
		res.send();
	}

}
