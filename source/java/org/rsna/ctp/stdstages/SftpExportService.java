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
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

//import org.apache.commons.vfs2.FileObject; //to avoid collision with org.rsna.ctp.objects.FileObject
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;

/**
 * An ExportService that exports files via the Ftp protocol.
 */
public class SftpExportService extends AbstractExportService {

	static final Logger logger = Logger.getLogger(SftpExportService.class);

	String username;					
	String password;
	String host;
	int port;
	String hostRoot;
	String structure;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 * @throws Exception on any error
	 */
	public SftpExportService(Element element) throws Exception {
		super(element);
		username = element.getAttribute("username").trim();
		password = element.getAttribute("password").trim();

		//Get the destination parameters
		String urlString = element.getAttribute("url").trim();
		if (!urlString.startsWith("sftp://")) {
			logger.error(name+": Illegal protocol ("+urlString+")");
			throw new Exception();
		}
		urlString = "ftp" + urlString.substring(4); //so URL constructor accepts it.
		URL url = new URL(urlString);
		host = url.getHost();
		port = url.getPort();
		if (port == -1) port = 22;
		hostRoot = url.getPath();
		structure = element.getAttribute("structure");
	}

	/**
	 * Export a file.
	 * @param fileToExport the file to export.
	 * @return the status of the attempt to export the file.
	 */
    @Override
	public Status export(File fileToExport) {
		try {
			FileObject fileObject = FileObject.getInstance(fileToExport);
			this.send(fileObject);
			makeAuditLogEntry(fileObject, Status.OK, getName(), hostRoot);
			return Status.OK;
		}
		catch (Exception ex) {
			logger.warn("Unable to export "+fileToExport);
			return Status.RETRY;
		}
	}
	
	public void send(FileObject fileObject) throws Exception {
		StandardFileSystemManager manager = new StandardFileSystemManager();
		try {		
			//Construct the destination path from the object elements	
			String remotePath = replaceElementNames(structure, fileObject);			
			if (remotePath.equals("")) remotePath = "bullpen/";
			if (remotePath.endsWith("/")) remotePath += StringUtil.makeNameFromDate();
			String ext = fileObject.getStandardExtension();
			if (!remotePath.endsWith(ext)) remotePath += ext;
			remotePath = filter(remotePath);

			//Create the URI using the host name, username, password,  remote path and file name
			String uri = "sftp://" + username + ":" + password
								+ "@" + host + ":" + port
								+ hostRoot + remotePath;
								
			logger.debug("URI: "+uri);

			//Initialize the file manager
			manager.init();
			
			//Setup the SFTP configuration
			FileSystemOptions opts = new FileSystemOptions();
			SftpFileSystemConfigBuilder.getInstance().setStrictHostKeyChecking(opts, "no");
			SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, true);
			SftpFileSystemConfigBuilder.getInstance().setTimeout(opts, 10000);

			// Create local file object
			org.apache.commons.vfs2.FileObject localFile = manager.resolveFile(fileObject.getFile().getAbsolutePath());

			// Create remote file object
			org.apache.commons.vfs2.FileObject remoteFile = manager.resolveFile(uri, opts);

			// Copy local file to sftp server
			remoteFile.copyFrom(localFile, Selectors.SELECT_SELF);
		}
		finally { manager.close(); }
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

	private String filter(String s) {
		return s.replaceAll("[^/0-9a-zA-Z()\\.\\[\\]_]+", "_");
	}
}
