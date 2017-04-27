/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.stdstages.email.EmailSender;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * The EmailService pipeline stage class.
 */
public class EmailService extends AbstractPipelineStage implements Processor, Scriptable {

	static final Logger logger = Logger.getLogger(EmailService.class);

	File dicomScriptFile = null;
	Hashtable<String,Study> studies;
	String smtpServer;
	String username;
	String password;
	String to;
	String from;
	String cc;
	String subject;
	boolean includePatientName = false;
	boolean includePatientID = false;
	boolean includeModality = false;
	boolean includeStudyDate = false;
	boolean includeAccessionNumber = false;
	boolean logSentEmails = false;

	static final long aSecond = 1000;
	static final long aMinute = 60 * aSecond;
	static final long anHour = 60 * aMinute;

	long maxAge = 10 * aMinute;
	long interval = aMinute;

	Emailer emailer = null;

	/**
	 * Construct the EmailService PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public EmailService(Element element) {
		super(element);

		dicomScriptFile = getFilterScriptFile(element.getAttribute("dicomScript"));
		studies = new Hashtable<String,Study>();
		includePatientName = element.getAttribute("includePatientName").toLowerCase().trim().equals("yes");
		includePatientID = element.getAttribute("includePatientID").toLowerCase().trim().equals("yes");
		includeModality = element.getAttribute("includeModality").toLowerCase().trim().equals("yes");
		includeStudyDate = element.getAttribute("includeStudyDate").toLowerCase().trim().equals("yes");
		includeAccessionNumber = element.getAttribute("includeAccessionNumber").toLowerCase().trim().equals("yes");
		logSentEmails = element.getAttribute("logSentEmails").toLowerCase().trim().equals("yes");

		smtpServer = element.getAttribute("smtpServer").trim();
		username = element.getAttribute("username").trim();
		password = element.getAttribute("password").trim();
		to = element.getAttribute("to").trim();
		from = element.getAttribute("from").trim();
		cc = element.getAttribute("cc").trim();
		subject = element.getAttribute("subject").trim();

		emailer = new Emailer();
	}

	/**
	 * Start the stage.
	 */
	public void start() {
		if (emailer != null) emailer.start();
	}

	/**
	 * Stop the stage.
	 */
	public void shutdown() {
		stop = true;
		if (emailer != null) emailer.interrupt();
	}

	//Implement the Scriptable interface
	/**
	 * Get the script files.
	 * @return the script files used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] { dicomScriptFile, null, null };
	}

	/**
	 * Get the list of links for display on the summary page.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public LinkedList<SummaryLink> getLinks(User user) {
		LinkedList<SummaryLink> links = super.getLinks(user);
		if (allowsAdminBy(user)) {
			String qs = "?p="+pipeline.getPipelineIndex()+"&s="+stageIndex;
			if (dicomScriptFile != null) {
				links.addFirst( new SummaryLink("/script"+qs+"&f=0", null, "Edit the Stage Filter Script", false) );
			}
		}
		return links;
	}

	/**
	 * Process a DicomObject, adding it into the table of studies
	 * for which email notifications are to be sent.
	 * If the object is not a DicomObject, ignore it.
	 * This stage does not modify the object.
	 * @param fileObject the object to process.
	 * @return the processed FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if (fileObject instanceof DicomObject) {
			DicomObject dob = (DicomObject)fileObject;

			//If there is a dicomScriptFile, use it to determine whether to consider this object.
			if (dob.matches(dicomScriptFile)) {

				//Okay, add the object to the table.
				String siuid = dob.getStudyInstanceUID();
				Study study = studies.get(siuid);
				if (study == null) study = new Study(dob);
				study.add(dob);
				studies.put(siuid, study);
			}
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	class Study {
		public String patientName;
		public String patientID;
		public String modality;
		public String studyDate;
		public String accessionNumber;
		int objectCount = 0;
		int imageCount = 0;
		HashSet<String> series;
		long lastTime = 0;

		public Study(DicomObject dob) {
			this.patientName = dob.getPatientName();
			this.patientID = dob.getPatientID();
			this.modality = dob.getModality();
			this.studyDate = dob.getStudyDate();
			this.accessionNumber = dob.getAccessionNumber();
			series = new HashSet<String>();
		}

		public synchronized void add(DicomObject dob) {
			objectCount++;
			if (dob.isImage()) {
				imageCount++;
				series.add(dob.getSeriesInstanceUID());
				lastTime = System.currentTimeMillis();
			}
		}

		public synchronized int getObjectCount() {
			return objectCount;
		}

		public synchronized int getImageCount() {
			return imageCount;
		}

		public synchronized int getSeriesCount() {
			return series.size();
		}

		public synchronized boolean isComplete() {
			long age = System.currentTimeMillis() - lastTime;
			return (age > maxAge);
		}
	}

	class Emailer extends Thread {
		EmailSender sender = null;
		public Emailer() {
			super(name + " - email");
			try { sender = new EmailSender(smtpServer, username, password); }
			catch (Exception ex) {
				logger.warn("Unable to instantiate the EmailSender.");
			}
		}
		public void run() {
			if (sender != null) {
				while (!stop && !isInterrupted()) {
					try {
						processCompletedStudies();
						sleep(interval);
					}
					catch (Exception ex) { }
				}
			}
		}
		private void processCompletedStudies() {
			String[] siuids;
			synchronized (studies) {
				siuids = new String[studies.size()];
				siuids = studies.keySet().toArray(siuids);
			}
			for (String siuid : siuids) {
				Study study = studies.get(siuid);
				if (study.isComplete()) {
					sendEmail(study);
					studies.remove(siuid);
				}
			}
		}
		private void sendEmail(Study study) {
			boolean ok = sender.sendHTML(
							to,
							from,
							cc,
							subject,
							getPlainText(study),
							getHtmlText(study));
			if (ok && logSentEmails) {
				logger.info(name+": Study email sent to "+to+" ("+subject+")");
			}
			else if (!ok) logger.info(name+": Unable to send email to "+to+" ("+subject+")");
		}
		private String getPlainText(Study study) {
			StringBuffer sb = new StringBuffer();
			sb.append("A study was received and processed by CTP.\n");
			if (includePatientName) 	sb.append("Patient Name: "+study.patientName+"\n");
			if (includePatientID  ) 	sb.append("Patient ID:   "+study.patientID+"\n");
			if (includeModality   ) 	sb.append("Modality:     "+study.modality+"\n");
			if (includeStudyDate  ) 	sb.append("Study Date:   "+study.studyDate+"\n");
			if (includeAccessionNumber) sb.append("Accession:    "+study.studyDate+"\n");
			sb.append("Objects:      "+study.getObjectCount()+"\n");
			sb.append("Series:       "+study.getSeriesCount()+"\n");
			sb.append("Images:       "+study.getImageCount()+"\n");
			return sb.toString();
		}
		private String getHtmlText(Study study) {
			StringBuffer sb = new StringBuffer();
			sb.append("<html><head><title>Study Received</title></head><body>\n");
			sb.append("<h2>A study was received and processed by CTP.</h2>\n");
			sb.append("<table>\n");
			if (includePatientName) {
				sb.append("<tr>\n");
				sb.append("<td>Patient Name:</td>\n");
				sb.append("<td>"+study.patientName+"</td>\n");
				sb.append("</tr>\n");
			}
			if (includePatientID) {
				sb.append("<tr>\n");
				sb.append("<td>Patient ID:</td>\n");
				sb.append("<td>"+study.patientID+"</td>\n");
				sb.append("</tr>\n");
			}
			if (includeModality) {
				sb.append("<tr>\n");
				sb.append("<td>Modality:</td>\n");
				sb.append("<td>"+study.modality+"</td>\n");
				sb.append("</tr>\n");
			}
			if (includeStudyDate) {
				sb.append("<tr>\n");
				sb.append("<td>Study Date:</td>\n");
				sb.append("<td>"+study.studyDate+"</td>\n");
				sb.append("</tr>\n");
			}
			if (includeAccessionNumber) {
				sb.append("<tr>\n");
				sb.append("<td>Accession Number:</td>\n");
				sb.append("<td>"+study.accessionNumber+"</td>\n");
				sb.append("</tr>\n");
			}
			sb.append("<tr>\n");
			sb.append("<td>Number of objects:</td>\n");
			sb.append("<td>"+study.getObjectCount()+"</td>\n");
			sb.append("</tr>\n");
			sb.append("<tr>\n");
			sb.append("<td>Number of series:</td>\n");
			sb.append("<td>"+study.getSeriesCount()+"</td>\n");
			sb.append("</tr>\n");
			sb.append("<tr>\n");
			sb.append("<td>Number of images:</td>\n");
			sb.append("<td>"+study.getImageCount()+"</td>\n");
			sb.append("</tr>\n");

			sb.append("</table>\n");
			sb.append("</body></html>\n");
			return sb.toString();
		}
	}

}
