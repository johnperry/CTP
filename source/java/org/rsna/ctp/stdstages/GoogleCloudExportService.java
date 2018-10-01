/*---------------------------------------------------------------
 *  Copyright 2015 by the Radiological Society of North America
 *
 *  This source software is released under the terms of the
 *  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
 *----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.rsna.server.HttpResponse;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.codeminders.demo.DICOMGoogleClientHttpRequest;
import com.codeminders.demo.DICOMStoreDescriptor;
import com.codeminders.demo.GoogleAPIClient;
import com.codeminders.demo.GoogleAPIClientFactory;

/**
 * An ExportService that exports files via the DICOM STOW-RS protocol.
 */
public class GoogleCloudExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(GoogleCloudExportService.class);

	static final int oneSecond = 1000;
	final int connectionTimeout = 20 * oneSecond;
	final int readTimeout = 120 * oneSecond;

	boolean includeContentDispositionHeader = false;
	boolean logUnauthorizedResponses = true;
	boolean logDuplicates = false;

	/**/ LinkedList<String> recentUIDs = new LinkedList<String>();
	/**/ LinkedList<Long> recentTimes = new LinkedList<Long>();
	/**/static final int maxQueueSize = 10;

	private DICOMStoreDescriptor dicomStoreDecriptor;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 *
	 * @param element
	 *            the configuration element.
	 * @throws Exception
	 *             on any error
	 */
	public GoogleCloudExportService(Element element) throws Exception {
		super(element);

		// Get the flag for including the Content-Disposition header in requests
		includeContentDispositionHeader = element.getAttribute("includeContentDispositionHeader").trim().toLowerCase().equals("yes");

		String projectId = element.getAttribute("projectId").trim();
		String locationId = element.getAttribute("locationId").trim();
		String dataSetName = element.getAttribute("dataSetName").trim();
		String dicomStoreName = element.getAttribute("dicomStoreName").trim();

		dicomStoreDecriptor = new DICOMStoreDescriptor(projectId, locationId, dataSetName, dicomStoreName);

	}

	/**
	 * Export a file.
	 *
	 * @param fileToExport
	 *            the file to export.
	 * @return the status of the attempt to export the file.
	 */
	public Status export(File fileToExport) {

		// Do not export zero-length files
		long fileLength = fileToExport.length();
		if (fileLength == 0) {
			return Status.FAIL;
		}

		try {
			GoogleAPIClient apiClient = GoogleAPIClientFactory.getInstance().createGoogleClient();
			apiClient.signIn();
			FileObject fileObject = FileObject.getInstance(fileToExport);

			// Establish the connection
			apiClient.checkDicomstore(dicomStoreDecriptor);
			URL url = new URL(apiClient.getGHCDicomstoreUrl(dicomStoreDecriptor) + "/dicomWeb/studies");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setReadTimeout(connectionTimeout);
			conn.setConnectTimeout(readTimeout);
			conn.setRequestProperty("Authorization", "Bearer " + apiClient.getAccessToken());

			// Send the file to the server
			DICOMGoogleClientHttpRequest req = new DICOMGoogleClientHttpRequest(conn,
					"multipart/related; type=application/dicom;");
			if (!includeContentDispositionHeader) {
				req.addFilePart(fileToExport, "application/dicom");
			} else {
				String ctHeader = "Content-Type: application/dicom";
				String cdHeader = "Content-Disposition: form-data; name=\"stowrs\"; filename=\""
						+ fileToExport.getName() + "\";";
				String[] headers = { cdHeader, ctHeader };
				req.addFilePart(fileToExport, headers);
			}
			InputStream is = req.post();
			String response = FileUtil.getText(is, "UTF-8");
			conn.disconnect();

			logger.info("POST file("+fileToExport.getAbsolutePath()+") to dicomstore("+url+"). Response:" + response);
			
			// Get the response code and log Unauthorized responses
			int responseCode = conn.getResponseCode();
			if (logger.isDebugEnabled()) {
				try {
					Document doc = XmlUtil.getDocument(response);
					response = XmlUtil.toPrettyString(doc);
				} catch (Exception ex) {
					logger.error("Error during log formatting", ex);
				}
				logger.debug(name + ": Response code: " + responseCode);
				logger.debug(name + ": XML Response Message:\n" + response);
			}
			
			if (responseCode == HttpResponse.unauthorized) {
				if (logUnauthorizedResponses) {
					logger.warn("Unauthorized for " + url);
					logUnauthorizedResponses = false;
				}
				conn.disconnect();
				enableExport = false;
				return reportStatus(fileObject, Status.FAIL);
			} else if (responseCode == HttpResponse.forbidden) {
				if (logUnauthorizedResponses) {
					logger.warn("Forbidden for " + url);
					logUnauthorizedResponses = false;
				}
				conn.disconnect();
				enableExport = false;
				return reportStatus(fileObject, Status.FAIL);
			} else if (!logUnauthorizedResponses) {
				logUnauthorizedResponses = true;
			}

			if (responseCode == HttpResponse.ok) {
				makeAuditLogEntry(fileObject, Status.OK, getName(), url.toString());
				return reportStatus(fileObject, Status.OK);
			} else {
				return reportStatus(fileObject, Status.FAIL);
			}
		} catch (Exception e) {
			if (logger.isDebugEnabled()) {
				logger.debug(name + ": export failed: " + e.getMessage(), e);
			} else {
				logger.warn(name + ": export failed: " + e.getMessage());
			}
			return reportStatus(fileToExport, Status.FAIL);
		}
	}

	private Status reportStatus(File file, Status status) {
		ReportService.getInstance().addExported(file.getAbsolutePath(), status, "");
		return status;
	}

	private Status reportStatus(FileObject fileObject, Status status) {
		String info = ((DicomObject)fileObject).getFileMetaInfo().toString();
		ReportService.getInstance().addExported(fileObject.getFile().getAbsolutePath(), status, info);
		return status;
	}
}