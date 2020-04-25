/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.dicom;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.LinkedList;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DcmServiceBase;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4che.net.Dimse;
import org.dcm4che.server.DcmHandler;
import org.dcm4che.server.Server;
import org.dcm4che.server.ServerFactory;
import org.dcm4che.util.DcmProtocol;
import org.rsna.ctp.stdstages.BlackList;
import org.rsna.ctp.stdstages.WhiteList;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.stdstages.DicomImportService;

public class DicomStorageSCP extends DcmServiceBase {

    final static Logger logger = Logger.getLogger(DicomStorageSCP.class);

    private final static ServerFactory srvFact = ServerFactory.getInstance();
    private final static AssociationFactory fact = AssociationFactory.getInstance();
	final static DcmParserFactory pFact = DcmParserFactory.getInstance();
	final static DcmObjectFactory oFact = DcmObjectFactory.getInstance();

    private DcmProtocol protocol = DcmProtocol.DICOM;

    private Dataset overwrite = oFact.newDataset();
    private AcceptorPolicy policy = fact.newAcceptorPolicy();
    private DcmServiceRegistry services = fact.newDcmServiceRegistry();
    private DcmHandler handler = srvFact.newDcmHandler(policy, services);
    private Server server = srvFact.newServer(handler);

    private final int bufferSize = 2048;
	private final int maxPDULength = 16352;
	private final int soCloseDelay = 500;
	private final int dimseTimeout = 0;
	private final int rqTimeout = 20000; //changed from 10000
	private final int maxClients = 50; //changed from 10
    private final long rspDelay;

	private File temp = null;
	private String localAddress = null; //the IP on which to open the server
	private int calledAETTag = 0;
	private int callingAETTag = 0;
	private int connectionIPTag = 0;
	private int timeTag = 0;
	private DicomImportService dicomImportService = null;
	private boolean suppressDuplicates = false;
	private boolean logAllConnections = false;
	private boolean logRejectedConnections = false;
	private WhiteList ipWhiteList = null;
	private BlackList ipBlackList = null;
	private WhiteList calledAETWhiteList = null;
	private BlackList calledAETBlackList = null;
	private WhiteList callingAETWhiteList = null;
	private BlackList callingAETBlackList = null;
	PCTable pcTable = null;

/**/List<String> recentUIDs;
/**/List<Long> recentTimes;
/**/static final int maxQueueSize = 20;

	ExecutorService execSvc;

    public DicomStorageSCP(DicomImportService dicomImportService) {
		super();
		this.dicomImportService  = dicomImportService;
		localAddress = dicomImportService.getLocalAddress();
		temp = dicomImportService.getTempDirectory();
		calledAETTag = dicomImportService.getCalledAETTag();
		callingAETTag = dicomImportService.getCallingAETTag();
		connectionIPTag = dicomImportService.getConnectionIPTag();
		logAllConnections = dicomImportService.getLogAllConnections();
		logRejectedConnections = dicomImportService.getLogRejectedConnections();
		timeTag = dicomImportService.getTimeTag();
		rspDelay = dicomImportService.getThrottle();
		suppressDuplicates = dicomImportService.getSuppressDuplicates();
		ipWhiteList = dicomImportService.getIPWhiteList();
		ipBlackList = dicomImportService.getIPBlackList();
		calledAETWhiteList = dicomImportService.getCalledAETWhiteList();
		calledAETBlackList = dicomImportService.getCalledAETBlackList();
		callingAETWhiteList = dicomImportService.getCallingAETWhiteList();
		callingAETBlackList = dicomImportService.getCallingAETBlackList();
		pcTable = dicomImportService.getPCTable();
		recentUIDs = new LinkedList<String>();
		recentTimes = new LinkedList<Long>();

		//Set up a thread pool with 4 concurrent threads.
		execSvc = Executors.newFixedThreadPool( 4 );

        initServer(dicomImportService.getPort());
        initPolicy();
    }

    public void start() throws IOException {
        server.start();
        logger.info(dicomImportService.getName()+": SCP open on port "+dicomImportService.getPort());
    }

    public void stop() {
		server.stop();
	}

    //Note: this method does not handle file sets.
    protected void doCStore(ActiveAssociation assoc, Dimse rq, Command rspCmd)
        		throws IOException {
        InputStream in = rq.getDataAsStream();
        try {
			Command rqCmd = rq.getCommand();
			Association a = assoc.getAssociation();
			String calledAET = a.getCalledAET();
			String callingAET = a.getCallingAET();
			String connectionIP = a.getSocket().getInetAddress().getHostAddress();
			String currentUID = rqCmd.getAffectedSOPInstanceUID();

			boolean accept = ipWhiteList.contains(connectionIP) && !ipBlackList.contains(connectionIP)
							&& calledAETWhiteList.contains(calledAET) && !calledAETBlackList.contains(calledAET)
							&& callingAETWhiteList.contains(callingAET) && !callingAETBlackList.contains(callingAET);

			//Log the connection if logging is enabled
			if (logAllConnections || (!accept && logRejectedConnections)) {
				logger.warn(dicomImportService.getName()
								+ (accept?" accepted ":" rejected ")
									+ currentUID + " from " + connectionIP
										+ "("+calledAET+":"+callingAET+")");
			}

			if (!accept) {
				skipObject(in);
			}
			else {
				String affectedSOPClassUID = rqCmd.getAffectedSOPClassUID();
				String affectedSOPInstanceUID = rqCmd.getAffectedSOPInstanceUID();
				String transferSyntaxUID = rq.getTransferSyntaxUID();
				if (logger.isDebugEnabled()) {
					logger.warn(dicomImportService.getName()+": request parameters:");
					logger.warn("    AffectedSOPClassUID:    " + affectedSOPClassUID);
					logger.warn("    AffectedSOPInstanceUID: " + affectedSOPInstanceUID);
					logger.warn("    TransferSyntaxUID:      " + transferSyntaxUID);
				}
				FileMetaInfo fmi = objFact.newFileMetaInfo(
						affectedSOPClassUID,
						affectedSOPInstanceUID,
						transferSyntaxUID);

				boolean isDuplicate = false;
				boolean isRecent = false;

				//*********************************************************************************************
				//doCStore may be called from multiple threads in the DICOM library simultaneously.
				//This section must be synchronized because the LinkedList class is not thread-safe.
				synchronized (recentUIDs) {
					//See if this object has the same UID as a recent one.
					isDuplicate = recentUIDs.contains(currentUID);
					if (isDuplicate && dicomImportService.logDuplicates) {
						logger.warn("----------------------------------------------------------------");
						logger.warn(dicomImportService.getName());
						logger.warn("Duplicate UID in last "+maxQueueSize+" objects: "+currentUID);
						logger.warn("DICOM command: " + rqCmd.cmdFieldAsString());
						String s = "";
						long time = 0;
						for (int i=0; i<recentUIDs.size(); i++) {
							String uid = recentUIDs.get(i);
							s += uid.equals(currentUID) ? "!" : ".";
							time = recentTimes.get(i).longValue();
						}
						long deltaT = System.currentTimeMillis() - time;
						isRecent = (deltaT < 60000);
						logger.warn("[oldest] "+s+"! [newest]  deltaT = "+deltaT+"ms");
						logger.warn("----------------------------------------------------------------");
					}
					recentUIDs.add(currentUID);
					recentTimes.add( new Long( System.currentTimeMillis() ) );
					if (recentUIDs.size() > maxQueueSize) { recentUIDs.remove(0); recentTimes.remove(0); }
				}
				//*********************************************************************************************

				//Handle the object
				if (!isDuplicate || !isRecent || !suppressDuplicates) {
					storeToDir(in, fmi, calledAET, callingAET, connectionIP, System.currentTimeMillis());
				}
				else {
					skipObject(in);
					logger.warn("Duplicate object was suppressed.");
				}
			}
        }
        catch (IOException ioe) { 
			ioe.printStackTrace();
			logger.debug("doCStore exception", ioe);
		}
        finally { in.close(); }
        if (rspDelay > 0L) {
            try { Thread.sleep(rspDelay); }
            catch (Exception ignore) { boolean dummy = true; }
        }
        rspCmd.putUS(Tags.Status, Status.Success);
    }

    //Store the object in the temp directory and then queue it.
    private void storeToDir(InputStream in,
    						FileMetaInfo fmi,
    						String calledAET,
    						String callingAET,
    						String connectionIP,
    						long time) throws IOException {
		File file = File.createTempFile("TMP-",".dcm",temp);
		OutputStream out = null;
        try {
			out = new BufferedOutputStream(new FileOutputStream(file));
            fmi.write(out);
            copy(in, out, -1);
            out.close();
            out = null;
            //Queue up the rest of the processing so we can return now.
            execSvc.execute( new Handler(file, calledAET, callingAET, connectionIP, time) );
        }
        catch (Exception ex) { logger.warn("Unable to store a received file.",ex); }
        finally {
            try { if (out != null) out.close(); }
            catch (IOException ignore) {
				logger.debug("Unable to close the received file.");
			}
        }
    }

    //Skip an object completely. This is called if we are suppressing recent duplicates.
    private void skipObject(InputStream in) throws IOException {
		byte[] buffer = new byte[bufferSize];
		int len;
		while ((len=in.read(buffer, 0, buffer.length)) != -1) /*do nothing*/;
	}

    private void copy(InputStream in, OutputStream out, int totLen) throws IOException {
        int toRead = (totLen == -1) ? Integer.MAX_VALUE : totLen;
		byte[] buffer = new byte[bufferSize];
		for (int len; toRead > 0; toRead -= len) {
			len = in.read(buffer, 0, Math.min(toRead, buffer.length));
			if (len == -1) {
				if (totLen == -1) return;
				throw new EOFException();
			}
			out.write(buffer, 0, len);
		}
    }

    private void initServer(int port) {
        server.setPort(port);
        if ((localAddress != null) && !localAddress.trim().equals("")) {
        	server.setLocalAddress(localAddress.trim());
		}
        server.setMaxClients(maxClients);
        handler.setRqTimeout(rqTimeout);
        handler.setDimseTimeout(dimseTimeout);
        handler.setSoCloseDelay(soCloseDelay);
        handler.setPackPDVs(false);
    }

    private void initPolicy() {
        policy.setCalledAETs(null);
        policy.setCallingAETs(null);
        policy.setMaxPDULength(maxPDULength);
        policy.setAsyncOpsWindow(0, 1);
        Enumeration<String> en = pcTable.keys();
        while (en.hasMoreElements()) {
			String asUID = en.nextElement();
            List<String> uidList = pcTable.get(asUID);
			String[] tsUIDs = new String[uidList.size()];
			tsUIDs = uidList.toArray(tsUIDs);
			policy.putPresContext(asUID, tsUIDs);
			services.bind(asUID, this);
        }
    }

	//This class does the AET update and DicomImportService
	//notification in a separate thread to allow the SCP to
	//reply to the SCU as soon as possible.
	class Handler extends Thread {
		File file;
		String calledAET;
		String callingAET;
		String connectionIP;
		long time;

		public Handler(File file, String calledAET, String callingAET, String connectionIP, long time) {
			super("DicomStorageSCP Handler");
			this.file = file;
			this.calledAET = calledAET;
			this.callingAET = callingAET;
			this.connectionIP = connectionIP;
			this.time = time;
		}

		public void run() {
			file = setAET();
            if (file != null) dicomImportService.fileReceived(file);
		}

		private File setAET() {
			if ((calledAETTag == 0)
					&& (callingAETTag == 0)
						&& (connectionIPTag == 0)
							&& (timeTag == 0)) return file;
			DicomObject dob = null;
			try {
				dob = new DicomObject(file, true);

				if (logger.isDebugEnabled() && !dob.isImage()) {
					logger.info("Non-image object received from "+connectionIP+" ["+calledAET+","+callingAET+"]");
					logger.info("...PatientName: "+dob.getPatientName());
					logger.info("...PatientID:   "+dob.getPatientID());
					logger.info("...Modality:    "+dob.getModality());
					logger.info("...SOPClass:    "+dob.getSOPClassName());
				}

				if (calledAETTag != 0) dob.setElementValue(calledAETTag, calledAET);
				if (callingAETTag != 0) dob.setElementValue(callingAETTag, callingAET);
				if (connectionIPTag != 0) dob.setElementValue(connectionIPTag, connectionIP);
				if (timeTag != 0) dob.setElementValue(timeTag, Long.toString(time));
				File tFile = File.createTempFile("TMP-",".dcm",temp);
				dob.saveAs(tFile, false);
				dob.close();
				dob.getFile().delete();
				return tFile;
			}
			catch (Exception ex) {
				if (dob != null) dob.close();
				file.delete();
				return null;
			}
		}
	}

}
