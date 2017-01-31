/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.*;
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
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.rsna.ctp.stdstages.ScriptableDicom;
import org.rsna.ctp.stdstages.SupportsLookup;
import org.rsna.ctp.stdstages.XmlAnonymizer;
import org.rsna.ctp.stdstages.ZipAnonymizer;
import org.rsna.multipart.UploadedFile;
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

/**
 * The Lookup Table Editor servlet.
 * This servlet provides a browser-accessible user interface for
 * configuring the lookup table file for an anonymizer.
 * This servlet responds to both HTTP GET and POST.
 */
public class LookupServlet extends CTPServlet {

	static final Logger logger = Logger.getLogger(LookupServlet.class);
	static final String prefix = "..";

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
	public void doGet(HttpRequest req, HttpResponse res) {
		super.loadParameters(req);

		//Make sure the user is authorized to do this.
		if (!userIsAuthorized) {
			res.setResponseCode(res.forbidden);
			res.send();
			return;
		}

		//Now make either the page listing the various editable stages
		//or the page listing the contents of the specified file.
		int p,s;
		String format;
		File lutFile = null;
		res.setContentType("html");
		try {
			p = Integer.parseInt(req.getParameter("p"));
			s = Integer.parseInt(req.getParameter("s"));
			lutFile = getLookupTableFile(p, s);
			if (lutFile != null) {
				format = req.getParameter("format", "html").toLowerCase();
				if (format.equals("csv")) {
					res.write(getCSV(lutFile));
					res.setContentType("csv");
					res.setContentDisposition(new File(lutFile.getName()+".csv"));
				}
				else res.write(getEditorPage(p, s, lutFile));
			}
			else res.write(getListPage());
		}
		catch (Exception ex) { res.write(getListPage()); }

		//Return the page
		res.disableCaching();
		res.setContentEncoding(req);
		res.send();
	}
	
	/**
	 * The servlet method that responds to an HTTP PUT.
	 * This method updates a single key in the lookup table.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doPut(HttpRequest req, HttpResponse res) {
		super.loadParameters(req);
		res.disableCaching();
		res.setContentType("txt");
		
		//Make sure the user is authorized to do this.
		if (!userIsAuthorized) {
			res.setResponseCode(res.forbidden);
			res.send();
			return;
		}
		
		String id = req.getParameter("id");
		String key = req.getParameter("key");
		String value = req.getParameter("value");
		
		if ((id == null) || (key == null) || (value == null)) {
			res.setResponseCode(res.notmodified);
			res.send();
			return;
		}
		
		PipelineStage stage = Configuration.getInstance().getRegisteredStage(id);
		if ((stage == null) || !(stage instanceof SupportsLookup)) {
			res.setResponseCode(res.notmodified);
			res.send();
			return;
		}

		SupportsLookup lookupableStage = (SupportsLookup)stage;
		File lutFile = lookupableStage.getLookupTableFile();
		LookupTable lut = LookupTable.getInstance(lutFile);
		if (lut == null) {
			res.setResponseCode(res.notmodified);
			res.send();
			return;
		}
		
		Properties props = lut.getProperties();
		props.put(key, value);
		lut.save();

		res.write("OK");
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
	public void doPost(HttpRequest req, HttpResponse res) {
		super.loadParameters(req);

		//Make sure the user is authorized to do this.
		if (!userIsAuthorized || !req.isReferredFrom(context)) {
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
				HashSet<String> keyTypeSet = getKeyTypes(p, s);
				String defaultKeyType = ( (keyTypeSet.size()==1) ? keyTypeSet.iterator().next() : null );

				//Convert and save the file
				if ((file != null) && (files.size() > 0)) {
					File csvFile = files.getFirst().getFile();
					String props = getProps(csvFile, defaultKeyType);
					synchronized (this) { FileUtil.setText(file, props); }
				}

				//Make a new page from the new data and send it out
				res.disableCaching();
				res.setContentType("html");
				res.write(getEditorPage(p, s, file));
				res.setContentEncoding(req);
				res.send();
				return;
			}
			catch (Exception ex) { }
			FileUtil.deleteAll(dir);
		}
		else {
			//This is a post of the form from the page itself.
			//Get the parameters from the form.
			try {
				int p = Integer.parseInt(req.getParameter("p"));
				int s = Integer.parseInt(req.getParameter("s"));
				File file = getLookupTableFile(p,s);
				String defaultKeyType = req.getParameter("defaultKeyType", "").trim() + "/";
				boolean hasDefaultKeyType = !defaultKeyType.equals("/");

				if (file != null) {
					synchronized (this) {
						Properties props = getProperties(file);

						//Handle the main entry fields.
						String phi = req.getParameter("phi");
						String replacement = req.getParameter("replacement");
						boolean changed = false;
						if ((phi != null) && (replacement != null)) {
							phi = phi.trim();
							replacement = replacement.trim();
							if (!phi.equals("")) {
								if (hasDefaultKeyType && !phi.startsWith(defaultKeyType) && !phi.startsWith(prefix)) {
									phi = defaultKeyType + phi;
								}
								props.setProperty(phi, replacement);
								changed = true;
							}
						}

						//Handle the preset fields
						int k = 1;
						while ( true ) {
							String index = "[" + k + "]";
							phi = req.getParameter( "phi"+index );
							if (phi == null) break;
							phi = phi.trim();
							if (!phi.equals("")) {
								if (hasDefaultKeyType && !phi.startsWith(defaultKeyType)) {
									phi = defaultKeyType + phi;
								}
								String phikey = req.getParameter( "phikey"+index, "" ).trim();
								props.remove(prefix + phikey);
								props.setProperty(phi, phikey);
								changed = true;
							}
							k++;
						}

						//Save the LUT if the properties changed
						if (changed) saveProperties(props, file);
					}

					//Make a new page from the new data and send it out
					res.disableCaching();
					res.setContentType("html");
					res.write(getEditorPage(p, s, file));
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

	//Get the anonymizer script file
	private File getScriptFile(int p, int s) {
		PipelineStage stage = getPipelineStage(p, s);
		if ((stage != null) && (stage instanceof DicomAnonymizer)) {
			return ((DicomAnonymizer)stage).getScriptFile();
		}
		return null;
	}

	//Get the lookup table file
	private File getLookupTableFile(int p, int s) {
		PipelineStage stage = getPipelineStage(p, s);
		if ((stage != null) && (stage instanceof SupportsLookup)) {
			return ((SupportsLookup)stage).getLookupTableFile();
		}
		return null;
	}

	//Get a set containing all the KeyTypes in use in the script
	private HashSet<String> getKeyTypes(int p, int s) {
		PipelineStage stage = getPipelineStage(p, s);
		return getKeyTypes(stage);
	}

	private HashSet<String> getKeyTypes(PipelineStage stage) {
		HashSet<String> keyTypeSet = new HashSet<String>();
		if (stage instanceof DicomAnonymizer) {
			DicomAnonymizer anonymizer = (DicomAnonymizer)stage;
			File scriptFile = anonymizer.getScriptFile();
			DAScript script = DAScript.getInstance(scriptFile);
			Properties scriptProps = script.toProperties();
			Pattern pattern = Pattern.compile("@\\s*lookup\\s*\\([^,]+,([^),]+)");
			for (Object replObject : scriptProps.values()) {
				String repl = (String)replObject;
				Matcher matcher = pattern.matcher(repl);
				while (matcher.find()) {
					String group = matcher.group(1);
					keyTypeSet.add(group.trim());
				}
			}
		}
		else if ((stage instanceof XmlAnonymizer) || (stage instanceof ZipAnonymizer)) {
			File scriptFile = 
				(stage instanceof XmlAnonymizer) ? 
					((XmlAnonymizer)stage).getScriptFile() :
					((ZipAnonymizer)stage).getScriptFile() ;					
			String script = FileUtil.getText(scriptFile);
			Pattern pattern = Pattern.compile("\\s*\\$lookup\\s*\\([^,]+,([^),]+)");
			Matcher matcher = pattern.matcher(script);
			while (matcher.find()) {
				String group = matcher.group(1);
				group = group.trim();
				if (group.startsWith("\"") && group.endsWith("\"")) {
					group = group.substring(1, group.length()-1);
				}
				keyTypeSet.add(group.trim());
			}
		}
		return keyTypeSet;
	}

	//Convert the lookup table properties file text to a CSV string.
	private String getCSV(File file) {
		StringBuffer sb = new StringBuffer();
		BufferedReader br = null;
		try {
			br = new BufferedReader( new InputStreamReader(new FileInputStream(file), "UTF-8") );
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
		FileUtil.close(br);
		return sb.toString();
	}

	//Get the properties string from a CSV file
	private String getProps(File csvFile, String defaultKeyType) {
		boolean hasDefaultKeyType = (defaultKeyType != null);
		if (hasDefaultKeyType) defaultKeyType = defaultKeyType.trim() + "/";
		StringBuffer sb = new StringBuffer();
		BufferedReader br = null;
		try {
			br = new BufferedReader( new InputStreamReader(new FileInputStream(csvFile), "UTF-8") );
			String line;
			while ( (line=br.readLine()) != null ) {
				String[] s = line.split(",");
				if (s.length == 2) {
					String key = s[0].trim();
					if (hasDefaultKeyType && !key.startsWith(prefix) && !key.startsWith(defaultKeyType)) {
						key = defaultKeyType + key;
					}
					sb.append(key);
					sb.append("=");
					sb.append(s[1].trim());
					sb.append("\n");
				}
				else if (s.length == 1) {
					String key = s[0].trim();
					if (key.startsWith(prefix)) {
						sb.append(key);
						sb.append("=\n");
					}
				}
			}
		}
		catch (Exception returnWhatWeHaveSoFar) { }
		FileUtil.close(br);
		return sb.toString();
	}

	//Create an HTML page containing the list of files.
	private String getListPage() {
		try {
			Document doc = getStages();
			Document xsl = XmlUtil.getDocument( FileUtil.getStream( "/LookupServlet-List.xsl" ) );
			Object[] params = {
				"context", context,
				"home", home
			};
			return XmlUtil.getTransformedText( doc, xsl, params );
		}
		catch (Exception unable) { }
		return "";
	}

	private Document getStages() throws Exception {
		Configuration config = Configuration.getInstance();
		List<Pipeline> pipelines = config.getPipelines();
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("Stages");
		doc.appendChild(root);
		for (int p=0; p<pipelines.size(); p++) {
			Pipeline pipe = pipelines.get(p);
			List<PipelineStage> stages = pipe.getStages();
			for (int s=0; s<stages.size(); s++) {
				PipelineStage stage = stages.get(s);
				File file = null;
				if (stage instanceof SupportsLookup) {
					file = ((SupportsLookup)stage).getLookupTableFile();
				}
				if (file != null) {
					Element el = doc.createElement("Stage");
					el.setAttribute("pipelineName", pipe.getPipelineName());
					el.setAttribute("p", Integer.toString(p));
					el.setAttribute("stageName", stage.getName());
					el.setAttribute("s", Integer.toString(s));
					el.setAttribute("file", file.getAbsolutePath());
					root.appendChild(el);
				}
			}
		}
		return doc;
	}

	//Create an HTML page containing the form for configuring the file.
	private String getEditorPage(int p, int s, File lutFile) {
		try {
			Configuration config = Configuration.getInstance();
			List<Pipeline> pipelines = config.getPipelines();
			Pipeline pipeline = pipelines.get(p);
			List<PipelineStage> stages = pipeline.getStages();
			PipelineStage stage = stages.get(s);

			Document doc = getLUTDoc(stage, lutFile);
			//logger.info("LUTDoc:\n"+XmlUtil.toPrettyString(doc));

			Document xsl = XmlUtil.getDocument( FileUtil.getStream( "/LookupServlet-Editor.xsl" ) );
			Object[] params = {
				"context", context,
				"home", home,
				"pipeline", Integer.toString(p),
				"stage", Integer.toString(s),
				"pipelineName", pipeline.getPipelineName(),
				"stageName", stage.getName()
			};
			return XmlUtil.getTransformedText( doc, xsl, params );
		}
		catch (Exception unable) { }
		return "";
	}

	private Document getLUTDoc(PipelineStage stage, File lutFile) throws Exception {
		Document doc = XmlUtil.getDocument();
		Element root = doc.createElement("LookupTable");
		doc.appendChild(root);
		root.setAttribute("lutFile", lutFile.getAbsolutePath());
		if (stage instanceof DicomAnonymizer) {
			File scriptFile = ((DicomAnonymizer)stage).getScriptFile();
			root.setAttribute("scriptFile", scriptFile.getAbsolutePath());
		}

		//Put in the key types
		HashSet<String> keyTypeSet = getKeyTypes(stage);
		for (String keyType : keyTypeSet) {
			Element el = doc.createElement("KeyType");
			el.setAttribute("type", keyType);
			root.appendChild(el);
		}

		//Now add in the individual LUT entries
		Properties lutProps = getProperties(lutFile);
		Set<String> keySet = lutProps.stringPropertyNames();
		String[] keys = new String[keySet.size()];
		keys = keySet.toArray(keys);
		Arrays.sort(keys);
		for (String key : keySet) {
			if (key.startsWith(prefix)) {
				Element el = doc.createElement("Preset");
				el.setAttribute("key", key.substring(prefix.length()));
				el.setAttribute("value", "");
				root.appendChild(el);
			}
			else {
				Element el = doc.createElement("Entry");
				el.setAttribute("key", key);
				el.setAttribute("value", lutProps.getProperty(key));
				root.appendChild(el);
			}
		}
		return doc;
	}

	//Load a Properties file.
	private Properties getProperties(File file) {
		Properties props = new Properties();
		BufferedReader br = null;
		try {
			br = new BufferedReader(
					new InputStreamReader(
						new FileInputStream(file), "UTF-8") );
			props.load(br);
		}
		catch (Exception returnWhatWeHaveSoFar) { }
		finally { FileUtil.close(br); }
		return props;
	}

	// Save a Properties object in a file.
	public void saveProperties(Properties props, File file) {
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(
					new OutputStreamWriter(
						new FileOutputStream(file), "UTF-8") );
			props.store( bw, file.getName() );
			bw.flush();
		}
		catch (Exception e) { }
		finally { FileUtil.close(bw); }
	}

}
