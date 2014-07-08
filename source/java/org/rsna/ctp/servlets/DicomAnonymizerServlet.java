/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.rsna.ctp.stdstages.ScriptableDicom;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.Path;
import org.rsna.servlets.Servlet;
import org.rsna.util.Cache;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.StringUtil;

/**
 * The Anonymizer Configurator servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * configuring the anonymizer script file.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 */
public class DicomAnonymizerServlet extends CTPServlet {

	static final Logger logger = Logger.getLogger(DicomAnonymizerServlet.class);

	File dicomProfiles = new File("profiles/dicom");
	File savedProfiles = new File("profiles/saved");

	/**
	 * Construct a DicomAnonymizerServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public DicomAnonymizerServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
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

		//Disable caching of the response
		res.disableCaching();

		//Get the script file, if one is specified
		File file = getScriptFile();

		//Figure out what kind of GET this is
		Path path = new Path(req.getPath());
		int len = path.length();

		if ((len == 1) && (file == null)) {
			//This is a request for the script selection page
			res.setContentType("html");
			res.write(getListPage());
		}

		else if ((len == 1) && (file != null)) {
			//This is a request for the editor for the script specified by p and s
			res.setContentType("html");
			res.write(getScriptPage(p, s, file));
		}

		else if ((len == 2) && path.element(1).equals("profiles")) {
			//This is a request for a list of all the stored profiles
			res.setContentType("xml");
			res.write(getProfilesXML());
		}

		else if ((len == 4) && path.element(1).equals("profile")) {
			//This is a request for a profile specified in the URL path
			res.setContentType("xml");
			res.write(getProfileXML(path.element(2), path.element(3)));
		}

		else if ((len == 2) && path.element(1).equals("script")) {
			//This is a request for the script specified by p and s
			res.setContentType("xml");
			res.write(getScriptXML(file));
		}

		else res.setResponseCode(res.notfound);
		res.send();
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 *
	 * This method interprets the posted parameters as a new
	 * script and stores it either as an anonymizer script or
	 * a profile. It returns a text/plain string containing the
	 * "OK" if the store succeeded, or an error message if it failed.
	 *
	 * Note: This method is designed to be called by an AJAX method
	 * in the Javascript of the anonymizer configurator page.
	 *
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

		//Set up the response
		res.disableCaching();
		res.setContentType("txt");

		//Get the script file, if one is specified
		File file = getScriptFile();

		//Get the XML text to store
		String xml = req.getParameter("xml");
		if (xml != null) xml = xml.trim();
		else xml = "";

		//Figure out what kind of POST this is
		Path path = new Path(req.getPath());
		int len = path.length();

		if ((len == 3) && (path.element(1).equals("profile")) && !xml.equals("")) {
			//This is a request to store a specific profile.
			File profileFile = new File(savedProfiles, filter(path.element(2)));
			if (FileUtil.setText(profileFile, FileUtil.utf8, xml))
				res.write("OK");
			else
				res.write("Unable to store "+profileFile);
		}

		else if ((len == 2) && path.element(1).equals("script") && (file != null)) {
			//This is a request to save a specific script.
			//Don't force the extension on scripts because that
			//might invalidate the reference in the config file.
			if (FileUtil.setText(file, FileUtil.utf8, xml)) {
				res.write("OK");
				logger.debug("Successfully stored the posted script to "+file);
			}
			else {
				res.write("Unable to store "+file);
				logger.debug("Unable to store the posted script to "+file);
			}
		}

		else res.setResponseCode(res.notimplemented);
		res.send();
	}

	//Get the script file, if possible
	private File getScriptFile() {
		if (stage instanceof ScriptableDicom) {
			return ((ScriptableDicom)stage).getScriptFile();
		}
		return null;
	}

	private String getProfilesXML() {
		dicomProfiles.mkdirs();
		savedProfiles.mkdirs();
		StringBuffer sb = new StringBuffer();
		sb.append("<profiles>\n");
		addFiles(sb, dicomProfiles);
		addFiles(sb, savedProfiles);
		sb.append("</profiles>\n");
		return sb.toString();
	}

	private void addFiles(StringBuffer sb, File dir) {
		File[] files = dir.listFiles();
		String dirName = dir.getName().toLowerCase();
		for (int i=0; i<files.length; i++) {
			sb.append("  <"+dirName+" file=\""+files[i].getName()+"\"/>\n");
		}
	}

	private String getProfileXML(String library, String filename) {
		filename = filter(filename);
		if (library.equals("dicom")) {
			return getScriptXML(new File(dicomProfiles, filename));
		}
		else if (library.equals("saved")) {
			return getScriptXML(new File(savedProfiles, filename));
		}
		return "<script/>";
	}

	private String getScriptXML(File file) {
		if (file.exists() && file.isFile()) {
			DAScript dascript = DAScript.getInstance(file);
			return dascript.toXMLString();
		}
		return "<script/>";
	}

	//Create an HTML page containing the list of script files.
	private String getListPage() {
		String template = "/DAList.html";
		String page = FileUtil.getText( Cache.getInstance().getFile(template) );
		String table = makeList();
		Properties props = new Properties();
		props.setProperty("home", home);
		props.setProperty("table", table);
		props.setProperty("homeicon", "");
		if (!home.equals("")) {
			props.setProperty(
				"homeicon",
				"<img src=\"/icons/home.png\" onclick=\"window.open('"+home+"','_self');\" title=\"Return to the home page\" style=\"margin:2\"/>"
			);
		}
		page = StringUtil.replace(page, props);
		return page;
	}

	private String makeList() {
		StringBuffer sb = new StringBuffer();
		Configuration config = Configuration.getInstance();
		List<Pipeline> pipelines = config.getPipelines();
		int count = 0;
		if (pipelines.size() != 0) {
			sb.append("<table border=\"1\" width=\"100%\">\n");
			for (int p=0; p<pipelines.size(); p++) {
				Pipeline pipe = pipelines.get(p);
				List<PipelineStage> stages = pipe.getStages();
				for (int s=0; s<stages.size(); s++) {
					PipelineStage stage = stages.get(s);
					File scriptFile = null;
					if (stage instanceof ScriptableDicom)
						scriptFile = ((ScriptableDicom)stage).getScriptFile();
					if ((scriptFile != null) && scriptFile.exists()) {
						String scriptPath = scriptFile.getAbsolutePath();
						scriptPath = scriptPath.replace("\\","/");
						sb.append("<tr>\n");
						sb.append("<td class=\"list\">"+pipe.getPipelineName()+"</td>\n");
						sb.append("<td class=\"list\">"+stage.getName()+"</td>\n");
						sb.append("<td class=\"list\"><a href=\"/"
											+context
											+"?p="+p
											+"&s="+s
											+(home.equals("") ? "&suppress" : "")
											+"\">"
											+scriptPath+"</a></td>\n");
						sb.append("</tr>\n");
						count++;
					}
				}
			}
			sb.append("</table>");
		}
		if (count == 0) sb.append("<p>The pipeline contains no DICOM anonymizers.</p>");
		return sb.toString();
	}

	//Create an HTML page containing the form for configuring the file.
	private String getScriptPage(int pipe, int stage, File file) {
		String template = "/DAEditor.html";
		String page = FileUtil.getText( Cache.getInstance().getFile(template) );
		String table = makeList();
		Properties props = new Properties();
		props.setProperty("closebox", "/icons/home.png");
		props.setProperty("home", home);
		props.setProperty("context", "/"+context);
		props.setProperty("profilespath", "/profiles");
		props.setProperty("profilepath", "/profile");
		props.setProperty("scriptpath", "/script");
		props.setProperty("scriptfile", file.getAbsolutePath().replaceAll("\\\\","/"));
		props.setProperty("pipe", ""+pipe);
		props.setProperty("stage", ""+stage);
		page = StringUtil.replace(page, props);
		return page;
	}

}










