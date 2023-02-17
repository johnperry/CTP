/*---------------------------------------------------------------
*  Copyright 2019 by the Radiological Society of North America
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
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteOrder;
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
 * The CTP DICOM PhotometricInterpretation Converter. This class has one
 * static method that converts YBR images to RGB.
 */
public class DICOMPhotometricInterpretationConverter {

	static final Logger logger = Logger.getLogger(DICOMPhotometricInterpretationConverter.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

   /**
     * Convert a YBR image to RGB.
     * @param inFile the file to convert.
     * @param outFile the output file, which may be same as inFile.
     * @return the static status result
     */
    public static AnonymizerStatus convert(File inFile, File outFile) {

		long fileLength = inFile.length();
		logger.debug("Entering DICOMPhotometricInterpretationConverter.convert");
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

			//Get the dataset (excluding pixels) and leave the input stream open
			Dataset dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			parser.parseDcmFile(fileFormat, Tags.PixelData);

			//Make sure this is an image
            if (parser.getReadTag() != Tags.PixelData) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Not an image");
			}

			//Get the required parameters and make sure they are okay
			int numberOfFrames = getInt(dataset, Tags.NumberOfFrames, 1);
			int rows = getInt(dataset, Tags.Rows, 0);
			int columns = getInt(dataset, Tags.Columns, 0);
			int bitsAllocated = getInt(dataset, Tags.BitsAllocated, 16);
			int samplesPerPixel = getInt(dataset, Tags.SamplesPerPixel, 1);
			int planarConfiguration = getInt(dataset, Tags.PlanarConfiguration, 0);
			String photometricInterpretation = getString(dataset, Tags.PhotometricInterpretation, "");
			if ((rows == 0) || (columns == 0)) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unable to get the rows and columns");
			}
			if (samplesPerPixel != 3) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported SamplesPerPixel: "+samplesPerPixel);
			}
			if (bitsAllocated != 8) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported BitsAllocated: "+bitsAllocated);
			}
			if (planarConfiguration != 0) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported PlanarConfiguration: "+planarConfiguration);
			}
			if (!photometricInterpretation.startsWith("YBR")) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported PhotometricInterpretation: "+photometricInterpretation);
			}

            if (parser.getReadTag() != Tags.PixelData) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "No pixels");
			}

			//Set the encoding
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
        	String prefEncodingUID = UIDs.ExplicitVRLittleEndian;
			FileMetaInfo fmi = dataset.getFileMetaInfo();
            //if (fmi != null) prefEncodingUID = fmi.getTransferSyntaxUID();
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(prefEncodingUID);
			boolean swap = fileParam.byteOrder != encoding.byteOrder;

			if (encoding.encapsulated) {
				logger.debug("Encapsulated pixel data found");
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Encapsulated pixel data not supported");
			}

			//Set PlanarConfiguration to 0
			dataset.putCS(Tags.PhotometricInterpretation, "RGB");

			//Save the dataset to a temporary file, and rename at the end.
			File tempDir = outFile.getParentFile();
			tempFile = File.createTempFile("DCMtemp-", ".anon", tempDir);
            out = new FileOutputStream(tempFile);

            //Create and write the metainfo for the encoding we are using
			fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

			//Write the dataset as far as was parsed
			dataset.writeDataset(out, encoding);

			//Note: we are guaranteed that the number of columns is even, so
			//everything else is even, and we won't have to pad.
			int numberOfPixels = numberOfFrames * rows * columns;
			int numberOfBytes = 3 * numberOfPixels;
			
			//Process the pixels
			dataset.writeHeader(
				out,
				encoding,
				parser.getReadTag(),
				parser.getReadVR(),
				numberOfBytes);
			processPixels(parser, out, numberOfFrames, rows, columns);
			logger.debug("Finished writing the pixels");

			 //Get ready for the next element
			logger.debug("Stream position after processPixels = "+parser.getStreamPosition());
			if (parser.getStreamPosition() < fileLength) parser.parseHeader();

			//Now do any elements after the pixels one at a time.
			//This is done to allow streaming of large raw data elements
			//that occur above Tags.PixelData.
			int tag;
			while (!parser.hasSeenEOF()
//					&& (parser.getStreamPosition() < fileLength)
						&& ((tag=parser.getReadTag()) != -1)
							&& (tag != 0xFFFAFFFA)
							&& (tag != 0xFFFCFFFC)) {
				logger.debug("About to write post-pixels element "+Integer.toHexString(tag));
				dataset.writeHeader(
					out,
					encoding,
					parser.getReadTag(),
					parser.getReadVR(),
					parser.getReadLength());
				writeValueTo(parser, buffer, out, swap);
				parser.parseHeader();
			}
			logger.debug("Finished writing the post-pixels elements");
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

    private static void processPixels(
							DcmParser parser,
							OutputStream out,
							int numberOfFrames,
							int rows,
							int columns) throws Exception {

		InputStream in = parser.getInputStream();
		byte[] ybrFrame = new byte[rows * columns * 2];
		byte[] rgbFrame = new byte[rows * columns * 3];
		for (int frame=0; frame<numberOfFrames; frame++) {
			getYBRFrame(in, ybrFrame);
			int ybrIndex = 0;
			int rgbIndex = 0;
			for (int row=0; row<rows; row++) {
				for (int col=0; col<columns; col+=2) {
					int y1 = 0xff & ybrFrame[ybrIndex++];
					int y2 = 0xff & ybrFrame[ybrIndex++];
					int cb = 0xff & ybrFrame[ybrIndex++];
					int cr = 0xff & ybrFrame[ybrIndex++];
					getRGB(rgbFrame, rgbIndex, y1, cb, cr);
					rgbIndex += 3;
					getRGB(rgbFrame, rgbIndex, y2, cb, cr);
					rgbIndex += 3;					
				}
			}
			out.write(rgbFrame, 0, rgbFrame.length);
		}
	}

	private static void getYBRFrame(InputStream in, byte[] buffer) throws Exception {
		int c = in.read(buffer, 0, buffer.length);
		if ((c == -1) || (c != buffer.length)) throw new EOFException("Read error");
	}
	
	static double[][] ybrToRGB = {
		{ 1.0, 0.0, 1.402, -179.4497 },
		{ 1.0, -0.3441, -0.7141, 135.4518 },
		{ 1.0, 1.7720, -0.0001, -226.7960 },
		{ 0.0, 0.0, 0.0, 1.0 }
	};
	
	static int count=20;
	private static void getRGB(byte[] rgb, int index, int y, int cb, int cr) {
		double[] d = new double[] { y, cb, cr, 1.0 };
		rgb[index++] = dot( ybrToRGB[0], d );
		rgb[index++] = dot( ybrToRGB[1], d );
		rgb[index++] = dot( ybrToRGB[2], d );
	}
	
	private static byte dot( double[] a, double[] b ) {
		double sum = 0;
		for (int i=0; i<a.length; i++) sum += a[i] * b[i];
		int sumInt = (int)sum;
		if (sumInt < 0) sumInt = 0;
		if (sumInt > 255) sumInt = 255;
		return (byte)sumInt;
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
				for (int i=0; i<len; ++i, ++i) {
					tmp = in.read();
					out.write(in.read());
					out.write(tmp);
				}
			} else {
				for (int i=0; i<len; ++i) {
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
					for (int i=0; i<c; ++i, ++i) {
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
