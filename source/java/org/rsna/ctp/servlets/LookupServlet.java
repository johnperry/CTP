/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.rsna.ctp.stdstages.ScriptableDicom;
import org.rsna.ctp.stdstages.XmlAnonymizer;
import org.rsna.multipart.UploadedFile;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.StringUtil;

/**
 * The Lookup Table Editor servlet.
 * This servlet provides a browser-accessible user interface for
 * configuring the lookup table file for an anonymizer.
 * This servlet responds to both HTTP GET and POST.
 */
public class LookupServlet extends Servlet {

	static final Logger logger = Logger.getLogger(LookupServlet.class);
	String home = "/";

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
		if (!req.userHasRole("admin")) { res.redirect(home); return; }

		//Now make either the page listing the various editable stages
		//or the page listing the contents of the specified file.
		int p,s;
		String format;
		File file = null;
		try {
			p = Integer.parseInt(req.getParameter("p"));
			s = Integer.parseInt(req.getParameter("s"));
			format = req.getParameter("format", "html").toLowerCase();
			file = getLookupTableFile(p, s);

			if (file != null) {
				if (format.equals("csv")) {
					res.write(getCSV(file));
					res.setContentType("csv");
					res.setContentDisposition(new File(file.getName()+".csv"));
				}
				else {
					res.write(getEditorPage(p, s, file, home));
					res.setContentType("html");
				}
			}
			else {
				res.write(getListPage(home));
				res.setContentType("html");
			}
		}
		catch (Exception ex) {
			res.write(getListPage(home));
			res.setContentType("html");
		}

		//Return the page
		res.disableCaching();
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
		if (!req.userHasRole("admin") || !req.isReferredFrom(context)) {
			res.setResponseCode(res.forbidden);
			res.send();
			return;
		}

		File dir = null;
		String contentType = req.getContentType().toLowerCase();
		if (contentType.contains("multipart/form-data")) {
			//This is a CSV submission
			try {
				//Make a temporary directory to receive the files
				dir = File.createTempFile("CSV-", ".dir");
				dir.delete();
				dir.mkdirs();

				//Get the parts. Note: this must be done before getting the parameters.
				LinkedList<UploadedFile> files = req.getParts(dir, 10*1024*1024);
				int p = Integer.parseInt(req.getParameter("p"));
				int s = Integer.parseInt(req.getParameter("s"));
				File file = getLookupTableFile(p,s);

				//Convert and save the file
				if ((file != null) && (files.size() > 0)) {
					File csvFile = files.getFirst().getFile();
					String props = getProps(csvFile);
					synchronized (this) {
						FileUtil.setText(file, props);
					}
				}

				//Make a new page from the new data and send it out
				res.disableCaching();
				res.setContentType("html");
				res.write(getEditorPage(p, s, file, home));
				res.send();
				return;
			}
			catch (Exception ex) { }
			FileUtil.deleteAll(dir);
		}
		else {
			//This is a post of the form from the page itself.
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
							props.setProperty(phi, replacement);
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
		}
		//If we get here, we couldn't handle the submission
		res.setResponseCode(res.notfound); //Unable to perform the function.
		res.send();
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

	//Get the lookup table file
	private File getLookupTableFile(int p, int s) {
		PipelineStage stage = getPipelineStage(p, s);
		if ((stage != null) && (stage instanceof DicomAnonymizer)) {
			return ((DicomAnonymizer)stage).lookupTableFile;
		}
		return null;
	}

	//Get the lookup table file
	private HashSet<String> getKeyTypes(int p, int s) {
		HashSet<String> set = new HashSet<String>();
		Pattern pattern = Pattern.compile("@\\s*lookup\\s*\\([^,]+,([^)]+)\\)");
		try {
			PipelineStage stage = getPipelineStage(p, s);
			if ((stage != null) && (stage instanceof DicomAnonymizer)) {
				DicomAnonymizer anonymizer = (DicomAnonymizer)stage;
				DAScript script = DAScript.getInstance(anonymizer.scriptFile);
				Properties props = script.toProperties();
				for (Object replObject : props.values()) {
					String repl = (String)replObject;
					Matcher matcher = pattern.matcher(repl);
					while (matcher.find()) {
						String group = matcher.group(1);
						set.add(group);
					}
				}
			}
		}
		catch (Exception ex) { logger.warn(ex.getMessage(), ex); }
		return set;
	}

	//Convert the lookup table file to a string.
	private String getCSV(File file) {
		StringBuffer sb = new StringBuffer();
		try {
			BufferedReader br = new BufferedReader(
									new InputStreamReader(new FileInputStream(file), "UTF-8") );
			String line;
			while ( (line=br.readLine()) != null ) {
				int k;
				line = line.trim();
				if (!line.startsWith("#") && ((k=line.indexOf("=")) != -1)) {
					sb.append( line.substring(0,k).trim() );
					sb.append(",");
					sb.append( line.substring(k+1).trim() );
					sb.append("\n");
				}
			}
		}
		catch (Exception returnWhatWeHaveSoFar) { }
		return sb.toString();
	}

	//Get the properties from a CSV file
	private String getProps(File csvFile) {
		StringBuffer sb = new StringBuffer();
		try {
			BufferedReader br = new BufferedReader(
									new InputStreamReader(new FileInputStream(csvFile), "UTF-8") );
			String line;
			while ( (line=br.readLine()) != null ) {
				String[] s = line.split(",");
				if (s.length == 2) {
					sb.append(s[0].trim());
					sb.append("=");
					sb.append(s[1].trim() );
					sb.append("\n");
				}
			}
		}
		catch (Exception returnWhatWeHaveSoFar) { }
		return sb.toString();
	}

	//Create an HTML page containing the list of files.
	private String getListPage(String home) {
		return responseHead("Select the Lookup Table File to Edit", "", home, false, null)
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
		HashSet<String> keyTypes = getKeyTypes(p, s);
		return responseHead("Lookup Table Editor", file.getAbsolutePath(), home, true, keyTypes)
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
		form.append("<form id=\"URLEncodedFormID\" method=\"POST\" accept-charset=\"UTF-8\" action=\"/"+context+"\">\n");
		form.append(hidden("p", p + ""));
		form.append(hidden("s", s + ""));

		form.append("<center>\n");
		form.append("<table>\n");
		form.append("<tr>");
		form.append("<td><b><u>KeyType/PHI value</u></b></td>");
		form.append("<td/>");
		form.append("<td><b><u>Replacement value</u></b></td>");
		form.append("</tr>");

		form.append("<tr>");
		form.append("<td><input id=\"phi\" name=\"phi\"/></td>");
		form.append("<td>&nbsp;=&nbsp;</td>");
		form.append("<td><input name=\"replacement\"/></td>");
		form.append("</tr>");

		for (int i= 0; i<keys.length; i++) {
			String key = (String)keys[i];
			form.append("<tr>");
			form.append("<td>"+key+"</td>");
			form.append("<td><b>&nbsp;=&nbsp;</b></td>");
			form.append("<td>"+props.getProperty(key)+"</td>");
			form.append("</tr>\n");
		}

		form.append("</table>\n");
		form.append("</center>");
		form.append("<br/>\n");
		form.append("<input class=\"button\" type=\"submit\" value=\"Update the file\"/>\n");
		form.append("</form>\n");
		return form.toString();
	}

	private String hidden(String name, String text) {
		return "<input type=\"hidden\" id=\"" + name + "\" name=\"" + name + "\" value=\"" + text + "\"/>";
	}

	private String responseHead(String title, String subtitle, String home, boolean includeCSVLinks, HashSet<String> keyTypes) {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>"+title+"</title>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/BaseStyles.css\"></link>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/JSPopup.css\"></link>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/LookupServlet.css\"></link>\n"
			+	"  <script language=\"JavaScript\" type=\"text/javascript\" src=\"/JSUtil.js\">;</script>\n"
			+	"  <script language=\"JavaScript\" type=\"text/javascript\" src=\"/JSAJAX.js\">;</script>\n"
			+	"  <script language=\"JavaScript\" type=\"text/javascript\" src=\"/JSPopup.js\">;</script>\n"
			+	"  <script language=\"JavaScript\" type=\"text/javascript\" src=\"/LookupServlet.js\">;</script>\n"
			+	" </head>\n"
			+	" <body>\n"

			+	"  <div style=\"float:right;\">\n"
			+	"   <img src=\"/icons/home.png\"\n"
			+	"    onclick=\"window.open('"+home+"','_self');\"\n"
			+	"    style=\"margin-right:2px;\"\n"
			+	"    title=\"Return to the home page\"/>\n"
			+	"   <br>\n"
			+	"   <img src=\"/icons/save.png\"\n"
			+	"    onclick=\"submitURLEncodedForm();\"\n"
			+	"    style=\"margin-right:2px;\"\n"
			+	"    title=\"Update the file\"/>\n"
			+	"   <br>\n";

		if (includeCSVLinks) {
			head +=
				    "   <br>\n"
				+	"   <img src=\"/icons/arrow-up.png\"\n"
				+	"    onclick=\"uploadCSV();\"\n"
				+	"    style=\"margin-left:4px; width:28px;\"\n"
				+	"    title=\"Upload CSV Lookup Table File\"/>\n"
				+	"   <br>\n"
				+	"   <img src=\"/icons/arrow-down.png\"\n"
				+	"    onclick=\"downloadCSV();\"\n"
				+	"    style=\"margin-left:4px; width:28px;\"\n"
				+	"    title=\"Download CSV Lookup Table File\"/>\n";
		}

		head +=
			    "  </div>\n"
			 +	"  <h1>"+title+"</h1>\n"
			 +	(subtitle.equals("") ? "" : "  <h2>"+subtitle+"</h2>\n")
			 +	"  <center>\n"
			 +	(subtitle.equals("") ? "" : "  <p>For instructions, see <a href=\""
			 +   "http://mircwiki.rsna.org/index.php?title=The_CTP_Lookup_Table_Editor\""
			 +   "target=\"wiki\">this article</a>.</p>\n");

		if (keyTypes != null) {
			if (keyTypes.size() == 0) {
				head += "<p>There are no KeyTypes specified in this DicomAnonymizer script.</p>\n";
			}
			else {
				head += "<p>KeyTypes used in this DicomAnonymizer script: ";
				boolean first = true;
				for (String kt : keyTypes) {
					head += (first ? "" : ", ") + kt;
				}
			}
		}

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











