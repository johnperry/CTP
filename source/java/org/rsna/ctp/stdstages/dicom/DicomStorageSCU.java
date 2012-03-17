/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
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
    private String currentTSUID = null;
    private String currentSOPClassUID = null;
	private PresContext pc = null;
	private boolean forceClose;
    private int calledAETTag = 0;
    private int callingAETTag = 0;
    private String currentCalledAET = "";
    private String currentCallingAET = "";

    private DcmURL url = null;

	/**
	 * Class constructor; creates a DICOM sender.
	 * @param url the URL in the form "<tt>dicom://calledAET:callingAET@host:port</tt>".
	 * @param forceClose true to force the closure of the association after sending an object; false to leave
	 * the association open after a transmission.
	 * @param calledAETTag the tag in the DicomObject from which to get the calledAET, or 0 if
	 * the calledAET in the URL is to be used for all transmissions
	 * @param callingAETTag the tag in the DicomObject from which to get the callingAET, or 0 if
	 * the callingAET in the URL is to be used for all transmissions
	 */
	public DicomStorageSCU(String url, boolean forceClose, int calledAETTag, int callingAETTag) {
		this.url = new DcmURL(url);
		this.forceClose = forceClose;
		this.calledAETTag = calledAETTag;
		this.callingAETTag = callingAETTag;
        buffer = new byte[bufferSize];
        initAssocParam(this.url);
	}

	/**
	 * Close the association if it is open.
	 */
	public void close() {
		if (active != null) {
			try { active.release(true); }
			catch (Exception ignore) { }
			active = null;
			currentTSUID = null;
			pc = null;
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
	public Status send(File file) {
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
	 */
	public Status send(DicomObject dicomObject) {
		DcmParser parser = dicomObject.getDcmParser();
		Dataset ds = dicomObject.getDataset();

        String sopInstUID = dicomObject.getSOPInstanceUID();
        String sopClassUID = dicomObject.getSOPClassUID();
        String tsUID = dicomObject.getTransferSyntaxUID();

        String requestedCalledAET = getAET(dicomObject, calledAETTag, url.getCalledAET());
        String requestedCallingAET = getAET(dicomObject, callingAETTag, url.getCallingAET());

		try {
			//See if we have to make a new association for this request.
			if (
				//if the active association does not exist or if it has been closed by the other end, then YES
				(active == null) || (active.getAssociation().getState() != Association.ASSOCIATION_ESTABLISHED) ||

				//if the transfer syntax has changed, then YES
				(currentTSUID == null) || !tsUID.equals(currentTSUID) ||

				//if the SOP Class has changed, then YES
				(currentSOPClassUID == null) || !sopClassUID.equals(currentSOPClassUID) ||

				//if the called AET has changed, then YES
				!requestedCalledAET.equals(currentCalledAET) ||

				//if the calling AET has changed, then YES
				!requestedCallingAET.equals(currentCallingAET)

				) {

				//Alas, we can't reuse the current association.
				//Close it if it is open
				close();

				//Create a new association
				assocRQ.setCalledAET(requestedCalledAET);
				assocRQ.setCallingAET(maskNull(requestedCallingAET));
				initPresContext(sopClassUID);
				active = openAssoc();
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
					return Status.FAIL;
				}
				currentTSUID = pc.getTransferSyntaxUID();
				currentSOPClassUID = sopClassUID;
				currentCalledAET = requestedCalledAET;
				currentCallingAET = requestedCallingAET;
			}

			//Make the command and do the transfer.
			Command command = oFact.newCommand();
			command = command.initCStoreRQ(assoc.nextMsgID(), sopClassUID, sopInstUID, priority);
			Dimse request = aFact.newDimse(pc.pcid(), command, new MyDataSource(parser, ds, buffer));
			Dimse response = active.invoke(request).get();
			int status = response.getCommand().getStatus();
			if (forceClose) close();
			if (status == 0) return Status.OK;
			else { close(); return Status.FAIL; }
		}
		catch (Exception ex) {
			logger.warn(ex);
			logger.warn("..."+dicomObject.getSOPInstanceUID());
			close();
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

    private final void initAssocParam(DcmURL url) {
        assocRQ.setCalledAET( url.getCalledAET() );
        assocRQ.setCallingAET( maskNull( url.getCallingAET() ) );
        assocRQ.setMaxPDULength( maxPDULength );
        assocRQ.setAsyncOpsWindow( aFact.newAsyncOpsWindow(0,1) );
    }

    private ActiveAssociation openAssoc()
        	throws IOException, GeneralSecurityException {
        Association assoc =
            aFact.newRequestor(newSocket(url.getHost(), url.getPort()));
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

}
