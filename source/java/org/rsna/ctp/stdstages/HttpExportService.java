/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

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

/**
 * An ExportService that exports files via the HTTP or HTTPS protocols.
 */
public class HttpExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(HttpExportService.class);

	static final long defaultMaxUnchunked = 20;
	static final int oneSecond = 1000;
	final int connectionTimeout = 20 * oneSecond;
	final int readTimeout = 120 * oneSecond;

	URL url;
	String protocol;
	boolean zip = false;
	String username = null;
	String password = null;
	boolean authenticate = false;
	String authHeader = null;
	String contentType = "application/x-mirc";
	boolean logUnauthorizedResponses = true;
	boolean logDuplicates = false;
	boolean sendDigestHeader = false;
	long maxUnchunked = defaultMaxUnchunked;

	int cacheSize = 0;
    String[] dirs = null;
	String defaultString = "UNKNOWN";
	String whitespaceReplacement = "_";
	String filter = "[^a-zA-Z0-9\\[\\]\\(\\)\\^\\.\\-_,;]+";
	Compressor compressor = null;
	ExportSession session = null;

/**/LinkedList<String> recentUIDs = new LinkedList<String>();
/**/LinkedList<Long> recentTimes = new LinkedList<Long>();
/**/static final int maxQueueSize = 10;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 * @throws Exception on any error
	 */
	public HttpExportService(Element element) throws Exception {
		super(element);

		//Get the attribute which specifies whether files
		//are to be zipped before transmission.
		zip = element.getAttribute("zip").trim().equals("yes");
		
		//Get the compressor parameters, if any
		getCompressorParameters(element);
		
		//Get the maxUnchunked parameter
		maxUnchunked = StringUtil.getLong(element.getAttribute("maxUnchunked"), defaultMaxUnchunked) * 1024 * 1024;

		//Get the Session object, if any
		session = new ExportSession(element);
		
		//See if we are to log duplicate transmissions
		logDuplicates = element.getAttribute("logDuplicates").equals("yes");

		//See if we are to send a digest header with a file transmission.
		//Note: digest headers are not supplied if zip file transmission is enabled.
		sendDigestHeader = element.getAttribute("sendDigestHeader").equals("yes");

		//Get the destination url
		url = new URL(element.getAttribute("url").trim());
		
		//Get the Content-Type
		contentType = element.getAttribute("contentType").trim();
		if (contentType.equals("")) contentType = "application/x-mirc";
		
		//Get the credentials attributes, if they are present.
		//Note: the credentials might be included in the username and password
		//attributes or embedded in the URL's userinfo. The username and password
		//attributes take precedence.
		username = element.getAttribute("username").trim();
		password = element.getAttribute("password").trim();
		String userinfo = url.getUserInfo();
		if (username.equals("") && (userinfo != null)) {
			String[] creds = userinfo.split(":");
			if (creds.length == 2) {
				username = creds[0];
				password = creds[1];
			}
		}
		authenticate = !username.equals("");
		
		if (authenticate) {
			authHeader = "Basic " + Base64.encodeToString((username + ":" + password).getBytes());
		}

		//Get the destination url
		url = new URL(element.getAttribute("url").trim());
		protocol = url.getProtocol().toLowerCase();
		if (!protocol.startsWith("https") && !protocol.startsWith("http")) {
			logger.error(name+": Illegal protocol ("+protocol+")");
			throw new Exception();
		}
		if (url.getPort() == -1) {
			logger.error(name+": No port specified: "+element.getAttribute("url"));
			throw new Exception();
		}
		logger.info(name+": "+url.getProtocol()+" protocol; port "+url.getPort());
	}
	
	private void getCompressorParameters(Element element) {
		//Get the compression child element, if present
		Element compressor = XmlUtil.getFirstNamedChild(element, "compressor");
		if (compressor != null) {
			//Get the attribute that specifies how many files
			//are to be cached into a zip file before transmission.
			//Note that the function of this attribute is independent 
			//of the zip attribute. If both are enabled, then the file
			//that is ultimately transmitted is a zip file containing
			//one zip file that itself contains cacheSize files. If 
			//neither is enabled, the individual files are transmitted
			//without compression.
			cacheSize = StringUtil.getInt(compressor.getAttribute("cacheSize").trim());
			if (cacheSize > 0) {
				File cache = new File(root, "cache");
				cacheManager = new QueueManager(cache, 0, 0);
			}
			//Get the structure of the directory tree and filename for files
			//to be stored in the zip file. This attribute is not used for 
			//non-DicomObjects or for non-cached files.
			String structure = compressor.getAttribute("structure").trim();
			if (!structure.equals("")) {
				dirs = structure.split("/");
			}
			//Get the replacement parameters, if supplied.
			String temp = compressor.getAttribute("defaultString").trim();
			if (!temp.equals("")) defaultString = temp;
			temp = compressor.getAttribute("whitespaceReplacement").trim();
			if (!temp.equals("")) whitespaceReplacement = temp;		
		}
	}

	/**
	 * Start the compressor thread if enabled.
	 */
	public void start() {
		super.start();
		if (cacheSize > 0) {
			compressor = new Compressor();
			compressor.start();
		}
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

			//Establish the connection
			conn = HttpUtil.getConnection(url);
			conn.setReadTimeout(connectionTimeout);
			conn.setConnectTimeout(readTimeout);
			conn.setRequestProperty("Content-Type", contentType);
			if (authenticate) {
				conn.setRequestProperty("Authorization", authHeader);
				conn.setRequestProperty("RSNA", username+":"+password); //for backward compatibility
			}
			if (sendDigestHeader && !zip) {
				conn.setRequestProperty("Digest", fileObject.getDigest());
			}
			session.setCookie(conn);
			if (fileLength > maxUnchunked) conn.setChunkedStreamingMode(0);
			if (logger.isDebugEnabled()) logConnection(conn);
			conn.connect();

			if (logDuplicates) {
				//*********************************************************************************************
				//See if this object has the same UID as a recent one.
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
			svros = conn.getOutputStream();
			if (!zip) FileUtil.streamFile(fileToExport, svros);
			else FileUtil.zipStreamFile(fileToExport, svros);

			//Get the response code and log Unauthorized responses
			int responseCode = conn.getResponseCode();
			logger.debug(name+": Transmission response code = "+responseCode);
			
			if (responseCode == HttpResponse.notfound) {
				conn.disconnect();
				return Status.RETRY;
			}
			
			if (responseCode == HttpResponse.unauthorized) {
				if (logUnauthorizedResponses) {
					logger.warn(name + ": Credentials for "+username+" were not accepted by "+url);
					logUnauthorizedResponses = false;
				}
				conn.disconnect();
				enableExport = false;
				return failOrRetry();
			}
			else if (responseCode == HttpResponse.forbidden) {
				if (logUnauthorizedResponses) {
					logger.warn(name + ": User "+username+" does not have the \"import\" privilege on "+url);
					logUnauthorizedResponses = false;
				}
				conn.disconnect();
				enableExport = false;
				return failOrRetry();
			}
			else if (!logUnauthorizedResponses) {
				logger.warn(name + ": Credentials for "+username+" have been accepted by "+url);
				logUnauthorizedResponses = true;
			}

			//Get the response.
			//Note: this rather odd way of acquiring a success
			//result is for backward compatibility with MIRC.
			//We leave the input stream open in order to make
			//the disconnect actually close the connection.
			String result = "";
			try { result = FileUtil.getTextOrException( conn.getInputStream(), FileUtil.utf8, false ); }
			catch (Exception ex) { logger.warn("Unable to read response: "+ex.getMessage()); }
			logger.debug(name+": Response: "+result);
			conn.disconnect();
			if (result.equals("OK")) {
				makeAuditLogEntry(fileObject, Status.OK, getName(), url.toString());
				return Status.OK;
			}
			else return Status.FAIL;
		}
		catch (Exception e) {
			if (logger.isDebugEnabled()) logger.debug(name+": export failed: " + e.getMessage(), e);
			else logger.warn(name+": export failed: " + e.getMessage());
			return failOrRetry();
		}
	}
	
	private Status failOrRetry() {
		return logger.isDebugEnabled() ? Status.FAIL : Status.RETRY;
	}
	
	private void logConnection(HttpURLConnection conn) {
		StringBuffer sb = new StringBuffer();
		sb.append(name+": Connection parameters:\n");
		sb.append("Request method: "+conn.getRequestMethod()+"\n");
		sb.append("URL: "+conn.getURL()+"\n");
		sb.append("Request properties:\n");
		Map<String,List<String>> props = conn.getRequestProperties();
		for (String prop : props.keySet()) {
			List<String> list = props.get(prop);
			for (String value : list) {
				sb.append(prop + " : " + value + "\n");
			}
		}
		logger.debug(sb.toString());
	}

	/**
	 * Get HTML text displaying the active status of the stage.
	 * @return HTML text displaying the active status of the stage.
	 */
	public synchronized String getStatusHTML() {
		StringBuffer sb = new StringBuffer();
		if (cacheSize > 0) {
			sb.append("<tr><td width=\"20%\">Cache queue size:</td>");
			sb.append("<td>" + ((cacheManager!=null) ? cacheManager.size() : "???") + "</td></tr>");
		}
		return super.getStatusHTML(sb.toString());
	}
	
	class Compressor extends Thread {
		File cacheTemp;
		File cacheZip;
		File zip;
		NameTable names;
		public Compressor() {
			super(name + " - compressor");
			cacheTemp = new File(root, "cacheTemp");
			cacheZip = new File(root, "cacheZip");
			cacheTemp.mkdirs();
			cacheZip.mkdirs();
			zip = new File(cacheZip, "cache.zip");
		}
		public void run() {
			while (!stop && !interrupted() && (cacheSize > 0)) {
				names = new NameTable();
				LinkedList<File> fileList = new LinkedList<File>();
				int nFiles = 0;
				for (int i=0; i<cacheSize; i++) {
					File file = cacheManager.dequeue(cacheTemp);
					if (file == null) break;
					nFiles++;
					File destDir = cacheTemp;

					//If this is a DicomObject, put it in the hierarchy
					try {
						//Construct the child directories under cacheTemp.
						DicomObject dob = new DicomObject(file);
						for (int k=0; k<dirs.length - 1; k++) {
							String dir = dirs[k].trim();
							dir = replace(dir, dob);
							if (dir.equals("")) dir = defaultString;
							destDir = new File(destDir, dir);
						}
						destDir.mkdirs();
						String name = dirs[dirs.length - 1].trim();
						if (!name.equals("")) {
							name = replace(name, dob);
							if (name.equals("")) name = defaultString;
							name += ".dcm";
							name = names.getDuplicateName(destDir, name, ".dcm");
							File dobFile = new File(destDir, name);
							dob.renameTo(dobFile);
							dob.setStandardExtension();						
						}
					}
					catch (Exception notDICOM) { }
				}
				if (nFiles > 0) {
					logger.debug("Compressing "+nFiles+" files for transmission.");
					if (FileUtil.zipDirectory(cacheTemp, zip, true)) {
						getQueueManager().enqueue(zip);
						zip.delete();
						for (File file : cacheTemp.listFiles()) FileUtil.deleteAll(file);
					}
				}
				if (cacheManager.size() <= 0) {
					try { Thread.sleep(getInterval()); }
					catch (Exception ex) { }
				}
			}
		}
		private String replace(String string, DicomObject dob) {
			try {
				String singleTag = "[\\[\\(][0-9a-fA-F]{0,4}[,]?[0-9a-fA-F]{1,4}[\\]\\)]";
				Pattern pattern = Pattern.compile( singleTag + "(::"+singleTag+")*" );

				Matcher matcher = pattern.matcher(string);
				StringBuffer sb = new StringBuffer();
				while (matcher.find()) {
					String group = matcher.group();
					String repl = getElementValue(dob, group);
					if (repl.equals("")) repl = defaultString;
					matcher.appendReplacement(sb, repl);
				}
				matcher.appendTail(sb);
				string = sb.toString();
				string = string.replaceAll("[\\\\/\\s]+", whitespaceReplacement).trim();
				string = string.replaceAll(filter, "");
			}
			catch (Exception ex) { logger.warn(ex); }
			return string;
		}
		private String getElementValue(DicomObject dob, String group) {
			String value = "";
			try {
				int[] tags = DicomObject.getTagArray(group);
				value = dob.getElementString(tags);
			}
			catch (Exception ex) { logger.debug("......exception processing: "+group); }
			return value;
		}
		
		//An implementation of java.io.FileFilter to return
		//only files whose names begin with a specified string.
		class NameFilter implements FileFilter {
			String name;
			public NameFilter(String name) {
				this.name = name;
			}
			public boolean accept(File file) {
				if (file.isFile()) {
					String fn = file.getName();
					return fn.equals(name) || fn.startsWith(name + ".") || fn.startsWith(name + "[");
				}
				return false;
			}
		}
		
		class NameTable {
			Hashtable<String,Integer> names;
			public NameTable() {
				names = new Hashtable<String,Integer>();
			}
			private String getDuplicateName(File dir, String name, String ext) {
				boolean hasExtension = name.toLowerCase().endsWith(ext.toLowerCase());
				if (hasExtension) name = name.substring( 0, name.length() - ext.length() );
				Integer count = names.get(name);
				if (count == null) {
					names.put(name, new Integer(1));
					return name + (hasExtension ? ext : "");
				}
				else {
					int n = count.intValue();
					names.put(name, new Integer(n + 1));
					return name + "["+n+"]" + (hasExtension ? ext : "");
				}
			}
		}	
	}
	
	class ExportSession {
		URL url = null;
		String cookieName = null;
		String cookieValue = null;
		String credentials = null;
		long lastTime = 0;
		long timeout = 10 * 60 * 1000; //10 minutes
		
		public ExportSession(Element element) {
			Element xnat = XmlUtil.getFirstNamedChild(element, "xnat");
			if (xnat != null) {
				String urlString = xnat.getAttribute("url").trim();
				cookieName = xnat.getAttribute("cookieName").trim();
				if (cookieName.equals("")) cookieName = "JSESSIONID";
				String username = xnat.getAttribute("username").trim();
				String password = xnat.getAttribute("password").trim();
				credentials = "Basic " + Base64.encodeToString((username + ":" + password).getBytes());
				try { url = new URL(urlString); }
				catch (Exception ex) {
					logger.warn("Unable to construct XNAT URL: \""+urlString+"\"");
				}
			}
		}
		
		public boolean isConfigured() {
			return cookieName != null;
		}
		
		public void invalidate() {
			lastTime = 0;
		}
		
		public void setCookie(HttpURLConnection conn) {
			if (url != null) {
				long time = System.currentTimeMillis();
				if ((time - lastTime) > timeout) cookieValue = getCookie();
				if (cookieValue != null) {
					conn.setRequestProperty("Cookie", cookieName+"="+cookieValue);
					lastTime = time;
				}
			}
		}
		
		private String getCookie() {
			try {
				HttpURLConnection conn = HttpUtil.getConnection(url);
				conn.setRequestMethod("GET");
				conn.setReadTimeout(connectionTimeout);
				conn.setConnectTimeout(readTimeout);
				conn.setRequestProperty("Authorization", credentials);
				conn.connect();
				int responseCode = conn.getResponseCode();
				logger.debug(name+": XNAT Session request response code = "+responseCode);
				String response = null;
				if (responseCode == HttpResponse.ok) {
					response = FileUtil.getTextOrException( conn.getInputStream(), FileUtil.utf8, false ).trim();
				}
				conn.disconnect();
				logger.debug("XNAT Session response: "+response);
				if ((response != null) && !response.contains("<")) return response;
			}
			catch (Exception unable) { 
				if (logger.isDebugEnabled()) logger.debug(name+": Unable to establish the XNAT session ("+url.toString()+")", unable);
				else logger.warn(name+": Unable to establish the XNAT session ("+url.toString()+")");
			}
			return null;
		}
	}
}