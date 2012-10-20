/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.rsna.ctp.stdstages.ScriptableDicom;
import org.rsna.ctp.stdstages.XmlAnonymizer;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.StringUtil;

/**
 * The Lookup Table Configurator servlet.
 * This servlet provides a browser-accessible user interface for
 * configuring the lookup table file for an anonymizer.
 * This servlet responds to both HTTP GET and POST.
 */
public class LookupServlet extends Servlet {

	static final Logger logger = Logger.getLogger(LookupServlet.class);

	/**
	 * Construct a LookupServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public LookupServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * This method returns an HTML page containing a form for
	 * changing the contents of the lookup table.
	 * The contents of the form are constructed from the text of the file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
			HttpRequest req,
			HttpResponse res) {

		//Make sure the user is authorized to do this.
		String home = req.getParameter("home", "/");
		if (!req.userHasRole("admin")) { res.redirect(home); return; }

		//Now make either the page listing the various editable stages
		//or the page listing the contents of the specified file.
		int p,s;
		File file = null;
		try {
			p = Integer.parseInt(req.getParameter("p"));
			s = Integer.parseInt(req.getParameter("s"));
			file = getLookupTableFile(p,s);

			if (file != null)
				res.write(getEditorPage(p, s, file, home));
			else
				res.write(getListPage(home));
		}
		catch (Exception ex) { res.write(getListPage(home)); }

		//Return the page
		res.disableCaching();
		res.setContentType("html");
		res.send();
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
	public void doPost(
			HttpRequest req,
			HttpResponse res) {

		//Make sure the user is authorized to do this.
		String home = req.getParameter("home", "/");
		if (!req.userHasRole("admin") || !req.isReferredFrom(context)) {
			res.redirect(home);
			return;
		}

		//Get the parameters from the form.
		String phi = req.getParameter("phi");
		String replacement = req.getParameter("replacement");
		int p,s;
		File file = null;
		try {
			p = Integer.parseInt(req.getParameter("p"));
			s = Integer.parseInt(req.getParameter("s"));
			file = getLookupTableFile(p,s);

			//Update the file if possible.
			if ((phi != null) && (replacement != null) && (file != null)) {

				synchronized (this) {
					Properties props = getProperties(file);
					phi = phi.trim();
					replacement = replacement.trim();
					if (!phi.equals("")) {
						props.setProperty(phi,replacement);
						saveProperties(props,file);
					}
				}

				//Make a new page from the new data and send it out
				res.disableCaching();
				res.setContentType("html");
				res.write(getEditorPage(p, s, file, home));
				res.send();
				return;
			}
		}
		catch (Exception ex) { }
		res.setResponseCode(500); //Unable to perform the function.
		res.send();
	}

	//Get the lookup table file, if possible
	private File getLookupTableFile(int p, int s) {
		try {
			Configuration config = Configuration.getInstance();
			List<Pipeline> pipelines = config.getPipelines();
			Pipeline pipe = pipelines.get(p);
			List<PipelineStage> stages = pipe.getStages();
			PipelineStage stage = stages.get(s);
			if (stage instanceof DicomAnonymizer) {
				return ((DicomAnonymizer)stage).lookupTableFile;
			}
		}
		catch (Exception ex) { }
		return null;
	}

	//Create an HTML page containing the list of files.
	private String getListPage(String home) {
		return responseHead("Select the Lookup Table File to Edit", "", home)
				+ makeList()
					+ responseTail();
	}

	private String makeList() {
		StringBuffer sb = new StringBuffer();
		Configuration config = Configuration.getInstance();
		List<Pipeline> pipelines = config.getPipelines();
		if (pipelines.size() != 0) {
			int count = 0;
			sb.append("<table border=\"1\" width=\"100%\">");
			for (int p=0; p<pipelines.size(); p++) {
				Pipeline pipe = pipelines.get(p);
				List<PipelineStage> stages = pipe.getStages();
				for (int s=0; s<stages.size(); s++) {
					PipelineStage stage = stages.get(s);
					File file = null;
					if (stage instanceof ScriptableDicom) {
						file = ((ScriptableDicom)stage).getLookupTableFile();
					}
					if (file != null) {
						sb.append("<tr>");
						sb.append("<td>"+pipe.getPipelineName()+"</td>");
						sb.append("<td>"+stage.getName()+"</td>");
						sb.append("<td><a href=\"/"+context
										+"?p="+p
										+"&s="+s
										+"\">"
										+file.getAbsolutePath()+"</a></td>");
						sb.append("</tr>");
						count++;
					}
				}
			}
			sb.append("</table>");
			if (count == 0) sb.append("<p>The configuration contains no lookup tables.</p>");
		}
		return sb.toString();
	}

	//Create an HTML page containing the form for configuring the file.
	private String getEditorPage(int p, int s, File file, String home) {
		return responseHead("Lookup Table Editor", file.getAbsolutePath(), home)
				+ makeForm(p, s, file, home)
					+ responseTail();
	}

	private String makeForm(int p, int s, File file, String home) {
		Properties props = getProperties(file);
		Set<Object> keySet = props.keySet();
		Object[] keys = new Object[keySet.size()];
		keys = keySet.toArray(keys);
		Arrays.sort(keys);

		StringBuffer form = new StringBuffer();
		form.append("<form method=\"POST\" accept-charset=\"UTF-8\" action=\"/"+context+"\">\n");
		form.append(hidden("home", home));
		form.append(hidden("p", p + ""));
		form.append(hidden("s", s + ""));

		form.append("<center>\n");
		form.append("<table>\n");
		form.append("<tr>");
		form.append("<td><b><u>PHI value</u></b></td>");
		form.append("<td/>");
		form.append("<td><b><u>Replacement value</u></b></td>");
		form.append("</tr>");

		for (int i= 0; i<keys.length; i++) {
			String key = (String)keys[i];
			form.append("<tr>");
			form.append("<td>"+key+"</td>");
			form.append("<td><b>&nbsp;=&nbsp;</b></td>");
			form.append("<td>"+props.getProperty(key)+"</td>");
			form.append("</tr>\n");
		}
		form.append("<tr>");
		form.append("<td><input id=\"phi\" name=\"phi\"/></td>");
		form.append("<td>&nbsp;=&nbsp;</td>");
		form.append("<td><input name=\"replacement\"/></td>");
		form.append("</tr>");

		form.append("</table>\n");
		form.append("</center>");
		form.append("<br/>\n");
		form.append("<input class=\"button\" type=\"submit\" value=\"Update the file\"/>\n");
		form.append("</form>\n");
		return form.toString();
	}

	private String hidden(String name, String text) {
		return "<input type=\"hidden\" name=\"" + name + "\" value=\"" + text + "\"/>";
	}

	private String responseHead(String title, String subtitle, String home) {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>"+title+"</title>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/BaseStyles.css\"></link>\n"
			+	"   <style>\n"
			+	"    body {margin-top:0; margin-right:0;}\n"
			+	"    h1 {text-align:center; margin-top:10;}\n"
			+	"    h2 {text-align:center; font-size:12pt; margin:0; padding:0; font-weight:normal;}\n"
			+	"    textarea {width:75%; height:300px;}\n"
			+	"    .button {width:250}\n"
			+	"   </style>\n"
			+	"   <script>\n"
			+	"    function setFocus() {\n"
			+	"     var x = document.getElementById('phi');\n"
			+	"     if (x) x.focus();\n"
			+	"    }\n"
			+	"    window.onload = setFocus;\n"
			+	"   </script>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	HtmlUtil.getCloseBox(home)
			+	"  <h1>"+title+"</h1>\n"
			+	(subtitle.equals("") ? "" : "  <h2>"+subtitle+"</h2>")
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

	//Load a Properties file.
	private Properties getProperties(File file) {
		Properties props = new Properties();
		FileInputStream stream = null;
		try {
			stream = new FileInputStream(file);
			props.load(stream);
		}
		catch (Exception ignore) { }
		FileUtil.close(stream);
		return props;
	}

	// Save a Properties object in a file.
	public void saveProperties(Properties props, File file) {
		FileOutputStream stream = null;
		try {
			stream = new FileOutputStream(file);
			props.store(stream, file.getName());
		}
		catch (Exception e) {
			logger.warn("Unable to save the properties file "+file);
		}
		FileUtil.close(stream);
	}

}











