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
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Quarantine;
import org.rsna.ctp.pipeline.QueueManager;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.StringUtil;

/**
 * The QuarantineServlet. This implementation provides access
 * to the contents of the quarantines.
 */
public class QuarantineServlet extends Servlet {

	static final Logger logger = Logger.getLogger(QuarantineServlet.class);

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
	 * There are several possible query strings:
	 * <ol>
	 *  <li>no query string:
	 *      return a page listing of the sizes of the quarantines for all pipelines.</li>
	 *  <li>?p=[p]:
	 *      return a listing of the sizes of the quarantines for pipeline p.
	 *  <li>?p=[p]&s=[s]:
	 *      return a listing of the contents of the quarantine for pipeline p, stage s.
	 *  <li>?p=[p]&s=[s]&file=[filename]:
	 *      return a listing of the file if one is available; otherwise,
	 *      return the identified file.
	 *  <li>?p=[p]&s=[s]&file=[filename]&list=no:
	 *      force return of the file, even if a listing page is available.
	 * </ol>
	 * The queue and delete query parameters cause files to be queued or deleted.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		boolean admin = req.userHasRole("qadmin");
		String home = req.getParameter("home", "/");
		//check the path information
		String p = req.getParameter("p");
		int pInt = StringUtil.getInt(p, -1);
		String s = req.getParameter("s");
		int sInt = StringUtil.getInt(s, -1);
		String file = req.getParameter("file");
		String list = req.getParameter("list");
		boolean queue = (req.getParameter("queue") != null);
		boolean delete = (req.getParameter("delete") != null);
		if (list == null) list = "";
		if ((p == null) || p.equals("") || (pInt == -1)) sizesPage(res, home);
		else if ((s == null) || s.equals("") || (sInt == -1)) sizesPage(res, pInt, home);
		else if ((file == null) || file.equals("")) {
			if (admin && queue) queueAll(pInt, sInt);
			if (admin && delete) deleteAll(pInt, sInt);
			contentsPage(req, res, pInt, sInt, admin);
		}
		else {
			if (admin && (queue || delete)) {
				if (queue) queueFile(pInt, sInt, file);
				if (delete) deleteFile(pInt, sInt, file);
				contentsPage(req, res, pInt, sInt, admin);
			}
			else downloadFile(res, pInt, sInt, file, list, admin, home);
		}
	}

	//Get a page listing the sizes of all the quarantines
	void sizesPage(HttpResponse res, String home) {
		StringBuffer sb = new StringBuffer();
		sb.append("<html><head><title>Quarantine</title>"+getStyles()+"</head><body>");
		sb.append(HtmlUtil.getCloseBox(home));
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
	void sizesPage(HttpResponse res, int pipelineIndex, String home) {
		//Make sure the index is valid
		Pipeline pipeline;
		try { pipeline = Configuration.getInstance().getPipelines().get(pipelineIndex); }
		catch (Exception quit) {
			res.setResponseCode(404);
			res.send();
			return;
		}
		StringBuffer sb = new StringBuffer();

		sb.append("<html><head><title>Quarantine</title>"+getStyles()+"</head><body>");
		sb.append(HtmlUtil.getCloseBox(home));
		sb.append("<center><h1>"+pipeline.getPipelineName()+" Quarantines</h1>");
		sb.append("<table border=\"1\">");
		getSizes(sb, pipeline, pipelineIndex);
		sb.append("</table>");
		sb.append("</center></body></html>");
		//Send the response;
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
		sb.append("</td><td>"+quarantine.getSize()+"</td></tr>");
		return true;
	}

	//Delete all the objects in a quarantine
	void deleteAll(int pipelineIndex, int stageIndex) {
		Pipeline pipeline;
		PipelineStage stage;
		try { pipeline = Configuration.getInstance().getPipelines().get(pipelineIndex); }
		catch (Exception quit) { return; }
		try { stage = pipeline.getStages().get(stageIndex); }
		catch (Exception quit) { return; }
		Quarantine quarantine = stage.getQuarantine();
		if (quarantine == null) return;
		quarantine.deleteAll();
	}

	//Queue all the objects in a quarantine to the closest QueueManager available.
	void queueAll(int pipelineIndex, int stageIndex) {
		//First find the pipeline and the stage.
		Pipeline pipeline;
		PipelineStage stage;
		try { pipeline = Configuration.getInstance().getPipelines().get(pipelineIndex); }
		catch (Exception quit) { return; }
		try { stage = pipeline.getStages().get(stageIndex); }
		catch (Exception quit) { return; }
		//Next, get the stage's quarantine.
		Quarantine quarantine = stage.getQuarantine();
		if (quarantine == null) return;
		//Now find the closest QueueManager.
		//If the stage is an ExportService with a queue, then use the stage's QueueManager.
		//If the stage is not an ExportService, then use the QueueManager of the pipeline's ImportService.
		//If the ImportService does not have a QueueManager, do nothing.
		QueueManager queueManager = null;
		if (stage instanceof AbstractQueuedExportService) {
			queueManager = ((AbstractQueuedExportService)stage).getQueueManager();
		}
		else {
			//Get the ImportService
			PipelineStage head = pipeline.getStages().get(0);
			if (head instanceof AbstractImportService) {
				queueManager = ((AbstractImportService)head).getQueueManager();
			}
		}
		if (queueManager == null) return;
		//Okay, now get all the files from the quarantine and enqueue them.
		quarantine.queueAll(queueManager);
	}

	//List the contents of a quarantine
	void contentsPage(HttpRequest req, HttpResponse res, int pipelineIndex, int stageIndex, boolean admin) {
		Pipeline pipeline;
		PipelineStage stage;
		try { pipeline = Configuration.getInstance().getPipelines().get(pipelineIndex); }
		catch (Exception quit) {
			res.setResponseCode(404);
			res.send();
			return;
		}
		try { stage = pipeline.getStages().get(stageIndex); }
		catch (Exception quit) {
			res.setResponseCode(404);
			res.send();
			return;
		}
		StringBuffer sb = new StringBuffer();
		sb.append("<html><head><title>Quarantine</title>"+getStyles()+"</head><body>");
		sb.append(HtmlUtil.getCloseBox());
		sb.append("<br><center><h1>"+pipeline.getPipelineName()
					+"<br>"+stage.getName()+" Quarantine</h1>");
		Quarantine quarantine = stage.getQuarantine();
		if (quarantine == null) {
			sb.append("<p>No quarantine is defined.</p>");
		}
		else {
			File[] files = quarantine.getFiles();
			if (files.length > 0) {
				if (admin) {
					sb.append("<input type=\"button\"");
					sb.append(" onclick=\"window.open('?p="+pipelineIndex+"&s="+stageIndex+"&queue','_self')\"");
					sb.append(" value=\"Queue All\"/>");
					sb.append("&nbsp;&nbsp;&nbsp;");
					sb.append("<input type=\"button\"");
					sb.append(" onclick=\"window.open('?p="+pipelineIndex+"&s="+stageIndex+"&delete','_self')\"");
					sb.append(" value=\"Delete All\"/>");
					sb.append("<br><br>");
				}
				sb.append("<table border=\"1\">");
				for (int i=0; i<files.length; i++) {
					sb.append("<tr>");
					sb.append("<td>");
					sb.append("<a href=\""
								+"?p="+pipelineIndex
								+"&s="+stageIndex
								+"&file="+files[i].getName()
								+"\">"+files[i].getName()+"</a>");
					sb.append("</td>");
					sb.append("<td>");
					sb.append(StringUtil.getDateTime(files[i].lastModified(),"&nbsp;&nbsp;&nbsp;"));
					sb.append("</td>");
					if (admin) {
						sb.append("<td>");
						sb.append("<a href=\""
									+"?p="+pipelineIndex
									+"&s="+stageIndex
									+"&file="+files[i].getName()
									+"&queue"
									+"\">queue</a>");
						sb.append("</td>");
						sb.append("<td>");
						sb.append("<a href=\""
									+"?p="+pipelineIndex
									+"&s="+stageIndex
									+"&file="+files[i].getName()
									+"&delete"
									+"\">delete</a>");
						sb.append("</td>");
					}
					sb.append("</tr>");
				}
				sb.append("</table>");
			}
			else sb.append("<p>The quarantine is empty.</p>");
		}
		sb.append("</center></body></html>");
		//Send the response;
		res.disableCaching();
		res.write(sb.toString());
		res.setContentType("html");
		res.send();
	}

	//Delete a single object from a quarantine
	void deleteFile(int pipelineIndex, int stageIndex, String filename) {
		Pipeline pipeline;
		PipelineStage stage;
		try { pipeline = Configuration.getInstance().getPipelines().get(pipelineIndex); }
		catch (Exception quit) { return; }
		try { stage = pipeline.getStages().get(stageIndex); }
		catch (Exception quit) { return; }
		Quarantine quarantine = stage.getQuarantine();
		if (quarantine == null) return;
		quarantine.deleteFile(filename);
	}

	//Queue a single object in a quarantine to the closest QueueManager available.
	void queueFile(int pipelineIndex, int stageIndex, String filename) {
		Pipeline pipeline;
		PipelineStage stage;
		try { pipeline = Configuration.getInstance().getPipelines().get(pipelineIndex); }
		catch (Exception quit) { return; }
		try { stage = pipeline.getStages().get(stageIndex); }
		catch (Exception quit) { return; }
		Quarantine quarantine = stage.getQuarantine();
		if (quarantine == null) return;
		QueueManager queueManager = null;
		if (stage instanceof AbstractQueuedExportService) {
			queueManager = ((AbstractQueuedExportService)stage).getQueueManager();
		}
		else {
			//Get the ImportService
			PipelineStage head = pipeline.getStages().get(0);
			if (head instanceof AbstractImportService) {
				queueManager = ((AbstractImportService)head).getQueueManager();
			}
		}
		if (queueManager == null) return;
		//Okay, now get all the files from the quarantine and enqueue them.
		quarantine.queueFile(filename, queueManager);
	}

	//Download a file
	void downloadFile(HttpResponse res,
					  int pipelineIndex,
					  int stageIndex,
					  String filename,
					  String list,
					  boolean admin,
					  String home) {
		Pipeline pipeline;
		PipelineStage stage;
		try { pipeline = Configuration.getInstance().getPipelines().get(pipelineIndex); }
		catch (Exception quit) {
			res.setResponseCode(404);
			res.send();
			return;
		}
		try { stage = pipeline.getStages().get(stageIndex); }
		catch (Exception quit) {
			res.setResponseCode(404);
			res.send();
			return;
		}
		res.disableCaching();
		StringBuffer sb = new StringBuffer();
		Quarantine quarantine = stage.getQuarantine();
		if (quarantine == null) {
			sb.append(getPageStart(pipeline, stage, false, null, home));
			sb.append("<p>No quarantine is defined.</p>");
			sb.append("</center></body></html>");
			res.write(sb.toString());
			res.setContentType("html");
			res.send();
			return;
		}
		File file = quarantine.getFile(filename);
		if (!file.exists()) {
			sb.append(getPageStart(pipeline, stage, false, null, home));
			sb.append("<p>The requested file is not in the quarantine.</p>");
			sb.append("</center></body></html>");
			res.write(sb.toString());
			res.setContentType("html");
			res.send();
			return;
		}
		//Okay, we finally have the file to download.
		//First, parse it to see if it is a DicomObject.
		DicomObject dicomObject = null;
		try { dicomObject = new DicomObject(file); }
		catch (Exception ignore) { dicomObject = null; }

		if ((dicomObject == null) || list.equals("no")) {
			//Just download the file with a standard
			//Content-Type as defined by its extension.
			res.write(file);
			res.setContentType(file);
			res.setHeader("Content-Disposition","attachment; filename=\"" + file.getName() + "\"");
			res.send();
			return;
		}
		//It is a DicomObject and listing is not suppressed.
		//Download a page containing an element listing
		//and a button allowing downloading of the file.
		sb.append(getPageStart(pipeline, stage, admin, file, home));
		sb.append("<body>");
		sb.append(dicomObject.getElementTable(admin));
		sb.append("<p>");
		sb.append("<a href=\""
					+"?p="+pipelineIndex
					+"&s="+stageIndex
					+"&file="+file.getName()
					+"&list=no"
					+"\">Download the file</a>");
		sb.append("</p>");
		sb.append("</center></body></html>");
		res.write(sb.toString());
		res.setContentType("html");
		res.send();
	}

	//Get the head part of the page.
	private String getPageStart(Pipeline pipeline, PipelineStage stage, boolean admin, File file, String home) {
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
		sb.append(HtmlUtil.getCloseBox(home));
		sb.append("<center><h1>"+pipeline.getPipelineName()
					+"<br>"+stage.getName()+" Quarantine</h1>");
		return sb.toString();
	}

	//Set up a standard set of styles for these pages.
	private String getStyles() {
		StringBuffer sb = new StringBuffer();
		sb.append("\n<style>\n");
		sb.append("body {background-color:#c6d8f9; margin-top:0; margin-right:0; padding:0;}\n");
		sb.append("td {background-color:white; padding:5;}\n");
		sb.append("td span {background-color:#c6d8f9;}\n");
		sb.append("h1 {margin-top:10; margin-bottom:10;}\n");
		sb.append("</style>\n");
		return sb.toString();
	}

}

