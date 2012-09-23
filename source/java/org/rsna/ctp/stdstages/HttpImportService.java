/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.zip.*;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import org.apache.log4j.Logger;
import org.rsna.ctp.pipeline.AbstractImportService;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.server.Users;
import org.rsna.service.HttpService;
import org.rsna.service.Service;
import org.w3c.dom.Element;

/**
 * An ImportService that receives files via the HTTP protocol.
 */
public class HttpImportService extends AbstractImportService {

	static final Logger logger = Logger.getLogger(HttpImportService.class);

	HttpService httpReceiver = null;
	int port = 9000;
	boolean ssl = false;
	boolean zip = false;
	boolean requireAuthentication = false;

	boolean logAllConnections = false;
	boolean logRejectedConnections = false;
	WhiteList ipWhiteList = null;
	BlackList ipBlackList = null;

	/**
	 * Class constructor; creates a new instance of the ImportService.
	 * @param element the configuration element.
	 */
	public HttpImportService(Element element) throws Exception {
		super(element);

		//Get the port
		try { port = Integer.parseInt(element.getAttribute("port")); }
		catch (Exception ex) { logger.error(name+": Unparseable port value"); }

		//Get the protocol
		ssl = element.getAttribute("ssl").equals("yes");

		//Get the flag indicating whether we are to log the
		//IP addresses of connections
		String s = element.getAttribute("logConnections").trim();
		logAllConnections = s.equals("yes") || s.equals("all");
		logRejectedConnections = s.equals("rejected");

		//Get the attribute that specifies whether files
		//are to be unzipped when received.
		zip = element.getAttribute("zip").equals("yes");

		//Get the attribute that determines whether
		//authentication is required on all connections.
		requireAuthentication =
				element.getAttribute("requireAuthentication").equals("yes");

		//Get the whitelist and blacklist
		ipWhiteList = new WhiteList(element, "ip");
		ipBlackList = new BlackList(element, "ip");

		//Create the HttpReceiver
		try {
			Receiver receiver = new Receiver(requireAuthentication);
			httpReceiver = new HttpService(ssl, port, receiver, name);
		}
		catch (Exception ex) {
			logger.error(name + ": Unable to instantiate the VerifierService on port "+port);
		}
	}

	/**
	 * Stop the pipeline stage.
	 */
	public void shutdown() {
		if (httpReceiver != null) httpReceiver.stopServer();
		stop = true;
	}

	/**
	 * Start the receiver.
	 */
	public void start() {
		if (httpReceiver != null) httpReceiver.start();
	}

	class Receiver implements Service {

		boolean requireAuthentication;
		boolean logAuthenticationFailures = true;

		public Receiver(boolean requireAuthentication) {
			this.requireAuthentication = requireAuthentication;
		}

		public void process(HttpRequest req, HttpResponse res) {
			String connectionIP = req.getRemoteAddress();
			boolean accept = ipWhiteList.contains(connectionIP) && !ipBlackList.contains(connectionIP);

			//Log the connection if logging is enabled
			if (logAllConnections || (!accept && logRejectedConnections)) {
				logger.info(name
								+ (accept?" accepted":" rejected")
									+ " connection from " + connectionIP);
				if (requireAuthentication && !req.userHasRole("import")) {
					logger.info("Connection failed authentication requirement");
					if (logAuthenticationFailures) {
						logger.info("HTTP Request: "+req.toString() + "\n" +
									"Headers:\n"+"-----------\n"+req.listHeaders("    ") +
									"Cookies:\n"+"-----------\n"+req.listCookies("    ") );
						logAuthenticationFailures = false;
					}
				}
			}

			if (accept) {
				res.setContentType("txt");
				if (!requireAuthentication || req.userHasRole("import")) {

					//Good authentication, turn on auth logging again.
					logAuthenticationFailures = true;

					//Only accept POST requests have Content-Type = application/x-mirc.
					if ( req.method.equals("POST") &&
							req.getContentType().contains("application/x-mirc") ) {
						if (getPostedFile(req)) {
							res.write("OK");
							if (logAllConnections) logger.info("Posted file received successfully");
						}
						else {
							res.setResponseCode(res.notfound); //error during transmission
							if (logAllConnections || logRejectedConnections) {
								logger.info("Unable to obtain the posted file");
							}
						}
					}
					else {
						discardPostedFile(req);
						res.setResponseCode(res.notfound); //error - wrong method or content type
						if (logAllConnections || logRejectedConnections) {
							logger.info("Unacceptable method or Content-Type");
						}
					}
				}
				else {
					discardPostedFile(req);
					res.setResponseCode(res.unauthorized);
				}
				res.send();
			}
		}

		//Read one file from the HttpRequest and discard it.
		private void discardPostedFile(HttpRequest req) {
			int contentLength = req.getContentLength();
			if (contentLength > 0) {
				InputStream in = req.getInputStream();
				try {
					byte[] b = new byte[10000];
					int len;
					while ((contentLength > 0) && ((len=in.read(b,0,b.length)) > 0)) {
						contentLength -= len;
					}
				}
				catch (Exception ignore) { }
			}
		}

		//Read one file from the HttpRequest.
		//Write the file with a temporary name in the temp
		//directory and then rename it to the queue directory.
		private boolean getPostedFile(HttpRequest req) {
			int contentLength = req.getContentLength();
			if (contentLength <= 0) {
				logger.warn("File posted with Content-Length = "+contentLength);
				return false;
			}
			InputStream in = req.getInputStream();
			FileOutputStream out = null;
			boolean result = true;
			try {
				String prefix = "HTTP-";
				File tempFile = File.createTempFile(prefix,".md", getTempDirectory());
				out = new FileOutputStream(tempFile);
				byte[] b = new byte[10000];
				int len;
				while ((contentLength > 0) && ((len=in.read(b,0,b.length)) > 0)) {
					out.write(b,0,len);
					contentLength -= len;
				}
				out.flush(); out.close(); out = null;
				if (!zip) fileReceived(tempFile);
				else unpackAndReceive(tempFile);
			}
			catch (Exception ex) {
				result = false;
				logger.warn("Exception caught while importing a file", ex);
			}
			finally {
				if (out != null) {
					try { out.close(); }
					catch (Exception ignore) { logger.warn("Unable to close the output stream."); }
				}
			}
			return result;
		}

		//Try to unpack a file and receive all its contents.
		//If it doesn't work, then receive the file itself.
		private void unpackAndReceive(File file) {
			if (!file.exists()) return;
			File parent = file.getParentFile();
			try {
				ZipFile zipFile = new ZipFile(file);
				Enumeration zipEntries = zipFile.entries();
				while (zipEntries.hasMoreElements()) {
					ZipEntry entry = (ZipEntry)zipEntries.nextElement();
					if (!entry.isDirectory()) {
						String name = entry.getName();
						name = name.substring(name.lastIndexOf("/")+1).trim();
						if (!name.equals("")) {
							File outFile = File.createTempFile("FS-",".tmp",parent);
							BufferedOutputStream out =
								new BufferedOutputStream(
									new FileOutputStream(outFile));
							BufferedInputStream in =
								new BufferedInputStream(
									zipFile.getInputStream(entry));
							int size = 1024;
							int n = 0;
							byte[] b = new byte[size];
							while ((n = in.read(b,0,size)) != -1) out.write(b,0,n);
							in.close();
							out.close();
							fileReceived(outFile);
						}
					}
				}
				zipFile.close();
				file.delete();
			}
			catch (Exception e) {
				fileReceived(file);
			}
		}
	}

}