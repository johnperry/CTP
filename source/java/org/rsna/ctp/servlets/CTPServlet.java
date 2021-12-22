/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.ImportService;
import org.rsna.ctp.pipeline.ExportService;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Quarantine;
import org.rsna.ctp.stdplugins.AuditLog;
import org.rsna.ctp.stdstages.FileStorageService;
import org.rsna.ctp.stdstages.LookupTableChecker;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.StringUtil;

/**
 * The CTPServlet servlet.
 * This servlet is a framework for CTP servlets that access
 * data from PipelineStages using the p and s query parameters
 * to identify the stage.
 */
public class CTPServlet extends Servlet {

	static final Logger logger = Logger.getLogger(CTPServlet.class);
	String home = "/";
	String suppress = "";
	User user = null;
	String protocol = "";
	String host = "";
	int p = -1;
	int s = -1;
	Pipeline pipeline = null;
	PipelineStage stage = null;
	boolean userIsAdmin = false;
	boolean userIsStageAdmin = false;
	boolean userIsAuthorized = false;
	Configuration config = null;

	/**
	 * Construct a CTPServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public CTPServlet(File root, String context) {
		super(root, context);
	}

	public void loadParameters(HttpRequest req) {
		config = Configuration.getInstance();
		user = req.getUser();
		protocol = req.getProtocol();
		host = req.getHeader("Host");
		if (req.hasParameter("suppress")) {
			home = "";
			suppress = "&suppress";
		}

		try {
			p = StringUtil.getInt(req.getParameter("p"), -1);
			s = StringUtil.getInt(req.getParameter("s"), -1);
			if (p >= 0) {
				List<Pipeline> pipelines = config.getPipelines();
				pipeline = pipelines.get(p);
				if (s >= 0) {
					List<PipelineStage> stages = pipeline.getStages();
					stage = stages.get(s);
				}
			}
		}
		catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.warn("Unable to load pipeline and stage for ("+p+","+s+")", ex);
			}
			else logger.warn("Unable to load pipeline and stage for ("+p+","+s+")");
		}
		
		userIsAdmin = req.userHasRole("admin");
		userIsStageAdmin = (stage != null) && stage.allowsAdminBy(user);
		userIsAuthorized = userIsAdmin || userIsStageAdmin;
	}

}
