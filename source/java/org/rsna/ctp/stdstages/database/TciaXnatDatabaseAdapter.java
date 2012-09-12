/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.database;

import java.io.File;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.Status;
import org.rsna.server.HttpResponse;
import org.rsna.util.Base64;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.net.HttpURLConnection;

/**
 * An adapter for passing data on objects to an external
 * XNAT data service.
 */
public class TciaXnatDatabaseAdapter extends DatabaseAdapter {

	static final Logger logger = Logger.getLogger(TciaXnatDatabaseAdapter.class);

	URL url;
	String username = null;
	String password = null;
	boolean authenticate = false;
	String authHeader = null;

	String sessionTag;
	String sessionName;

	/**
	 * DatabaseAdapter constructor, providing the ability to obtain
	 * configuration information from the DatabaseExportsService's
	 * config file element.
	 * @param element the configuration element
	 */
	public TciaXnatDatabaseAdapter(Element element) throws Exception {
		super(element);

		//Get the session identifiers
		sessionTag = element.getAttribute("sessionTag").trim();
		sessionName = element.getAttribute("sessionName").trim();

		//Get the credentials, if they are present.
		username = element.getAttribute("username").trim();
		password = element.getAttribute("password").trim();
		authenticate = !username.equals("");
		if (authenticate) {
			authHeader = "Basic " + Base64.encodeToString((username + ":" + password).getBytes());
		}

		//Get the destination url
		url = new URL(element.getAttribute("url"));
		String protocol = url.getProtocol().toLowerCase();
		if (!protocol.startsWith("https") && !protocol.startsWith("http")) {
			logger.error("Illegal protocol ("+protocol+")");
			throw new Exception();
		}
		if (url.getPort() == -1) {
			logger.error("No port specified: "+element.getAttribute("url"));
			throw new Exception();
		}
/**/	logger.info(url.getProtocol()+" protocol; port "+url.getPort());
	}

	/**
	 * Process a DICOM object. This method is called by
	 * the DatabaseExportService when it receives a DICOM file.
	 * @param dicomObject The DicomObject to be processed. This object points
	 * to a transient file in the queue.
	 * @param storedFile The File pointing to the object stored in the
	 * FileStorageService, or null if the object has not been stored.
	 * @param url The URL pointing to the stored object or null if no URL is available.
	 */
	public Status process(DicomObject dicomObject, File storedFile, String url) {
		try {
			//Require that the file have been stored.
			if (storedFile == null) {
				logger.error("Attempt to export an unstored file.");
				return Status.FAIL;
			}

			String session = sessionName;
			if (!sessionTag.equals("")) session = dicomObject.getElementValue(sessionTag, session);
			session = session.replaceAll("[^a-zA-Z0-9_]", "_");
			if (session.equals("")) session = "UNKNOWN";

			//Construct the XML document for transmission to the XNAT data service.
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("DICOMResource");
			doc.appendChild(root);

			root.setAttributeNS("xsi", "DICOMResource", "dicom-resource.xsd");
			root.setAttributeNS("xmlns", "xsi", "http://www.w3.org/2001/XMLSchema-instance");

			root.setAttribute("patient", dicomObject.getPatientID());
			root.setAttribute("session", session);
			root.setAttribute("studyUID", dicomObject.getStudyInstanceUID());
			root.setAttribute("seriesUID", dicomObject.getSeriesInstanceUID());
			root.setAttribute("seriesNumber", dicomObject.getSeriesNumber());
			root.setAttribute("instanceUID", dicomObject.getSOPInstanceUID());
			root.setAttribute("instanceNumber", dicomObject.getInstanceNumber());
			root.setAttribute("classUID", dicomObject.getSOPClassUID());

			Element uri = doc.createElement("URI");
			root.appendChild(uri);
			uri.setTextContent(storedFile.toURI().toString());

			String data = XmlUtil.toPrettyString(doc);
			logger.debug("Sending to XNAT data service:\n"+data);

			return send(data);
		}
		catch (Exception ex) { return Status.RETRY; }
	}

	private Status send(String data) {
		HttpURLConnection conn;
		BufferedWriter out;
		try {
			//Establish the connection
			conn = HttpUtil.getConnection(url);
			if (authenticate) conn.setRequestProperty("Authorization", authHeader);
			conn.connect();
			out = new BufferedWriter( new OutputStreamWriter( conn.getOutputStream(), FileUtil.utf8 ));

			//Send the data to the server
			out.write(data);
			out.flush();
			out.close();

			//Get the response code
			int responseCode = conn.getResponseCode();
			return (responseCode == HttpResponse.ok) ? Status.OK : Status.FAIL;
		}
		catch (Exception e) {
			logger.warn("Export failed: " + e.getMessage());
			logger.debug(e);
			return Status.RETRY;
		}
	}
}
