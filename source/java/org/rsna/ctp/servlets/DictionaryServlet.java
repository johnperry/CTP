/*---------------------------------------------------------------
*  Copyright 2021 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
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
 * Dictionary Servlet.
 * This servlet provides a browser-accessible access to the DICOM dictionary
 * of elements, UIDs, and statuses contained in the dcm4che DICOM library.
 */
public class DictionaryServlet extends Servlet {

	static final Logger logger = Logger.getLogger(DictionaryServlet.class);

	/**
	 * Construct a LookupTableCheckerServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public DictionaryServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * This method returns an HTML page containing tables of
	 * elements, UIDs, and statuses contained in the dcm4che DICOM library.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(HttpRequest req, HttpResponse res) {

		res.write(getPage());
		res.setContentType("html");
		res.setContentEncoding(req);
		res.disableCaching();
		res.send();
	}

	//Create an HTML page containing the dictionary.
	private String getPage() {
		try {
			Configuration config = Configuration.getInstance();
			PipelineStage stage = config.getRegisteredStage(context);
			LookupTableChecker ltcStage = (LookupTableChecker)stage;
			Document doc = XmlUtil.getDocument( FileUtil.getStream( "/dictionary.xml" ) );
			Document xsl = XmlUtil.getDocument( FileUtil.getStream( "/DictionaryServlet.xsl" ) );
			return XmlUtil.getTransformedText( doc, xsl, null );
		}
		catch (Exception unable) { 
			logger.warn("Unable to create the Dictionary page", unable);
		}
		return "Unable to create the Dictionary page";
	}

}
