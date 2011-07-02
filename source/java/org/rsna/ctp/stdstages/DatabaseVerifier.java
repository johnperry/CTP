/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Properties;
import jdbm.RecordManager;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;
import jdbm.btree.BTree;
import jdbm.htree.HTree;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.stdstages.verifier.StudyObject;
import org.rsna.ctp.stdstages.verifier.UnverifiedObject;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.JdbmUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * A stage to build tables allowing a servlet to check on whether
 * objects submitted to a DatabaseExportService have made it all
 * the way to the database.
 */
public class DatabaseVerifier extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(DatabaseVerifier.class);

    RecordManager recman = null;
    public BTree unverifiedList = null;
    public HTree sopiIndex = null;		//SOPInstanceUID	>> StudyInstanceUID
    public BTree ptidIndex = null;		//PatientID			>> StudyInstanceUID (HashSet)
    public BTree dateIndex = null;		//Processing date	>> StudyInstanceUID (HashSet)
    public HTree studyTable = null;		//StudyInstanceUID	>> StudyObject

    String url;
    String username;
    String password;
    Verifier verifier;
    boolean authenticate;

	static final int defaultInterval = 600000; //10 minutes
	static final int minInterval = 10000;	   //10 seconds
	int interval = defaultInterval;
	long maxAge = 0;
	long aDay = 24 * 60 * 60 * 1000;
	boolean test = false;

	/**
	 * Construct the DatabaseVerifier PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DatabaseVerifier(Element element) {
		super(element);
		if (root != null) {
			File indexFile = new File(root, "__tables");
			getIndex(indexFile.getPath());
		}
		else logger.error(name+": No root directory was specified.");
		url = element.getAttribute("url").trim();
		username = element.getAttribute("username").trim();
		password = element.getAttribute("password").trim();
		authenticate = !username.equals("") && !password.equals("");
		interval = StringUtil.getInt(element.getAttribute("interval"));
		if (interval < minInterval) interval = minInterval;
		maxAge = StringUtil.getInt(element.getAttribute("maxAge")) * aDay;
		test = element.getAttribute("test").equals("yes");
		if (maxAge < 0) maxAge = 0;
		if (!url.equals("")) {
			verifier = new Verifier();
			verifier.start();
		}
	}

	/**
	 * Stop the stage.
	 */
	public void shutdown() {
		//Commit and close the database
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
			}
			catch (Exception ex) {
				logger.debug("Unable to commit and close the database");
			}
		}
		//Set stop so the isDown method will return the correct value.
		stop = true;
	}

	/**
	 * Remove all the entries in the unverified list
	 * without deleting the list and recreating it.
	 * This method does a commit on the database.
	 */
	public void clearUnverifiedList() {
		synchronized (unverifiedList) {
			try {
				Tuple tuple;
				while ( (tuple=unverifiedList.findGreaterOrEqual("")) != null) {
					unverifiedList.remove(tuple.getKey());
				}
			}
			catch (Exception abort) { /*do nothing*/ }
		}
	}

	/**
	 * Update the tables for the IDs contained in this object.
	 * @param fileObject the object to process.
	 * @return the same FileObject if the result is true; otherwise null.
	 */
	public FileObject process(FileObject fileObject) {

		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		//Now process the object.
		try {
			String date 		= StringUtil.getDate("");
			String ptID			= fileObject.getPatientID();
			String ptName		= fileObject.getPatientName();
			String siUID		= fileObject.getStudyInstanceUID();
			String sopiUID		= fileObject.getSOPInstanceUID();

			synchronized (unverifiedList) {
				try {
					unverifiedList.insert(sopiUID,
										  new UnverifiedObject(
											  			System.currentTimeMillis(),
											  			fileObject.getDigest()),
										  true);
					sopiIndex.put(sopiUID, siUID);
					StudyObject sob = (StudyObject)studyTable.get(siUID);
					if (sob == null) {
						//The study object does not exist.
						//We haven't seen this study before.
						//Create the StudyObject.
						sob = new StudyObject(date, siUID, ptID, ptName);

						//Enter the study into the index of studies by date
						HashSet<String> studies = (HashSet<String>)dateIndex.find(date);
						if (studies == null) {
							studies = new HashSet<String>();
						}
						studies.add(siUID);
						dateIndex.insert(date, studies, true);

						//Enter the study into the index of studies by ptid
						studies = (HashSet<String>)ptidIndex.find(ptID);
						if (studies == null) {
							studies = new HashSet<String>();
						}
						studies.add(siUID);
						ptidIndex.insert(ptID, studies, true);
					}
					sob.putSubmitDate(sopiUID);
					sob.putEntryDate(sopiUID, "");
					studyTable.put(siUID, sob);
				}
				catch (Exception ignore) {
					logger.warn("Unable to update the verification tables for:");
					logger.warn("   sopiUID = "+sopiUID);
					logger.warn("   siUID   = "+siUID);
					logger.warn(" ",ignore);
				}
				//Now commit everything
				recman.commit();
			}
		}
		catch (Exception skip) {
			logger.debug("Unable to process "+fileObject.getFile());
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		return getStatusHTML("");
	}

	//Load the index HTrees
	private void getIndex(String indexPath) {
		try {
			recman			= JdbmUtil.getRecordManager( indexPath );
			unverifiedList	= JdbmUtil.getBTree(recman, "unverifiedList");
			sopiIndex		= JdbmUtil.getHTree(recman, "sopiIndex");
			dateIndex		= JdbmUtil.getBTree(recman, "dateIndex");
			ptidIndex		= JdbmUtil.getBTree(recman, "ptidIndex");
			studyTable		= JdbmUtil.getHTree(recman, "studyTable");
		}
		catch (Exception ex) {
			recman = null;
			logger.warn("Unable to load the indexes.");
		}
	}

	//The thread that tries to verify that objects have made it to the database.
	class Verifier extends Thread {

		static final int maxUIDs = 20;

		public Verifier() { super(); }

		public void run() {
			logger.info(name+" started");
			String lastKey = "0";
			Tuple tuple = new Tuple();
			try {
				while (true) {
					while (!stop && !interrupted()) {
						int count = 0;
						StringBuffer sb = new StringBuffer();
						synchronized (unverifiedList) {
							TupleBrowser tb = unverifiedList.browse(lastKey);
							while ((unverifiedList.size() > 0) && count < maxUIDs) {
								if (tb.getNext(tuple)) {
									if (count > 0) sb.append(";");
									lastKey = (String)tuple.getKey();
									sb.append(lastKey);
									count++;
								}
								else { lastKey = "0"; break; }
							}
						}
						if (count > 0) {
							try {
								HttpURLConnection conn = HttpUtil.getConnection(url + "?uids=" + sb.toString());
								if (authenticate) conn.setRequestProperty("RSNA", username+":"+password);
								conn.connect();
								if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
									String result = FileUtil.getText( conn.getInputStream() );
									Document doc = XmlUtil.getDocument(result);
									Element root = doc.getDocumentElement();
									Node child = root.getFirstChild();
									while (child != null) {
										process(child);
										child = child.getNextSibling();
									}
									recman.commit();
								}
								else logger.debug("Unable to contact the remote DatabaseExportService");
							}
							catch (Exception skip) { logger.debug("exception", skip); }
						}
						if (!stop) {
							try { sleep(interval); }
							catch (Exception ex) {
								logger.debug(name+": Verifier thread caught exception while asleep; verifier stopping");
								return;
							}
						}
					}
				}
			}
			catch (Exception ex) {
				logger.warn("Verifier thread caught exception while running; verifier stopping.", ex);
			}
		}

		private void process(Node node) {
			int testCount = 0;
			if (node instanceof Element) {
				try {
					Element e = (Element)node;
					String sopiUID = e.getAttribute("uid");

					synchronized (unverifiedList) {

						//Get the digest from the unverifiedList.
						UnverifiedObject uvobj = (UnverifiedObject)unverifiedList.find(sopiUID);
						if (uvobj.digest != null) {

							//Make sure the digest is the same as the one in the database
							String digest = e.getAttribute("digest");
							if (digest.equals(uvobj.digest) || (test && (++testCount % 2) == 1)) {

								String date = e.getAttribute("date");
								date = StringUtil.getDateTime(Long.parseLong(date), " ");

								//Get the StudyInstanceUID for this SOPInstanceUID (uid)
								String siUID = (String)sopiIndex.get(sopiUID);

								//Get the StudyObject for this study
								StudyObject sob = (StudyObject)studyTable.get(siUID);

								//Update the StudyObject for this uid.
								sob.putEntryDate(sopiUID, date);

								//Put the updated StudyObject back in the table
								studyTable.put(siUID, sob);

								//Finally, remove this uid from the unverifiedList.
								unverifiedList.remove(sopiUID);
							}

							//Remove the object if it has timed out, even if it has not yet been verified.
							if ((maxAge > 0) && ((System.currentTimeMillis() - uvobj.datetime) > maxAge)) {
								unverifiedList.remove(sopiUID);
							}
						}

					}
				}
				catch (Exception skip) { }
			}
		}
	}

}