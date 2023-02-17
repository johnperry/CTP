/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.data.SpecificCharacterSet;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;

import org.rsna.ctp.Configuration;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.ctp.objects.PrivateTagIndex;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerFunctions;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;

import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;

import org.apache.log4j.Logger;

/**
 * The MIRC DICOM anonymizer. The anonymizer provides de-identification and
 * re-identification of DICOM objects for clinical trials. Each element
 * as well as certain groups of elements are scriptable.
 * See the <a href="http://mircwiki.rsna.org">RSNA MIRC Wiki</a>
 * for more more information.
 */
public class DICOMAnonymizer {

	static final Logger logger = Logger.getLogger(DICOMAnonymizer.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();
	static final CodeMeaningTable deIdentificationCodeMeanings = new CodeMeaningTable();

	static final String blanks = "                                                       ";

   /**
     * Anonymizes the input file, writing the result to the output file.
     * The input and output files are allowed to be the same.
     * The fields to anonymize are scripted in the properties file.
     * <p>
     * Important note: if the script generates a skip() or quarantine()
     * function call, the output file is not written and the input file
     * is unmodified, even if it is the same as the output file.
     * @param inFile the file to anonymize.
     * @param outFile the output file.  It may be same as inFile if you want
     * to anonymize in place.
     * @param cmds the properties object containing the anonymization commands.
     * @param lkup the properties object containing the local lookup table; null
     * if local lookup is not to be used.
     * @param intTable the integer table containing integer remappings for elements.
     * @param forceIVRLE force the transfer syntax to IVRLE if true; leave
     * the syntax unmodified if false.
     * @param renameToSOPIUID rename the output file to [SOPInstanceUID].dcm, where
     * [SOPInstanceUID] is the value in the anonymized object (in case it is
     * remapped during anonymization.
     * @return the static status result
     */
    public static AnonymizerStatus anonymize(
			File inFile,
			File outFile,
			Properties cmds,
			Properties lkup,
			IntegerTable intTable,
			boolean forceIVRLE,
			boolean renameToSOPIUID) {

		String exceptions = "";
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		File tempFile = null;
		byte[] buffer = new byte[4096];
		try {
			//The strategy is to have two copies of the dataset.
			//One (dataset) will be modified. The other (origds)
			//will serve as the original data for reference during
			//the anonymization process.

			//Get the origds (up to the pixels), and close the input stream.
			BufferedInputStream origin = new BufferedInputStream(new FileInputStream(inFile));
			DcmParser origp = pFact.newDcmParser(origin);
			FileFormat origff = origp.detectFileFormat();
			Dataset origds = oFact.newDataset();
			origp.setDcmHandler(origds.getDcmHandler());
			origp.parseDcmFile(origff, Tags.PixelData);
			origin.close();

			//Get the dataset (up to the pixels) and leave the input stream open.
			//This one needs to be left open so we can read the pixels and any
			//data that comes afterward.
			in = new BufferedInputStream(new FileInputStream(inFile));
			DcmParser parser = pFact.newDcmParser(in);
			FileFormat fileFormat = parser.detectFileFormat();
			if (fileFormat == null) throw new IOException("Unrecognized file format: "+inFile);
			Dataset dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			parser.parseDcmFile(fileFormat, Tags.PixelData);
			
			//Set a default for the SpecificCharacterSet, if necessary, in both datasets
			SpecificCharacterSet cs = origds.getSpecificCharacterSet();
			if (cs == null) {
				origds.putCS(Tags.SpecificCharacterSet, "ISO_IR 100");
				dataset.putCS(Tags.SpecificCharacterSet, "ISO_IR 100");
			}

			//Encapsulate everything in a context
			DICOMAnonymizerContext context = new DICOMAnonymizerContext(cmds, lkup, intTable, origds, dataset);

			//There are two steps in anonymizing the dataset:
			// 1. Insert any elements that are required by the script
			//    but are missing from the dataset.
			// 2. Walk the tree of the dataset and modify any elements
			//    that have scripts or that match global modifiers.

			//Step 1: insert new elements
			insertElements(context);

			//Step 2: modify the remaining elements according to the commands
			processElements(context);

			//Write the dataset to a temporary file in the same directory
			File tempDir = outFile.getParentFile();
			tempFile = File.createTempFile("DCMtemp-", ".anon", tempDir);
            out = new BufferedOutputStream(new FileOutputStream(tempFile));

            //Get the SOPInstanceUID in case we need it for the rename.
            String sopiUID = null;
			try {
				sopiUID = dataset.getString(Tags.SOPInstanceUID);
				sopiUID = sopiUID.trim();
			}
			catch (Exception e) {
				logger.warn("Unable to get the SOPInstanceUID.");
				sopiUID = "1";
			}

			//Set the encoding
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
        	String prefEncodingUID = UIDs.ExplicitVRLittleEndian;
			FileMetaInfo fmi = dataset.getFileMetaInfo();
            if ((fmi != null) && (fileParam.encapsulated || !forceIVRLE)) {
            	prefEncodingUID = fmi.getTransferSyntaxUID();
            	logger.debug("FMI TransferSyntaxUID = "+prefEncodingUID);
			}
			else if (forceIVRLE) {
				prefEncodingUID = UIDs.ExplicitVRLittleEndian;
			}
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(prefEncodingUID);
			boolean swap = fileParam.byteOrder != encoding.byteOrder;

            //Create and write the metainfo for the encoding we are using
			fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

			//Write the dataset as far as was parsed
			dataset.writeDataset(out, encoding);

			//Write the pixels if the parser actually stopped before pixeldata
			logger.debug("Parser stopped at "+Tags.toString(parser.getReadTag()));
            if (parser.getReadTag() == Tags.PixelData) {
                dataset.writeHeader(
                    out,
                    encoding,
                    parser.getReadTag(),
                    parser.getReadVR(),
                    parser.getReadLength());
                if (encoding.encapsulated) {
                    parser.parseHeader();
                    while (parser.getReadTag() == Tags.Item) {
                        dataset.writeHeader(
                            out,
                            encoding,
                            parser.getReadTag(),
                            parser.getReadVR(),
                            parser.getReadLength());
                        writeValueTo(parser, buffer, out, false);
                        parser.parseHeader();
                    }
                    if (parser.getReadTag() != Tags.SeqDelimitationItem) {
                        throw new Exception(
                            "Unexpected Tag: " + Tags.toString(parser.getReadTag()));
                    }
                    if (parser.getReadLength() != 0) {
                        throw new Exception(
                            "(fffe,e0dd), Length:" + parser.getReadLength());
                    }
                    dataset.writeHeader(
                        out,
                        encoding,
                        Tags.SeqDelimitationItem,
                        VRs.NONE,
                        0);
                } 
                else {
                    writeValueTo(parser, buffer, out, swap && (parser.getReadVR() == VRs.OW));
                }
                parser.parseHeader();
			}
			
			logger.debug("parser.getReadTag after writing pixels: "+Tags.toString(parser.getReadTag()));
			
			//We have already parsed the header of the first post-pixels element,
			//so we need to suppress parsing it again.
			boolean suppress = true;
			
			//Get the PrivateTagIndex for checking the post-pixels elements
			PrivateTagIndex ptIndex = PrivateTagIndex.getInstance();
			
			//Set up for building the index of creators for a private group
			int lastGroup = 0;
			Hashtable<Integer,String> creatorIndex = new Hashtable<Integer,String>();

			//Now do any elements after the pixels one at a time.
			//This is done to allow streaming of large raw data elements
			//that occur above Tags.PixelData.
			int tag;
			long fileLength = inFile.length();
			logger.debug("fileLength = "+fileLength+" ("+Long.toHexString(fileLength)+")");
			while (logPosition("About to seek post-pixels element:", parser)
					&& !parser.hasSeenEOF()
//					&& (parser.getStreamPosition() < fileLength)
					&& (suppress || (parser.parseHeader() != -1))
					&& ((tag=parser.getReadTag()) != -1)
					&& (tag != 0xFFFAFFFA)
					&& (tag != 0xFFFCFFFC)) {
				suppress = false;
				logPosition("Found post-pixels element: "+Tags.toString(tag), parser);
				int len = parser.getReadLength();
				logger.debug("...readLength = "+len+" ("+Integer.toHexString(len)+")");
				
				//Build an index of the creators for the current private group
				int group = (tag >> 16) & 0xffff;
				boolean isPrivate = ((tag & 0x10000) != 0);
				boolean isCreator = ((tag & 0xFF00) == 0);
				if (isPrivate && isCreator) {
					if (lastGroup != group) {
						creatorIndex = new Hashtable<Integer,String>();
						lastGroup = group;
						logger.debug("Found new private group: "+Integer.toHexString(group));
					}
					//Read the creator
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					InputStream inStream = parser.getInputStream();
					for (int i = 0; i < len; ++i) baos.write(inStream.read());
					String creator = new String(baos.toByteArray()).toString().trim();
					creatorIndex.put(new Integer(group), creator);
					logger.debug("Creator element: "+Tags.toString(tag)+": \""+creator+"\"");
					if (!context.rpg || context.kspe) {
						logger.debug("Writing element: "+Tags.toString(tag));
						dataset.writeHeader(
							out,
							encoding,
							parser.getReadTag(),
							parser.getReadVR(),
							parser.getReadLength());
						out.write(baos.toByteArray());
					}
					else logger.debug("Skipping element: "+Tags.toString(tag));
				}
				else {
					String script = context.getScriptFor(tag);
					boolean isSafePrivateElement = false;
					if (isPrivate) {
						String creator = creatorIndex.get(new Integer(group));
						logger.debug("Found creator \""+creator+"\" for "+Tags.toString(tag));
						String code = ptIndex.getCode(group, creator, tag & 0xff).trim();
						logger.debug("Got \""+code+"\" code for "+Tags.toString(tag));
						if (creator != null) isSafePrivateElement = code.equals("K");
						logger.debug("isSafePrivateElement = "+isSafePrivateElement);
					}
					if ((isPrivate && context.rpg && !(context.kspe && isSafePrivateElement)) || 
							((script == null) && context.rue) || 
							((script != null) && script.startsWith("@remove()") ) ) {
						//skip this element
						logger.debug("Skipping element: "+Tags.toString(tag));
						//read past the data
						InputStream inStream = parser.getInputStream();
						for (int i = 0; i < len; ++i) inStream.read();
					}
					else {
						//write this element
						logger.debug("Writing element: "+Tags.toString(tag));
						dataset.writeHeader(
							out,
							encoding,
							parser.getReadTag(),
							parser.getReadVR(),
							parser.getReadLength());
						writeValueTo(parser, buffer, out, swap);
					}
				}
				logPosition("Position after processing last post-pixels element:", parser);
			}
			out.flush();
			out.close();
			in.close();

			//Rename the temp file to the specified outFile.
			if (renameToSOPIUID) outFile = new File(outFile.getParentFile(),sopiUID+".dcm");
			if (outFile.exists() && !outFile.delete()) {
				logger.warn("Unable to delete " + outFile);
			}
			if (!tempFile.renameTo(outFile)) {
				logger.warn("Unable to rename "+ tempFile + " to " + outFile);
			}
		}

		catch (Exception e) {
			FileUtil.close(in);
			FileUtil.close(out);
			FileUtil.deleteAll(tempFile);
			//Now figure out what kind of response to return.
			String msg = e.getMessage();
			if (msg == null) {
				msg = "!error! - no message";
				if (logger.isDebugEnabled()) logger.debug("Error call from "+inFile, e);
				else logger.info("Error call from "+inFile);
				return AnonymizerStatus.QUARANTINE(inFile,msg);
			}
			if (msg.contains("!skip!")) {
				return AnonymizerStatus.SKIP(inFile,msg);
			}
			if (msg.contains("!quarantine!")) {
				logger.info("Quarantine call from "+inFile);
				logger.info("...Message: "+msg);
				return AnonymizerStatus.QUARANTINE(inFile,msg);
			}
			logger.info("Unknown exception from "+inFile, e);
			return AnonymizerStatus.QUARANTINE(inFile,msg);
		}
		return AnonymizerStatus.OK(outFile, exceptions);
    }
    
    private static boolean logPosition(String msg, DcmParser parser) {
		if (logger.isDebugEnabled()) {
			long pos = parser.getStreamPosition();
			logger.debug(msg);
			logger.debug("...parser.hasSeenEOF() = "+parser.hasSeenEOF());
			logger.debug("...streamPosition      = "+pos+" ("+Long.toHexString(pos)+")");
		}
		return true;
	}		

	private static void writeValueTo(
					DcmParser parser,
					byte[] buffer,
					OutputStream out,
					boolean swap) throws Exception {
		InputStream in = parser.getInputStream();
		int len = parser.getReadLength();
		if (swap && (len & 1) != 0) {
			throw new Exception(
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
					logger.warn("Unable to read element "+Integer.toHexString(parser.getReadTag()));
					logger.warn("...remain = "+remain);
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
	}

	//Insert any elements that are missing from the dataset but required by the script.
	//Notes:
	// 1. It is not possible to insert SQ elements.
	// 2. It is not possible to insert elements in SQ elements.
	private static void insertElements(DICOMAnonymizerContext context) throws Exception {
		Dataset ds = context.outDS;
		int vr = 0;
		for (Integer intTag : context.scriptTable.keySet()) {
			int tag = intTag.intValue();
			if (!ds.contains(tag)) {
				String script = context.getScriptFor(tag).trim();

				TagDictionary.Entry entry = tagDictionary.lookup(tag);
				try { vr = VRs.valueOf(entry.vr); }
				catch (Exception ex) { vr = VRs.valueOf("SH"); }

				if (script.startsWith("@always()") && (vr != VRs.SQ)) {
					String value = makeReplacement(script, context, tag);
					if (value.equals("@keep()") || value.equals("@remove()")) {
						//do nothing
					}
					else if (value.startsWith("@blank(") || value.equals("@empty()")) {
						try { context.putXX(tag, vr, ""); }
						catch (Exception unable) { logger.warn("Unable to create "+Tags.toString(tag)+": "+script); }
					}
					else {
						try { context.putXX(tag, vr, value); }
						catch (Exception unable) { logger.warn("Unable to create "+Tags.toString(tag)+": "+script); }
					}
				}
				else if (script.startsWith("@always()@require()") && (vr == VRs.SQ)) {
					context.putXX(tag, vr, "");
				}
				else if (script.startsWith("@always()@call") && (vr == VRs.SQ)) {
					makeReplacement(script, context, tag); //just do the call; let the call modify the output dataset
				}
				else if (tag == 0x00120064) updateDeIdentificationMethodCodeSeq(script, context);
			}
		}
	}

	//Walk the tree in the context dataset and modify the output dataset as required,
	private static String processElements(DICOMAnonymizerContext context) throws Exception {
		PrivateTagIndex ptIndex = PrivateTagIndex.getInstance();
		String exceptions = "";
		String value;

		//If the output dataset is null, don't do anything
		Dataset ds = context.outDS;
		if (ds == null) return "";

		for (Iterator it=context.inDS.iterator(); it.hasNext(); ) {
			DcmElement el = (DcmElement)it.next();
			int tag = el.tag();
			int vr = el.vr();

			int group = (tag >> 16) & 0xFFFF;
			boolean isOverlay = ((group & 0xFF00) == 0x6000);
			boolean isCurve = ((group & 0xFF00) == 0x5000);
			boolean isPrivate = ((group & 0x1) != 0);
			boolean isCreatorBlock = isPrivate && ((tag & 0xFF00) == 0);
			boolean isSafe = false;
			
			if (isPrivate) {
				logger.debug("Private element: "+Tags.toString(tag)+"; VR="+VRs.toString(el.vr()));
			}
				
			if (isPrivate && !isCreatorBlock) {
				String block = context.getCreator(tag);
				isSafe = ptIndex.getCode(block, tag).equals("K");
			}

			String script = context.getScriptFor(tag);
			boolean hasScript = (script != null);

			boolean keep  = context.containsKeepGroup(group) ||
							(isCreatorBlock && context.kspe) ||
							(isSafe && context.kspe) ||
							(tag == 0x00080016)   	 || 	//SopClassUID
							(tag == 0x00080018)   	 || 	//SopInstanceUID
							(tag == 0x0020000D)   	 ||		//StudyInstanceUID
							(group == 0x0002) 		 ||		//FMI group
							(group == 0x0028) 		 ||		//the image description
							(group == 0x7FE0) 		 ||		//the image
							(isOverlay && !context.rol && !(isPrivate && context.rpg)) || //overlays
							(isCurve && !context.rc && !(isPrivate && context.rpg));      //curves

			/*
			if (isPrivate) logger.info(Tags.toString(tag)
										+": keep.group"+String.format("%04x",group)+"="+context.containsKeepGroup(group)
										+" / rpg="+context.rpg
										+" / isSafe="+isSafe+" / hasScript="+hasScript+" / keep="+keep);
			*/
			
			if (context.rpg && isPrivate && !hasScript && !keep) {
				try { ds.remove(tag); }
				catch (Exception ignore) { logger.debug("Unable to remove "+tag+" from dataset."); }
			}

			else if (context.rue && !hasScript && !keep) {
				try { ds.remove(tag); }
				catch (Exception ignore) { logger.debug("Unable to remove "+tag+" from dataset."); }
			}

			else if (context.rol && isOverlay) {
				try { ds.remove(tag); }
				catch (Exception ignore) { logger.debug("Unable to remove "+tag+" from dataset."); }
			}

			else if (hasScript) {
				if (tag != Tags.DeIdentificationMethodCodeSeq) {
					//The element wasn't handled globally
					//and it isn't DeIdentificationMethodCodeSequence,
					//process it now.
					value = makeReplacement(script, context, tag);
					value = (value != null) ? value.trim() : "";

					if (value.contains("@remove()")) { //note, value.equals("") no longer supported
						try { ds.remove(tag); }
						catch (Exception ignore) { logger.debug("Unable to remove "+tag+" from dataset."); }
					}

					else if (value.equals("@keep()")) ; //@keep() leaves the element in place

					else if (value.startsWith("@blank(")) {
						//@blank(n) inserts an element with n blank chars
						String nString = value.substring("@blank(".length());
						int paren = nString.indexOf(")");
						int n = 0;
						if (paren != -1) {
							nString = "0" + nString.substring(0, paren).replaceAll("\\D","");
							n = Integer.parseInt(nString);
						}
						if (n > blanks.length()) n = blanks.length();
						try { context.putXX(tag, vr, blanks.substring(0,n)); }
						catch (Exception e) {
							String tagString = Tags.toString(tag);
							logger.warn(tagString + " exception: " + e.toString()
										+ "\nscript=" + script);
							if (!exceptions.equals("")) exceptions += ", ";
							exceptions += tagString;
						}
					}
					else {
						try {
							if (value.equals("@empty()")) value = "";
							context.putXX(tag, vr, value);
						}
						catch (Exception e) {
							String tagString = Tags.toString(tag);
							logger.warn(tagString + " exception:\n" + e.toString()
										+ "\nscript=" + script
										+ "\nvalue= \"" + value + "\"");
							if (!exceptions.equals("")) exceptions += ", ";
							exceptions += tagString;
						}
					}
				}
				else {
					//Handle the DeIdentificationMethodCodeSeq element specially
					updateDeIdentificationMethodCodeSeq(script, context);
				}
			}
		}
		return exceptions;
	}

	private static void updateDeIdentificationMethodCodeSeq(String script, DICOMAnonymizerContext context) throws Exception {
		Dataset ds = context.outDS;
		int tag = Tags.DeIdentificationMethodCodeSeq;
		if (!script.trim().equals("")) {
			if (script.equals("@remove()")) {
				if (ds.contains(tag)) ds.remove(tag);
			}
			else if (script.equals("@keep()")) {
				return;
			}
			else {
				String value = makeReplacement(script, context, tag);
				value = (value != null) ? value.trim() : "";
				DcmElement e = null;
				try {
					if (!ds.contains(tag)) e = ds.putSQ(tag);
					else e = ds.get(tag);
					String[] codes = value.split("/");
					for (String code : codes) {
						code = code.trim();
						if (code.toLowerCase().equals("reset")) {
							ds.remove(tag);
							e = ds.putSQ(tag);
						}
						else {
							String scheme = "DCM";
							String meaning = deIdentificationCodeMeanings.getProperty(code);
							if (meaning == null) {
								meaning = "UNKNOWN";
								scheme = "UNKNOWN";
							}
							Dataset item = e.addNewItem();
							item.putSH( Tags.CodingSchemeDesignator, scheme );
							item.putSH( Tags.CodeValue, code );
							item.putLO( Tags.CodeMeaning, meaning );
						}
					}
				}
				catch (Exception ex) {
					logger.warn("Unable to update DeIdentificationMethodCodeSeq", ex);
				}
			}
		}
	}

	static class CodeMeaningTable extends Properties {
		public CodeMeaningTable() {
			super();
			setProperty("113100", "Basic Application Confidentiality Profile");
			setProperty("113101", "Clean Pixel Data Option");
			setProperty("113102", "Clean Recognizable Visual Features Option");
			setProperty("113103", "Clean Graphics Option");
			setProperty("113104", "Clean Structured Content Option");
			setProperty("113105", "Clean Descriptors Option");
			setProperty("113106", "Retain Longitudinal Temporal Information Full Dates Option");
			setProperty("113107", "Retain Longitudinal Temporal Information Modified Dates Option");
			setProperty("113108", "Retain Patient Characteristics Option");
			setProperty("113109", "Retain Device Identity Option");
			setProperty("113110", "Retain UIDs");
			setProperty("113111", "Retain Safe Private Option");
			setProperty("113112", "Retain Institution Identity Option");
		}
	}

	static final char escapeChar 		= '\\';
	static final char functionChar 		= '@';
	static final char delimiterChar 	= '^';
	static final String contentsFn 		= "contents";
	static final String valueFn 		= "value";
	static final String truncateFn 		= "truncate";
	static final String dateFn 			= "date";
	static final String decryptFn 		= "decrypt";
	static final String encryptFn 		= "encrypt";
	static final String hashFn 			= "hash";
	static final String hashnameFn 		= "hashname";
	static final String hashuidFn 		= "hashuid";
	static final String hashptidFn 		= "hashptid";
	static final String ifFn 			= "if";
	static final String selectFn 		= "select";
	static final String appendFn 		= "append";
	static final String alwaysFn 		= "always";
	static final String hashdateFn 		= "hashdate";
	static final String incrementdateFn = "incrementdate";
	static final String dateintervalFn	= "dateinterval";
	static final String lowercaseFn		= "lowercase";
	static final String uppercaseFn		= "uppercase";
	static final String modifydateFn	= "modifydate";
	static final String initialsFn 		= "initials";
	static final String lookupFn		= "lookup";
	static final String integerFn		= "integer";
	static final String paramFn 		= "param";
	static final String quarantineFn 	= "quarantine";
	static final String requireFn 		= "require";
	static final String roundFn 		= "round";
	static final String skipFn	 		= "skip";
	static final String timeFn 			= "time";
	static final String processFn		= "process";
	static final String callFn			= "call";
	static final String pathelementFn	= "pathelement";


	//Create the replacement for one element.
	public static String makeReplacement(String cmd, DICOMAnonymizerContext context, int thisTag) throws Exception {
		logger.debug(Tags.toString(thisTag)+": \""+cmd+"\"");
		if (cmd == null) return "";
		String out = "";
		char c;
		int i = 0;
		boolean escape = false;
		while (i < cmd.length()) {
			c = cmd.charAt(i++);
			if (escape) {
				out += c;
				escape = false;
			}
			else if (c == escapeChar) escape = true;
			else if (c == functionChar) {
				FnCall fnCall = new FnCall(cmd.substring(i), context, thisTag);
				if (fnCall.length == -1) break;
				i += fnCall.length;
				if (fnCall.name.equals(contentsFn)) 		out += contents(fnCall);
				else if (fnCall.name.equals(valueFn))		out += value(fnCall);
				else if (fnCall.name.equals(truncateFn))	out += truncate(fnCall);
				else if (fnCall.name.equals(dateFn)) 		out += date(fnCall);
				else if (fnCall.name.equals(dateintervalFn)) out += dateinterval(fnCall);
				else if (fnCall.name.equals(decryptFn))		out += decrypt(fnCall);
				else if (fnCall.name.equals(encryptFn))		out += encrypt(fnCall);
				else if (fnCall.name.equals(hashFn))		out += hash(fnCall);
				else if (fnCall.name.equals(hashnameFn))	out += hashname(fnCall);
				else if (fnCall.name.equals(hashptidFn))	out += hashptid(fnCall);
				else if (fnCall.name.equals(hashuidFn)) 	out += hashuid(fnCall);
				else if (fnCall.name.equals(ifFn))			out += iffn(fnCall);
				else if (fnCall.name.equals(selectFn))		out += selectfn(fnCall);
				else if (fnCall.name.equals(appendFn))		out += appendfn(fnCall);
				else if (fnCall.name.equals(alwaysFn))		out += alwaysfn(fnCall);
				else if (fnCall.name.equals(hashdateFn))	out += hashdate(fnCall);
				else if (fnCall.name.equals(incrementdateFn)) out += incrementdate(fnCall);
				else if (fnCall.name.equals(lowercaseFn))	out += lowercase(fnCall);
				else if (fnCall.name.equals(uppercaseFn))	out += uppercase(fnCall);
				else if (fnCall.name.equals(modifydateFn))	out += modifydate(fnCall);
				else if (fnCall.name.equals(initialsFn)) 	out += initials(fnCall);
				else if (fnCall.name.equals(lookupFn)) 		out += lookup(fnCall);
				else if (fnCall.name.equals(integerFn))		out += integer(fnCall);
				else if (fnCall.name.equals(paramFn)) 		out += param(fnCall);
				else if (fnCall.name.equals(quarantineFn))	throw new Exception("!quarantine!");
				else if (fnCall.name.equals(requireFn))		out += require(fnCall);
				else if (fnCall.name.equals(roundFn))		out += round(fnCall);
				else if (fnCall.name.equals(skipFn))		throw new Exception("!skip!");
				else if (fnCall.name.equals(timeFn)) 		out += time(fnCall);
				else if (fnCall.name.equals(processFn))		out += processfn(fnCall);
				else if (fnCall.name.equals(callFn))		out += callfn(fnCall);
				else if (fnCall.name.equals(pathelementFn))	out += pathelement(fnCall);
				else out += functionChar + fnCall.getCall();
			}
			else out += c;
		}
		return out;
	}

	//Execute the process function call for an SQ element.
	//This function loops through the items, calling processElements for each item dataset.
	//To process an item dataset, it pushes the current context datasets and replaces them
	//with the corresponding item's datasets. When it has completed processing all the items,
	//it returns "@keep()" to force the processElements method to retain the SQ element in the
	//output dataset.
	private static String processfn(FnCall fn) throws Exception {
		Dataset newInDS;
		Dataset newOutDS;
		int tag = fn.thisTag;
		DICOMAnonymizerContext ctx = fn.context;
		DcmElement inElement = ctx.inDS.get(tag);
		DcmElement outElement = ctx.outDS.get(tag);

		//Only do this for an SQ element
		if ( inElement.vr() == VRs.SQ ) {
			int item = 0;
			while ( ((newInDS = inElement.getItem(item)) != null)
						&& ((newOutDS = outElement.getItem(item)) != null) ) {
				ctx.push(newInDS, newOutDS);
				processElements(ctx);
				ctx.pop();
				item++;
			}
		}
		return "@keep()";
	}

	//Execute the append function call
	private static String appendfn(FnCall fn) throws Exception {
		String value = makeReplacement(fn.trueCode, fn.context, fn.thisTag);
		DcmElement el = fn.context.get(fn.thisTag);
		if (el == null) return value;
		SpecificCharacterSet cs = fn.context.getSpecificCharacterSet();
		int vm = el.vm(cs);
		if (vm == 0) return value;
		return fn.context.contents(fn.thisTag) + "\\" + value;
	}

	//Execute the always function call
	private static String alwaysfn(FnCall fn) {
		return "";
	}

	//Execute the select function call
	private static String selectfn(FnCall fn) throws Exception {
		if (fn.context.isRootDataset()) {
			return makeReplacement(fn.trueCode, fn.context, fn.thisTag);
		}
		return makeReplacement(fn.falseCode, fn.context, fn.thisTag);
	}

	//Execute the if function call
	private static String iffn(FnCall fn) throws Exception {
		if (testCondition(fn)) {
			return makeReplacement(fn.trueCode, fn.context, fn.thisTag);
		}
		return makeReplacement(fn.falseCode, fn.context, fn.thisTag);
	}

	//Determine whether a condition in an if statement is met
	private static boolean testCondition(FnCall fn) {
		if (fn.args.length < 2) return false;
		String tagName = fn.getArg(0);
		int tag = fn.getTag(tagName);
		String element = fn.context.contents(tagName, tag);
		if (fn.args[1].equals("isblank")) {
			return (element == null) || element.trim().equals("");
		}
		else if (fn.args[1].equals("contains")) {
			if ((element == null) || (fn.args.length < 3)) return false;
			return element.toLowerCase().contains(getArg(fn, 2).toLowerCase());
		}
		else if (fn.args[1].equals("equals")) {
			if ((element == null) || (fn.args.length < 3)) return false;
			return element.toLowerCase().equals(getArg(fn, 2).toLowerCase());
		}
		else if (fn.args[1].equals("matches")) {
			if ((element == null) || (fn.args.length < 3)) return false;
			return element.matches(getArg(fn, 2).trim());
		}
		else if (fn.args[1].equals("exists")) {
			return fn.context.contains(tag);
		}
		else if (fn.args[1].equals("greaterthan")) {
			if ((element == null) || (fn.args.length < 3)) return false;
			int eValue = StringUtil.getInt(element.replaceAll("[^0-9]", ""));
			int cValue = StringUtil.getInt(getArg(fn, 2).replaceAll("[^0-9]", ""));
			return (eValue > cValue);
		}
		return false;
	}

	private static String getArg(FnCall fn, int k) {
		String arg = fn.getArg(k);
		if (arg.startsWith("@")) {
			String param = fn.context.getParam(arg);
			if (param != null) return param;
		}
		return arg;
	}

	//Filter a quoted argument, removing the quotes
	private static String getArgument(String arg) {
		arg = arg.trim();
		if (arg.startsWith("\"") && arg.endsWith("\"")) {
			arg = arg.substring(1,arg.length()-1);
		}
		return arg;
	}

	//Execute the contents function call.
	//There are three possible calls:
	//   @contents(ElementName)
	//   @contents(ElementName,"regex")
	//   @contents(ElementName,"regex","replacement")
	private static String contents(FnCall fn) {
		String value = fn.context.contents(fn.args[0], fn.thisTag);
		if (value == null) return null;
		if (fn.args.length == 1) return value;
		else if (fn.args.length == 2) return value.replaceAll(fn.getArg(1), "");
		else if (fn.args.length == 3) return value.replaceAll(fn.getArg(1), fn.getArg(2));
		return "";
	}

	//Execute the value function call.
	//There are two possible calls:
	//   @value(ElementName)
	//   @value(ElementName,"default")
	private static String value(FnCall fn) {
		String value = fn.context.contents(fn.args[0], fn.thisTag);
		String def = (fn.args.length == 1) ? "" : fn.getArg(1);
		return (!value.equals("")) ? value : def;
	}

	//Execute the truncate function call.
	private static String truncate(FnCall fn) {
		String value = fn.context.contents(fn.args[0], fn.thisTag);
		if (value == null) return null;
		if (fn.args.length == 1) return value;
		else {
			try {
				int n = Integer.parseInt(fn.args[1]);
				if (n >= 0) {
					n = Math.min(value.length(), n);
					return value.substring(0, n);
				}
				else {
					value = value.trim();
					n = Math.max(0, value.length() + n);
					return value.substring(n);
				}
			}
			catch (Exception ex) { return value; }
		}
	}

	//Execute the param function call
	private static String param(FnCall fn) {
		return fn.context.getParam(fn.args[0]);
	}

	//Get the value of a parameter identified by a function call argument
	private static String getParam(FnCall fn) {
		return fn.context.getParam(fn.args[0]);
	}

	//Get the value of a parameter from the script
	private static String getParam(FnCall fn, String param) {
		return fn.context.getParam(param);
	}

	//Execute the require function. This function checks whether the
	//specified element is present. If it is, it leaves the element alone;
	//otherwise, it inserts an element with the specified value.
	private static String require(FnCall fn) {
		//Return a @keep() call if the element is present
		if (fn.context.contains(fn.thisTag)) return "@keep()";

		//The element was not present, return a value for a new element
		//If there are no arguments, return an empty string.
		if (fn.args.length == 0) return "";

		//There are some arguments, get the element tag
		//and see if it is in the dataset.
		String value = null;
		int tag = fn.getTag(fn.args[0]);
		if (fn.context.contains(tag)) {
			//It is, get the value
			try { value = fn.context.contents(tag); } //was getString!!!
			catch (Exception e) { value = null; }
		}
		else {
			//It isn't; get a default value from the arguments
			//or an empty string if there is no default.
			value = (fn.args.length > 1) ? fn.getArg(1) : "";
		}
		//Convert an all-blank value to a blank function call
		//so that the element isn't removed when the replacements
		//are processed.
		if (value.trim().equals("")) value = "@blank("+value.length()+")";
		return value;
	}

	//Execute the lookup function. This function uses the value of an element
	//as an index into a local unencrypted lookup table and returns the result.
	//If the requested element is not present or the value of the element is not
	//a key in the lookup table, throw a quarantine exception.
	//The arguments are: ElementName, keyType
	//where keyType is the name of a specific key, which must appear in the lkup
	//Properties as keyType/value = replacement.
	//Values and replacements are trimmed before use.
	private static String lookup(FnCall fn) throws Exception {
		String key = "";
		try {
			String[] names = fn.args[0].split("\\|");
			boolean first = true;
			for (String name : names) {
				if (!first) key += "|";
				key += fn.context.contents(name.trim(), fn.thisTag);
				first = false;
			}
			key = key.replaceAll("\\\\","");
			if (logger.isDebugEnabled()) {
				logger.debug("Calling @lookup"+fn.getArgs());
				logger.debug("   keytype: \""+fn.args[1]+"\"");
				logger.debug("   key:     \""+key+"\"");
			}
			String value = AnonymizerFunctions.lookup(fn.context.lkup, fn.args[1], key);
			return value;
		}
		catch (Exception ex) {
			if (fn.args.length <= 2) logger.debug("Exception caught in lookup function", ex);
			else {
				String action = fn.getArg(2).trim();
				if (action.equals("keep")) return "@keep()";
				if (action.equals("remove")) return "@remove()";
				if (action.equals("empty")) return "@empty()";
				if (fn.args.length > 3) {
					if (action.equals("default")) return fn.getArg(3).trim();
					if (action.equals("ignore") && key.matches(fn.getArg(3))) return key;
				}
			}
			throw new Exception("!quarantine! - "+ex.getMessage());
		}
	}

	//Execute the dateinterval function. This function computes the number of days
	//between a date in an element and a base date contained in a local unencrypted 
	//lookup table indexed by the value of another element and returns the result.
	//If the requested element is not present or the value of the element is not
	//a key in the lookup table, throw a quarantine exception.
	//The arguments are: DateElementName, keyType, KeyElement, [origindate]
	//where keyType is the name of a specific key, which must appear in the lkup
	//Properties as keyType/KeyElementValue = replacement.
	//KeyElement is always obtained from the root dataset, even when processing item
	//datasets of SQ elements.
	//[origindate] is an optional argument (which can be a parameter reference) specifying
	//a date in DICOM format (YYYYMMDD).
	//If [origindate] is not present, the function returns the computed number of days.
	//If [origindate] is returned, the function returns the origindate incremented by
	//the computed number of days.
	//Values and replacements are trimmed before use.
	private static final long oneDay = 24 * 60 * 60 * 1000;
	private static SimpleDateFormat dcmDF = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
	private static SimpleDateFormat basedateDF = new SimpleDateFormat("M/d/yyyy", Locale.ENGLISH);
	private static String dateinterval(FnCall fn) throws Exception {
		try {
			if (fn.args.length > 2) {
				String dateElementName = fn.args[0];
				String dateElementValue = fn.context.contents(dateElementName.trim(), fn.thisTag);
				String keyElementName = fn.args[2];
				String key = fn.context.contents(keyElementName.trim(), fn.thisTag, fn.context.getRootDataset());
				
				logger.debug("dateElementName:  \""+dateElementName+"\"");
				logger.debug("dateElementValue: \""+dateElementValue+"\"");
				logger.debug("keyElementName:   \""+keyElementName+"\"");
				logger.debug("key:              \""+key+"\"");
					
				String basedate = AnonymizerFunctions.lookup(fn.context.lkup, fn.args[1], key).trim();
				if (dateElementValue.length() > 8) dateElementValue = dateElementValue.substring(0,8);
				long dateMS = dcmDF.parse(dateElementValue).getTime();
				long basedateMS = basedateDF.parse(basedate).getTime();
				long days = Math.round( (double)(dateMS - basedateMS) / (double)oneDay );
				
				if (fn.args.length == 3) return Long.toString(days);
				else {
					String originDate = fn.context.getParam(fn.args[3]);
					return AnonymizerFunctions.incrementDate(originDate, days);
				}
			}
		}
		catch (Exception error) { logger.debug("Error in dateinterval"+fn.getArgs(), error); }
		throw new Exception("!quarantine!");
	}
	
	//Execute the integer function. This function uses the value of an element
	//to create an integer replacement string of a specified width.
	//If the requested element is not present or the value of the replacement
	//string cannot be created, throw a quarantine exception.
	//The arguments are: ElementName, keyType, width
	//where keyType is a name identifying a category of element (e.g., ptid).
	//Values and replacements are trimmed before use.
	private static String integer(FnCall fn) throws Exception {
		try {
			String text = fn.context.contents(fn.args[0], fn.thisTag);
			String keyType = fn.args[1];
			int width = 0;
			if (fn.args.length > 2) {
				try { width = Integer.parseInt(fn.args[2]); }
				catch (Exception useDefault) { }
			}
			return AnonymizerFunctions.integer(fn.context.intTable, keyType, text, width);
		}
		catch (Exception ex) {
			throw new Exception("!quarantine! - "+ex.getMessage());
		}
	}

	//Execute the pathelement function. This function returns
	//the an element in a path stored in an element. The path
	//element returned is the one indexed by the second 
	//argument. Positive indexes work from the root of the
	//path (0 based). Negative indexes work from the
	//end of the path. Thus, -1 is the last element in the path;
	//0 is the root element of the path, and 2 is the first child
	//of the root. If the index is not within the length of the path,
	//the whole path is returned.
	private static String pathelement(FnCall fn) {
		String s = fn.context.contents(fn.args[0], fn.thisTag);
		int index = 1000;
		try { index = Integer.parseInt(fn.args[1]); }
		catch (Exception useDefault) { }
		String[] pathElements = s.split("/");
		if (index < 0) index = pathElements.length + index;
		if ((index >= 0) && (index < pathElements.length)) {
			return pathElements[index];
		}
		return s;
	}

	//Execute the lowercase function. This function returns
	//the value of an element, converted to lower case.
	private static String lowercase(FnCall fn) {
		String s = fn.context.contents(fn.args[0], fn.thisTag);
		return s.toLowerCase();
	}

	//Execute the uppercase function. This function returns
	//the value of an element, converted to upper case.
	private static String uppercase(FnCall fn) {
		String s = fn.context.contents(fn.args[0], fn.thisTag);
		return s.toUpperCase();
	}

	//Execute the initials function. This function is typically used
	//to generate the initials of a patient from the contents of the
	//PatientName element. The second argument, if present, offsets
	//the characters using a Caesar cipher.
	private static String initials(FnCall fn) {
		String s = fn.context.contents(fn.args[0], fn.thisTag);
		s = AnonymizerFunctions.initials(s);
		if (fn.args.length > 1) {
			int offset = StringUtil.getInt(fn.args[1]);
			s = AnonymizerFunctions.encrypt(s, offset);
		}
		return s;
	}

	//Execute the hashname function. This function is typically used
	//to generate a numeric value from the contents of the PatientName element.
	private static String hashname(FnCall fn) {
		try {
			String string = fn.context.contents(fn.args[0], fn.thisTag);

			String lengthString = fn.context.getParam(fn.args[1]);
			int length;
			try { length = Integer.parseInt(lengthString); }
			catch (Exception ex) { length = 4; }

			int wordCount = Integer.MAX_VALUE;
			if (fn.args.length > 2) {
				String wordCountString = fn.context.getParam(fn.args[2]);
				try { wordCount = Integer.parseInt(wordCountString); }
				catch (Exception keepDefault) { wordCount = Integer.MAX_VALUE; }
			}
			return AnonymizerFunctions.hashName(string, length, wordCount);
		}
		catch (Exception e) {
			logger.warn(Tags.toString(fn.thisTag)+": Exception in hashname"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return fn.getArgs();
		}
	}

	//Execute the round function call. This function is used to
	//round an age field into groups of a given size.
	private static String round(FnCall fn) {
		//arg must contain: AgeElementName, groupSize
		try {
			String ageString = fn.context.contents(fn.args[0], fn.thisTag);
			String sizeString = fn.context.getParam(fn.args[1]);
			int size = Integer.parseInt(sizeString);
			return AnonymizerFunctions.round(ageString,size);
		}
		catch (Exception e) {
			logger.warn(Tags.toString(fn.thisTag)+": Exception caught in round"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return fn.getArgs();
		}
	}

	//Execute the hashptid function call.
	private static String hashptid(FnCall fn) {
		//args must contain: siteid, elementname, and optionally maxlen
		try {
			if (fn.args.length < 2) return fn.getArgs();
			String siteid = fn.context.getParam(fn.args[0]);
			String ptid = fn.context.contents(fn.args[1], fn.thisTag);
			if (ptid == null) ptid = "null";
			String maxlenString = "";
			if (fn.args.length >= 3) maxlenString = fn.context.getParam(fn.args[2]);
			int maxlen = Integer.MAX_VALUE;
			try { maxlen = Integer.parseInt(maxlenString); }
			catch (Exception ex) { maxlen = Integer.MAX_VALUE; }
			return AnonymizerFunctions.hashPtID(siteid, ptid, maxlen);
		}
		catch (Exception e) {
			logger.warn(Tags.toString(fn.thisTag)+": Exception caught in hashptid"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return fn.getArgs();
		}
	}

	//Execute the hash function call. This function is used
	//to generate an MD5 hash of any element text, with the
	//result being a base-10 digit string.
	private static String hash(FnCall fn) {
		String remove = "@remove()";
		try {
			String value = fn.context.contentsNull(fn.args[0], fn.thisTag);
			if (value == null) return remove;
			int len = Integer.MAX_VALUE;
			if (fn.args.length > 1) {
				String lenString = fn.context.getParam(fn.args[1]);
				if (lenString.length() != 0) {
					try { len = Integer.parseInt(lenString); }
					catch (Exception ex) { len = Integer.MAX_VALUE; }
				}
			}
			return AnonymizerFunctions.hash(value,len);
		}
		catch (Exception e) {
			logger.warn(Tags.toString(fn.thisTag)+": Exception caught in hash"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return fn.getArgs();
		}
	}

	//Execute the hashdate function call.
	//Get a new date by adding a constant to an date value, determining the
	//increment by hashing the value of an element and limiting the increment
	//to the last 10 years..
	private static String hashdate(FnCall fn) {
		//arg must contain: DateElementName, HashElementName
		//DateElementName is the name of the element containing the date to be incremented.
		//HashElementName specifies an element whose value is to be hashed and then
		//converted to an increment in the last 10 years.
		String emptyDate = "@empty()";
		String removeDate = "@remove()";
		try {
			String date = fn.context.contentsNull(fn.args[0], fn.thisTag);
			if (date == null) return removeDate;
			if (date.length() < 8) return emptyDate;
			String unhashed = fn.context.contentsNull(fn.args[1], fn.thisTag);
			if (unhashed == null) return removeDate;
			String incString = AnonymizerFunctions.hash(unhashed, -1);
			int n = incString.length();
			if (n > 4) incString = incString.substring( n-4, n);
			long inc = Long.parseLong(incString);
			inc = -1 * (inc % (10 * 365));
			if (inc == 0) inc = -1;
			String[] dates = date.split("\\\\");
			StringBuffer sb = new StringBuffer();
			for (int i=0; i<dates.length; i++) {
				if (i > 0) sb.append("\\");
				sb.append(AnonymizerFunctions.incrementDate(dates[i], inc));
			}
			return sb.toString();
		}
		catch (Exception e) {
			logger.warn(Tags.toString(fn.thisTag)+": Exception caught in hashdate"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return emptyDate;
		}
	}

	//Execute the incrementdate function call.
	//Get a new date by adding a constant to an date value.
	private static String incrementdate(FnCall fn) {
		//arg must contain: DateElementName, increment
		//or DateElementName, {PatientID or other element name}, lookup table keytype
		//DateElementName is the name of the element containing the date to be incremented.
		//increment specifies the number of days to add to the date (positive generates
		//later dates; negative generates earlier dates.
		String emptyDate = "@empty()";
		String removeDate = "@remove()";
		try {
			String date = fn.context.contentsNull(fn.args[0], fn.thisTag);
			if (date == null) return removeDate;
			if (date.length() < 8) return emptyDate;
			String incString = "0";
			if (fn.args.length == 2) {
				//first argument sequence
				incString = fn.context.getParam(fn.args[1]);
			}
			else if (fn.args.length == 3) {
				//second argument sequence
				String key = fn.context.contentsNull(fn.args[1], fn.thisTag);
				String keytype = fn.context.getParam(fn.args[2]);
				incString = AnonymizerFunctions.lookup(fn.context.lkup, keytype, key);
				if (logger.isDebugEnabled()) {
					logger.debug("Calling @incrementdate"+fn.getArgs());
					logger.debug("   key:     \""+key+"\"");
					logger.debug("   keytype: \""+fn.args[1]+"\"");
					logger.debug("   offset:  \""+incString+"\"");
				}
			}
			else throw new Exception("Unknown calling sequence in incrementdate");
			long inc = Long.parseLong(incString);
			String[] dates = date.split("\\\\");
			StringBuffer sb = new StringBuffer();
			for (int i=0; i<dates.length; i++) {
				if (i > 0) sb.append("\\");
				sb.append(AnonymizerFunctions.incrementDate(dates[i], inc));
			}
			return sb.toString();
		}
		catch (Exception e) {
			logger.warn(Tags.toString(fn.thisTag)+": Exception caught in incrementdate"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return emptyDate;
		}
	}

	//Execute the modifydate function call.
	//Get a new date by modifying the current one.
	private static String modifydate(FnCall fn) {
		//arg must contain: DateElementName, year, month, day
		//DateElementName is the name of the element containing the date to be incremented.
		//year specifies the value of the year (* means to keep the current value).
		//month specifies the value of the month (* means to keep the current value).
		//day specifies the value of the day of the month (* means to keep the current value).
		String removeDate = "@remove()";
		String emptyDate = "@empty()";
		try {
			String date = fn.context.contentsNull(fn.args[0], fn.thisTag);
			if (date == null) return removeDate;
			if (date.length() < 8) return emptyDate;
			int y = getReplacementValue(fn.context.getParam(fn.args[1]).trim());
			int m = getReplacementValue(fn.context.getParam(fn.args[2]).trim());
			int d = getReplacementValue(fn.context.getParam(fn.args[3]).trim());
			String[] dates = date.split("\\\\");
			StringBuffer sb = new StringBuffer();
			for (int i=0; i<dates.length; i++) {
				if (i > 0) sb.append("\\");
				sb.append(AnonymizerFunctions.modifyDate(dates[i], y, m, d));
			}
			return sb.toString();
		}
		catch (Exception e) {
			logger.warn(Tags.toString(fn.thisTag)+": Exception caught in modifydate"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return emptyDate;
		}
	}

	private static int getReplacementValue(String s) {
		try { return Integer.parseInt(s); }
		catch (Exception ex) { return -1; }
	}

	//Execute the hashuid function call. Generate a new uid
	//from a prefix and an old uid. The old uid is hashed and
	//appended to the prefix.
	//If there is a third argument, its value must be an element,
	//in which case its script is computed and the result is
	//appended to the second argument's value before hashing.
	private static String hashuid(FnCall fn) {
		String removeUID = "@remove()";
		try {
			if (fn.args.length < 2) return fn.getArgs();
			String prefix = getParam(fn);
			String uid = fn.context.contentsNull(fn.args[1], fn.thisTag);
			//If there is no UID in the dataset, then return @remove().
			if (uid == null) return removeUID;
			//Make sure the prefix ends in a period
			if (!prefix.endsWith(".")) prefix += ".";
			//See if there is a third argument
			if (fn.args.length > 2) {
				int argTag = fn.context.getElementTag(fn.args[2]);
				String argScript = fn.context.getScriptFor(argTag);
				if (argScript != null) {
					uid += makeReplacement(argScript, fn.context, argTag);
				}
			}
			//Create the replacement UID
			return AnonymizerFunctions.hashUID(prefix,uid);
		}
		catch (Exception e) {
			logger.warn(Tags.toString(fn.thisTag)+": Exception caught in hashuid"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return fn.getArgs();
		}
	}

	//Execute the decrypt function call. This function is used
	//to decrypt an encrypted string from an element text value.
	private static String decrypt(FnCall fn) {
		logger.debug("decrypt"+fn.getArgs()+" called");
		if (fn.args.length < 2) return fn.getArgs();
		try {
			String value = fn.context.contents(fn.args[0], fn.thisTag);
			String key = fn.context.getParam(fn.args[1]);
			return AnonymizerFunctions.decrypt(value, key);
		}
		catch (Exception e) {
			logger.debug(Tags.toString(fn.thisTag)+": Exception caught in decrypt"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return fn.getArgs();
		}
	}

	//Execute the encrypt function call. This function is used
	//to generate an encrypted string from an element text value.
	private static String encrypt(FnCall fn) {
		logger.debug("encrypt"+fn.getArgs()+" called");
		if (fn.args.length < 2) return fn.getArgs();
		try {
			String value = fn.context.contents(fn.args[0], fn.thisTag);
			String key = fn.context.getParam(fn.args[1]);
			return AnonymizerFunctions.encrypt(value, key);
		}
		catch (Exception e) {
			logger.debug(Tags.toString(fn.thisTag)+": Exception caught in encrypt"+fn.getArgs()+": "+e.getMessage());
			logger.debug(e.getMessage(), e);
			return fn.getArgs();
		}
	}

	private static String time(FnCall fn) {
		return AnonymizerFunctions.time(fn.getArg(0));
	}

	private static String date(FnCall fn) {
		return AnonymizerFunctions.date(fn.getArg(0));
	}

	//Execute the plugin function call. This function provides access
	//to a method in a plugin that implements the AnonymizerExtension
	//interface. The first argument of the FnCall must be the id of the
	//plugin.
	private static String callfn(FnCall fn) throws Exception {
		try {
			String id = fn.args[0];
			Configuration config = Configuration.getInstance();
			if (config != null) {
				Plugin plugin = config.getRegisteredPlugin(id);
				if (plugin instanceof AnonymizerExtension) {
					AnonymizerExtension ext = (AnonymizerExtension)plugin;
					return ext.call(fn);
				}
			}
			throw new Exception("Unable to load plugin "+id);
		}
		catch (Exception e) {
			logger.warn(Tags.toString(fn.thisTag)+": Exception caught in plugin call", e);
			logger.debug(e.getMessage(), e);
			throw new Exception("!quarantine! - plugin call: "+e.getMessage());
		}
	}

//********************** End of function calls ***************************
}
