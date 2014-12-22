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

	static int objectCount = 0;
	static Hashtable<String,String> digests = new Hashtable<String,String>();
	int lastPresentCount = 1;

	/**
	 * Empty TestDatabaseAdapter constructor.
	 */
	public TestDatabaseAdapter() {
		super();
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
		objectCount++;
		String uid = dicomObject.getUID();
		String digest = dicomObject.getDigest();
		digests.put(uid, digest);
		logger.info(id+"DicomObject received: "+dicomObject.getFile());
		logger.info(id+"      SOPInstanceUID: "+uid);
		logger.info(id+"               count: "+objectCount);
		logger.info(id+"              digest: "+digest);
		logger.info(id+"          storedFile: "+((storedFile!=null)?storedFile:"null"));
		return Status.FAIL;
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
		Hashtable<String, UIDResult> map = new Hashtable<String, UIDResult>();
		int count = 0;
		int present = 0;
		for (String uid : uidSet) {
			count++;
			String digest = digests.get(uid);
			if ( ((count & 1) != 0) && (digest != null) ) {
				map.put(uid, UIDResult.PRESENT(System.currentTimeMillis(), digest));
				present++;
			}
			else map.put(uid, UIDResult.MISSING());
		}
		if ((lastPresentCount > 0) || (present > 0)) {
			int size = uidSet.size();
			logger.info(id+"Verification request received for "+size
						+" instance" +((size == 1) ? "" : "s")+". "
						+present
						+((present == 1) ? " was" : " were")+" found.");
		}
		lastPresentCount = present;
		return map;
	}
}
