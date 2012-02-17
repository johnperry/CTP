/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.Properties;
import javax.net.ssl.HttpsURLConnection;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.rsna.server.HttpResponse;
import org.rsna.util.Base64;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.w3c.dom.Element;

/**
 * An ExportService that exports files via the HTTP or HTTPS protocols.
 */
public class HttpExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(HttpExportService.class);

	URL url;
	String protocol;
	boolean zip = false;
	String username = null;
	String password = null;
	boolean authenticate = false;
	String authHeader = null;
	boolean logUnauthorizedResponses = true;
	boolean logDuplicates = false;

/**/LinkedList<String> recentUIDs = new LinkedList<String>();
/**/LinkedList<Long> recentTimes = new LinkedList<Long>();
/**/static final int maxQueueSize = 10;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 */
	public HttpExportService(Element element) throws Exception {
		super(element);

		//Get the attribute which specifies whether files
		//are to be zipped before transmission.
		zip = element.getAttribute("zip").equals("yes");

		//See if we are to log duplicate transmissions
		logDuplicates = element.getAttribute("logDuplicates").equals("yes");

		//Get the credentials, if they are present.
		username = element.getAttribute("username").trim();
		password = element.getAttribute("password").trim();
		authenticate = !username.equals("");
		if (authenticate) {
			authHeader = "Basic " + Base64.encodeToString((username + ":" + password).getBytes());
		}

		//Get the destination url
		url = new URL(element.getAttribute("url"));
		protocol = url.getProtocol().toLowerCase();
		if (!protocol.startsWith("https") && !protocol.startsWith("http")) {
			logger.error(name+": Illegal protocol ("+protocol+")");
			throw new Exception();
		}
		if (url.getPort() == -1) {
			logger.error(name+": No port specified: "+element.getAttribute("url"));
			throw new Exception();
		}
/**/	logger.info(name+": "+url.getProtocol()+" protocol; port "+url.getPort());
	}

	/**
	 * Start the export thread.
	 */
	public void start() {
		startExportThread();
	}

	/**
	 * Export a file.
	 * @param fileToExport the file to export.
	 * @return the status of the attempt to export the file.
	 */
	public Status export(File fileToExport) {
		HttpURLConnection conn;
		OutputStream svros;
		try {
			//Establish the connection
			conn = HttpUtil.getConnection(url);
			if (authenticate) {
				conn.setRequestProperty("Authorization", authHeader);
				conn.setRequestProperty("RSNA", username+":"+password); //for backward compatibility
			}
			conn.connect();
			svros = conn.getOutputStream();

			if (logDuplicates) {
				//*********************************************************************************************
				//See if this object has the same UID as a recent one.
				FileObject fileObject = FileObject.getInstance( fileToExport );
				String currentUID = fileObject.getUID();
				if (recentUIDs.contains(currentUID)) {
					logger.warn("----------------------------------------------------------------");
					logger.warn(name);
					logger.warn("Duplicate UID in last "+maxQueueSize+" objects: "+currentUID);
					String s = "";
					long time = 0;
					for (int i=0; i<recentUIDs.size(); i++) {
						String uid = recentUIDs.get(i);
						s += uid.equals(currentUID) ? "!" : "*";
						time = recentTimes.get(i).longValue();
					}
					long deltaT = System.currentTimeMillis() - time;
					logger.warn("[oldest] "+s+"! [newest]  deltaT = "+deltaT+"ms");
					logger.warn("----------------------------------------------------------------");
				}
				recentUIDs.add(currentUID);
				recentTimes.add( new Long( System.currentTimeMillis() ) );
				if (recentUIDs.size() > maxQueueSize) { recentUIDs.remove(); recentTimes.remove(); }
				//*********************************************************************************************
			}

			//Send the file to the server
			if (!zip) FileUtil.streamFile(fileToExport, svros);
			else FileUtil.zipStreamFile(fileToExport, svros);

			//Get the response code and log Unauthorized responses
			int responseCode = conn.getResponseCode();
			if (responseCode == HttpResponse.unauthorized) {
				if (logUnauthorizedResponses) {
					logger.warn(name + ": Credentials for "+username+" were not accepted by "+url);
					logUnauthorizedResponses = false;
				}
				return Status.RETRY;
			}
			else if (responseCode == HttpResponse.forbidden) {
				if (logUnauthorizedResponses) {
					logger.warn(name + ": User "+username+" does not have the \"import\" privilege on "+url);
					logUnauthorizedResponses = false;
				}
				return Status.RETRY;
			}
			else if (!logUnauthorizedResponses) {
				logger.warn(name + ": Credentials for "+username+" have been accepted by "+url);
				logUnauthorizedResponses = true;
			}

			//Get the response.
			//Note: this rather odd way of acquiring a success
			//result is for backward compatibility with MIRC.
			String result = FileUtil.getText( conn.getInputStream() );
			if (result.equals("OK")) return Status.OK;
			else if (result.equals("")) return Status.RETRY;
			else return Status.FAIL;
		}
		catch (Exception e) {
			logger.warn(name+": export failed: " + e.getMessage());
			logger.debug(e);
			return Status.RETRY;
		}
	}
}