package edu.uams.tcia;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.QueueManager;
import org.rsna.ctp.pipeline.Status;
import org.rsna.server.HttpResponse;
import org.rsna.util.Base64;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Element;
import java.security.DigestInputStream;
import java.security.MessageDigest;

/**
 * An ExportService that exports files via the HTTP or HTTPS protocols to Posda sites.
 */
public class PosdaExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(PosdaExportService.class);

	static final int oneSecond = 1000;
	final int connectionTimeout = 20 * oneSecond;
	final int readTimeout = 120 * oneSecond;

	String url;
	String apikey;
	String protocol;
	String contentType = "application/x-mirc";
	boolean logUnauthorizedResponses = true;
	String lastPatientID = null;
	String eventID = "0";

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 * @throws Exception on any error
	 */
	public PosdaExportService(Element element) throws Exception {
		super(element);

		//Get the destination url
		url = element.getAttribute("url").trim();
		apikey = element.getAttribute("apikey").trim();
		logger.info(name+": url: "+url);
		logger.info(name+": apikey: \""+apikey+"\"");
	}
	
	/**
	 * Export a file.
	 * @param fileToExport the file to export.
	 * @return the status of the attempt to export the file.
	 */
	public Status export(File fileToExport) {
		
		//Do not export zero-length files
		long fileLength = fileToExport.length();
		if (fileLength == 0) return Status.FAIL;
		
		HttpURLConnection conn = null;
		OutputStream svros = null;
		try {
			FileObject fileObject = FileObject.getInstance( fileToExport );
			String patientID = fileObject.getPatientID();
			if ((patientID == null) || patientID.trim().equals("")) {
				logger.debug("PatientID null or blank");
				logger.debug("...Object instance: "+fileObject.getClass().getName());
				logger.debug("...PatientID = \""+patientID+"\"");
				logger.debug("...replacing with UNKNOWN");
				patientID = "UNKNOWN";
			}
			if ((lastPatientID == null) || !patientID.equals(lastPatientID)) {
				logger.debug("Requesting new eventID");
				logger.debug("...lastPatientID = \""+lastPatientID+"\"");
				logger.debug("...patientID = \""+patientID+"\"");
				lastPatientID = patientID;
				eventID = getImportEventID(patientID);
				logger.debug("...new eventID = "+eventID);
			}

			String hash = getDigest(fileToExport).toLowerCase();
			String query = "?import_event_id="+eventID+"&digest="+hash;
			if (!apikey.equals("")) query += "&apikey="+apikey;
			URL u = new URL(getURL() + query);
			logger.debug("Export URL: "+u.toString());
			
			//Establish the connection
			conn = HttpUtil.getConnection(u);
			conn.setReadTimeout(connectionTimeout);
			conn.setConnectTimeout(readTimeout);
			conn.setRequestMethod("PUT");
			conn.connect();

			//Send the file to the server
			svros = conn.getOutputStream();
			FileUtil.streamFile(fileToExport, svros);

			//Get the response
			Status result = Status.OK;
			int responseCode = conn.getResponseCode();
			String responseText = "";
			try { responseText = FileUtil.getTextOrException( conn.getInputStream(), FileUtil.utf8, false ); }
			catch (Exception ex) { logger.warn("Unable to read response: "+ex.getMessage()); }
			conn.disconnect();
			if (responseCode == HttpResponse.unprocessable) {
				logger.warn("Unprocessable response from server for: " + fileToExport);
				logger.warn("Response text: "+responseText);
				result = Status.FAIL;
			}
			else if (responseCode != HttpResponse.ok) {
				logger.warn("Failure response from server ("+responseCode+") for: " + fileToExport);
				logger.warn("Response text: "+responseText);
				result = Status.RETRY;
			}
			conn.disconnect();
			return result;
		}
		catch (Exception e) {
			if (logger.isDebugEnabled()) logger.debug(name+": export failed: " + e.getMessage(), e);
			else logger.warn(name+": export failed: " + e.getMessage());
			return logger.isDebugEnabled() ? Status.FAIL : Status.RETRY;
		}
	}
	
	public String getURL() throws Exception {
		return url + "/v1/import/file";
	}

	public String getEventIDRequestURL(String message) throws Exception {
		String u = url + "/v1/import/event?source=" + message;
		if (!apikey.equals("")) u += "&apikey="+apikey;
		return u;
	}
		
	//curl -X PUT http://localhost/.../v1/import/event?source=some+useful+message
	//{"status":"success","import_event_id":15}
	private String getImportEventID(String message) {
		HttpURLConnection conn = null;
		try {
			URL u = new URL(getEventIDRequestURL(message));
			logger.debug("getImportEventID");
			logger.debug("...URL: "+u.toString());
			conn = HttpUtil.getConnection(u);
			conn.setReadTimeout(connectionTimeout);
			conn.setConnectTimeout(readTimeout);
			conn.setRequestMethod("PUT");
			conn.connect();
			int responseCode = conn.getResponseCode();
			logger.debug("...responseCode: " + responseCode);
			String text = FileUtil.getTextOrException( conn.getInputStream(), FileUtil.utf8, false );
			conn.disconnect();
			logger.debug("...response text: \""+text+"\"");
			if (text.contains("\"status\":\"success\"") && text.contains("\"import_event_id\":")) {
				text = text.replaceAll("[^0-9]", "");
			}
			else text = "0";
			logger.debug("...returning "+text);
			return text;
		}
		catch (Exception unable) { 
			logger.debug("...unable to get import_event_id; returning 0");
			return "0"; 
		}
	}
	
	private String getDigest(File file) {
		String result = "";
		BufferedInputStream bis = null;
		DigestInputStream dis = null;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.reset();
			bis = new BufferedInputStream( new FileInputStream( file ) );
			dis = new DigestInputStream( bis, md );
			while (dis.read() != -1) ; //empty loop
			result = bytesToHex(md.digest());
		}
		catch (Exception ex) { result = ""; }
		finally {
			try { dis.close(); }
			catch (Exception ignore) { }
			try { bis.close(); }
			catch (Exception ignore) { }
		}
		return result.toString();
	}

	private String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	/**
	 * Get HTML text displaying the active status of the stage.
	 * @return HTML text displaying the active status of the stage.
	 */
	public synchronized String getStatusHTML() {
		return super.getStatusHTML("");
	}
	
}