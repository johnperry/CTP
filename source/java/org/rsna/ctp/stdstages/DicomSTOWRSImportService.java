/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractImportService;
import org.rsna.multipart.UploadedFile;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.server.Users;
import org.rsna.service.HttpService;
import org.rsna.service.Service;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.*;

/**
 * An ImportService that receives files via the DICOM STOW-RS protocol.
 */
public class DicomSTOWRSImportService extends AbstractImportService {

	static final Logger logger = Logger.getLogger(DicomSTOWRSImportService.class);

	HttpService httpReceiver = null;
	int port = 9000;
	boolean ssl = false;
	boolean requireAuthentication = false;

	boolean logAllConnections = false;
	boolean logRejectedConnections = false;
	WhiteList ipWhiteList = null;
	BlackList ipBlackList = null;

	/**
	 * Class constructor; creates a new instance of the ImportService.
	 * @param element the configuration element.
	 * @throws Exception on any error
	 */
	public DicomSTOWRSImportService(Element element) throws Exception {
		super(element);

		//Get the port
		try { port = Integer.parseInt(element.getAttribute("port").trim()); }
		catch (Exception ex) { logger.error(name+": Unparseable port value"); }

		//Get the protocol
		ssl = element.getAttribute("ssl").trim().equals("yes");

		//Get the flag indicating whether we are to log the
		//IP addresses of connections
		String s = element.getAttribute("logConnections").trim();
		logAllConnections = s.equals("yes") || s.equals("all");
		logRejectedConnections = s.equals("rejected");

		//Get the attribute that determines whether
		//authentication is required on all connections.
		requireAuthentication =
				element.getAttribute("requireAuthentication").trim().equals("yes");

		//Get the whitelist and blacklist
		ipWhiteList = new WhiteList(element, "ip");
		ipBlackList = new BlackList(element, "ip");

		//Create the HttpReceiver
		try {
			Receiver receiver = new Receiver(requireAuthentication);
			httpReceiver = new HttpService(ssl, port, receiver, name);
		}
		catch (Exception ex) {
			logger.error(name + ": Unable to instantiate the HttpReceiver on port "+port);
		}
	}

	/**
	 * Stop the pipeline stage.
	 */
	public synchronized void shutdown() {
		if (httpReceiver != null) {
			httpReceiver.stopServer();
		}
		super.shutdown();
	}

	/**
	 * Start the receiver.
	 */
	public void start() {
		if (httpReceiver != null) {
			httpReceiver.start();
		}
	}

	class Receiver implements Service {

		boolean requireAuthentication;
		boolean logAuthenticationFailures = true;

		public Receiver(boolean requireAuthentication) {
			this.requireAuthentication = requireAuthentication;
		}

		public void process(HttpRequest req, HttpResponse res) {
			if (logger.isDebugEnabled()) {
				logger.debug("Entering process");
				logger.debug("Request Content-Type: "+req.getContentType()+"\n"+req.toString()+"\nHeaders:\n"+req.listHeaders("  "));
			}

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

			logger.debug("accept = "+accept);
			
			try {
				if (accept) {
					if (req.method.equals("OPTIONS")) {
						(new Servlet(root, "")).doOptions(req, res);
						return;
					}
					
					if (!requireAuthentication || req.userHasRole("import")) {

						//Good authentication, turn on auth logging again.
						logAuthenticationFailures = true;

						//Only accept POST requests that have Content-Type = multipart/related.
						if ( req.method.equals("POST") 
								&& req.getContentType().toLowerCase().contains("multipart") ) {
									
							logger.debug("multipart request detected");
							
							//Make a response XML structure;
							Document doc = XmlUtil.getDocument();
							Element rootEl = doc.createElement("NativeDicomModel");
							doc.appendChild(rootEl);
							rootEl.appendChild(dicomAttribute("00081190", "UI", "RetrieveURL", null, doc));
							Element refSOPSeqEl = doc.createElement("DicomAttribute");
							refSOPSeqEl.setAttribute("tag", "00081199");
							refSOPSeqEl.setAttribute("vr", "SQ");
							refSOPSeqEl.setAttribute("keyword", "ReferencedSOPSequence");
							rootEl.appendChild(refSOPSeqEl);
							int currentItem = 1;
							
							//Process the received files
							File dir = FileUtil.createTempDirectory(getTempDirectory());
							logger.debug("About to get parts");
							LinkedList<UploadedFile> uploadedFiles = req.getParts(dir, Integer.MAX_VALUE);
							logger.debug("Number of parts = "+uploadedFiles.size());
							for (UploadedFile uploadedFile : uploadedFiles) {
								File file = uploadedFile.getFile();
								FileObject fob = FileObject.getInstance(file);
								if (fob instanceof DicomObject) {
									DicomObject dob = (DicomObject)fob;
									Element itemEl = doc.createElement("Item");
									itemEl.setAttribute("number", Integer.toString(currentItem++));
									itemEl.appendChild(dicomAttribute("00081150", "UI", "ReferencedSOPClassUID", dob.getSOPClassUID(), doc));
									itemEl.appendChild(dicomAttribute("00081155", "UI", "ReferencedSOPInstanceUID", dob.getSOPInstanceUID(), doc));
									itemEl.appendChild(dicomAttribute("00081190", "UI", "RetrieveURL", null, doc));
									refSOPSeqEl.appendChild(itemEl);
									fileReceived(file);
								}
								else logger.debug(name+": non-DICOM object received");
							}
							if (!logger.isDebugEnabled()) FileUtil.deleteAll(dir);
							res.setContentType("xml");
							res.write(XmlUtil.toString(doc));
							logger.debug(name+": XML response:\n"+XmlUtil.toPrettyString(doc));
						}
						else {
							//Unsupported method or Content-Type
							res.setResponseCode(res.badrequest);
						}
					}
					else {
						res.setResponseCode(res.unauthorized);
						res.setHeader("WWW-Authenticate", "Basic realm=\"DicomSTOWRSImportService\"");
					}
					res.send();
				}
				else {
					//Do not respond to a rejected connection
				}
			}
			catch (Exception ex) { 
				logger.debug("Processing error", ex);
				res.setResponseCode(res.servererror);
				res.send();
			}
			logger.debug("Leaving process");
		}
		
		private Element dicomAttribute(String tag, String vr, String keyword, String value, Document doc) {
			Element el = doc.createElement("DicomAttribute");
			el.setAttribute("tag", tag);
			el.setAttribute("vr", vr);
			el.setAttribute("keyword", keyword);
			if (value != null) {
				Element valueEl = doc.createElement("Value");
				valueEl.setAttribute("number", "1");
				valueEl.setTextContent(value);
				el.appendChild(valueEl);
			}
			return el;
		}
	}

}