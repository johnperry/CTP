/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
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

import org.rsna.ctp.stdstages.anonymizer.AnonymizerFunctions;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;

import org.apache.log4j.Logger;

/**
 * The CTP DICOM pixel anonymizer. The anonymizer blanks regions of
 * DICOM objects for clinical trials.
 */
public class DICOMPixelAnonymizer {

	static final Logger logger = Logger.getLogger(DICOMPixelAnonymizer.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

   /**
     * Blanks the specified regions of the input file, writing the
     * result to the output file. The input and output files are allowed
     * to be the same.
     * @param inFile the file to anonymize.
     * @param outFile the output file, which may be same as inFile you if want
     * to anonymize in place.
     * @param regions the object containing the pixel areas to blank.
     * @return the static status result
     */
    public static AnonymizerStatus anonymize(
			File inFile,
			File outFile,
			Regions regions) {

		long fileLength = inFile.length();
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
			if ((rows == 0) || (columns == 0)) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unable to get the rows and columns");
			}
			if ((bitsAllocated % 8) != 0) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported BitsAllocated: "+bitsAllocated);
			}

			//Set the encoding
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
        	String prefEncodingUID = UIDs.ImplicitVRLittleEndian;
			FileMetaInfo fmi = dataset.getFileMetaInfo();
            if (fmi != null) prefEncodingUID = fmi.getTransferSyntaxUID();
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(prefEncodingUID);
			boolean swap = fileParam.byteOrder != encoding.byteOrder;

/**/		//While in development, abort on encapsulated pixel data
/**/		if (encoding.encapsulated) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Encapsulated pixel data not supported");
			}

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

			//Process the pixels
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
					processPixels(parser,
								  out,
								  swap && (parser.getReadVR() == VRs.OW),
								  numberOfFrames, samplesPerPixel, planarConfiguration,
								  rows, columns, bitsAllocated,
								  regions);
					logger.debug("Stream position after processPixels = "+parser.getStreamPosition());
                }
				if (parser.getStreamPosition() < fileLength) parser.parseHeader(); //get ready for the next element
			}
			//Now do any elements after the pixels one at a time.
			//This is done to allow streaming of large raw data elements
			//that occur above Tags.PixelData.
			int tag;
			while (!parser.hasSeenEOF()
					&& (parser.getStreamPosition() < fileLength)
						&& ((tag=parser.getReadTag()) != -1)
							&& (tag != 0xFFFCFFFC)) {
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

    private static void processPixels(
							DcmParser parser,
							OutputStream out,
							boolean swap,
							int numberOfFrames,
							int samplesPerPixel,
							int planarConfiguration,
							int rows,
							int columns,
							int bitsAllocated,
							Regions regions) throws Exception {

		int len = parser.getReadLength();
		logger.debug("Read length       = "+len);

		int bytesPerPixel = bitsAllocated/8;

		if (planarConfiguration == 0) {
			//MONOCHROME2 or RGB images arranged as RGBRGBRGB...
			//Here, we treat each frame as a single frame with
			//pixels which are samplesPerPixel wide.
			bytesPerPixel *= samplesPerPixel;
		}
		else {
			//RGB images arranged in three layers, as RRR...GGG...BBB...
			//Here, we treat each frame as samplesPerPixel subframes,
			//one for each sample, with each pixel in a subframe being
			//a single sample wide.
			numberOfFrames *= samplesPerPixel;
		}

		int bytesPerRow = bytesPerPixel * columns;
		byte[] buffer = new byte[bytesPerRow];
		InputStream in = parser.getInputStream();
		for (int frame=0; frame<numberOfFrames; frame++) {
			for (int row=0; row<rows; row++) {
				int c = in.read(buffer, 0, buffer.length);
				if ((c == -1) || (c != bytesPerRow)) throw new EOFException("Unable to read all the pixels");
				blankRegions(buffer, row, columns, bytesPerPixel, regions);
				if (swap) swapBytes(buffer);
				out.write(buffer, 0, bytesPerRow);
			}
		}
		//Add a byte to the end if we have written an odd number of bytes
		long nbytes = numberOfFrames * rows * bytesPerRow;
		logger.debug("numberOfFrames    = "+numberOfFrames);
		logger.debug("rows              = "+rows);
		logger.debug("columns           = "+columns);
		logger.debug("bytesPerPixel     = "+bytesPerPixel);
		logger.debug("Total image bytes = "+nbytes);
		if ((nbytes & 1) != 0) out.write(0);

		parser.setStreamPosition(parser.getStreamPosition() + len);
	}

	private static void blankRegions(byte[] bytes, int row, int columns, int bytesPerPixel, Regions regions) {
		int[] ranges = regions.getRangesFor(row);
		for (int i=0; i<ranges.length; i+=2) {
			int left = bytesPerPixel * ranges[i];
			int right = Math.min( bytesPerPixel * (ranges[i+1] + 1), bytes.length );
			for (int k=left; k<right; k++) bytes[k] = 0;
		}
	}

	private static void swapBytes(byte[] bytes) {
		int len = bytes.length & 0xffffFFFE;
		byte b;
		for (int i=0; i<len; i+=2) {
			b = bytes[i];
			bytes[i] = bytes[i+1];
			bytes[i+1] = b;
		}
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
