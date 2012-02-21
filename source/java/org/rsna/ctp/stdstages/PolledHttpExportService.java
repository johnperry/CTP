/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import org.apache.log4j.Logger;
import org.rsna.ctp.pipeline.AbstractQueuedExportService;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An ExportService that serves files via the HTTP protocol.
 */
public class PolledHttpExportService extends AbstractQueuedExportService {

	static final Logger logger = Logger.getLogger(PolledHttpExportService.class);

	Connector connector = null;
	int port = 9100;
	volatile boolean waiting = false;
	volatile boolean handling = false;

	/**
	 * Class constructor; creates a new instance of the ExportService.
	 * @param element the configuration element.
	 */
	public PolledHttpExportService(Element element) throws Exception {
		super(element);

		//Get the port
		try { port = Integer.parseInt(element.getAttribute("port")); }
		catch (Exception ex) { logger.error(name+": Unparseable port value"); }

		//Create the Connector
		try { connector = new Connector(); }
		catch (Exception ex) {
			logger.warn("Unable to instantiate the connector", ex);
			throw ex;
		}
	}

	/**
	 * Stop the stage.
	 */
	public void shutdown() {
		stop = true;
		if (connector != null) connector.interrupt();
	}

	/**
	 * Determine whether the pipeline stage has shut down.
	 */
	public boolean isDown() {
		return stop && !handling;
	}

	/**
	 * Start the connector.
	 */
	public void start() {
		connector.start();
	}

	//A server to return files from the queue.
	class Connector extends Thread {

		ServerSocket serverSocket;

		public Connector() throws Exception {
			super(name + " Connector");
			ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
			serverSocket = serverSocketFactory.createServerSocket(port);
		}

		/**
		 * Start the Connector and accept connections.
		 */
		public void run() {
			while (!stop && !isInterrupted()) {
				try {
					//Wait for a connection
					waiting = true;
					final Socket socket = serverSocket.accept();
					handling = true;
					waiting = false;

					//Serve the connection in this thread
					//to ensure that files are delivered
					//synchronously.
					if (!socket.isClosed()) handle(socket);
					handling = false;
				}
				catch (Exception ex) {
					logger.warn("Shutting down");
					break;
				}
			}
			try { serverSocket.close(); }
			catch (Exception ignore) { logger.warn("Unable to close the server socket"); }
			serverSocket = null;
			waiting = false;
			handling = false;
		}

		//Handle one connection.
		private void handle(Socket socket) {
			//logger.warn("Entering handle method");
			try {
				//Set parameters on the socket
				try {
					socket.setTcpNoDelay(true);
					//socket.setSoLinger(true,10);
					socket.setSoTimeout(0);
				}
				catch (Exception ex) { logger.warn("Unable to set socket params",ex); }

				//Get the streams
				InputStream in = socket.getInputStream();
				OutputStream out = socket.getOutputStream();

				//Get the file
				File next = getNextFile();
				if (next != null) {

					logger.debug("Exporting "+next);

					//Send the length
					sendLong(out, next.length());

					//Send the file
					FileInputStream fis = new FileInputStream(next);
					int nbytes;
					byte[] buffer = new byte[2048];
					while ((nbytes = fis.read(buffer)) != -1) {
						out.write(buffer,0,nbytes);
					}
					fis.close();

					//Get the response
					if (in.read() == 1) {
						//Success, release the file from the queue
						release(next);
					}
					else {
						//Something went wrong. Requeue the file and
						//delete it from its temporary location.
						getQueueManager().enqueue(next);
						next.delete();
					}
				}
				else {
					//No file is available, send a zero length;
					sendLong(out, 0);
				}
			}
			catch (Exception ex) {
				logger.error("Internal error.",ex);
			}

			//Close everything.
			try { socket.close(); }
			catch (Exception ignore) { logger.warn("Unable to close the socket."); }
			//logger.warn("Leaving handle method");
		}

		//Send a long as four bytes
		private void sendLong(OutputStream out, long x) throws Exception {

			for (int i=0; i<4; i++) {
				out.write((byte)(x & 0xff));
				x >>>= 8;
			}
		}
	}

}