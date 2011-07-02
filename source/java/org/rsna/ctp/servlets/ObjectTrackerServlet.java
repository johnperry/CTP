/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
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
import org.rsna.ctp.stdstages.ObjectTracker;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A Servlet which provides web access to the indexed data stored by an ObjectTracker pipeline stage.
 */
public class ObjectTrackerServlet extends Servlet {

	static final Logger logger = Logger.getLogger(ObjectTrackerServlet.class);

	/**
	 * Construct an ObjectTrackerServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public ObjectTrackerServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page displaying either the list of
	 * ObjectTracker stages or the search form.for a selected ObjectTracker.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {

		//Make sure the user is authorized to do this.
		String home = req.getParameter("home", "/");
		if (!req.userHasRole("admin")) { res.redirect(home); return; }

		//Get the selected stage, if possible.
		ObjectTracker tracker = null;
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
				if (stage instanceof ObjectTracker) tracker = (ObjectTracker)stage;
			}
			catch (Exception ex) { tracker = null; }
		}

		//Now make either the page listing the various ObjectTracker stages
		//or the search page for the specified ObjectTracker.
		if (tracker == null) res.write(getListPage(home));
		else res.write(getSearchPage(tracker, p, s, home));

		//Return the page
		res.disableCaching();
		res.setContentType("html");
		res.send();
	}

	/**
	 * The POST handler
	 * This method interprets the posted parameters as search criteria
	 * for the selected ObjectTracker. It performs the search and then
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

		//Find the ObjectTracker stage.
		ObjectTracker tracker = null;
		int p = -1;
		int s = -1;
		try {
			p = Integer.parseInt(pParam);
			s = Integer.parseInt(sParam);
			Pipeline pipe = Configuration.getInstance().getPipelines().get(p);
			PipelineStage stage = pipe.getStages().get(s);
			if (stage instanceof ObjectTracker) tracker = (ObjectTracker)stage;
		}
		catch (Exception ex) {
			doGet(req,res);
			return;
		}
		if (tracker == null) {
			res.setResponseCode(404);
			res.setContentType("html");
			res.disableCaching();
			res.send();
			return;
		}

		//Get the selected data
		Document data = getData(tracker, keyType, keys);
		if (data == null) {
			res.setResponseCode(404);
			res.setContentType("html");
			res.disableCaching();
			res.send();
			return;
		}

		//Return the selected data in the specified format.
		try {
			if (format.equals("xml")) {
				res.setContentType("xml");
				res.write(XmlUtil.toString(data));
			}
			else if (format.equals("csv")) {
				String disposition = "attachment; filename=map.csv";
				res.setHeader("Content-Disposition",disposition);
				res.setContentType("txt");
				res.write(getCSV(data));
			}
			else {
				//The default is HTML
				res.setContentType("html");
				res.write(
					responseHead("Search Results from "+tracker.getName(),
								 "/" + context + "?home="+home+"&p="+p+"&s="+s,
								 "_self",
								 "Return to the search page")
						+ getHTML(data)
							+ responseTail());
			}
			res.disableCaching();
			res.send();
		}
		catch (Exception ex) { logger.warn("uh oh", ex); }
	}

	//Create an HTML page containing the list of ObjectTracker stages.
	private String getListPage(String home) {
		return responseHead("Select the ObjectTracker to Search", home)
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
					if (stage instanceof ObjectTracker) {
						sb.append("<tr>");
						sb.append("<td width=\"50%\">"+pipe.getPipelineName()+"</td>");
						sb.append("<td><a href=\"/"+context+"?p="+p+"&s="+s+"\">"+stage.getName()+"</a></td>");
						sb.append("</tr>");
						count++;
					}
				}
			}
			sb.append("</table>");
			if (count == 0) sb.append("<p>The configuration contains no ObjectTracker stages.</p>");
		}
		return sb.toString();
	}

	//Create an HTML page containing the form for configuring the file.
	private String getSearchPage(ObjectTracker tracker, int p, int s, String home) {
		return responseHead("Search "+tracker.getName(), home)
				+ makeForm(tracker, p, s, home)
					+ responseTail();
	}

	private String makeForm(ObjectTracker tracker, int p, int s, String home) {
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
		form.append("<option value=\"date\">Date</option>");
		form.append("<option value=\"patient\">PatientID</option>");
//		form.append("<option value=\"study\">StudyInstanceUID</option>");
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

	private String responseHead(String title, String home, String target, String tooltip) {
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
			+	HtmlUtil.getCloseBox(home, target, tooltip)
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

	private String getCSV(Document data) {
		StringBuffer sb = new StringBuffer();
		Element root = data.getDocumentElement();

		//Set the titles.
		NodeList nl = root.getElementsByTagName("Date");
		boolean includeDateColumn = (nl.getLength() != 0);
		if (includeDateColumn) sb.append("Date,");
		sb.append("PatientID,");
		sb.append("StudyUID,");
		sb.append("SeriesUID,");
		sb.append("Instances\n");

		//Insert the rows
		if (includeDateColumn) insertDates(sb, root, false);
		else insertPatients(sb, root, false);

		return sb.toString();
	}

	private String getHTML(Document data) {
		StringBuffer sb = new StringBuffer();
		Element root = data.getDocumentElement();

		sb.append("<table border=\"1\">\n");

		//Set the titles.
		sb.append("<tr>\n");
		NodeList nl = root.getElementsByTagName("Date");
		boolean includeDateColumn = (nl.getLength() != 0);
		if (includeDateColumn) sb.append("<th>Date</th>");
		sb.append("<th>PatientID</th>");
		sb.append("<th>StudyUID</th>");
		sb.append("<th>SeriesUID</th>");
		sb.append("<th>Instances</th>\n");
		sb.append("</tr>\n");

		//Insert the rows
		if (includeDateColumn) insertDates(sb, root, true);
		else insertPatients(sb, root, true);

		sb.append("</table>\n");
		return sb.toString();
	}

	private void insertDates(StringBuffer sb, Element parent, boolean html) {
		Node child = parent.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) && child.getNodeName().equals("Date")) {
				insertPatients(sb, (Element)child, html);
			}
			child = child.getNextSibling();
		}
	}

	private void insertPatients(StringBuffer sb, Element parent, boolean html) {
		Node child = parent.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) && child.getNodeName().equals("Patient")) {
				insertStudies(sb, (Element)child, html);
			}
			child = child.getNextSibling();
		}
	}

	private void insertStudies(StringBuffer sb, Element parent, boolean html) {
		Node child = parent.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) && child.getNodeName().equals("Study")) {
				insertSeries(sb, (Element)child, html);
			}
			child = child.getNextSibling();
		}
	}

	private void insertSeries(StringBuffer sb, Element parent, boolean html) {
		Node child = parent.getFirstChild();
		while (child != null) {
			if ((child.getNodeType() == Node.ELEMENT_NODE) && child.getNodeName().equals("Series")) {
				insertRow(sb, (Element)child, html);
			}
			child = child.getNextSibling();
		}
	}

	private void insertRow(StringBuffer sb, Element seriesElement, boolean html) {
		String seriesUID = seriesElement.getAttribute("uid");
		String size = seriesElement.getAttribute("size");
		Element studyElement = (Element)seriesElement.getParentNode();
		String studyUID = studyElement.getAttribute("uid");
		Element patientElement = (Element)studyElement.getParentNode();
		String patientID = patientElement.getAttribute("id");

		//Get the Date element, even though it might not be there.
		Element dateElement = (Element)patientElement.getParentNode();
		String date = dateElement.getAttribute("date");

		//Now make the row.
		if (html) {
			sb.append("<tr>\n");
			if (dateElement.getNodeName().equals("Date")) sb.append("<td>" + date + "</td>");
			sb.append("<td>" + patientID + "</td>");
			sb.append("<td>" + studyUID + "</td>");
			sb.append("<td>" + seriesUID + "</td>");
			sb.append("<td>" + size + "</td>\n");
			sb.append("</tr>\n");
		}
		else {
			if (dateElement.getNodeName().equals("Date")) sb.append(date + ",");
			sb.append(patientID + ",");
			sb.append(studyUID + ",");
			sb.append(seriesUID + ",");
			sb.append(size + "\n");
		}
	}

	private Document getData(ObjectTracker tracker, String keyType, String keyString) {
		try {
			String[] keys = keyString.trim().split("\n");
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("data");
			doc.appendChild(root);
			if (keyType.equals("date")) appendDates(root, tracker, keys, "\n  ");
			else appendPatients(root, tracker, keys, "\n  ");
			return doc;
		}
		catch (Exception ex) { return null; }
	}

	private void appendDates(Element parent, ObjectTracker tracker, String[] keys, String margin) {
		try {
			HTree index = tracker.dateIndex;
			if ((keys == null) || (keys.length == 0) || ((keys.length == 1) && keys[0].trim().equals(""))) {
				//Return all the data in the index
				LinkedList<String> keyList = new LinkedList<String>();
				FastIterator fit = index.keys();
				String key;
				while ((key=(String)fit.next()) != null) keyList.add(key);
				keys = new String[keyList.size()];
				keys = keyList.toArray(keys);
			}
			Arrays.sort(keys);
			for (int i=0; i<keys.length; i++) {
				parent.appendChild(parent.getOwnerDocument().createTextNode(margin));
				Element dateElement = parent.getOwnerDocument().createElement("Date");
				dateElement.setAttribute("date", keys[i]);
				parent.appendChild(dateElement);
				HashSet<String> patientSet = (HashSet<String>)index.get(keys[i]);
				if (patientSet != null) {
					String[] patients = new String[patientSet.size()];
					patients = patientSet.toArray(patients);
					appendPatients(dateElement, tracker, patients, margin+"  ");
				}
			}
		}
		catch (Exception ignore) { logger.debug(ignore); }
	}

	private void appendPatients(Element parent, ObjectTracker tracker, String[] keys, String margin) {
		try {
			HTree index = tracker.patientIndex;
			if ((keys == null) || (keys.length == 0) || ((keys.length == 1) && keys[0].trim().equals(""))) {
				//Return all the data in the index
				LinkedList<String> keyList = new LinkedList<String>();
				FastIterator fit = index.keys();
				String key;
				while ((key=(String)fit.next()) != null) keyList.add(key);
				keys = new String[keyList.size()];
				keys = keyList.toArray(keys);
			}
			Arrays.sort(keys);
			for (int i=0; i<keys.length; i++) {
				parent.appendChild(parent.getOwnerDocument().createTextNode(margin));
				Element patientElement = parent.getOwnerDocument().createElement("Patient");
				patientElement.setAttribute("id", keys[i]);
				parent.appendChild(patientElement);
				HashSet<String> studySet = (HashSet<String>)index.get(keys[i]);
				if (studySet != null) {
					String[] studies = new String[studySet.size()];
					studies = studySet.toArray(studies);
					appendStudies(patientElement, tracker, studies, margin+"  ");
				}
			}
		}
		catch (Exception ignore) { logger.debug(ignore); }
	}

	private void appendStudies(Element parent, ObjectTracker tracker, String[] keys, String margin) {
		try {
			HTree index = tracker.studyIndex;
			if ((keys == null) || (keys.length == 0) || ((keys.length == 1) && keys[0].trim().equals(""))) {
				//Don't do anything.
				return;
			}
			Arrays.sort(keys);
			for (int i=0; i<keys.length; i++) {
				parent.appendChild(parent.getOwnerDocument().createTextNode(margin));
				Element studyElement = parent.getOwnerDocument().createElement("Study");
				studyElement.setAttribute("uid", keys[i]);
				parent.appendChild(studyElement);
				HashSet<String> seriesSet = (HashSet<String>)index.get(keys[i]);
				if (seriesSet != null) {
					String[] series = new String[seriesSet.size()];
					series = seriesSet.toArray(series);
					appendSeries(studyElement, tracker, series, margin+"  ");
				}
			}
		}
		catch (Exception ignore) { logger.debug(ignore); }
	}

	private void appendSeries(Element parent, ObjectTracker tracker, String[] keys, String margin) {
		try {
			HTree index = tracker.seriesIndex;
			if ((keys == null) || (keys.length == 0) || ((keys.length == 1) && keys[0].trim().equals(""))) {
				return; //Don't do anything.
			}
			Arrays.sort(keys);
			for (int i=0; i<keys.length; i++) {
				parent.appendChild(parent.getOwnerDocument().createTextNode(margin));
				Element seriesElement = parent.getOwnerDocument().createElement("Series");
				seriesElement.setAttribute("uid", keys[i]);
				HashSet<String> sopSet = (HashSet<String>)index.get(keys[i]);
				int size = (sopSet != null) ? sopSet.size() : 0;
				seriesElement.setAttribute("size", ""+size);
				parent.appendChild(seriesElement);
			}
		}
		catch (Exception ignore) { logger.debug(ignore); }
	}

}