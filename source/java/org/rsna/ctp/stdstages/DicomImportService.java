/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.*;
import java.util.HashSet;
import org.apache.log4j.Logger;
import org.rsna.ctp.pipeline.AbstractImportService;
import org.rsna.ctp.stdstages.dicom.DicomStorageSCP;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * An ImportService that receives files via the DICOM protocol.
 */
public class DicomImportService extends AbstractImportService {

	static final Logger logger = Logger.getLogger(DicomImportService.class);

	DicomStorageSCP dicomStorageSCP = null;
	int port = 104;
	int calledAETTag = 0;
	int callingAETTag = 0;
	int connectionIPTag = 0;
	int timeTag = 0;
	boolean suppressDuplicates = false;
	boolean logAllConnections = false;
	boolean logRejectedConnections = false;
	WhiteList ipWhiteList = null;
	BlackList ipBlackList = null;
	WhiteList calledAETWhiteList = null;
	BlackList calledAETBlackList = null;
	WhiteList callingAETWhiteList = null;
	BlackList callingAETBlackList = null;

	/**
	 * Class constructor; creates a new instance of the ImportService.
	 * @param element the configuration element.
	 */
	public DicomImportService(Element element) throws Exception {
		super(element);

		//Get the port
		port = StringUtil.getInt(element.getAttribute("port"), port);

		//Get the calledAETTag, if any
		calledAETTag = StringUtil.getHexInt(element.getAttribute("calledAETTag"), calledAETTag);

		//Get the callingAETTag, if any
		callingAETTag = StringUtil.getHexInt(element.getAttribute("callingAETTag"), callingAETTag);

		//Get the connectionIPTag, if any
		connectionIPTag = StringUtil.getHexInt(element.getAttribute("connectionIPTag"), connectionIPTag);

		//Get the timeTag, if any
		timeTag = StringUtil.getHexInt(element.getAttribute("timeTag"), timeTag);

		//Get the flag indicating whether we are to suppress recent duplicates
		suppressDuplicates = element.getAttribute("suppressDuplicates").trim().equals("yes");

		//Get the flag indicating whether we are to log the IP addresses of connections
		String s = element.getAttribute("logConnections").trim();
		logAllConnections = s.equals("yes") || s.equals("all");
		logRejectedConnections = s.equals("rejected");

		//Get the whitelists and blacklists
		ipWhiteList = new WhiteList(element, "ip");
		ipBlackList = new BlackList(element, "ip");
		calledAETWhiteList = new WhiteList(element, "calledAET");
		calledAETBlackList = new BlackList(element, "calledAET");
		callingAETWhiteList = new WhiteList(element, "callingAET");
		callingAETBlackList = new BlackList(element, "callingAET");

		//Create the DicomStorageSCP
		dicomStorageSCP = new DicomStorageSCP(this);
	}

	/**
	 * Start the SCP.
	 */
	public void start() {
		try { dicomStorageSCP.start(); }
		catch (Exception ex) {
			logger.warn("Unable to start the DicomStorageSCP on port "+port);
			logger.warn("Message: "+ex.getMessage());
		}
	}

	/**
	 * Stop the pipeline stage.
	 */
	public void shutdown() {
		dicomStorageSCP.stop();
		stop = true;
	}

	/**
	 * Get the port
	 * @return the port on which to listen for DICOM transfers
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Get the called AE Title tag
	 * @return the DICOM tag in which to store the called AE Title
	 */
	public int getCalledAETTag() {
		return calledAETTag;
	}

	/**
	 * Get the calling AE Title tag
	 * @return the DICOM tag in which to store the calling AE Title
	 */
	public int getCallingAETTag() {
		return callingAETTag;
	}

	/**
	 * Get the connectionIP AE Title tag
	 * @return the DICOM tag in which to store the IP address of the connection
	 */
	public int getConnectionIPTag() {
		return connectionIPTag;
	}

	/**
	 * Get the Time tag
	 * @return the DICOM tag in which to store the system time
	 */
	public int getTimeTag() {
		return timeTag;
	}

	/**
	 * Get the flag indicating whether to suppress objects with SOPInstanceUIDs
	 * which are duplicates of recently received objects.
	 * @return true if objects which are duplicates of recently received objects
	 * are to be suppressed.
	 */
	public boolean getSuppressDuplicates() {
		return suppressDuplicates;
	}

	/**
	 * Get the flag indicating whether to log the IP addresses of all connections.
	 * @return true if all connection IP addresses are to be logged.
	 */
	public boolean getLogAllConnections() {
		return logAllConnections;
	}

	/**
	 * Get the flag indicating whether to log the IP addresses of only rejected connections.
	 * @return true if only rejected connection IP addresses are to be logged.
	 */
	public boolean getLogRejectedConnections() {
		return logRejectedConnections;
	}

	/**
	 * Get the IP white list
	 * @return the IP white list
	 */
	public WhiteList getIPWhiteList() {
		return ipWhiteList;
	}

	/**
	 * Get the IP black list
	 * @return the IP black list
	 */
	public BlackList getIPBlackList() {
		return ipBlackList;
	}

	/**
	 * Get the calledAET white list
	 * @return the calledAET white list
	 */
	public WhiteList getCalledAETWhiteList() {
		return calledAETWhiteList;
	}

	/**
	 * Get the calledAET black list
	 * @return the calledAET black list
	 */
	public BlackList getCalledAETBlackList() {
		return calledAETBlackList;
	}

	/**
	 * Get the callingAET white list
	 * @return the callingAET white list
	 */
	public WhiteList getCallingAETWhiteList() {
		return callingAETWhiteList;
	}

	/**
	 * Get the callingAET black list
	 * @return the callingAET black list
	 */
	public BlackList getCallingAETBlackList() {
		return callingAETBlackList;
	}

}