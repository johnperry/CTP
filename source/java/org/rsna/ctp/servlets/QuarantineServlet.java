/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.pipeline.AbstractQueuedExportService;
import org.rsna.ctp.pipeline.AbstractImportService;
import org.rsna.ctp.pipeline.ImportService;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Quarantine;
import org.rsna.ctp.pipeline.QueueManager;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.Path;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The QuarantineServlet. This implementation provides access
 * to the contents of the quarantines.
 */
public class QuarantineServlet extends CTPServlet {

	static final Logger logger = Logger.getLogger(QuarantineServlet.class);

	Quarantine quarantine = null;

	/**
	 * Construct a QuarantineServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public QuarantineServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page showing the quarantines.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		super.loadParameters(req);

		if (!userIsAuthorized(req)) {
			res.setResponseCode(res.forbidden);
			res.send();
			return;
		}

		//Get the command
		Path path = req.getParsedPath();
		String command = path.element(1);

		//Get the parameters and find the quarantine

		if (logger.isDebugEnabled()) logger.debug(req.toString()+"\n"+
			("pipeline is "+((pipeline!=null)?"not ":"")+"null")+"  (p="+p+")\n"+
			("stage is "+((stage!=null)?"not ":"")+"null")+"  (s="+s+")\n"+
			"command: \""+command+"\"");

		if (stage != null) quarantine = stage.getQuarantine();

		if (pipeline == null) sizesPage(res);

		else if (stage == null) sizesPage(res, p);

		else if (quarantine == null) sizesPage(res, p);

		else if (command.equals("")) studiesPage(req, res, p, s);

		else if (command.equals("series")) seriesXML(req, res, p, s);

		else if (command.equals("files")) filesXML(req, res, p, s);

		else if (command.equals("queueAll")) {
			if (quarantine != null) {
				QueueManager queueManager = getClosestQueueManager();
				if (queueManager != null) quarantine.queueAll(queueManager);
			}
			studiesPage(req, res, p, s);
		}

		else if (command.equals("rebuildIndex")) {
			if (quarantine != null) {
				try { quarantine.rebuildIndex(); }
				catch (Exception ignore) { }
			}
			studiesPage(req, res, p, s);
		}

		else if (command.equals("queueStudy")) {
			String studyUID = req.getParameter("studyUID");
			if (quarantine != null) {
				QueueManager queueManager = getClosestQueueManager();
				if (queueManager != null) quarantine.queueStudy(studyUID, queueManager);
			}
			studiesPage(req, res, p, s);
		}

		else if (command.equals("queueSeries")) {
			String seriesUID = req.getParameter("seriesUID");
			if (quarantine != null) {
				QueueManager queueManager = getClosestQueueManager();
				if (queueManager != null) quarantine.queueSeries(seriesUID, queueManager);
			}
			studiesPage(req, res, p, s); //******remove this when switching to AJAX
		}

		else if (command.equals("queueFile")) {
			String filename = req.getParameter("filename");
			if ((quarantine != null) && (filename != null)) {
				QueueManager queueManager = getClosestQueueManager();
				if (queueManager != null) {
					File file = quarantine.getFile(filename);
					quarantine.queueFile(file, queueManager);
				}
			}
			studiesPage(req, res, p, s); //******remove this when switching to AJAX
		}

		else if (command.equals("deleteAll")) {
			if (quarantine != null) {
				quarantine.deleteAll();
			}
			studiesPage(req, res, p, s);
		}

		else if (command.equals("deleteStudy")) {
			String studyUID = req.getParameter("studyUID");
			if (quarantine != null) {
				quarantine.deleteStudy(studyUID);
			}
			studiesPage(req, res, p, s);
		}

		else if (command.equals("deleteSeries")) {
			String seriesUID = req.getParameter("seriesUID");
			if (quarantine != null) {
				quarantine.deleteSeries(seriesUID);
			}
			studiesPage(req, res, p, s); //******remove this when switching to AJAX
		}

		else if (command.equals("deleteFile")) {
			if (quarantine != null) {
				String filename = req.getParameter("filename");
				quarantine.deleteFile(filename);
			}
			studiesPage(req, res, p, s); //******remove this when switching to AJAX
		}

		else if (command.equals("displayFile")) displayFile(req, res);

		else if (command.equals("listFile")) listFile(req, res, p, s);

		else if (command.equals("downloadFile")) downloadFile(req, res);
	}

	private boolean userIsAuthorized(HttpRequest req) {
		boolean userIsAdmin = req.userHasRole("qadmin") || req.userHasRole("admin");
		return userIsAdmin || userIsStageAdmin;
	}

	//Get a page listing the sizes of all the quarantines
	void sizesPage(HttpResponse res) {
		StringBuffer sb = new StringBuffer();
		sb.append("<html><head><title>Quarantine</title>"+getStyles()+"</head><body>");
		sb.append("<center><h1>All Quarantines</h1>");
		sb.append("<table border=\"1\">");
		Configuration config = Configuration.getInstance();
		List<Pipeline> pipelines = config.getPipelines();
		if (pipelines.size() != 0) {
			for (int i=0; i<pipelines.size(); i++) {
				getSizes(sb, pipelines.get(i), i);
			}
		}
		sb.append("</table>");
		sb.append("</center></body></html>");
		//Send the response;
		res.disableCaching();
		res.write(sb.toString());
		res.setContentType("html");
		res.send();
	}

	//Get a page listing the sizes of the quarantines for a single pipeline
	void sizesPage(HttpResponse res, int pipelineIndex) {
		//Make sure the index is valid
		Pipeline pipeline;
		try { pipeline = Configuration.getInstance().getPipelines().get(pipelineIndex); }
		catch (Exception quit) {
			res.setResponseCode(res.notfound);
			res.send();
			return;
		}
		StringBuffer sb = new StringBuffer();
		sb.append("<html><head><title>Quarantine</title>"+getStyles()+"</head><body>");
		sb.append("<center><h1>"+pipeline.getPipelineName()+" Quarantines</h1>");
		sb.append("<table border=\"1\">");
		getSizes(sb, pipeline, pipelineIndex);
		sb.append("</table>");
		sb.append("</center></body></html>");
		res.disableCaching();
		res.write(sb.toString());
		res.setContentType("html");
		res.send();
	}

	//List the sizes of the quarantines for a single pipeline
	void getSizes(StringBuffer sb, Pipeline pipeline, int pipelineIndex) {
		List<PipelineStage> stages = pipeline.getStages();
		boolean first = true;
		for (int i=0; i<stages.size(); i++) {
			first &= !getSize(sb, pipeline, stages.get(i), first, pipelineIndex, i);
		}
	}

	//List the size of the quarantine for a single PipelineStage.
	boolean getSize(StringBuffer sb,
					Pipeline pipeline,
					PipelineStage stage,
					boolean first,
					int pipelineIndex,
					int stageIndex) {
		Quarantine quarantine = stage.getQuarantine();
		if (quarantine == null) return false;
		sb.append("<tr><td>");
		if (first) {
			sb.append("<a href=\"?p="+pipelineIndex+"\">"+pipeline.getPipelineName()+"</a>");
			first = false;
		}
		sb.append("</td><td>");
		sb.append("<a href=\"?p="+pipelineIndex+"&s="+stageIndex+"\">"+stage.getName()+"</a>");
		sb.append("</td><td style=\"text-align:right\">"+quarantine.getSize()+"</td></tr>");
		return true;
	}

	//List the studies in a quarantine
	void studiesPage(HttpRequest req, HttpResponse res, int pipelineIndex, int stageIndex) {
		try {
			Document doc = quarantine.getStudiesXML();
			Document xsl = XmlUtil.getDocument( FileUtil.getStream( "/QuarantineServlet.xsl" ) );
			Object[] params = {
				"context", context,
				"pipeline", pipeline.getName(),
				"stage", stage.getName(),
				"p", Integer.toString(pipelineIndex),
				"s", Integer.toString(stageIndex)
			};
			res.write( XmlUtil.getTransformedText( doc, xsl, params ) );
			res.setContentType("html");
			res.send();
		}
		catch (Exception quit) {
			logger.warn("studiesPage", quit);
			res.setResponseCode(res.notfound);
			res.send();
		}
	}

	void seriesXML(HttpRequest req, HttpResponse res, int pipelineIndex, int stageIndex) {
		try {
			String studyUID = req.getParameter("studyUID");
			Document doc = quarantine.getSeriesXML(studyUID);
			Document xsl = XmlUtil.getDocument( FileUtil.getStream( "/QuarantineSeriesList.xsl" ) );
			Object[] params = {
				"context", context,
				"p", Integer.toString(pipelineIndex),
				"s", Integer.toString(stageIndex)
			};
			res.write( XmlUtil.getTransformedText( doc, xsl, params ) );
			res.setContentType("xml");
			res.send();
		}
		catch (Exception quit) {
			res.setResponseCode(res.notfound);
			res.send();
		}
	}

	void filesXML(HttpRequest req, HttpResponse res, int pipelineIndex, int stageIndex) {
		try {
			String seriesUID = req.getParameter("seriesUID");
			Pipeline pipeline = Configuration.getInstance().getPipelines().get(pipelineIndex);
			PipelineStage stage = pipeline.getStages().get(stageIndex);
			Quarantine quarantine = stage.getQuarantine();
			Document doc = quarantine.getFilesXML(seriesUID);
			Document xsl = XmlUtil.getDocument( FileUtil.getStream( "/QuarantineFilesList.xsl" ) );
			Object[] params = {
				"context", context,
				"p", Integer.toString(pipelineIndex),
				"s", Integer.toString(stageIndex)
			};
			res.write( XmlUtil.getTransformedText( doc, xsl, params ) );
			res.setContentType("xml");
			res.send();
		}
		catch (Exception quit) {
			res.setResponseCode(res.notfound);
			res.send();
		}
	}

	//Find the closest QueueManager.
	//If the stage is an ExportService with a queue, then use the stage's QueueManager.
	//If the stage is not an ExportService, then use the QueueManager of the pipeline's ImportService.
	//If there are multiple ImportServices, pick the first one with a QueueManager
	//If there is no QueueManager, return null.
	QueueManager getClosestQueueManager() {
		QueueManager queueManager = null;
		if (stage instanceof AbstractQueuedExportService) {
			queueManager = ((AbstractQueuedExportService)stage).getQueueManager();
		}
		else {
			//Get the first ImportService with a QueueManager
			List<ImportService> importServices = pipeline.getImportServices();
			for (ImportService importService : importServices) {
				if (importService instanceof AbstractImportService) {
					queueManager = ((AbstractImportService)importService).getQueueManager();
					if (queueManager != null) break;
				}
			}
		}
		return queueManager;
	}

	//Get the file specified in a req
	File getFile(HttpRequest req, HttpResponse res) {
		if (quarantine == null) return null;
		String filename = req.getParameter("filename");
		File file = quarantine.getFile(filename);
		if (file == null) {
			res.setResponseCode(res.notfound);
			res.send();
		}
		return file;
	}

	//Download a file
	void downloadFile(HttpRequest req, HttpResponse res) {
		File file = getFile(req, res);
		if (file == null) return;
		res.disableCaching();
		res.write(file);
		res.setContentType(file);
		res.setHeader("Content-Disposition","attachment; filename=\"" + file.getName() + "\"");
		res.send();
	}

	void displayFile(HttpRequest req, HttpResponse res) {
		File file = getFile(req, res);
		if (file == null) return;
		DicomObject dicomObject = null;
		try {
			dicomObject = new DicomObject(file);
			if (dicomObject.isImage()) {
				File temp = File.createTempFile("Image-", ".jpeg");
				dicomObject.saveAsJPEG(temp, 0, 1500, 256, -1);
				res.write(temp);
				res.setContentType(temp);
				res.send();
				temp.delete();
				return;
			}
		}
		catch (Exception unable) { }
		downloadFile(req, res);
	}

	void listFile(HttpRequest req, HttpResponse res, int pipelineIndex, int stageIndex) {
		File file = getFile(req, res);
		if (file == null) return;
		DicomObject dicomObject = null;
		try {
			dicomObject = new DicomObject(file);
			StringBuffer sb = new StringBuffer();
			sb.append(getPageStart(pipeline, stage, true, file));
			sb.append("<body>");
			sb.append(dicomObject.getElementTable(true));
			sb.append("<p>");
			sb.append("<a href=\""
						+"/quarantines/downloadFile?p="+pipelineIndex
						+"&s="+stageIndex
						+"&filename="+file.getName()
						+"\">Download the file</a>");
			sb.append("</p>");
			sb.append("</center></body></html>");
			res.write(sb.toString());
			res.setContentType("html");
			res.send();
		}
		catch (Exception unable) { }
		downloadFile(req, res);
	}

	//Get the head part of the page.
	private String getPageStart(Pipeline pipeline, PipelineStage stage, boolean admin, File file) {
		StringBuffer sb = new StringBuffer();
		sb.append("<html><head><title>Quarantine</title>");
		sb.append(getStyles());
		sb.append("<script language=\"JavaScript\" type=\"text/javascript\" src=\"/JSAJAX.js\">;</script>\n");
		if (admin) {
			String filepath = "";
			if (file != null) filepath = file.getAbsolutePath();
			filepath = filepath.replaceAll("\\\\","/");
			sb.append("<script>\n");
			sb.append("var filepath = \""+filepath+"\";\n");
			sb.append(FileUtil.getText(new File("pages/decipher.js")));
			sb.append("</script>\n");
		}
		sb.append("</head>");
		sb.append("<body>");
		sb.append("<center><h1>"+pipeline.getPipelineName()
					+"<br>"+stage.getName()+" Quarantine</h1>");
		return sb.toString();
	}

	//Set up a standard set of styles for these pages.
	private String getStyles() {
		StringBuffer sb = new StringBuffer();
		sb.append("<link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/BaseStyles.css\"></link>");
		sb.append("\n<style>\n");
		sb.append("td {background-color:white; padding:5;}\n");
		sb.append("td span {background-color:white;}\n");
		sb.append("h1 {margin-top:10; margin-bottom:10;}\n");
		sb.append("</style>\n");
		return sb.toString();
	}

}
