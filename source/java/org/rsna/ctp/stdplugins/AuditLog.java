/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdplugins;

import java.util.LinkedList;
import java.io.File;
import jdbm.btree.BTree;
import jdbm.htree.HTree;
import jdbm.helper.FastIterator;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;
import jdbm.RecordManager;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.plugin.AbstractPlugin;
import org.rsna.server.ServletSelector;
import org.rsna.util.JdbmUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;
import org.rsna.ctp.servlets.AuditLogServlet;

/**
 * A Plugin to implement an audit log repository that can be
 * accessed through a servlet..
 */
public class AuditLog extends AbstractPlugin {

	static final Logger logger = Logger.getLogger(AuditLog.class);

	static final String databaseName = "AuditLog";
	static final String defaultID = "auditlog";
	static final String lastIDName = "__lastID";

	String servletContext;

	private RecordManager recman;

	private HTree count = null;
	private HTree entryTable = null;
	private HTree contentTypeTable = null;
	private HTree timeTable = null;
	private HTree patientIDIndex = null;
	private HTree studyUIDIndex = null;
	private HTree objectUIDIndex = null;

	/**
	 * Construct a plugin implementing an audit log repository .
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the plugin.
	 */
	public AuditLog(Element element) {
		super(element);

		//See if there is a valid ID.
		//The ID is used as the context of the servlet.
		id = id.replaceAll("\\s+", "");
		if (id.equals("")) id = defaultID;

		//Open the database
		try {
			File dbFile = new File(root, databaseName);
			recman = JdbmUtil.getRecordManager(dbFile.getAbsolutePath());
			count = JdbmUtil.getHTree(recman, "count");
			entryTable = JdbmUtil.getHTree(recman, "entry");
			contentTypeTable = JdbmUtil.getHTree(recman, "contentType");
			timeTable = JdbmUtil.getHTree(recman, "time");
			patientIDIndex = JdbmUtil.getHTree(recman, "patientID");
			studyUIDIndex = JdbmUtil.getHTree(recman, "studyUID");
			objectUIDIndex = JdbmUtil.getHTree(recman, "objectUID");
		}
		catch (Exception unable) { logger.warn("Unable to open the AuditLog database."); }

		logger.info("AuditLog Plugin instantiated");
	}

	/**
	 * Start the plugin.
	 */
	public void start() {
		//Install the servlet
		Configuration config = Configuration.getInstance();
		ServletSelector selector = config.getServer().getServletSelector();
		selector.addServlet(id, AuditLogServlet.class);
		logger.info("AuditLog Plugin started with context \""+id+"\"");
	}

	/**
	 * Stop the plugin.
	 */
	public void shutdown() {
		if (recman != null) {
			try { recman.commit(); recman.close(); recman = null; }
			catch (Exception ignore) { }
		}
		stop = true;
		logger.info("AuditLog Plugin stopped");
	}

	/**
	 * Get HTML text displaying the current status of the plugin.
	 * @return HTML text displaying the current status of the plugin.
	 */
	public synchronized String getStatusHTML() {
		int size;
		try { size = ((Integer)count.get(lastIDName)).intValue(); }
		catch (Exception mustBeZero) { size = 0; }
		String sizeLine = "<tr><td>Number of entries</td><td>"+size+"</td></tr>";
		return getStatusHTML(sizeLine);
	}

	/**
	 * Add an entry to the log.
	 * @param entry the entry to be made in the audit log.
	 * @param contentType the contentType of the entry (not used by the audit log itself,
	 * but may be used by applications that parse audit log entries). If the contentType
	 * parameter is null, no entry is made in the contentType table.
	 * @param patientID the key in the patientID index under which the entry is to listed,
	 * or null if no entry is to be made in the patientID index.
	 * @param studyUID the key in the studyUID index under which the entry is to listed,
	 * or null if no entry is to be made in the studyUID index.
	 * @param objectUID the key in the objectUID index under which the entry is to listed,
	 * or null if no entry is to be made in the objectUID index.
	 * @return the entry ID of the audit log entry.
	 * @throws Exception if the entry could not be made in the audit log.
	 */
	public synchronized Integer addEntry(String entry,
										 String contentType,
										 String patientID,
										 String studyUID,
										 String objectUID) throws Exception {
		Integer id = getNextID();
		entryTable.put(id, entry);
		timeTable.put(id, new Long(System.currentTimeMillis()));
		if (contentType != null) contentTypeTable.put(id, contentType);
		if (patientID != null) appendID(patientIDIndex, patientID, id);
		if (studyUID != null) appendID(studyUIDIndex, studyUID, id);
		if (objectUID != null) appendID(objectUIDIndex, objectUID, id);
		recman.commit();
		return id;
	}

	//Get the next available ID for an entry
	private Integer getNextID() throws Exception {
		try {
			Integer id = (Integer)count.get(lastIDName);
			if (id == null) id = new Integer(0);
			id = new Integer( id.intValue() + 1 );
			count.put(lastIDName, id);
			return id;
		}
		catch (Exception ex) { logger.warn("getNextID:",ex); throw ex; }
	}

	private void appendID(HTree index, String key, Integer id) throws Exception {
		LinkedList<Integer> list = (LinkedList<Integer>)index.get(key);
		if (list == null) list = new LinkedList<Integer>();
		list.add(id);
		index.put(key, list);
	}

	/**
	 * Get a list of entries for a specific patient ID.
	 * @param patientID the ID of the patient.
	 * @return the list of audit log entry IDs corresponding to the patient ID,
	 * or an empty list if no entry appears in the patientID index for the UID.
	 */
	public synchronized LinkedList<Integer> getEntriesForPatientID(String patientID) {
		return getIDs(patientIDIndex, patientID);
	}

	/**
	 * Get a list of entries for a specific study UID.
	 * @param studyUID the UID of the study.
	 * @return the list of audit log entry IDs corresponding to the study UID,
	 * or an empty list if no entry appears in the studyUID index for the UID.
	 */
	public synchronized LinkedList<Integer> getEntriesForStudyUID(String studyUID) {
		return getIDs(studyUIDIndex, studyUID);
	}

	/**
	 * Get a list of entries for a specific object UID.
	 * @param objectUID the UID of the object.
	 * @return the list of audit log entry IDs corresponding to the object UID,
	 * or an empty list if no entry appears in the objectUID index for the UID.
	 */
	public synchronized LinkedList<Integer> getEntriesForObjectUID(String objectUID) {
		return getIDs(objectUIDIndex, objectUID);
	}

	private synchronized LinkedList<Integer> getIDs(HTree index, String key) {
		LinkedList<Integer> list = null;
		try { list = (LinkedList<Integer>)index.get(key); }
		catch (Exception ex) { }
		if (list == null) list = new LinkedList<Integer>();
		return list;
	}

	/**
	 * Get a specified entry in the audit log.
	 * @param id the ID of the entry.
	 * @return the entry corresponding to the ID, or null if no entry appears in the log for the ID.
	 */
	public synchronized String getText(Integer id) {
		try { return (String)entryTable.get(id); }
		catch (Exception ex) { return null; }
	}

	/**
	 * Get the content type of a specified entry in the audit log.
	 * @param id the ID of the entry.
	 * @return the content Type of the entry corresponding to the ID,
	 * or null if no content type is available for the ID.
	 * @throws Exception if an error occurs while reading the audit log.
	 */
	public synchronized String getContentType(Integer id) {
		try { return (String)contentTypeTable.get(id); }
		catch (Exception ex) { return null; }
	}

	/**
	 * Get the time a specified entry was made in the audit log.
	 * @param id the ID of the entry.
	 * @return the time string in the form yyyymmdd hh:mm:ss.sss,
	 * or null if no time is available.
	 */
	public synchronized String getTime(Integer id) {
		try {
			Long time = (Long)timeTable.get(id);
			long t = time.longValue();
			return StringUtil.getDate(t, "-") + " " + StringUtil.getTime(t, ":");
		}
		catch (Exception ex) { return null; }
	}

}