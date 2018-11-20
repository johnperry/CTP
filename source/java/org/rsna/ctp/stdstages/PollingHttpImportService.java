/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.*;
import org.apache.log4j.Logger;
import org.rsna.ctp.pipeline.AbstractImportService;
import org.rsna.util.HttpUtil;
import org.w3c.dom.Element;

/**
 * An ImportService that polls a PolledHttpExportService to obtain files on request.
 */
public class PollingHttpImportService extends AbstractImportService {

	static final Logger logger = Logger.getLogger(PollingHttpImportService.class);

	URL url;
	boolean zip = false;
	Poller poller = null;
	long interval = 10000;

	/**
	 * Construct a PollingHttpImportService.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 * @throws Exception on any error
	 */
	public PollingHttpImportService(Element element) throws Exception {
		super(element);

		//Get the destination url
		url = new URL(element.getAttribute("url").trim());

		//Get the attribute that specifies whether files
		//are to be unzipped when received.
		zip = element.getAttribute("zip").trim().equals("yes");
	}

	/**
	 * Start the service. This method can be overridden by stages
	 * which can use it to start subordinate threads created in their constructors.
	 * This method is called by the Pipeline after all the stages have been
	 * constructed.
	 */
	public synchronized void start() {
		poller = new Poller();
		poller.start();
	}

	/**
	 * Stop the service.
	 */
	public synchronized void shutdown() {
		if (poller != null) poller.interrupt();
		super.shutdown();
	}

	class Poller extends Thread {
		String prefix = "IS-";

		public Poller() {
			super("Poller");
		}

		public void run() {
			File file;
			while (!isInterrupted()) {
				while ( !isInterrupted() && (file=getFile()) != null ) {
					if (!zip) fileReceived(file);
					else unpackAndReceive(file);
				}
				if (!isInterrupted()) {
					try { sleep(interval); }
					catch (Exception ignore) { }
				}
			}
		}

		//Get a file from the external system.
		private File getFile() {
			File file = null;
			try {
				HttpURLConnection conn = HttpUtil.getConnection(url);
				conn.setRequestMethod("GET");
				conn.connect();
				if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
					long length = conn.getContentLengthLong();
					InputStream in = conn.getInputStream();
					if (length > 0) {
						file = File.createTempFile(prefix,".md", getTempDirectory());
						BufferedInputStream is = new BufferedInputStream(in);
						FileOutputStream fos = null;
						try {
							fos = new FileOutputStream(file);
							int n;
							byte[] bbuf = new byte[1024];
							while ((length > 0) && ((n=is.read(bbuf,0,bbuf.length)) >= 0)) {
								fos.write(bbuf,0,n);
								length -= n;
							}
							fos.flush();
							fos.close();
						}
						catch (Exception ex) {
							logger.warn("Exception while receiving a file", ex);
							try { fos.close(); }
							catch (Exception ignore) { }
							file.delete();
							file = null;
						}
					}
				}
			}
			catch (Exception ex) { logger.debug("Exception while polling", ex); }
			return file;
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
							logger.debug("unpacking "+name+" to "+outFile);
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