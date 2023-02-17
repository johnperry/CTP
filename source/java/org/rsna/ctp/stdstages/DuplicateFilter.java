/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import jdbm.RecordManager;
import jdbm.htree.HTree;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.rsna.ctp.stdstages.XmlAnonymizer;
import org.rsna.ctp.stdstages.ZipAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizerContext;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.JdbmUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * A stage for filtering objects based on their md5 hashes.
 */
public class DuplicateFilter extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(DuplicateFilter.class);

    RecordManager recman = null;
    public HTree hashIndex = null;

	/**
	 * Construct the DuplicateFilter PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DuplicateFilter(Element element) {
		super(element);
		if (root != null) {
			try {
				File indexDir = new File(root, "..index");
				indexDir.mkdirs();
				File indexFile = new File(indexDir, "HashIndex");
				recman = JdbmUtil.getRecordManager( indexFile.getAbsolutePath() );
				hashIndex = JdbmUtil.getHTree( recman, "HashIndex" );
			}
			catch (Exception unable) {
				logger.error(name+": Unable to load the index.");
			}
		}
		else logger.error(name+": No root directory was specified.");
	}

	/**
	 * Stop the stage.
	 */
	public synchronized void shutdown() {
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
			}
			catch (Exception ex) {
				logger.debug("Unable to commit and close the database");
			}
		}
		super.shutdown();
	}

	/**
	 * Filter based on a FileObject's hash.
	 * @param fileObject the object to process.
	 * @return the same FileObject if the hash has never been seen; otherwise null.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		boolean duplicate = false;
		try {
			String digest = fileObject.getDigest();
			duplicate = (hashIndex.get(digest) != null);
			if (!duplicate) {
				hashIndex.put(digest,"");
				recman.commit();
			}
			else {
				if (quarantine != null) quarantine.insert(fileObject);
				lastFileOut = null;
				lastTimeOut = System.currentTimeMillis();
				return null;
			}
		}
		catch (Exception skip) { /* Treat it like it is not a dup */ }

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}
}