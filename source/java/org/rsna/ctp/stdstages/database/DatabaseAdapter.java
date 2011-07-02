/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.database;

import java.io.File;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.Status;

/**
 * An adapter for accessing an external database.
 * Classes extending this class can be accessed by the
 * CTP DatabaseExportService, which passes objects received
 * from the pipeline to it for processing.
 */
public class DatabaseAdapter {

	static final Logger logger = Logger.getLogger(DatabaseAdapter.class);

	int adapterNumber = 0;
	String id = "";

	/**
	 * Empty DatabaseAdapter constructor.
	 */
	public DatabaseAdapter() { }

	/**
	 * Set an ID string for this DatabaseAdapter.
	 */
	public void setID(int n) {
		this.adapterNumber = n;
		this.id = n + ": ";
	}

	/**
	 * Reset the database interface. This method is called by
	 * the DatabaseExportService if it is restarted.
	 */
	public Status reset() {
		logger.debug(id+"reset method call received");
		return Status.OK;
	}

	/**
	 * Establish a connection to the database. This method is called by
	 * the DatabaseExportService when it is about to call methods in
	 * the database interface. This call can be used  by the database
	 * interface to, for example, connect to a relational database.
	 */
	public Status connect() {
		logger.debug(id+"connect method call received");
		return Status.OK;
	}

	/**
	 * Disconnect from the database. This method is called by
	 * the DatabaseExportService when it is temporarily finished
	 * with the database. This call can be used by the database
	 * interface to, for example, disconnect from a relational
	 * database.
	 */
	public Status disconnect() {
		logger.debug(id+"disconnect method call received");
		return Status.OK;
	}

	/**
	 * Stop the database interface. This method is called by
	 * the DatabaseExportService when it is about to shut down.
	 * This call notifies the database interface that no further
	 * accesses will occur. The database interface should not
	 * rely on this call for anything critical since external
	 * conditions may prevent the call from occurring.
	 */
	public Status shutdown() {
		logger.debug(id+"shutdown method call received");
		return Status.OK;
	}

	/**
	 * Process a DICOM object. This method is called by
	 * the DatabaseExportService when it receives a DICOM file.
	 * @param dicomObject The DicomObject to be processed. This object points
	 * to a transient file in the queue.
	 * @param storedFile The File pointing to the object stored in the
	 * FileStorageService, or null if the object has not been stored.
	 * @param url The URL pointing to the stored object or null if no URL is available.
	 */
	public Status process(DicomObject dicomObject, File storedFile, String url) {
		logger.info(id+"DicomObject received: "+dicomObject.getFile());
		logger.info(id+"         Stored File: "+storedFile);
		logger.info(id+"                 URL: "+url);
		return Status.OK;
	}

	/**
	 * Process an XML object. This method is called by
	 * the DatabaseExportService when it receives an XML file.
	 * @param xmlObject The XmlObject to be processed. This object points
	 * to a transient file in the queue.
	 * @param storedFile The File pointing to the object stored in the
	 * FileStorageService, or null if the object has not been stored.
	 * @param url The URL pointing to the stored object or null if no URL is available.
	 */
	public Status process(XmlObject xmlObject, File storedFile, String url) {
		logger.info(id+"XmlObject received: "+xmlObject.getFile());
		logger.info(id+"       Stored File: "+storedFile);
		logger.info(id+"               URL: "+url);
		return Status.OK;
	}

	/**
	 * Process a Zip object. This method is called by
	 * the DatabaseExportService when it receives a Zip file.
	 * @param zipObject The ZipObject to be processed. This object points
	 * to a transient file in the queue.
	 * @param storedFile The File pointing to the object stored in the
	 * FileStorageService, or null if the object has not been stored.
	 * @param url The URL pointing to the stored object or null if no URL is available.
	 */
	public Status process(ZipObject zipObject, File storedFile, String url) {
		logger.info(id+"ZipObject received: "+zipObject.getFile());
		logger.info(id+"       Stored File: "+storedFile);
		logger.info(id+"               URL: "+url);
		return Status.OK;
	}

	/**
	 * Process a file object. This method is called by
	 * the DatabaseExportService when it receives a file that
	 * it cannot parse as any other known type.
	 * @param fileObject The FileObject to be processed. This object points
	 * to a transient file in the queue.
	 * @param storedFile The File pointing to the object stored in the
	 * FileStorageService, or null if the object has not been stored.
	 * @param url The URL pointing to the stored object or null if no URL is available.
	 */
	public Status process(FileObject fileObject, File storedFile, String url) {
		logger.info(id+"FileObject received: "+fileObject.getFile());
		logger.info(id+"        Stored File: "+storedFile);
		logger.info(id+"                URL: "+url);
		return Status.OK;
	}

	/**
	 * Query the database to determine whether it has received information for any
	 * of a set of instance UIDs. This method is called by the DatabaseExportService's
	 * query servlet when it receives a request to verify that objects which were
	 * submitted have been processed.
	 * @param uidSet The set of UIDs to be searched. Each UID is an instance UID (which,
	 * for a DicomObject, means SOPInstanceUID).
	 * @return a Map containing all the UIDs in the uidSet as keys, and UIDResult
	 * objects indicating whether the referenced objects have been processed. If the
	 * database does not support queries for object presence or absence, return null.
	 */
	public Map<String, UIDResult> uidQuery(Set<String> uidSet) {
		logger.info(id+"uidQuery received");
		return null;
	}
}
