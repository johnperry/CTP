/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.database;

import java.io.File;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.Status;

/**
 * An adapter for testing the DatabaseAdapter query mechanism.
 */
public class TestDatabaseAdapter extends DatabaseAdapter {

	static final Logger logger = Logger.getLogger(TestDatabaseAdapter.class);

	static long count = 0;

	/**
	 * Empty TestDatabaseAdapter constructor.
	 */
	public TestDatabaseAdapter() { super(); }

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
		logger.info(id+"      SOPInstanceUID: "+dicomObject.getUID());
		count++;
		logger.info(id+"               count: "+count);
//		try { Thread.sleep(1000 * (adapterNumber + 3)); }
//		catch (Exception ignore) { }
		return Status.OK;
	}

	/**
	 * Query the database to determine whether it has received information for any
	 * of a set of instance UIDs. This method is called by the DatabaseExportService's
	 * query servlet when it receives a request to verify that objects which were
	 * submitted have been processed.
	 * @param uidSet The set of UIDs to be searched. Each UID is an instance UID (which,
	 * for a DicomObject, means SOPInstanceUID).
	 * @return a Map containing all the UIDs in the uidSet as keys, and DBQueryResult
	 * values indicating whether the referenced object has been processed. If the
	 * database does not support queries for object presence or absence, return null.
	 */
	public Map<String, UIDResult> uidQuery(Set<String> uidSet) {
		logger.warn("Verification request received for "+uidSet.size()+" instances.");
		Hashtable<String, UIDResult> map = new Hashtable<String, UIDResult>();
		Iterator<String> it = uidSet.iterator();
		int count = 0;
		while (it.hasNext()) {
			String uid = it.next();
			count++;
			if ((count & 1) != 0)
				map.put(uid, UIDResult.PRESENT(System.currentTimeMillis(), ""));
			else
				map.put(uid, UIDResult.MISSING());
		}
		return map;
	}
}
