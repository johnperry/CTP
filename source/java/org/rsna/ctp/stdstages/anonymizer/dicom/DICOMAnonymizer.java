/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.regex.*;

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

import org.rsna.ctp.stdstages.anonymizer.AnonymizerFunctions;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;

import org.apache.log4j.Logger;

/**
 * The MIRC DICOM anonymizer. The anonymizer provides de-identification and
 * re-identification of DICOM objects for clinical trials. Each element
 * as well as certain groups of elements are scriptable. The script
 * language is defined in "How to Configure the Anonymizer for MIRC
 * Clinical Trial Services".
 * <p>
 * See the <a href="http://mirc.rsna.org/mircdocumentation">
 * MIRC documentation</a> for more more information.
 */
public class DICOMAnonymizer {

	static final Logger logger = Logger.getLogger(DICOMAnonymizer.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

   /**
     * Anonymizes the input file, writing the result to the output file.
     * The input and output files are allowed to be the same.
     * The fields to anonymize are scripted in the properties file.
     * <p>
     * Important note: if the script generates a skip() or quarantine()
     * function call, the output file is not written and the input file
     * is unmodified, even if it is the same as the output file.
     * @param inFile the file to anonymize.
     * @param outFile the output file.  It may be same as inFile you if want
     * to anonymize in place.
     * @param cmds the properties object containing the anonymization commands.
     * @param lkup the properties object containing the local lookup table; null
     * if local lookup is not to be used.
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
			//Check that this is a known format.
			in = new BufferedInputStream(new FileInputStream(inFile));
			DcmParser parser = pFact.newDcmParser(in);
			FileFormat fileFormat = parser.detectFileFormat();
			if (fileFormat == null) throw new IOException("Unrecognized file format: "+inFile);

			//Get the dataset (excluding pixels) and leave the input stream open
			Dataset dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			parser.parseDcmFile(fileFormat, Tags.PixelData);

			//Set up the replacements using the cmds properties and the dataset
			Properties theReplacements = setUpReplacements(dataset, cmds, lkup, intTable);

			// get booleans to handle the global cases
			boolean rpg = (cmds.getProperty("remove.privategroups") != null);
			boolean rue = (cmds.getProperty("remove.unspecifiedelements") != null);
			boolean rol = (cmds.getProperty("remove.overlays") != null);
			boolean rc  = (cmds.getProperty("remove.curves") != null);
			int[] keepGroups = getKeepGroups(cmds);

			//Modify the elements according to the commands
			exceptions = doOverwrite(dataset, theReplacements, rpg, rue, rol, rc, keepGroups);
			if (!exceptions.equals(""))
				logger.error("DicomAnonymizer exceptions for "+inFile+"\n"+exceptions);

			//Save the dataset.
			//Write to a temporary file in the same directory, and rename at the end.
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
        	String prefEncodingUID = UIDs.ImplicitVRLittleEndian;
			FileMetaInfo fmi = dataset.getFileMetaInfo();
            if ((fmi != null) && (fileParam.encapsulated || !forceIVRLE))
            	prefEncodingUID = fmi.getTransferSyntaxUID();
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(prefEncodingUID);
			boolean swap = fileParam.byteOrder != encoding.byteOrder;

            //Create and write the metainfo for the encoding we are using
			fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

			//Write the dataset as far as was parsed
			dataset.writeDataset(out, encoding);
			//Write the pixels if the parser actually stopped before pixeldata
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
                } else {
                    writeValueTo(parser, buffer, out, swap && (parser.getReadVR() == VRs.OW));
                }
				parser.parseHeader(); //get ready for the next element
			}
			//Now do any elements after the pixels one at a time.
			//This is done to allow streaming of large raw data elements
			//that occur above Tags.PixelData.
			while (!parser.hasSeenEOF() && parser.getReadTag() != -1) {
				dataset.writeHeader(
					out,
					encoding,
					parser.getReadTag(),
					parser.getReadVR(),
					parser.getReadLength());
				writeValueTo(parser, buffer, out, swap);
				parser.parseHeader();
			}
			out.flush();
			out.close();
			in.close();
			if (renameToSOPIUID) outFile = new File(outFile.getParentFile(),sopiUID+".dcm");
			outFile.delete();
			tempFile.renameTo(outFile);
		}

		catch (Exception e) {
			try {
				//Close the input stream if it actually got opened.
				if (in != null) in.close();
			}
			catch (Exception ignore) { logger.warn("Unable to close the input stream."); }
			try {
				//Close the output stream if it actually got opened,
				//and delete the tempFile in case it is still there.
				if (out != null) {
					out.close();
					tempFile.delete();
				}
			}
			catch (Exception ignore) { logger.warn("Unable to close the output stream."); }
			//Now figure out what kind of response to return.
			String msg = e.getMessage();
			if (msg == null) {
				msg = "!error! - no message";
				logger.info("Error call from "+inFile);
				return AnonymizerStatus.QUARANTINE(inFile,msg);
			}
			if (msg.indexOf("!skip!") != -1) {
				return AnonymizerStatus.SKIP(inFile,msg);
			}
			if (msg.indexOf("!quarantine!") != -1) {
				logger.info("Quarantine call from "+inFile);
				logger.info("...Message: "+msg);
				return AnonymizerStatus.QUARANTINE(inFile,msg);
			}
			logger.info("Unknown exception from "+inFile);
			logger.info("...Message: "+msg);
			return AnonymizerStatus.QUARANTINE(inFile,msg);
		}
		return AnonymizerStatus.OK(outFile, exceptions);
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

    /**
     * Create a Properties object that contains all the replacement values defined
     * by the scripts for each element. The value for an element is stored under
     * the key "(gggg,eeee)".
     * @param ds the DICOM dataset
     * @param cmds the anonymization commands
     * @param lkup the lookup table (or null if no lookup table is required)
     * @return a Properties object containing all the replacement valules defined
     * by the cmds scripts.
     */
    public static Properties setUpReplacements(
			Dataset ds,
			Properties cmds,
			Properties lkup,
			IntegerTable intTable) throws Exception {
		Properties props = new Properties();
		for (Enumeration it=cmds.keys(); it.hasMoreElements(); ) {
			String key = (String) it.nextElement();
			if (key.startsWith("set.")) {
				try {
					String replacement = makeReplacement(key, cmds, lkup, intTable, ds);
					props.setProperty(getTagString(key), replacement);
				}
				catch (Exception e) {
					String msg = e.getMessage();
					if (msg == null) msg = "";
					if (msg.indexOf("!skip!") != -1) throw e;
					if (msg.indexOf("!quarantine!") != -1) throw e;
					logger.error("Exception in setUpReplacements:",e);
					throw new Exception(
						"!error! during processing of:\n" + key + "=" + cmds.getProperty(key));
				}
			}
		}
		return props;
	}

	//Get an int[] containing all the keep.groupXXXX
	//elements' group numbers, sorted in order.
	private static int[] getKeepGroups(Properties cmds) {
		LinkedList<String> list = new LinkedList<String>();
		for (Enumeration it=cmds.keys(); it.hasMoreElements(); ) {
			String key = (String)it.nextElement();
			if (key.startsWith("keep.group")) {
				list.add(key.substring("keep.group".length()).trim());
			}
		}
		Iterator iter = list.iterator();
		int[] keepGroups = new int[list.size()];
		for (int i=0; i<keepGroups.length; i++) {
			try { keepGroups[i] = Integer.parseInt((String)iter.next(),16) << 16; }
			catch (Exception ex) { keepGroups[i] = 0; }
		}
		Arrays.sort(keepGroups);
		return keepGroups;
	}

	//Find "[gggg,eeee]" in a String and
	//return a tagString in the form "(gggg,eeee)".
	static final String defaultTagString = "(0000,0000)";
	private static String getTagString(String key) {
		int k = key.indexOf("[");
		if (k < 0) return defaultTagString;
		int kk = key.indexOf("]",k);
		if (kk < 0) return defaultTagString;
		return ("(" + key.substring(k+1,kk) + ")").toLowerCase();
	}

	/**
	 * Find "(gggg,eeee)" in a String and
	 * return an int corresponding to the hex value.
	 * @param key the string containing the DICOM group and element text.
	 * @return the dcm4che tag corresponding to the key.
	 */
	public  static int getTagInt(String key) {
		int k = key.indexOf("(");
		if (k < 0) return 0;
		int kk = key.indexOf(")",k);
		if (kk < 0) return 0;
		key = key.substring(k+1,kk).replaceAll("[^0-9a-fA-F]","");
		try { return Integer.parseInt(key,16); }
		catch (Exception e) { return 0; }
	}

	static final char escapeChar 		= '\\';
	static final char functionChar 		= '@';
	static final char delimiterChar 	= '^';
	static final String contentsFn 		= "contents";
	static final String truncateFn 		= "truncate";
	static final String dateFn 			= "date";
	static final String encryptFn 		= "encrypt";
	static final String hashFn 			= "hash";
	static final String hashnameFn 		= "hashname";
	static final String hashuidFn 		= "hashuid";
	static final String hashptidFn 		= "hashptid";
	static final String ifFn 			= "if";
	static final String appendFn 		= "append";
	static final String incrementdateFn = "incrementdate";
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


	//Create the replacement for one element starting from a key.
	private static String makeReplacement(
			String key,
			Properties cmds,
			Properties lkup,
			IntegerTable intTable,
			Dataset ds)  throws Exception {
		String cmd = cmds.getProperty(key);
		int thisTag = getTagInt(getTagString(key));
		return makeReplacement(cmd, cmds, lkup, intTable, ds,thisTag);
	}

	//Create the replacement for one element starting from a command.
	private static String makeReplacement(
			String cmd,
			Properties cmds,
			Properties lkup,
			IntegerTable intTable,
			Dataset ds,
			int thisTag)  throws Exception {
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
				FnCall fnCall = new FnCall(cmd.substring(i), cmds, lkup, intTable, ds,thisTag);
				if (fnCall.length == -1) break;
				i += fnCall.length;
				if (fnCall.name.equals(contentsFn)) 		out += contents(fnCall);
				else if (fnCall.name.equals(truncateFn))	out += truncate(fnCall);
				else if (fnCall.name.equals(dateFn)) 		out += date(fnCall);
				else if (fnCall.name.equals(encryptFn))		out += encrypt(fnCall);
				else if (fnCall.name.equals(hashFn))		out += hash(fnCall);
				else if (fnCall.name.equals(hashnameFn))	out += hashname(fnCall);
				else if (fnCall.name.equals(hashptidFn))	out += hashptid(fnCall);
				else if (fnCall.name.equals(hashuidFn)) 	out += hashuid(fnCall);
				else if (fnCall.name.equals(ifFn))			out += iffn(fnCall);
				else if (fnCall.name.equals(appendFn))		out += appendfn(fnCall);
				else if (fnCall.name.equals(incrementdateFn)) out += incrementdate(fnCall);
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
				else out += functionChar + fnCall.getCall();
			}
			else out += c;
		}
		return out;
	}

	//Execute the append function call
	private static String appendfn(FnCall fn) throws Exception {
		String value = makeReplacement(fn.trueCode, fn.cmds, fn.lkup, fn.intTable, fn.ds, fn.thisTag);
		DcmElement el = fn.ds.get(fn.thisTag);
		if (el == null) return value;
		SpecificCharacterSet cs = fn.ds.getSpecificCharacterSet();
		int vm = el.vm(cs);
		if (vm == 0) return value;
		return contents(fn.ds, fn.thisTag) + "\\" + value;
	}

	//Execute the if function call
	private static String iffn(FnCall fn) throws Exception {
		if (testCondition(fn))
			return makeReplacement(fn.trueCode, fn.cmds, fn.lkup, fn.intTable, fn.ds, fn.thisTag);
		return makeReplacement(fn.falseCode, fn.cmds, fn.lkup, fn.intTable, fn.ds, fn.thisTag);
	}

	//Determine whether a condition in an if statement is met
	private static boolean testCondition(FnCall fn) {
		if (fn.args.length < 2) return false;
		String tagName = fn.getArg(0);
		int tag = fn.getTag(tagName);
		String element = contents(fn.ds, tagName, tag);
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
			return fn.ds.contains(tag);
		}
		return false;
	}

	private static String getArg(FnCall fn, int k) {
		String arg = fn.getArg(k);
		if (arg.startsWith("@")) {
			String param = getParam(fn.cmds, arg);
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
		String value = contents(fn.ds, fn.args[0], fn.thisTag);
		if (value == null) return null;
		if (fn.args.length == 1) return value;
		else if (fn.args.length == 2) return value.replaceAll(fn.getArg(1), "");
		else if (fn.args.length == 3) return value.replaceAll(fn.getArg(1), fn.getArg(2));
		return "";
	}

	//Execute the truncate function call.
	private static String truncate(FnCall fn) {
		String value = contents(fn.ds, fn.args[0], fn.thisTag);
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
					n = Math.max(0, value.length() + n);
					return value.substring(n);
				}
			}
			catch (Exception ex) { return value; }
		}
	}

	//Get the contents of a dataset element by tagName
	private static String contents(Dataset ds, String tagName, int defaultTag) {
		String value = "";
		tagName = (tagName != null) ? tagName.trim() : "";
		if (!tagName.equals("")) {
			int tag = tagName.equals("this") ? defaultTag : Tags.forName(tagName);
			try { value = contents(ds, tag); }
			catch (Exception e) { value = null; };
		}
		if (value == null) value = "";
		return value;
	}

	//Get the contents of a dataset element by tagName, returning null if it is missing
	private static String contentsNull(Dataset ds, String tagName, int defaultTag) {
		if (tagName == null) return null;
		tagName = tagName.trim();
		if (tagName.equals("")) return null;
		String value = "";
		int tag = tagName.equals("this") ? defaultTag : Tags.forName(tagName);
		try { value = contents(ds, tag); }
		catch (Exception e) { return null; };
		return value;
	}

	//Get the contents of a dataset element by tag, handling
	//CTP elements specially.
	private static String contents(Dataset ds, int tag) throws Exception {
		boolean ctp = false;
		if (((tag & 0x00010000) != 0) && ((tag & 0x0000ff00) != 0)) {
			int blk = (tag & 0xffff0000) | ((tag & 0x0000ff00) >> 8);
			try { ctp = ds.getString(blk).equals("CTP"); }
			catch (Exception notCTP) { ctp = false; }
		}
		DcmElement el = ds.get(tag);
		if (el == null) throw new Exception(Tags.toString(tag) + " missing");

		if (ctp) return new String(ds.getByteBuffer(tag).array());

		SpecificCharacterSet cs = ds.getSpecificCharacterSet();
		String[] s = el.getStrings(cs);
		if (s.length == 1) return s[0];
		if (s.length == 0) return "";
		StringBuffer sb = new StringBuffer( s[0] );
		for (int i=1; i<s.length; i++) {
			sb.append( "\\" + s[i] );
		}
		return sb.toString();
	}


	//Execute the param function call
	private static String param(FnCall fn) {
		return getParam(fn.cmds,fn.args[0]);
	}

	//Get the value of a parameter identified by a function call argument
	private static String getParam(FnCall fn) {
		return getParam(fn.cmds,fn.args[0]);
	}

	//Get the value of a parameter from the script
	private static String getParam(Properties cmds, String param) {
		param = param.trim();
		if (!param.equals("") && (param.charAt(0) == functionChar)) {
			param = (String)cmds.getProperty("param." + param.substring(1));
		}
		return param;
	}

	//Execute the require function. This function checks whether the
	//specified element is present. If it is, it leaves the element alone;
	//otherwise, it inserts an element with the specified value.
	private static String require(FnCall fn) {
		//Return a @keep() call if the element is present
		if (fn.ds.contains(fn.thisTag)) return "@keep()";

		//The element was not present, return a value for a new element
		//If there are no arguments, return an empty string.
		if (fn.args.length == 0) return "";

		//There are some arguments, get the element tag
		//and see if it is in the dataset.
		String value = null;
		int tag = fn.getTag(fn.args[0]);
		if (fn.ds.contains(tag)) {
			//It is, get the value
			try { value = fn.ds.getString(tag); }
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
		try {
			String key = contents(fn.ds,fn.args[0],fn.thisTag);
			String value = AnonymizerFunctions.lookup(fn.lkup, fn.args[1], key);
			return value;
		}
		catch (Exception ex) {
			if (fn.args.length > 2) {
				String action = fn.getArg(2).trim();
				if (action.equals("keep")) return "@keep()";
				if (action.equals("remove")) return "@remove()";
				if (action.equals("empty")) return "@empty()";
			}
			throw new Exception("!quarantine! - "+ex.getMessage());
		}
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
			String text = contents(fn.ds, fn.args[0], fn.thisTag);
			String keyType = fn.args[1];
			int width = 0;
			if (fn.args.length > 2) {
				try { width = Integer.parseInt(fn.args[2]); }
				catch (Exception useDefault) { }
			}
			return AnonymizerFunctions.integer(fn.intTable, keyType, text, width);
		}
		catch (Exception ex) {
			throw new Exception("!quarantine! - "+ex.getMessage());
		}
	}

	//Execute the initials function. This function is typically used
	//to generate the initials of a patient from the contents of the
	//PatientName element.
	private static String initials(FnCall fn) {
		String s = contents(fn.ds,fn.args[0],fn.thisTag);
		return AnonymizerFunctions.initials(s);
	}

	//Execute the hashname function. This function is typically used
	//to generate a numeric value from the contents of the PatientName element.
	private static String hashname(FnCall fn) {
		try {
			String string = contents(fn.ds,fn.args[0],fn.thisTag);

			String lengthString = getParam(fn.cmds,fn.args[1]);
			int length;
			try { length = Integer.parseInt(lengthString); }
			catch (Exception ex) { length = 4; }

			int wordCount = Integer.MAX_VALUE;
			if (fn.args.length > 2) {
				String wordCountString = getParam(fn.cmds,fn.args[2]);
				try { wordCount = Integer.parseInt(wordCountString); }
				catch (Exception keepDefault) { wordCount = Integer.MAX_VALUE; }
			}
			return AnonymizerFunctions.hashName(string, length, wordCount);
		}
		catch (Exception ex) {
			logger.warn("Exception in hashname"+fn.getArgs()+": "+ex.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the round function call. This function is used to
	//round an age field into groups of a given size.
	private static String round(FnCall fn) {
		//arg must contain: AgeElementName, groupSize
		try {
			String ageString = contents(fn.ds,fn.args[0],fn.thisTag);
			String sizeString = getParam(fn.cmds,fn.args[1]);
			int size = Integer.parseInt(sizeString);
			return AnonymizerFunctions.round(ageString,size);
		}
		catch (Exception e) {
			logger.warn("Exception caught in round"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the hashptid function call.
	private static String hashptid(FnCall fn) {
		//args must contain: siteid, elementname, and optionally maxlen
		try {
			if (fn.args.length < 2) return fn.getArgs();
			String siteid = getParam(fn.cmds,fn.args[0]);
			String ptid = contents(fn.ds,fn.args[1],fn.thisTag);
			if (ptid == null) ptid = "null";
			String maxlenString = "";
			if (fn.args.length >= 3) maxlenString = getParam(fn.cmds,fn.args[2]);
			int maxlen = Integer.MAX_VALUE;
			try { maxlen = Integer.parseInt(maxlenString); }
			catch (Exception ex) { maxlen = Integer.MAX_VALUE; }
			return AnonymizerFunctions.hashPtID(siteid,ptid,maxlen);
		}
		catch (Exception e) {
			logger.warn("Exception caught in hashptid"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the hash function call. This function is used
	//to generate an MD5 hash of any element text, with the
	//result being a base-10 digit string.
	private static String hash(FnCall fn) {
		String remove = "@remove()";
		try {
			String value = contentsNull(fn.ds,fn.args[0],fn.thisTag);
			if (value == null) return remove;
			int len = Integer.MAX_VALUE;
			if (fn.args.length > 1) {
				String lenString = getParam(fn.cmds,fn.args[1]);
				if (lenString.length() != 0) {
					try { len = Integer.parseInt(lenString); }
					catch (Exception ex) { len = Integer.MAX_VALUE; }
				}
			}
			return AnonymizerFunctions.hash(value,len);
		}
		catch (Exception e) {
			logger.warn("Exception caught in hash"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the incrementdate function call.
	//Get a new date by adding a constant to an date value.
	private static String incrementdate(FnCall fn) {
		//arg must contain: DateElementName, increment
		//DateElementName is the name of the element containing the date to be incremented.
		//increment specifies the number of days to add to the date (positive generates
		//later dates; negative generates earlier dates.
		String emptyDate = "@empty()";
		String removeDate = "@remove()";
		try {
			String date = contentsNull(fn.ds,fn.args[0],fn.thisTag);
			if (date == null) return removeDate;
			String incString = getParam(fn.cmds,fn.args[1]);
			long inc = Long.parseLong(incString);
			return AnonymizerFunctions.incrementDate(date,inc);
		}
		catch (Exception e) {
			logger.debug("Exception caught in incrementdate"+fn.getArgs()+": "+e.getMessage());
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
			String date = contentsNull(fn.ds,fn.args[0],fn.thisTag);
			if (date == null) return removeDate;
			int y = getReplacementValue(getParam(fn.cmds,fn.args[1]).trim());
			int m = getReplacementValue(getParam(fn.cmds,fn.args[2]).trim());
			int d = getReplacementValue(getParam(fn.cmds,fn.args[3]).trim());
			return AnonymizerFunctions.modifyDate(date, y, m, d);
		}
		catch (Exception e) {
			logger.debug("Exception caught in modifydate"+fn.getArgs()+": "+e.getMessage());
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
	private static String hashuid(FnCall fn) {
		String removeUID = "@remove()";
		try {
			if (fn.args.length != 2) return fn.getArgs();
			String prefix = getParam(fn);
			String uid = contentsNull(fn.ds,fn.args[1],fn.thisTag);
			//If there is no UID in the dataset, then return @remove().
			if (uid == null) return removeUID;
			//Make sure the prefix ends in a period
			if (!prefix.endsWith(".")) prefix += ".";
			//Create the replacement UID
			return AnonymizerFunctions.hashUID(prefix,uid);
		}
		catch (Exception e) {
			logger.warn("Exception caught in hashuid"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	//Execute the encrypt function call. This function is used
	//to generate an encrypted string from an element text value.
	private static String encrypt(FnCall fn) {
		if (fn.args.length < 2) return fn.getArgs();
		try {
			String value = contents(fn.ds,fn.args[0],fn.thisTag);
			String key = getParam(fn.cmds,fn.args[1]);
			return AnonymizerFunctions.encrypt(value,key);
		}
		catch (Exception e) {
			logger.warn("Exception caught in encipher"+fn.getArgs()+": "+e.getMessage());
			return fn.getArgs();
		}
	}

	private static String time(FnCall fnCall) {
		return AnonymizerFunctions.time(fnCall.getArg(0));
	}

	private static String date(FnCall fnCall) {
		return AnonymizerFunctions.date(fnCall.getArg(0));
	}

//********************** End of function calls ***************************

	//Remove and modify elements in the dataset.
	private static String doOverwrite(
			Dataset ds,
			Properties theReplacements,
				// These global flags deal with groups of elements.
				// If anything appears in theReplacements for an element,
				// it overrides the action of the global flags.
				// A global keep flag overrides a global remove flag.
			boolean rpg,	//remove private groups
			boolean rue,	//remove unspecified elements
			boolean rol,	//remove overlay groups
			boolean rc,		//remove curve groups
			int[] keepGroups
			) throws Exception {
		String exceptions = "";
		String name;
		String value;

		//If we are removing anything globally, then go through the dataset
		//and look at each element individually.
		if (rpg || rue || rol) {
			//Make a list of the elements to remove
			LinkedList<Integer> list = new LinkedList<Integer>();
			for (Iterator it=ds.iterator(); it.hasNext(); ) {
				DcmElement el = (DcmElement)it.next();
				int tag = el.tag();
				int group = tag & 0xFFFF0000;
				boolean overlay = ((group & 0xFF000000) == 0x60000000);
				boolean curve = ((group & 0xFF000000) == 0x50000000);
				if (rpg && ((tag & 0x10000) != 0)) {
					if (theReplacements.getProperty(Tags.toString(tag).toLowerCase()) == null) {
						if (Arrays.binarySearch(keepGroups,group) < 0) {
							list.add(new Integer(tag));
						}
					}
				}
				if (rue) {
					if (theReplacements.getProperty(Tags.toString(tag).toLowerCase()) == null) {
						boolean keep  = (Arrays.binarySearch(keepGroups,group) >= 0) ||
									    (tag == 0x00080016)   || 	//SopClassUID
										(tag == 0x00080018)   || 	//SopInstanceUID
										(tag == 0x0020000D)   ||	//StudyInstanceUID
										(group == 0x00020000) ||	//FMI group
										(group == 0x00280000) ||	//the image description
										(group == 0x7FE00000) ||	//the image
										(overlay && !rol)     ||	//overlays
										(curve && !rc);				//curves
						if (!keep) list.add(new Integer(tag));
					}
				}
				if (rol && overlay) list.add(new Integer(tag));
			}
			//Okay, now remove them
			Iterator it = list.iterator();
			while (it.hasNext()) {
				Integer tagInteger = (Integer)it.next();
				int tag = tagInteger.intValue();
				try { ds.remove(tag); }
				catch (Exception ignore) {
					logger.debug("Unable to remove "+tag+" from dataset.");
				}
			}
		}

		//Now go through theReplacements and handle the instructions there
		for (Enumeration it = theReplacements.keys(); it.hasMoreElements(); ) {
			String key = (String) it.nextElement();
			int tag = getTagInt(key);
			value = (String)theReplacements.getProperty(key);
			value = (value != null) ? value.trim() : "";
			if (value.equals("") || value.contains("@remove()")) {
				try { ds.remove(tag); }
				catch (Exception ignore) {
					logger.debug("Unable to remove "+tag+" from dataset.");
				}
			}
			else if (value.equals("@keep()")) ; //@keep() leaves the element in place
			else if (value.startsWith("@blank(")) {
				//@blank(n) inserts an element with n blank chars
				String blanks = "                                                       ";
				String nString = value.substring("@blank(".length());
				int paren = nString.indexOf(")");
				int n = 0;
				if (paren != -1) {
					nString = "0" + nString.substring(0,paren).replaceAll("\\D","");
					n = Integer.parseInt(nString);
				}
				if (n > blanks.length()) n = blanks.length();
				try { putXX(ds,tag,getVR(tag),blanks.substring(0,n)); }
				catch (Exception e) {
					logger.warn(key + " exception: " + e.toString());
					if (!exceptions.equals("")) exceptions += ", ";
					exceptions += key;
				}
			}
			else {
				try {
					if (value.equals("@empty()")) value = "";
					putXX(ds,tag,getVR(tag),value);
				}
				catch (Exception e) {
					logger.warn(key + " exception:\n" + e.toString()
								+ "\ntag=" + Integer.toHexString(tag)
								+ ": value= \"" + value + "\"");
					if (!exceptions.equals("")) exceptions += ", ";
					exceptions += key;
				}
			}
		}
		return exceptions;
	}

	private static int getVR(int tag) {
		TagDictionary.Entry entry = tagDictionary.lookup(tag);
		try { return VRs.valueOf(entry.vr); }
		catch (Exception ex) { return VRs.valueOf("SH"); }
	}

	//This method works around the bug in dcm4che which inserts the wrong
	//VR (SH) when storing an empty element of VR = PN. It also handles the
	//problem in older dcm4che versions which threw an exception when an
	//empty DA element was created. It also forces the VR of private
	//elements to UN unless they are in the private creator block, in which
	//case they are LO. And finally, it handles multi-valued elements.
	private static void putXX(Dataset ds, int tag, int vr, String value) throws Exception {
		if ((value == null) || value.equals("")) {
			if (vr == VRs.PN) ds.putXX(tag,vr," ");
			else ds.putXX(tag,vr);
		}
		else if ((tag & 0x10000) != 0) {
			if ((tag & 0xffff) < 0x100) ds.putLO(tag,value);
			else ds.putUN(tag,value.getBytes("UTF-8"));
		}
		else {
			//Do this in such a way that we handle multivalued elements.
			String[] s = value.split("\\\\");
			ds.putXX(tag,vr,s);
		}
	}

}
