/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import jdbm.helper.FastIterator;
import jdbm.htree.HTree;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.IDMap;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.XmlUtil;

/**
 * A Servlet which provides web access to the indexed data stored by an IDMap pipeline stage.
 */
public class IDMapServlet extends Servlet {

	static final Logger logger = Logger.getLogger(IDMapServlet.class);

	/**
	 * Construct an IDMapServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public IDMapServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page displaying either the list of
	 * IDMap stages or the search form for a selected IDMap.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {

		//Make sure the user is authorized to do this.
		String home = req.getParameter("home", "/");
		if (!req.userHasRole("admin")) { res.redirect(home); return; }

		//Get the selected stage, if possible.
		IDMap idMap = null;
		int p = -1;
		int s = -1;
		String pipeAttr = req.getParameter("p");
		String stageAttr = req.getParameter("s");
		if ((pipeAttr != null) && !pipeAttr.equals("") && (stageAttr != null) && !stageAttr.equals("")) {
			try {
				p = Integer.parseInt(pipeAttr);
				s = Integer.parseInt(stageAttr);
				Pipeline pipe = Configuration.getInstance().getPipelines().get(p);
				PipelineStage stage = pipe.getStages().get(s);
				if (stage instanceof IDMap) idMap = (IDMap)stage;
			}
			catch (Exception ex) { idMap = null; }
		}

		//Now make either the page listing the various IDMap stages
		//or the search page for the specified IDMap.
		if (idMap == null) res.write(getListPage(home));
		else res.write(getSearchPage(idMap, p, s, home));

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
	public void doPost(
			HttpRequest req,
			HttpResponse res) {

		//Make sure the user is authorized to do this.
		String home = req.getParameter("home", "/");
		if (!req.userHasRole("admin")) { res.redirect(home); return; }

		//Get the parameters from the form.
		String keyType = req.getParameter("keytype");
		String keys = req.getParameter("keys");
		String format = req.getParameter("format");
		String pParam = req.getParameter("p");
		String sParam = req.getParameter("s");

		//Find the IDMap stage.
		IDMap idMap = null;
		int p = -1;
		int s = -1;
		try {
			p = Integer.parseInt(pParam);
			s = Integer.parseInt(sParam);
			Pipeline pipe = Configuration.getInstance().getPipelines().get(p);
			PipelineStage stage = pipe.getStages().get(s);
			if (stage instanceof IDMap) idMap = (IDMap)stage;
		}
		catch (Exception ex) {
			doGet(req,res);
			return;
		}
		if (idMap == null) {
			res.setResponseCode(res.notfound);
			res.setContentType("html");
			res.disableCaching();
			res.send();
			return;
		}

		//Get the selected index.
		HTree index = null;
		if (keyType.equals("originalUID")) index = idMap.uidIndex;
		else if (keyType.equals("trialUID")) index = idMap.uidInverseIndex;
		else if (keyType.equals("originalPtID")) index = idMap.ptIDIndex;
		else if (keyType.equals("trialPtID")) index = idMap.ptIDInverseIndex;
		else if (keyType.equals("originalAN")) index = idMap.anIndex;
		else if (keyType.equals("trialAN")) index = idMap.anInverseIndex;
		if (index == null) {
			res.setResponseCode(res.notfound);
			res.setContentType("html");
			res.disableCaching();
			res.send();
			return;
		}

		//Get the selected data
		try {
			String keyTitle = (String)index.get("__keyTitle");
			String valueTitle = (String)index.get("__valueTitle");
			Pair[] data = getData(index, keys);
			if (format.equals("xml")) {
				res.setContentType("xml");
				res.write(getXML(data, keyTitle, valueTitle));
			}
			else if (format.equals("csv")) {
				String disposition = "attachment; filename=map.csv";
				res.setHeader("Content-Disposition",disposition);
				res.setContentType("txt");
				res.write(getCSV(data, keyTitle, valueTitle));
			}
			else {
				//The default is HTML
				res.setContentType("html");
				res.write(
					responseHead("Search Results from "+idMap.getName(),
								 "/" + context + "?p="+p+"&s="+s,
								 "_self",
								 "Return to the search page")
						+ getMapTable(data, keyTitle, valueTitle)
							+ responseTail());
			}
			res.disableCaching();
			res.send();
		}
		catch (Exception ex) { logger.warn("uh oh", ex); }
	}

	//Create an HTML page containing the list of IDMap stages.
	private String getListPage(String home) {
		return responseHead("Select the IDMap to Search", home)
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
					if (stage instanceof IDMap) {
						sb.append("<tr>");
						sb.append("<td width=\"50%\">"+pipe.getPipelineName()+"</td>");
						sb.append("<td><a href=\"/"+context+"?p="+p+"&s="+s+"\">"+stage.getName()+"</a></td>");
						sb.append("</tr>");
						count++;
					}
				}
			}
			sb.append("</table>");
			if (count == 0) sb.append("<p>The configuration contains no IDMap stages.</p>");
		}
		return sb.toString();
	}

	//Create an HTML page containing the form for configuring the file.
	private String getSearchPage(IDMap idMap, int p, int s, String home) {
		return responseHead("Search "+idMap.getName(), home)
				+ makeForm(idMap, p, s, home)
					+ responseTail();
	}

	private String makeForm(IDMap idMap, int p, int s, String home) {
		StringBuffer form = new StringBuffer();
		form.append("<form method=\"POST\" accept-charset=\"UTF-8\" action=\"/"+context+"\">\n");
		form.append(hidden("home",home));
		form.append(hidden("p",Integer.toString(p)));
		form.append(hidden("s",Integer.toString(s)));

		form.append("<center>\n");

		form.append("<table border=\"1\">\n");
		form.append("<tr>");
		form.append("<td><b>Select Key Type: </b></td>");
		form.append("<td>");
		form.append("<select name=\"keytype\">");
		form.append("<option value=\"originalUID\">Original UID</option>");
		form.append("<option value=\"trialUID\">Trial UID</option>");
		form.append("<option value=\"originalPtID\">Original PatientID</option>");
		form.append("<option value=\"trialPtID\">Trial PatientID</option>");
		form.append("<option value=\"originalAN\">Original Accession Number</option>");
		form.append("<option value=\"trialAN\">Trial Accession Number</option>");
		form.append("</select>");
		form.append("</td>");
		form.append("</tr>");

		form.append("<tr>");
		form.append("<td><b>Enter Keys:</b><br><span style=\"font-size:10pt;\">(one per line)</span></td>");
		form.append("<td><textarea name=\"keys\"></textarea></td>");
		form.append("</tr>");

		form.append("<tr>");
		form.append("<td><b>Select Return Format:</b></td>");
		form.append("<td>");
		form.append("<select name=\"format\">");
		form.append("<option value=\"html\">HTML</option>");
		form.append("<option value=\"xml\">XML</option>");
		form.append("<option value=\"csv\">CSV</option>");
		form.append("</select>");
		form.append("</td>");
		form.append("</tr>");

		form.append("</table>\n");
		form.append("</center>");
		form.append("<br/>\n");
		form.append("<input class=\"button\" type=\"submit\" value=\"Search\"/>\n");
		form.append("</form>\n");
		return form.toString();
	}

	private String hidden(String name, String text) {
		return "<input type=\"hidden\" name=\"" + name + "\" value=\"" + text + "\"/>";
	}

	private String responseHead(String title, String home) {
		return responseHead(title, home, "_self", "Return to the home page");
	}

	private String responseHead(String title, String url, String target, String tooltip) {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>"+title+"</title>\n"
			+	"   <style>\n"
			+	"    body {background-color:#c6d8f9; margin-top:0; margin-right:0;}\n"
			+	"    h1 {text-align:center; margin-top:10;}\n"
			+	"    textarea {width:600px; height:300px;}\n"
			+	"    select {width:600px;}\n"
			+	"    th {padding:5px;}\n"
			+	"    td {padding:5px;}\n"
			+	"    .button {width:250}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	HtmlUtil.getCloseBox(url, target, tooltip)
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

	private String getXML(Pair[] data, String keyTitle, String valueTitle) {
		StringBuffer sb = new StringBuffer();
		sb.append("<Map>\n");
		sb.append("  <KeyTitle>"+keyTitle+"</KeyTitle>\n");
		sb.append("  <ValueTitle>"+keyTitle+"</ValueTitle>\n");
		for (int i=0; i<data.length; i++) {
			sb.append("  <Pair>\n");
			sb.append("    <Key>"+data[i].key+"</Key>\n");
			sb.append("    <Value>"+data[i].value+"</Value>\n");
			sb.append("  </Pair>\n");
		}
		sb.append("</Map>\n");
		return sb.toString();
	}

	private String getCSV(Pair[] data, String keyTitle, String valueTitle) {
		StringBuffer sb = new StringBuffer();
		sb.append(keyTitle + "," + valueTitle + "\n");
		for (int i=0; i<data.length; i++) {
			sb.append("\""+data[i].key + "\",\"" + data[i].value + "\"\n");
		}
		return sb.toString();
	}

	private String getMapTable(Pair[] data, String keyTitle, String valueTitle) {
		StringBuffer sb = new StringBuffer("<table border=\"1\">");
		sb.append("<tr>");
		sb.append("<th>"+keyTitle+"</th>");
		sb.append("<th>"+valueTitle+"</th>");
		sb.append("</tr>");
		for (int i=0; i<data.length; i++) {
			sb.append("<tr>");
			sb.append("<td>"+data[i].key+"</td>");
			sb.append("<td>"+data[i].value+"</td>");
			sb.append("</tr>");
		}
		sb.append("</table>");
		return sb.toString();
	}

	private Pair[] getData(HTree index, String keyString) {
		String[] keys = keyString.trim().split("\n");
		LinkedList<Pair> pairs = new LinkedList<Pair>();
		try {
			if ((keys.length == 0) || ((keys.length == 1) && keys[0].trim().equals(""))) {
				//Return all the data in the index
				FastIterator fit = index.keys();
				String key;
				while ((key=(String)fit.next()) != null) {
					if (!key.startsWith("__")) {
						try {
							String value = (String)index.get(key);
							pairs.add(new Pair(key, value));
						}
						catch (Exception skipKey) { logger.debug("Unable to process "+key); }
					}
				}
			}
			else {
				for (int i=0; i<keys.length; i++) {
					String key = keys[i].trim();
					if (!key.equals("")) {
						try {
							String value = (String)index.get(key);
							if (value != null) pairs.add(new Pair(key, value));
							else pairs.add(new Pair(key, "null"));
						}
						catch (Exception skipKey) { logger.debug("Unable to process "+key); }
					}
				}
			}
		}
		catch (Exception stopLoading) {
			logger.debug("Exception caught while loading data.", stopLoading);
		}
		Pair[] data = new Pair[pairs.size()];
		data = pairs.toArray(data);
		Arrays.sort(data);
		return data;
	}

	class Pair implements Comparable {
		public String key;
		public String value;
		public Pair(String key, String value) {
			this.key = key;
			this.value = value;
		}
		public int compareTo(Object p) {
			return this.key.compareTo( ((Pair)p).key );
		}
	}

}

