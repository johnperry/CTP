package org.rsna.ctp.stdstages;

import java.io.*;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

/**
 * An ExportService that exports files via the FTPS protocol.
 */
public class FtpsExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(FtpsExportService.class);

	String urlString;
	String username;					
	String password;
	String host;
	int port;
	String hostRoot;
	String structure;
	boolean lastTransferOK;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 * @throws Exception on any error
	 */
	public FtpsExportService(Element element) throws Exception {
		super(element);
		username = element.getAttribute("username").trim();
		password = element.getAttribute("password").trim();

		//Get the destination parameters
		urlString = element.getAttribute("url").trim();
		if (!urlString.startsWith("ftps://")) {
			logger.error(name+": Illegal protocol ("+urlString+")");
			throw new Exception();
		}
		urlString = "ftp" + urlString.substring(4); //so URL constructor accepts it.
		URL url = new URL(urlString);
		host = url.getHost();
		port = url.getPort();
		if (port == -1) port = 21;
		hostRoot = url.getPath();
		structure = element.getAttribute("structure");
		
		lastTransferOK = true;
	}

	/**
	 * Export a file.
	 * @param fileToExport the file to export.
	 * @return the status of the attempt to export the file.
	 */
    @Override
	public Status export(File fileToExport) {
		FileObject fileObject = FileObject.getInstance(fileToExport);
		return send(fileObject);
	}
	
	private Status send(FileObject fileObject) {
		boolean ok = false;
		try {		
			//Construct the destination path from the object elements	
			String remotePath = replaceElementNames(structure, fileObject);			
			if (remotePath.equals("")) remotePath = "bullpen/";
			if (remotePath.endsWith("/")) remotePath += StringUtil.makeNameFromDate();
			String ext = fileObject.getStandardExtension();
			if (!remotePath.endsWith(ext)) remotePath += ext;
			remotePath = filter(remotePath);
			int k = remotePath.lastIndexOf("/");
			String remoteDirPath = remotePath.substring(0, k);
			String remoteFilename = remotePath.substring(k+1);
			
			logger.debug("Remote path: "+remotePath);
			logger.debug("Remote dirPath: "+remoteDirPath);
			logger.debug("Remote filename: "+remoteFilename);

			FTPSClient client = new FTPSClient( /*isImplicit=*/false );
			
			// Connect to the host
			client.connect(host, port);
			int reply = client.getReplyCode();
			if (FTPReply.isPositiveCompletion(reply)) {

				//Login
				if (client.login(username, password)) {
					client.execPBSZ(0);		// Set protection buffer size
					client.execPROT("P");	// Set data channel protection to private
					client.enterLocalPassiveMode();
					client.setFileType(FTP.BINARY_FILE_TYPE);
					
					//Change to the remote directory
					if (cd(client, remoteDirPath) ) {
						// Store file on host
						InputStream is = new FileInputStream(fileObject.getFile());
						ok = client.storeFile(remoteFilename, is);
						FileUtil.close(is);
						client.logout();
						makeLogEntry("FTP transfer succeeded", ok);
						makeAuditLogEntry(fileObject, Status.OK, getName(), remotePath);
					}
					else {
						makeLogEntry("Unable to change remote directory to "+remoteDirPath, false);
					}
				} 
				else {
					makeLogEntry("FTP login failed", false);
				}
				client.disconnect();
			} 
			else {
				makeLogEntry("Unable to connect to FTP host (ftps://"+host+":"+port+")", false);
			}
		} 
		catch (IOException ioe) {
			makeLogEntry("FTP client received network error", false);
		} 
		return ok ? Status.OK : Status.RETRY;
	}

	private void makeLogEntry(String entry, boolean currentTransferOK) {
		if (lastTransferOK != currentTransferOK) {
			logger.warn("Unable to connect to FTP host ("+urlString+")");
			lastTransferOK = currentTransferOK;
		}
	}		
		
	private static String replaceElementNames(String string, FileObject fob) {
		if (fob instanceof DicomObject) {
			DicomObject dob = (DicomObject)fob;
			try {
				Pattern pattern = Pattern.compile("\\$\\{\\w+\\}");
				Matcher matcher = pattern.matcher(string);
				StringBuffer sb = new StringBuffer();
				while (matcher.find()) {
					String group = matcher.group();
					String dicomKeyword = group.substring(2, group.length()-1).trim();
					String repl = dob.getElementValue(dicomKeyword, null);
					if (repl == null) repl = matcher.quoteReplacement(group);
					matcher.appendReplacement(sb, repl);
				}
				matcher.appendTail(sb);
				string = sb.toString();
			}
			catch (Exception quit) { return ""; }
		}
		return string;
	}

	private boolean cd(FTPClient client, String dirPath) {
		try {
			if (!dirPath.startsWith("/")) dirPath = "/" + dirPath;
			String wd = client.printWorkingDirectory();
			logger.debug("Current FTP host working directory: "+wd);
			if (wd.equals(dirPath)) return true;
			logger.debug("...attempting change to: "+dirPath);
			
			boolean ok = client.changeWorkingDirectory("/");
			if (ok) {
				logger.debug("......working directory reset to \"/\"");
			}
			else {
				logger.debug("......unable to reset working directory to \"/\"");
				return false;
			}
			String[] pathElements = dirPath.split("/");
			for (String pathElement : pathElements) {
				if (!pathElement.equals("")) {
					logger.debug("......attempting change to: "+pathElement);
					if (client.changeWorkingDirectory(pathElement)) {
						logger.debug(".........success");
					}
					else {
						client.makeDirectory(pathElement);
						if (client.changeWorkingDirectory(pathElement)) {
							logger.debug(".........directory created and change successful");
						}
						else {
							logger.debug(".........unable to create and change to the directory");
							return false;
						}
					}
				}
			}
		}
		catch (Exception ex) { 
			logger.debug(ex.getMessage());
			return false;
		}
		return true;
	}
	
	private String filter(String s) {
		return s.replaceAll("[^/0-9a-zA-Z()\\.\\[\\]_]+", "_");
	}
}
