/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.dicom;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParseException;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDDictionary;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;
import org.dcm4che.net.AAssociateAC;
import org.dcm4che.net.AAssociateRQ;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DataSource;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.PDU;
import org.dcm4che.net.PresContext;
import org.dcm4che.util.DcmURL;

import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.pipeline.Status;

/**
  * Class to make DICOM associations and transmit instances over them.
  */
public class DicomStorageSCU {

    private static final String[] DEF_TS = { UIDs.ImplicitVRLittleEndian };

	static final Logger logger = Logger.getLogger(DicomStorageSCU.class);

    private static final UIDDictionary uidDict =
        DictionaryFactory.getInstance().getDefaultUIDDictionary();
    private static final AssociationFactory aFact =
        AssociationFactory.getInstance();
    private static final DcmObjectFactory oFact =
        DcmObjectFactory.getInstance();
    private static final DcmParserFactory pFact =
        DcmParserFactory.getInstance();

    private int priority = Command.MEDIUM;
    private int acTimeout = 15000;
    private int dimseTimeout = 0;
    private int soCloseDelay = 500;
    private int maxPDULength = 16352;
    private AAssociateRQ assocRQ = aFact.newAAssociateRQ();
    private boolean packPDVs = false;
    private int bufferSize = 2048;
    private byte[] buffer = null;
    private ActiveAssociation active = null;
    private Association assoc = null;
	private PresContext pc = null;

	private boolean forceClose;
    private int hostTag = 0;
    private int portTag = 0;
    private int calledAETTag = 0;
    private int callingAETTag = 0;

    private String currentHost = "";
    private int currentPort = 0;
    private String currentCalledAET = "";
    private String currentCallingAET = "";
    private String currentTSUID = null;
    private String currentSOPClassUID = null;

    private DcmURL url = null;

    private long lastFailureMessageTime = 0;
    private static long anHour = 60 * 60 * 1000;

    private long lastTransmissionTime = 0;
    private long associationTimeout = 0;
    private AssociationCloser associationCloser = null;

	/**
	 * Class constructor; creates a DICOM sender.
	 * @param urlString the URL in the form "<tt>dicom://calledAET:callingAET@host:port</tt>".
	 * @param associationTimeout true to force the closure of the association after a specified
	 * time during which no further transmissions have occurred.
	 * @param forceClose true to force the closure of the association after sending an object; false to leave
	 * the association open after a transmission.
	 * @param hostTag the tag in the DicomObject from which to get the host name of the destination SCP, or 0 if
	 * the host name in the URL is to be used for all transmissions
	 * @param portTag the tag in the DicomObject from which to get the port of the destination SCP, or 0 if
	 * the port in the URL is to be used for all transmissions
	 * @param calledAETTag the tag in the DicomObject from which to get the calledAET, or 0 if
	 * the calledAET in the URL is to be used for all transmissions
	 * @param callingAETTag the tag in the DicomObject from which to get the callingAET, or 0 if
	 * the callingAET in the URL is to be used for all transmissions
	 */
	public DicomStorageSCU(String urlString, int associationTimeout, boolean forceClose, int hostTag, int portTag, int calledAETTag, int callingAETTag) {
		this(new DcmURL(urlString), associationTimeout, forceClose, hostTag, portTag, calledAETTag, callingAETTag);
	}

	/**
	 * Class constructor; creates a DICOM sender.
	 * @param url the DcmURL.
	 * @param associationTimeout true to force the closure of the association after a specified
	 * time during which no further transmissions have occurred.
	 * @param forceClose true to force the closure of the association after sending an object; false to leave
	 * the association open after a transmission.
	 * @param hostTag the tag in the DicomObject from which to get the host name of the destination SCP, or 0 if
	 * the host name in the URL is to be used for all transmissions
	 * @param portTag the tag in the DicomObject from which to get the port of the destination SCP, or 0 if
	 * the port in the URL is to be used for all transmissions
	 * @param calledAETTag the tag in the DicomObject from which to get the calledAET, or 0 if
	 * the calledAET in the URL is to be used for all transmissions
	 * @param callingAETTag the tag in the DicomObject from which to get the callingAET, or 0 if
	 * the callingAET in the URL is to be used for all transmissions
	 */
	public DicomStorageSCU(DcmURL url, int associationTimeout, boolean forceClose, int hostTag, int portTag, int calledAETTag, int callingAETTag) {
		this.url = url;
		if (associationTimeout != 0) this.associationTimeout = Math.max(associationTimeout, 5000);
		this.forceClose = forceClose;
		this.hostTag = hostTag;
		this.portTag = portTag;
		this.calledAETTag = calledAETTag;
		this.callingAETTag = callingAETTag;
        buffer = new byte[bufferSize];
        if (!forceClose && (this.associationTimeout > 0)) {
			associationCloser = new AssociationCloser();
			associationCloser.start();
		}
	}

	/**
	 * Interrupt the timeout thread..
	 */
	public void interrupt() {
		if (associationCloser != null) associationCloser.interrupt();
	}

	//Close the current association if it has not been
	//used in a specified period of time.
	public synchronized void closeOnTimeout() {
		if (lastTransmissionTime > 0) {
			long currentTime = System.currentTimeMillis();
			if (currentTime - lastTransmissionTime > associationTimeout) {
				close();
			}
		}
	}

	//Close the association if it is open.
	public void close() {
		if (active != null) {
			logger.debug("...closing the open association");
			try { active.release(true); }
			catch (Exception ignore) { }
			active = null;
			currentTSUID = null;
			pc = null;
			lastTransmissionTime = 0;
		}
	}

	/**
	 * Send one file to the URL specified in the constructor.
	 * This method parses the file as a DicomObject and calls
	 * the send(DicomObject) method.
	 * @param file the file to parse and send
	 * @return the Status from the send(DicomObject) method,
	 * or Status.FAIL if the file does not parse as a DicomObject.
	 */
	public synchronized Status send(File file) {
		DicomObject dob = null;
		try {
			dob = new DicomObject(file, true);
			Status status = send(dob);
			dob.close();
			return status;
		}
		catch (Exception ex) {
			logger.warn("Unable to parse file as DicomObject: "+file);
			return Status.FAIL;
		}
	}

	/**
	 * Send one DicomObject to the URL specified in the constructor.
	 * NOTE: the DicomObject must be instantiated with the file left
	 * open so the sender can get at the whole dataset. Thus, the object
	 * should be opened with the full constructor:
	 *<br><br><tt>dicomObject = new DicomObject(fileToExport, true);</tt>
	 * @param dicomObject the object to be sent to the SCP
	 * @return the status result from the transmission
	 */
	public synchronized Status send(DicomObject dicomObject) {
		logger.debug("Exporting "+dicomObject.getFile().getName()+" to "+url.toString());
		DcmParser parser = dicomObject.getDcmParser();
		Dataset ds = dicomObject.getDataset();

        String sopInstUID = dicomObject.getSOPInstanceUID();
        String sopClassUID = dicomObject.getSOPClassUID();
        String tsUID = dicomObject.getTransferSyntaxUID();

		String requestedHost = getHost(dicomObject, hostTag, url.getHost());
		int requestedPort = getPort(dicomObject, portTag, url.getPort());
        String requestedCalledAET = getAET(dicomObject, calledAETTag, url.getCalledAET());
        String requestedCallingAET = getAET(dicomObject, callingAETTag, url.getCallingAET());

		try {
			//Test anything that could cause a NullPointerException
			if (tsUID == null) logger.warn("tsUID is null");
			if (sopClassUID == null) logger.warn("sopClassUID is null");
			if (requestedHost == null) logger.warn("requestedHost is null");
			if (requestedCalledAET == null) logger.warn("requestedCalledAET is null");
			if (requestedCallingAET == null) logger.warn("requestedCallingAET is null");

			//See if we have to make a new association for this request.
			if (logger.isDebugEnabled()) {
				logger.debug("active is "+((active!=null)?"not ":"")+"null");
				if (active != null) {
					boolean x = (active.getAssociation().getState() != Association.ASSOCIATION_ESTABLISHED);
					logger.debug("active is "+(x?"not ":"")+"established");
				}
				logger.debug("currentTSUID is "+((currentTSUID!=null)?"not ":"")+"null");
				if (currentTSUID != null) {
					boolean x = !tsUID.equals(currentTSUID);
					logger.debug("tsUID "+(x?"!":"")+"= currentTSUID");
				}
				logger.debug("currentSOPClassUID is "+((currentSOPClassUID!=null)?"not ":"")+"null");
				if (currentSOPClassUID != null) {
					boolean x = !currentSOPClassUID.equals(currentTSUID);
					logger.debug("currentSOPClassUID "+(x?"!":"")+"= currentSOPClassUID");
				}
				logger.debug("currentHost "+((!requestedHost.equals(currentHost))?"!":"")+"= requestedHost");
				logger.debug("currentPort "+((currentPort!=requestedPort)?"!":"")+"= requestedPort");
				logger.debug("currentCalledAET "+((!requestedCalledAET.equals(currentCalledAET))?"!":"")+"= requestedCalledAET");
				logger.debug("currentCallingAET "+((!requestedCallingAET.equals(currentCallingAET))?"!":"")+"= requestedCallingAET");
			}
			if (
				//if the active association does not exist or if it has been closed by the other end, then YES
				(active == null) || (active.getAssociation().getState() != Association.ASSOCIATION_ESTABLISHED) ||

				//if the transfer syntax has changed, then YES
				(currentTSUID == null) || !tsUID.equals(currentTSUID) ||

				//if the SOP Class has changed, then YES
				(currentSOPClassUID == null) || !sopClassUID.equals(currentSOPClassUID) ||

				//if the host has changed, then YES
				!requestedHost.equals(currentHost) ||

				//if the port has changed, then YES
				(requestedPort != currentPort) ||

				//if the called AET has changed, then YES
				!requestedCalledAET.equals(currentCalledAET) ||

				//if the calling AET has changed, then YES
				!requestedCallingAET.equals(currentCallingAET)

				) {

				//Alas, we can't reuse the current association.
				//Close it if it is open
				close();

				//Create a new association
		        initAssocParam(requestedCalledAET, maskNull(requestedCallingAET));
				initPresContext(sopClassUID, dicomObject.getTransferSyntaxUID());
				logger.debug("...attempting to open a new association");
				active = openAssoc(requestedHost, requestedPort);
				if (active == null) {
					logger.info("...unable to open a new association; returning Status.RETRY");
					return Status.RETRY; //probably off-line
				}
				assoc = active.getAssociation();

				//Negotiate the transfer syntax
				pc = assoc.getAcceptedPresContext(sopClassUID, tsUID);
				if (!parser.getDcmDecodeParam().encapsulated) {
					if (pc == null)
						pc = assoc.getAcceptedPresContext(sopClassUID, UIDs.ExplicitVRLittleEndian);
					if (pc == null)
						pc = assoc.getAcceptedPresContext(sopClassUID, UIDs.ExplicitVRBigEndian);
					if (pc == null)
						pc = assoc.getAcceptedPresContext(sopClassUID, UIDs.ImplicitVRLittleEndian);
				}
				if (pc == null) {
					currentTSUID = null;
					currentSOPClassUID = null;
					logger.debug("...unable to negotiate a transfer syntax for "+dicomObject.getSOPInstanceUID());
					logger.debug("......SOPClass: "+dicomObject.getSOPClassName());
					return Status.FAIL;
				}
				logger.debug("...successfully negotiated transfer syntax for "+dicomObject.getSOPInstanceUID());
				logger.debug("......SOPClass: "+dicomObject.getSOPClassName());
				currentTSUID = pc.getTransferSyntaxUID();
				currentSOPClassUID = sopClassUID;
				currentHost = requestedHost;
				currentPort = requestedPort;
				currentCalledAET = requestedCalledAET;
				currentCallingAET = requestedCallingAET;
			}
			else logger.debug("...reusing the open association");

			//Make the command and do the transfer.
			Command command = oFact.newCommand();
			command = command.initCStoreRQ(assoc.nextMsgID(), sopClassUID, sopInstUID, priority);
			Dimse request = aFact.newDimse(pc.pcid(), command, new MyDataSource(parser, ds, buffer));
			Dimse response = active.invoke(request).get();
			int status = response.getCommand().getStatus();
			if (forceClose) close();
			if (status == 0) {
				lastFailureMessageTime = 0;
				lastTransmissionTime = System.currentTimeMillis();
				logger.debug("...transmission succeeded; returning Status.OK");
				return Status.OK;
			}
			else { 
				logger.debug("...transmission failed ("+status+"); returning Status.FAIL");
				close(); 
				return Status.FAIL; 
			}
		}
		catch (Exception ex) {
			close();
			String msg = ex.getMessage();
			if ((msg != null) && msg.contains("Connection refused")) {
				long time = System.currentTimeMillis();
				if ((time - lastFailureMessageTime) > anHour) {
					logger.warn("dicom://"+requestedCalledAET+":"+requestedCallingAET+"@"+requestedHost+":"+requestedPort);
					logger.warn(ex);
					lastFailureMessageTime = time;
				}
			}
			else {
				logger.debug("Error processing a DicomObject for transmission", ex);
				logger.warn(ex);
				logger.warn("..."+dicomObject.getSOPInstanceUID());
				logger.warn("..."+dicomObject.getSOPClassName());
				if ((ex instanceof java.io.EOFException) ||
					(ex instanceof java.lang.NullPointerException)) return Status.FAIL;
			}
		}
		return Status.RETRY;
    }

    private final class MyDataSource implements DataSource {
        final DcmParser parser;
        final Dataset ds;
        final byte[] buffer;
        MyDataSource(DcmParser parser, Dataset ds, byte[] buffer) {
            this.parser = parser;
            this.ds = ds;
            this.buffer = buffer;
        }
        public void writeTo(OutputStream out, String tsUID) throws IOException {
            DcmEncodeParam netParam =
                (DcmEncodeParam) DcmDecodeParam.valueOf(tsUID);
			ds.writeDataset(out, netParam);
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
            if (parser.getReadTag() == Tags.PixelData) {
                ds.writeHeader(
                    out,
                    netParam,
                    parser.getReadTag(),
                    parser.getReadVR(),
                    parser.getReadLength());
                if (netParam.encapsulated) {
                    parser.parseHeader();
                    while (parser.getReadTag() == Tags.Item) {
                        ds.writeHeader(
                            out,
                            netParam,
                            parser.getReadTag(),
                            parser.getReadVR(),
                            parser.getReadLength());
                        writeValueTo(out, false);
                        parser.parseHeader();
                    }
                    if (parser.getReadTag() != Tags.SeqDelimitationItem) {
                        throw new DcmParseException(
                            "Unexpected Tag: " + Tags.toString(parser.getReadTag()));
                    }
                    if (parser.getReadLength() != 0) {
                        throw new DcmParseException(
                            "(fffe,e0dd), Length:" + parser.getReadLength());
                    }
                    ds.writeHeader(
                        out,
                        netParam,
                        Tags.SeqDelimitationItem,
                        VRs.NONE,
                        0);
                } else {
                    boolean swap =
                        fileParam.byteOrder != netParam.byteOrder
                            && parser.getReadVR() == VRs.OW;
                    writeValueTo(out, swap);
                }
				parser.parseHeader(); //get ready for the next element
			}
			//Now do any elements after the pixels one at a time.
			//This is done to allow streaming of large raw data elements
			//that occur above Tags.PixelData.
			boolean swap = fileParam.byteOrder != netParam.byteOrder;
			while (!parser.hasSeenEOF() && parser.getReadTag() != -1) {
				ds.writeHeader(
					out,
					netParam,
					parser.getReadTag(),
					parser.getReadVR(),
					parser.getReadLength());
				writeValueTo(out, swap);
				parser.parseHeader();
			}
        }

        private void writeValueTo(OutputStream out, boolean swap)
            throws IOException {
            InputStream in = parser.getInputStream();
            int len = parser.getReadLength();
            if (swap && (len & 1) != 0) {
                throw new DcmParseException(
                    "Illegal length for swapping value bytes: " + len);
            }
            if (buffer == null) {
                if (swap) {
                    int tmp;
                    for (int i = 0; i < len; ++i, ++i) {
                        tmp = in.read();
                        out.write(in.read());
                        out.write(tmp);
                    }
                } else {
                    for (int i = 0; i < len; ++i) {
                        out.write(in.read());
                    }
                }
            } else {
                byte tmp;
                int c, remain = len;
                while (remain > 0) {
                    c = in.read(buffer, 0, Math.min(buffer.length, remain));
                    if (c == -1) {
                        throw new EOFException("EOF while reading element value");
                    }
                    if (swap) {
                        if ((c & 1) != 0) {
                            buffer[c++] = (byte) in.read();
                        }
                        for (int i = 0; i < c; ++i, ++i) {
                            tmp = buffer[i];
                            buffer[i] = buffer[i + 1];
                            buffer[i + 1] = tmp;
                        }
                    }
                    out.write(buffer, 0, c);
                    remain -= c;
                }
            }
            parser.setStreamPosition(parser.getStreamPosition() + len);
        }
    }

    private Socket newSocket(String host, int port)
        throws IOException, GeneralSecurityException {
		return new Socket(host, port);
    }

    private static String maskNull(String aet) {
        return (aet != null) ? aet : "DCMSND";
    }

    private String getHost(DicomObject dicomObject, int tag, String defaultHost) {
		String host = defaultHost;
		try {
			if (tag != 0) {
				byte[] bytes = dicomObject.getElementBytes(tag);
				host = new String(bytes).trim();
				host = (host.equals("") ? defaultHost : host);
			}
		}
		catch (Exception ex) { host = defaultHost; }
		return host;
	}

    private int getPort(DicomObject dicomObject, int tag, int defaultPort) {
		int port = defaultPort;
		try {
			if (tag != 0) {
				byte[] bytes = dicomObject.getElementBytes(tag);
				try { port = Integer.parseInt( new String(bytes).trim() ); }
				catch (Exception ex) { port = 0; }
				port = ((port == 0) ? defaultPort : port);
			}
		}
		catch (Exception ex) { port = defaultPort; }
		return port;
	}

    private String getAET(DicomObject dicomObject, int tag, String defaultAET) {
		String aet = defaultAET;
		try {
			if (tag != 0) {
				byte[] bytes = dicomObject.getElementBytes(tag);
				aet = new String(bytes).trim();
				aet = (aet.equals("") ? defaultAET : aet);
			}
		}
		catch (Exception ex) { aet = defaultAET; }
		return aet;
	}

    private final void initAssocParam(String calledAET, String callingAET) {
        assocRQ.setCalledAET( calledAET );
        assocRQ.setCallingAET( maskNull( callingAET ) );
        assocRQ.setMaxPDULength( maxPDULength );
        assocRQ.setAsyncOpsWindow( aFact.newAsyncOpsWindow(0,1) );
    }

    private ActiveAssociation openAssoc(String host, int port)
        	throws IOException, GeneralSecurityException {
        Association assoc = aFact.newRequestor(newSocket(host, port));
        assoc.setAcTimeout(acTimeout);
        assoc.setDimseTimeout(dimseTimeout);
        assoc.setSoCloseDelay(soCloseDelay);
        assoc.setPackPDVs(packPDVs);
        PDU assocAC = assoc.connect(assocRQ);
        if (!(assocAC instanceof AAssociateAC)) return null;
        ActiveAssociation retval = aFact.newActiveAssociation(assoc, null);
        retval.start();
        return retval;
    }

    //method to force the PC to a specific syntax
    private final void initPresContext(String asUID, String tsUID) {
		assocRQ.addPresContext(aFact.newPresContext(1, asUID, tsUID));
    }

   //method to offer what is in the table
   private final void initPresContext(String asUID) {
		PCTable pcTable = PCTable.getInstance();
		assocRQ.clearPresContext();
		LinkedList<String> tsList = pcTable.get(asUID);
		if (tsList != null) {
			int pcid = 1;
			Iterator<String> it = tsList.iterator();
			while (it.hasNext()) {
				String[] tsUID = new String[1];
				tsUID[0] = it.next();
				assocRQ.addPresContext(aFact.newPresContext(pcid, asUID, tsUID));
				pcid += 2;
			}
		}
    }

    class AssociationCloser extends Thread {
		public AssociationCloser() {
			setName("DicomStorageSCU AssociationCloser");
			logger.info("AssociationCloser instantiated with "+(associationTimeout/1000)+" second timeout");
		}
		public void run() {
			if (associationTimeout > 0) {
				while (!interrupted()) {
					try {
						sleep(associationTimeout);
						closeOnTimeout();
					}
					catch (Exception ignore) { }
				}
			}
		}
	}

}
