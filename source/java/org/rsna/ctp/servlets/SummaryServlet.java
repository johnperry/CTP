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
 * The Summary servlet.
 * This servlet provides a summary of key parameters of pipelines and stages.
 */
public class SummaryServlet extends CTPServlet {

	static final Logger logger = Logger.getLogger(SummaryServlet.class);

	/**
	 * Construct a SummaryServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public SummaryServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * This method returns a summary page containing information
	 * on the pipeline or stage specified by the p and s query parameters.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		super.loadParameters(req);

		//Get the parameters.
		int x = StringUtil.getInt(req.getParameter("plugin"), -1);

		//Return the page
		res.write(getPage(p, s, x));
		res.setContentType("html");
		res.setContentEncoding(req);
		res.disableCaching();
		res.send();
	}

	//Get the referenced Plugin, if possible
	private Plugin getPlugin(int x) {
		try {
			Configuration config = Configuration.getInstance();
			List<Plugin> plugins = config.getPlugins();
			return plugins.get(x);
		}
		catch (Exception ex) { }
		return null;
	}

	//Get the referenced Pipeline, if possible
	private Pipeline getPipeline(int p) {
		try {
			Configuration config = Configuration.getInstance();
			List<Pipeline> pipelines = config.getPipelines();
			return pipelines.get(p);
		}
		catch (Exception ex) { }
		return null;
	}

	//Get the referenced PipelineStage, if possible
	private PipelineStage getPipelineStage(int p, int s) {
		try {
			Configuration config = Configuration.getInstance();
			List<Pipeline> pipelines = config.getPipelines();
			Pipeline pipe = pipelines.get(p);
			List<PipelineStage> stages = pipe.getStages();
			return stages.get(s);
		}
		catch (Exception ex) { }
		return null;
	}

	//Create an HTML page containing the list of files.
	private String getPage(int p, int s, int x) {
		if (x != -1) return getPluginPage(x);
		else if (p == -1) return getAllPipelinesPage();
		else if (s == -1) return getPipelinePage(p);
		else return getStagePage(p, s);
	}

	private String getPluginPage(int x) {
		Plugin plugin = getPlugin(x);
		if (plugin != null) {
			StringBuffer sb = new StringBuffer( responseHead("Plugin Summary") );
			sb.append("<table class=\"summary\">" + tableHeadings() + "\n");
			Configuration config = Configuration.getInstance();
			List<Pipeline> pipelines = config.getPipelines();
			for (Pipeline pipe : pipelines) {
				sb.append(getPipelineSummary(pipe));
			}
			sb.append("</table>\n");
			sb.append("</center>");
			sb.append("<hr/>");
			sb.append("<div id=\"status\" class=\"status\">");
			sb.append( "<h2>Status</h2>\n" );
			sb.append( plugin.getStatusHTML() );
			sb.append( getLinks(plugin.getLinks(user)) );
			if (userIsAuthorized) {
				sb.append( "<h2>Configuration</h2>\n" );
				sb.append( plugin.getConfigHTML(user) );
			}
			sb.append("</div>");
			sb.append("<center>");
			sb.append( responseTail() );
			return sb.toString();
		}
		return getAllPipelinesPage();
	}

	private String getAllPipelinesPage() {
		StringBuffer sb = new StringBuffer( responseHead("System Summary") );
		sb.append("<table class=\"summary\">" + tableHeadings() + "\n");
		Configuration config = Configuration.getInstance();
		List<Pipeline> pipelines = config.getPipelines();
		for (Pipeline pipe : pipelines) {
			sb.append(getPipelineSummary(pipe));
		}
		sb.append("</table>\n");
		sb.append( responseTail() );
		return sb.toString();
	}

	private String getPipelinePage(int p) {
		Pipeline pipe = getPipeline(p);
		if (pipe != null) {
			StringBuffer sb = new StringBuffer( responseHead("Pipeline Summary") );
			sb.append("<table class=\"summary\">" + tableHeadings() + "\n");
			sb.append(getPipelineSummary(pipe));
			sb.append("</table>\n");
			sb.append("</center>");
			sb.append("<hr/>");
			sb.append("<div id=\"status\" class=\"status\">");
			for (PipelineStage stage : pipe.getStages()) {
				sb.append( stage.getStatusHTML() );
			}
			sb.append("</div>");
			sb.append("<center>");
			sb.append( responseTail() );
			return sb.toString();
		}
		return getAllPipelinesPage();
	}

	private String getHostWithoutPort() {
		int k = host.indexOf(":");
		return (k>0) ? host.substring(0,k) : host;
	}

	private String getStagePage(int p, int s) {
		Pipeline pipe = getPipeline(p);
		if (pipe != null) {
			PipelineStage stage = getPipelineStage(p, s);
			if (stage != null) {
				StringBuffer sb = new StringBuffer( responseHead("Stage Summary") );
				sb.append("<table class=\"summary\">" + tableHeadings() + "\n");
				sb.append(getPipelineSummary(pipe));
				sb.append("</table>\n");
				sb.append("</center>");
				sb.append("<hr/>");
				sb.append("<div id=\"status\" class=\"status\">");
				sb.append( "<h2>Status</h2>\n" );
				sb.append( stage.getStatusHTML() );
				sb.append( getLinks(stage.getLinks(user)) );
				if (userIsAuthorized) {
					sb.append( "<h2>Configuration</h2>\n" );
					sb.append( stage.getConfigHTML(user) );
				}
				sb.append("</div>");
				sb.append("<center>");
				sb.append( responseTail() );
				return sb.toString();
			}
		}
		return getAllPipelinesPage();
	}

	private String getLinks(LinkedList<SummaryLink> links) {
		StringBuffer sb = new StringBuffer();
		for (SummaryLink link : links) {
			String url = link.getURL();
			url += (url.contains("?") ? "&" : "?") + "suppress";
			String windowURL = "//";
			windowURL += (url.startsWith(":") ? getHostWithoutPort() : host) + url;
			if (link.needsNewWindow()) {
				sb.append("<p class=\"link\">\n");
				sb.append("<input type=\"button\" class=\"summarylink\"");
				sb.append("  value=\""+link.getTitle()+"\"");
				sb.append("  onclick=\"window.open('"+windowURL+"','child')\"/>\n");
				sb.append("</p>");
			}
			else {
				sb.append("<p class=\"link\">\n");
				sb.append("<input type=\"button\" class=\"summarylink\"");
				sb.append("  value=\""+link.getTitle()+"\"");
				sb.append("  onclick=\"window.location='"+windowURL+"';\"/>\n");
				sb.append("</p>");
			}
		}
		return sb.toString();
	}

	private String getPipelineSummary(Pipeline pipe) {
		StringBuffer sb = new StringBuffer();
		sb.append("<tr>");
		sb.append("<td class=\"name\">"+pipe.getName()+"</td>");
		sb.append("<td class=\"number\">"+String.format("%,d",getImportQueueTotal(pipe))+"</td>");
		sb.append("<td class=\"number\">"+String.format("%,d",getExportQueueTotal(pipe))+"</td>");
		sb.append("<td class=\"number\">"+String.format("%,d",getQuarantineTotal(pipe))+"</td>");
		sb.append("</tr>\n");
		return sb.toString();
	}

	private int getImportQueueTotal(Pipeline pipe) {
		int count = 0;
		if (pipe != null) {
			for (PipelineStage stage : pipe.getStages()) {
				if (stage instanceof ImportService) {
					count += ((ImportService)stage).getQueueSize();
				}
			}
		}
		return count;
	}

	private int getExportQueueTotal(Pipeline pipe) {
		int count = 0;
		if (pipe != null) {
			for (PipelineStage stage : pipe.getStages()) {
				if (stage instanceof ExportService) {
					count += ((ExportService)stage).getQueueSize();
				}
			}
		}
		return count;
	}

	private int getQuarantineTotal(Pipeline pipe) {
		int count = 0;
		if (pipe != null) {
			for (PipelineStage stage : pipe.getStages()) {
				Quarantine q = stage.getQuarantine();
				if (q != null) count += q.getSize();
			}
		}
		return count;
	}

	private String tableHeadings() {
		return "<tr>"
				+ "<th class=\"name\"><br/>Pipeline</th>"
				+ "<th>Import<br/>Queues</th>"
				+ "<th>Export<br/>Queues</th>"
				+ "<th><br/>Quarantines</th></tr>";
	}

	private String responseHead(String title) {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>"+title+"</title>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/BaseStyles.css\"></link>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/SummaryServlet.css\"></link>\n"
			+	"  <script language=\"JavaScript\" type=\"text/javascript\" src=\"/JSUtil.js\">;</script>\n"
			+	"  <script language=\"JavaScript\" type=\"text/javascript\" src=\"/SummaryServlet.js\">;</script>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	"  <h1>"+title+"</h1>\n"
			+	"  <center>\n";

		return head;
	}

	private String responseTail() {
		String tail =
				"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return tail;
	}

}
