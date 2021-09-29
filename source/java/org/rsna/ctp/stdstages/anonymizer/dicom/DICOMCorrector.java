/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Iterator;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.SpecificCharacterSet;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;

import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.PrivateTagIndex;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;

import org.apache.log4j.Logger;

/**
 * The MIRC DICOM corrector. The corrector fixes certain errors
 * in DICOM objects.
 */
public class DICOMCorrector {

	static final Logger logger = Logger.getLogger(DICOMCorrector.class);
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

   /**
     * Correct the VRs of UN elements that are defined in the dictionary as FD.
     * @param inFile the file to correct.
     * @param outFile the output file.  It may be same as inFile if you want
     * to anonymize in place.
     * @param fixPrivateElements true if private elements are to be corrected.
     * @param quarantineUncorrectedMismatches true if objects are to be
     * quarantined if any mismatches cannot be corrected.
     * @param logUncorrectedMismatches true if log entyries are to be
     * quarantined if any mismatch cannot be corrected.
     * @return the static status result
     */
    public static AnonymizerStatus correct(
			File inFile,
			File outFile,
			boolean fixPrivateElements,
			boolean quarantineUncorrectedMismatches,
			boolean logUncorrectedMismatches) {

		DicomObject dob = null;
		try {
			dob = new DicomObject(inFile, true); //leave the inFile open
			Dataset ds = dob.getDataset();
			SpecificCharacterSet scs = ds.getSpecificCharacterSet();

			boolean changed = correctDataset(inFile, ds, scs, 
											 fixPrivateElements,
											 quarantineUncorrectedMismatches,
											 logUncorrectedMismatches);

			if (!changed) {
				dob.close();
				return AnonymizerStatus.SKIP(inFile, "");
			}
			else {
				//Write the dataset to a temporary file in the same directory
				File tempDir = outFile.getParentFile();
				File tempFile = File.createTempFile("DCMtemp-", ".corrected", tempDir);
				dob.saveAs(tempFile, false);
				dob.close();

				//Rename the temp file to the specified outFile.
				outFile.delete();
				tempFile.renameTo(outFile);

				return AnonymizerStatus.OK(outFile, "");
			}
		}
		catch (Exception unable) {
			if (dob != null) dob.close();
			return AnonymizerStatus.QUARANTINE(inFile, "");
		}
	}

	private static boolean correctDataset(File inFile, Dataset ds, SpecificCharacterSet scs,
										  boolean fixPrivateElements,
										  boolean quarantineUncorrectedMismatches,
										  boolean logUncorrectedMismatches) throws Exception {
		boolean changed = false;
		PrivateTagIndex ptIndex = PrivateTagIndex.getInstance();
		for (Iterator it=ds.iterator(); it.hasNext(); ) {
			DcmElement el = (DcmElement)it.next();
			int tag = el.tag();
			boolean isPrivate = ((tag & 0x10000) != 0);
			TagDictionary.Entry entry = tagDictionary.lookup(tag);
			if ( ((entry != null) && entry.vr.equals("SQ")) 
					|| VRs.toString(el.vr()).equals("SQ")) {
				int i = 0;
				Dataset sq;
				while ((sq=el.getItem(i++)) != null) {
					changed |= correctDataset(inFile, sq, scs, 
											  fixPrivateElements,
											  quarantineUncorrectedMismatches,
											  logUncorrectedMismatches);
				}
			}
			else if ((entry != null) && !entry.vr.equals(VRs.toString(el.vr()))) {
				int len = el.length();
				try {
					if (entry.vr.equals("FD") && (len > 0) && ((len % 8) == 0)) {
						int vm = len/8;
						ByteBuffer bb = el.getByteBuffer();
						double[] dbl = new double[vm];
						for (int k=0; k<vm; k++) dbl[k] = bb.getDouble(8*k);
						ds.putFD(tag, dbl);
						changed = true;
					}
					else if (entry.vr.equals("FL") && (len > 0) && ((len % 4) == 0)) {
						int vm = len/4;
						ByteBuffer bb = el.getByteBuffer();
						float[] flt = new float[vm];
						for (int k=0; k<vm; k++) flt[k] = bb.getFloat(4*k);
						ds.putFL(tag, flt);
						changed = true;
					}
					else if (entry.vr.equals("CS")) {
						String str = "";
						if (len > 0) {
							ByteBuffer bb = el.getByteBuffer();
							byte[] bytes = bb.array();
							str = scs.decode(bytes);
						}
						ds.putCS(tag, str);
						changed = true;
					}
					else if (entry.vr.equals("LO")) {
						String str = "";
						if (len > 0) {
							ByteBuffer bb = el.getByteBuffer();
							byte[] bytes = bb.array();
							str = scs.decode(bytes);
						}
						ds.putLO(tag, str);
						changed = true;
					}
					else if (entry.vr.equals("SH")) {
						String str = "";
						if (len > 0) {
							ByteBuffer bb = el.getByteBuffer();
							byte[] bytes = bb.array();
							str = scs.decode(bytes);
						}
						ds.putSH(tag, str);
						changed = true;
					}
					else if (entry.vr.equals("UI")) {
						String str = "";
						if (len > 0) {
							ByteBuffer bb = el.getByteBuffer();
							byte[] bytes = bb.array();
							str = scs.decode(bytes);
							if (str.endsWith(".")) str = str.substring(0,str.length()-1);
						}
						ds.putUI(tag, str);
						changed = true;
					}
					else {
						if (logUncorrectedMismatches) {
							logger.info(inFile+": Mismatched VR for "+Tags.toString(tag)+" "+entry.vr+"/"+VRs.toString(el.vr())+" len="+len);
						}
						if (quarantineUncorrectedMismatches) throw new Exception("Uncorrectable VR for "+Tags.toString(tag));
					}
				}
				catch (Exception skip) { logger.warn("Unable to convert "+Tags.toString(tag), skip); }
			}
		}
		return changed;
	}

}
