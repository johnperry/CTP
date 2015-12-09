/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.security.*;
import java.util.Arrays;
import java.util.Calendar;

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

import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;

import org.apache.log4j.Logger;

/**
 * The DICOMMammoPixelAnonymizer attempts to find PHI burned into
 * a rectangle on either the left or right side of mammography
 * images and blanks it out.
 */
public class DICOMMammoPixelAnonymizer {

	static final Logger logger = Logger.getLogger(DICOMMammoPixelAnonymizer.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

   /**
     * Convert an image to PlanarConfiguration 0.
     * @param inFile the file to anonymize.
     * @param outFile the output file, which may be same as inFile you if want
     * to anonymize in place.
     * @return the static status result
     */
    public static AnonymizerStatus anonymize(File inFile, File outFile) {

		long fileLength = inFile.length();
		logger.debug("Entering DICOMPlanarConfigurationConverter.convert");
		logger.debug("File length       = "+fileLength);

		BufferedInputStream in = null;
		FileOutputStream out = null;
		File tempFile = null;
		byte[] buffer = new byte[4096];
		try {
			//Check that this is a known format.
			in = new BufferedInputStream(new FileInputStream(inFile));
			DcmParser parser = pFact.newDcmParser(in);
			FileFormat fileFormat = parser.detectFileFormat();
			if (fileFormat == null) {
				throw new IOException("Unrecognized file format: "+inFile);
			}

			//Get the dataset (including the pixels), but skip everything after the pixels
			Dataset dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			parser.parseDcmFile(fileFormat, 0x7FE10000);

			//Get the required parameters and make sure they are okay
			int numberOfFrames = getInt(dataset, Tags.NumberOfFrames, 1);
			int rows = getInt(dataset, Tags.Rows, 0);
			int columns = getInt(dataset, Tags.Columns, 0);
			int bitsAllocated = getInt(dataset, Tags.BitsAllocated, 16);
			int samplesPerPixel = getInt(dataset, Tags.SamplesPerPixel, 1);
			int planarConfiguration = getInt(dataset, Tags.PlanarConfiguration, 0);
			String imageLaterality = getString(dataset, Tags.ImageLaterality, "");

			if ((rows == 0) || (columns == 0)) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unable to get the rows and columns");
			}
			if (samplesPerPixel != 1) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported SamplesPerPixel: "+samplesPerPixel);
			}
			if (bitsAllocated != 16) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported BitsAllocated: "+bitsAllocated);
			}
			if (planarConfiguration != 0) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported PlanarConfiguration: "+planarConfiguration);
			}

			//Set the encoding
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
        	String prefEncodingUID = UIDs.ExplicitVRLittleEndian;
			FileMetaInfo fmi = dataset.getFileMetaInfo();
            if (fmi != null) prefEncodingUID = fmi.getTransferSyntaxUID();
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(prefEncodingUID);
			boolean swap = fileParam.byteOrder != encoding.byteOrder;

			//Save the dataset to a temporary file, and rename at the end.
			File tempDir = outFile.getParentFile();
			tempFile = File.createTempFile("DCMtemp-", ".anon", tempDir);
            out = new FileOutputStream(tempFile);

            //Create and write the metainfo for the encoding we are using
			fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

			//Modify the pixels
			processPixels(dataset, rows, columns, imageLaterality);

			//Write the dataset
			dataset.writeDataset(out, encoding);

			//Done
			out.flush();
			out.close();
			in.close();
			outFile.delete();
			tempFile.renameTo(outFile);
			return AnonymizerStatus.OK(outFile,"");
		}

		catch (Exception e) {
			logger.debug("Exception while processing image.",e);

			//Close the input stream if it actually got opened.
			close(in);

			//Close the output stream if it actually got opened,
			//and delete the tempFile in case it is still there.
			try {
				if (out != null) {
					out.close();
					tempFile.delete();
				}
			}
			catch (Exception ex) {
				logger.warn("Unable to close the output stream.");
			}

			//Quarantine the object
			return AnonymizerStatus.QUARANTINE(inFile, e.getMessage());
		}
    }

    private static void close(InputStream in) {
		try { if (in != null) in.close(); }
		catch (Exception ex) {
			logger.warn("Unable to close the input stream.");
		}
	}

    private static int getInt(Dataset ds, int tag, int defaultValue) {
		try { return ds.getInteger(tag).intValue(); }
		catch (Exception ex) { return defaultValue; }
	}

    private static String getString(Dataset ds, int tag, String defaultValue) {
		try { return ds.getString(tag); }
		catch (Exception ex) { return defaultValue; }
	}

    private static void processPixels(Dataset dataset, int rows, int columns, String laterality) {

		if (logger.isDebugEnabled()) {
			logger.info("...rows = "+rows);
			logger.info("...cols = "+columns);
			logger.info("...laterality: "+laterality);
		}

		DcmElement de = dataset.get(Tags.PixelData);
		if (de == null) return;
		int len = de.length();
		ByteBuffer bb = de.getByteBuffer();
		ShortBuffer sb = bb.asShortBuffer();

		int[] sum = new int[columns];
		Arrays.fill(sum, 0);

		int nrows = 20;
		int rStart = rows/2 - nrows/2;
		int rEnd = rStart + nrows;
		for (int r=rStart; r<rEnd; r++) {
			int rIndex = r*columns;
			for (int k=0; k<columns; k++) {
				sum[k] += 0xFFFF & (int)sb.get(rIndex + k);
			}
		}

		if (logger.isDebugEnabled()) {
			logger.info("--------------");
			for (int k=0; k<sum.length; k+=100) {
				logger.info("sum["+k+"] = "+sum[k]);
			}
			logger.info("--------------");
			for (int k=1250; k<1300; k++) {
				logger.info("sum["+k+"] = "+sum[k]);
			}
			logger.info("--------------");
		}

		int kmin = findMinIndex(sum);
		int vmin = sum[kmin];
		int leftMax = findMax(sum, 0, kmin);
		int left = findLimit(sum, kmin, 0, (leftMax+vmin)/2);
		int rightMax = findMax(sum, kmin, sum.length);
		int right = findLimit(sum, kmin, sum.length, (rightMax+vmin)/2);

		int w = columns / 8;
		int middle = sum.length / 2;

		if (logger.isDebugEnabled()) {
			logger.info("...kmin = "+kmin);
			logger.info("...vmin = "+vmin);
			logger.info("...leftMax  = "+leftMax);
			logger.info("...rightMax = "+rightMax);
			logger.info("...left     = "+left);
			logger.info("...right    = "+right);
		}

		if (Math.abs(left - middle) < Math.abs(right - middle)) {
			//left
			logger.debug("...detected left laterality");
			blank(sb, columns-w, w, rows, columns);
		}
		else {
			//right
			logger.debug("...detected right laterality");
			blank(sb, 0, w, rows, columns);
		}

	}

	private static int findMinIndex(int[] v) {
		int kmin = 0;
		int vmin = Integer.MAX_VALUE;
		int len = v.length;
		int skip = len/5;
		for (int k=skip; k<len-skip; k++) {
			if (v[k] < vmin) {
				vmin = v[k];
				kmin = k;
			}
		}
		return kmin;
	}

	private static int findMax(int[] v, int k1, int k2) {
		int vmax = 0;
		for (int k=k1; k<k2; k++) {
			if (v[k] > vmax) vmax = v[k];
		}
		return vmax;
	}

	private static int findLimit(int[] v, int k1, int k2, int val) {
		int inc = (k1 < k2) ? 1 : -1;
		int k = k1;
		while ( (k != k2) && (k >= 0) && (k < v.length)) {
			if (v[k] > val) return k;
			k += inc;
		}
		return 0;
	}

	private static void blank(ShortBuffer sb, int c, int w, int rows, int columns) {
		if (logger.isDebugEnabled()) {
			logger.info("...blank c = "+c);
			logger.info("...blank w = "+w);
		}
		for (int r=0; r<rows; r++) {
			int rIndex = r*columns;
			for (int k=0; k<w; k++) sb.put(rIndex + c + k, (short)2000);
		}
	}
}
