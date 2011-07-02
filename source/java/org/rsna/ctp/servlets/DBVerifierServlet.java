/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;
import jdbm.htree.HTree;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.DatabaseVerifier;
import org.rsna.ctp.stdstages.verifier.StudyObject;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A Servlet which provides web access to the indexed data stored by a DatabaseVerifier pipeline stage.
 */
public class DBVerifierServlet extends Servlet {

	static final Logger logger = Logger.getLogger(DBVerifierServlet.class);

	/**
	 * Construct a DBVerifierServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public DBVerifierServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * Return an HTML page displaying either the list of
	 * DatabaseVerifier stages or a table of the requested
	 * data from a selected DatabaseVerifier.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {

		//Make sure the user is authorized to do this.
		String home = req.getParameter("home", "/");
		if (!req.userHasRole("admin")) { res.redirect(home); return; }

		//Get the selected stage, if possible.
		DatabaseVerifier verifier = null;
		int p = -1;
		int s = -1;
		String pipeAttr = req.getParameter("p", "");
		String stageAttr = req.getParameter("s", "");
		if (!pipeAttr.equals("") && !stageAttr.equals("")) {
			try {
				p = Integer.parseInt(pipeAttr);
				s = Integer.parseInt(stageAttr);
				Pipeline pipe = Configuration.getInstance().getPipelines().get(p);
				PipelineStage stage = pipe.getStages().get(s);
				if (stage instanceof DatabaseVerifier) verifier = (DatabaseVerifier)stage;
			}
			catch (Exception ex) { verifier = null; }
		}

		//Now make either the page listing the various DatabaseVerifier stages
		//or the search page for the specified DatabaseVerifier.
		if (verifier == null) {
			res.write(getListPage(home));
		}
		else {
			String date = req.getParameter("date");
			String ptid = req.getParameter("ptid");
			String siuid = req.getParameter("siuid");
			String clear = req.getParameter("clear");
			res.write(getResultsPage(verifier, p, s, date, ptid, siuid, clear, home));
		}

		//Return the page
		res.disableCaching();
		res.setContentType("html");
		res.send();
	}

	//Create an HTML page containing the list of ObjectTracker stages.
	private String getListPage(String home) {
		return responseHead("Select the DatabaseVerifier to Search", home)
				+ makeList()
					+ responseTail();
	}

	private String makeList() {
		StringBuffer sb = new StringBuffer();
		Configuration config = Configuration.getInstance();
		List<Pipeline> pipelines = config.getPipelines();
		if (pipelines.size() != 0) {
			int count = 0;
			sb.append("<table border=\"1\" width=\"75%\">");
			for (int p=0; p<pipelines.size(); p++) {
				Pipeline pipe = pipelines.get(p);
				List<PipelineStage> stages = pipe.getStages();
				for (int s=0; s<stages.size(); s++) {
					PipelineStage stage = stages.get(s);
					if (stage instanceof DatabaseVerifier) {
						sb.append("<tr>");
						sb.append("<td width=\"50%\">"+pipe.getPipelineName()+"</td>");
						sb.append("<td><a href=\"/"+context+"?p="+p+"&s="+s+"\">"+stage.getName()+"</a></td>");
						sb.append("</tr>");
						count++;
					}
				}
			}
			sb.append("</table>");
			if (count == 0) sb.append("<p>The configuration contains no DatabaseVerifier stages.</p>");
		}
		return sb.toString();
	}

	//Create an HTML page containing the requested results.
	private String getResultsPage(DatabaseVerifier verifier, int p, int s, String date, String ptid, String siuid, String clear, String home) {
		if ((siuid == null) || siuid.trim().equals("")) {
			return responseHead(verifier.getName(), home)
					+ getSummary(verifier, p, s, date, ptid, clear)
						+ responseTail();
		}
		else {
			String url = "/" + context + "?home="+home+"&p="+p+"&s="+s;
			if (date != null) url += "&date="+date;
			if (ptid != null) url += "&ptid="+ptid;
			return responseHead(verifier.getName(), url, "_self", "Back")
					+ getInstances(verifier, p, s, date, ptid, siuid)
						+ responseTail();
		}
	}

	private String getSummary(DatabaseVerifier verifier, int p, int s, String date, String ptid, String clear) {
		if ((clear != null) && clear.equals("yes")) verifier.clearUnverifiedList();
		StringBuffer sb = new StringBuffer();
		sb.append("<center>\n");
		boolean ptidSearch = true;
		HashSet<String> studies = null;
		if (ptid != null) {
			try { studies = (HashSet<String>)verifier.ptidIndex.find(ptid); }
			catch (Exception ex) { logger.warn(ex.getMessage()); }
		}
		if (studies == null) {
			ptidSearch = false;
			if (date == null) date = StringUtil.getDate("");
			try {
				studies = (HashSet<String>)verifier.dateIndex.find(date);
				if (studies == null) {
					//Okay, there is nothing on the specified date.
					//Look around and see if we can find something.
					synchronized (verifier.unverifiedList) {
						Tuple tuple = new Tuple();
						TupleBrowser tb = verifier.dateIndex.browse(date);
						//See if there is a previous key; if not, look for a next one.
						boolean ok = tb.getPrevious(tuple);
						if (!ok) ok = tb.getNext(tuple);
						if (ok) {
							date = (String)tuple.getKey();
							studies = (HashSet<String>)tuple.getValue();
						}
					}
				}
			}
			catch (Exception ex) { logger.warn(ex.getMessage()); }
		}
		if (studies != null) {
			sb.append("<table class=\"sortable\">\n");
			sb.append("<thead>");
				sb.append("<tr>");
					sb.append(getSortColumnHeader("studies", 0, "Date"));
					sb.append(getSortColumnHeader("studies", 1, "Patient ID"));
					sb.append(getSortColumnHeader("studies", 2, "Patient Name"));
					sb.append(getSortColumnHeader("studies", 3, "Instances"));
					sb.append(getSortColumnHeader("studies", 4, "Unverified"));
				sb.append("</tr>");
			sb.append("</thead>");
			sb.append("<tbody id=\"studies\">");

			Iterator<String> sit = studies.iterator();
			while (sit.hasNext()) {
				try {
					String siUID = sit.next();
					StudyObject sob = (StudyObject)verifier.studyTable.get(siUID);
					if (sob != null) {
						sb.append("<tr>");
						if (ptidSearch) {
							sb.append("<td>");
								sb.append("<a href=\"/"+context+"?p="+p+"&s="+s+"&date="+sob.date+"\">");
									sb.append(sob.date);
								sb.append("</a>");
							sb.append("</td>");
							sb.append("<td>" + sob.ptID + "</td>");
						}
						else {
							sb.append("<td>" + sob.date + "</td>");
							sb.append("<td>");
								sb.append("<a href=\"/"+context+"?p="+p+"&s="+s+"&ptid="+sob.ptID+"\">");
									sb.append(sob.ptID);
								sb.append("</a>");
							sb.append("</td>");
						}
						sb.append("<td>" + sob.ptName + "</td>");
						sb.append("<td class=\"numeric\">");
							sb.append("<a href=\"/"+context+"?p="+p+"&s="+s+"&siuid="+siUID+"\">");
								sb.append(sob.getInstanceCount());
							sb.append("</a>");
						sb.append("</td>");
						sb.append("<td class=\"numeric\">" + sob.getUnverifiedCount() + "</td>");
						sb.append("</tr>");
					}
				}
				catch (Exception ex) { logger.warn("Unable to verify study",ex); }
			}
			sb.append("</tbody>\n");
			sb.append("</table>\n");
		}
		else {
			sb.append("No studies were found for the specified parameters:<br>");
			sb.append("<br>date = "+date);
			sb.append("<br>ptid = "+ptid);
		}
		//Find the next and previous dates, if this was a date search
		String nextDate = null;
		String prevDate = null;
		if (!ptidSearch) {
			try {
				synchronized (verifier.unverifiedList) {
					Tuple tuple = new Tuple();
					TupleBrowser tb = verifier.dateIndex.browse(date);
					if (tb.getPrevious(tuple)) {
						prevDate = (String)tuple.getKey();
						tb.getNext(tuple); //get back to the original date
					}
					tb.getNext(tuple); //get to the next date
					if (tb.getNext(tuple)) nextDate = (String)tuple.getKey();
				}
			}
			catch (Exception skip) { }
		}
		//Put in the footer
		sb.append("<br>");
		sb.append("<table class=\"footer\" border=\"1\">");
		sb.append("<tr>");
		if ((nextDate != null) || (prevDate != null)) {
			if (nextDate == null) sb.append("<td/>");
			else {
				sb.append("<td>");
				sb.append("<input type=\"button\" value=\"Next Date\" ");
				sb.append("onclick=\"window.open('/"+context+"?p="+p+"&s="+s+"&date="+nextDate+"','_self');\"/>");
				sb.append("</td>");
			}
		}
		sb.append("<td>");
		sb.append("Date search:");
		sb.append("&nbsp;&nbsp;");
		sb.append("<input type=\"text\" id=\"dateField\">");
		sb.append("&nbsp;&nbsp;");
		sb.append("<input type=\"button\" value=\"Go\" ");
		sb.append("onclick=\"goto('"+context+"','"+p+"','"+s+"','date','dateField');\"");
		sb.append("/>");
		sb.append("</td>");
		sb.append("</tr>");

		sb.append("<tr>");
		if ((nextDate != null) || (prevDate != null)) {
			if (prevDate == null) sb.append("<td/>");
			else {
				sb.append("<td>");
				sb.append("<input type=\"button\" value=\"Prev Date\" ");
				sb.append("onclick=\"window.open('/"+context+"?p="+p+"&s="+s+"&date="+prevDate+"','_self');\"/>");
				sb.append("</td>");
			}
		}
		sb.append("<td>");
		sb.append("PtID search:");
		sb.append("&nbsp;&nbsp;");
		sb.append("<input type=\"text\" id=\"ptidField\">");
		sb.append("&nbsp;&nbsp;");
		sb.append("<input type=\"button\" value=\"Go\" ");
		sb.append("onclick=\"goto('"+context+"','"+p+"','"+s+"','ptid','ptidField');\"");
		sb.append("/>");
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("</table>");

		//Report on the unverified queue size
		sb.append("<br>");
		sb.append("<table class=\"footer\" border=\"1\">");
		sb.append("<tr>");
		sb.append("<td>Unverified queue size = "+verifier.unverifiedList.size()+"</td>");
		sb.append("</tr>");
		sb.append("<tr>");
		sb.append("<td>");
		sb.append("<input type=\"button\" value=\"Clear the unverified queue\" ");
		sb.append("onclick=\"if (window.confirm('Are you sure?'))window.open('/"+context+"?p="+p+"&s="+s+"&clear=yes','_self');\"/>");
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("</table>");

		sb.append("</center>");
		return sb.toString();
	}

	private String getInstances(DatabaseVerifier verifier, int p, int s, String date, String ptid, String siuid) {
		StringBuffer sb = new StringBuffer();
		sb.append("<center>\n");

		try {
			StudyObject sob = (StudyObject)verifier.studyTable.get(siuid);
			if (sob != null) {

				sb.append("<table>\n");
				sb.append("<tr><td>Study UID:</td><td>" + sob.siUID + "</td></tr>");
				sb.append("<tr><td>Process date:</td><td>" + sob.date + "</td></tr>");
				sb.append("<tr><td>Patient ID:</td><td>" + sob.ptID + "</td></tr>");
				sb.append("<tr><td>Patient name:</td><td>" + sob.ptName + "</td></tr>");
				sb.append("</table>\n");
				sb.append("<br>");
				sb.append("<table class=\"sortable\">\n");
				sb.append("<thead>");
				sb.append("<tr>");
				sb.append(getSortColumnHeader("instances", 0, "SOP Instance UID"));
				sb.append(getSortColumnHeader("instances", 1, "Submit Date"));
				sb.append(getSortColumnHeader("instances", 2, "Entry Date"));
				sb.append("</tr>");
				sb.append("</thead>");
				sb.append("<tbody id=\"instances\">");
				Enumeration<String> keys = sob.getInstances();
				while (keys.hasMoreElements()) {
					String key = keys.nextElement();
					sb.append("<tr>");
					sb.append("<td>" + key + "</td>");
					sb.append("<td>" + sob.getSubmitDate(key) + "</td>");
					sb.append("<td>" + sob.getEntryDate(key) + "</td>");
					sb.append("</tr>");
				}
				sb.append("</tbody>");
				sb.append("</table>\n");
			}
			else sb.append("The requested study could not be found:<br>"+siuid);
			sb.append("</center>");
			return sb.toString();
		}
		catch (Exception ex) {
			logger.warn("Unable to process the request.",ex);
			return "Unable to process the request.";
		}
	}

	private String getSortColumnHeader(String tableID, int column, String title) {
		return "<th><a href=\"\" onclick=\"this.blur(); return sortTable('"+tableID+"', "+column+", false, true, false);\">"+title+"</a></th>";
	}

	private String responseHead(String title, String home) {
		return responseHead(title, home, "_self", "Return to the home page");
	}

	private String responseHead(String title, String url, String target, String page) {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>"+title+"</title>\n"
			+	"  <link rel=\"stylesheet\" href=\"/JSTableSort.css\" type=\"text/css\"/>\n"
			+	"  <style>\n"
			+	"    body {background-color:#c6d8f9; margin-top:0; margin-right:0;}\n"
			+	"    h1 {text-align:center; margin-top:10;}\n"
			+	"    table.footer td {padding:5;}\n"
			+	"  </style>\n"
			+	"  <script language=\"JavaScript\" type=\"text/javascript\" src=\"/JSTableSort.js\">;</script>\n"
			+	"  <script>function goto(context,p,s,qp,valueID) {\n"
			+	"    window.open('/'+context+'?p='+p+'&s='+s+'&'+qp+'='+document.getElementById(valueID).value,'_self');}\n"
			+	"  </script>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	HtmlUtil.getCloseBox(url, target, page)
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