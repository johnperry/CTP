/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import com.enterprisedt.net.ftp.FileTransferClient;
import com.enterprisedt.net.ftp.FTPConnectMode;
import com.enterprisedt.net.ftp.FTPTransferType;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Properties;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An ExportService that exports files via the Ftp protocol.
 */
public class FtpExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(FtpExportService.class);

	FtpSender ftpSender;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 */
	public FtpExportService(Element element) throws Exception {
		super(element);
		String username = element.getAttribute("username");
		String password = element.getAttribute("password");

		//Get the destination parameters
		URL url = new URL(element.getAttribute("url"));
		String protocol = url.getProtocol().toLowerCase();
		if (!protocol.equals("ftp")) {
			logger.error(name+": Illegal protocol ("+protocol+")");
			throw new Exception();
		}
		String ftpHost = url.getHost();
		int ftpPort = url.getPort();
		if (ftpPort == -1) ftpPort = 21;
		String ftpRoot = url.getPath();

		//Instantiate the FtpSender
		ftpSender = new FtpSender(ftpHost, ftpPort, username, password, ftpRoot);
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
		try {
			FileObject fileObject = FileObject.getInstance(fileToExport);
			String ext = fileObject.getStandardExtension();
			String dirName = fileObject.getStudyUID();
			dirName = (dirName==null) ? "" : dirName.trim();
			if (dirName.equals("")) dirName = "bullpen";
			ftpSender.send(fileToExport, ext, dirName);
			return Status.OK;
		}
		catch (Exception ex) {
			logger.warn("Unable to export "+fileToExport);
			return Status.RETRY;
		}
	}


	//A simple FTP sender.
	class FtpSender {

		String ftpHost;
		int ftpPort;
		String username;
		String password;
		String ftpRoot;
		FileTransferClient client = null;

		public FtpSender(String ftpHost,
						 int ftpPort,
						 String username,
						 String password,
						 String ftpRoot) {
			this.ftpHost = ftpHost;
			this.ftpPort = ftpPort;
			this.username = username;
			this.password = password;
			this.ftpRoot = ftpRoot;
		}

		public void send(File file, String ext, String dirName) throws Exception {
			//Instantiate the client if it isn't there;
			if (client == null) {
				try {
					client = new FileTransferClient();
					client.setRemoteHost(ftpHost);
					client.setRemotePort(ftpPort);
					client.setUserName(username);
					client.setPassword(password);
					client.setContentType(FTPTransferType.BINARY);
					client.getAdvancedFTPSettings().setConnectMode(FTPConnectMode.PASV);
				}
				catch (Exception ex) {
					logger.warn("Unable to get the client",ex);
					client = null;
					throw ex;
				}
			}
			//Establish a connection if we aren't connected
			if (!client.isConnected()) {
				try { client.connect(); }
				catch (Exception ex) {
					client = null;
					logger.info("Unable to connect to the server " + ftpHost,ex);
					throw ex;
				}
			}
			try {
				//Get the directory
				cd(ftpRoot + "/" + dirName);

				//Make a name for the file on the server.
				//The "use unique name" function doesn't seem
				//to work on all servers, so make a name using
				//the makeNameFromDate method, and append the
				//supplied extension.
				String filename = StringUtil.makeNameFromDate() + ext;

				//Upload the file.
				client.uploadFile(file.getAbsolutePath(), filename);

				//Reposition the client to the top directory.
				client.changeToParentDirectory();

				//Disconnect. This might not be a good idea for performance,
				//but it's probably the safest thing to do since we don't know
				//when the next file will be uploaded and the server might
				//time out on its own. As a test, this call can be removed;
				//the rest of the code should re-establish the connection
				//when necessary.
				client.disconnect();
			}
			catch (Exception ex) {
				logger.warn("Unable to upload the file",ex);
				throw ex;
			}
		}

		private void cd(String dirPath) throws Exception {
			if (client.getRemoteDirectory().equals(dirPath)) return;
			String[] pathElements = dirPath.split("/");
			client.changeDirectory("/");
			for (int i=0; i<pathElements.length; i++) {
				if (!pathElements[i].equals("")) {
					try {  client.changeDirectory(pathElements[i]); }
					catch (Exception ex) {
						client.createDirectory(pathElements[i]);
						client.changeDirectory(pathElements[i]);
					}
				}
			}
		}
	}

}