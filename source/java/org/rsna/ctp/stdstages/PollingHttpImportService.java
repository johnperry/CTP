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
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractImportService;
import org.rsna.util.ChunkedInputStream;
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
					logger.debug("...enqueuing "+file);
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
			logger.debug("Sending poll request");
			File file = null;
			try {
				HttpURLConnection conn = HttpUtil.getConnection(url);
				conn.setRequestMethod("GET");
				conn.connect();
				int responseCode = conn.getResponseCode();
				logger.debug("...received response code "+responseCode);
				if (responseCode == HttpURLConnection.HTTP_OK) {
					long length = conn.getContentLengthLong();
					logger.debug("...response content length = "+length);
					InputStream in = conn.getInputStream();
					
					String transferEncoding = conn.getHeaderField("Transfer-Encoding");
					boolean isChunked = (transferEncoding != null) && transferEncoding.equals("chunked");
					logger.debug("...transferEncoding: "+transferEncoding);
										
					if (length <= 0) {
						if (!isChunked) {
							logger.warn("Non-chunked file posted with Content-Length = "+length);
						}
						length = Long.MAX_VALUE;
					}

					file = File.createTempFile(prefix,".md", getTempDirectory());
					InputStream is = new BufferedInputStream(in);
					if (isChunked) is = new ChunkedInputStream(is);
					FileOutputStream fos = null;
					try {
						fos = new FileOutputStream(file);
						byte[] b = new byte[10000];
						int len;
						int bytesRead = 0;
						while ((bytesRead < length) && ((len=is.read(b,0,b.length)) > 0)) {
							fos.write(b,0,len);
							bytesRead += len;
						}
						logger.debug("...bytesRead = "+bytesRead);
					}
					catch (Exception ex) {
						logger.warn("Exception while receiving a file", ex);
						file.delete();
						file = null;
					}
					finally {
						try { fos.flush(); fos.close(); fos = null; }
						catch (Exception ignore) { }
					}
				}
				else logger.debug("...responseCode test failed ("+responseCode+")");
			}
			catch (Exception ex) { logger.debug("...Exception while polling", ex); }
			if ((file != null) && logger.isDebugEnabled()) {
				logger.debug("...successfully received "+file);
				logger.debug("...file length = "+file.length());
				FileObject fob = FileObject.getInstance(file);
				logger.debug("...file parses as a "+fob.getType());
			}
			else if (file == null) logger.debug("...returning null file");
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