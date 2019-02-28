/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.PolledHttpExportService;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.servlets.Servlet;

/**
 * The PolledServlet. This servlet returns.
 */
public class PolledServlet extends Servlet {

	static final Logger logger = Logger.getLogger(PolledServlet.class);

	/**
	 * A servlet to return files from the PolledHttpExportService queue.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public PolledServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: get a file from the PolledHttpExportService
	 * and send it in the response.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) throws Exception {
		
		//Get the PolledHttpExportService stage.
		//The stage must have the same id attribute as this servlet's context.
		PipelineStage stage = Configuration.getInstance().getRegisteredStage(context);
		if ((stage == null) || !(stage instanceof PolledHttpExportService)) {
			logger.warn("Unable to find the PolledHttpExportService stage with id \""+context+"\"");
			res.setResponseCode(res.notfound);
			res.send();
			return;
		}
		PolledHttpExportService phes = (PolledHttpExportService)stage;

		//Check the IP
		String connectionIP = req.getRemoteAddress();
		boolean accept = phes.getWhiteList().contains(connectionIP) 
							&& !phes.getBlackList().contains(connectionIP);
		logger.debug("Poll request "+(accept?"accepted":"rejected")+" from "+connectionIP);
		
		//If ok, get the file and return it
		File next;
		if (accept && ((next=phes.getNextFile()) != null)) {
			res.setContentDisposition(next);
			res.write(next);
			if (res.send()) {
				logger.debug("...successfully transmitted "+next);
				//Success, delete the file
				phes.release(next);
			}
			else {
				logger.debug("...transmission failed for "+next);
				//Something went wrong. Requeue the file and
				//delete it from its temporary location.
				phes.getQueueManager().enqueue(next);
				next.delete();
			}
		}
		else {
			logger.debug("...returning "+res.notfound);
			res.setResponseCode(res.notfound);
			res.send();
		}
	}

}

