/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.awt.Rectangle;
import java.awt.Shape;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.security.*;
import java.util.Vector;

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

import com.pixelmed.codec.jpeg.*;

import org.rsna.ctp.stdstages.anonymizer.AnonymizerFunctions;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;

import org.apache.log4j.Logger;

/**
 * The CTP DICOM pixel anonymizer. The anonymizer blanks regions of
 * DICOM objects for clinical trials.
 */
public class DICOMPixelAnonymizer {

	static final String JPEGBaseline = "1.2.840.10008.1.2.4.50";

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
     * @param setBurnedInAnnotation true set the BurnedInAnnotation element to NO
     * if the object is processed.
     * @param test true to highlight blanked regions; false to render them in black.
     * @return the static status result
     */
    public static AnonymizerStatus anonymize(
			File inFile,
			File outFile,
			Regions regions,
			boolean setBurnedInAnnotation,
			boolean test) {

		long fileLength = inFile.length();
		logger.debug("Entering DICOMPixelAnonymizer.anonymize");
		logger.debug("File length = "+fileLength);

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
			logger.debug("Stream position after parsing up to the Pixels element: "
								+parser.getStreamPosition()+" ["+Long.toHexString(parser.getStreamPosition())+"]");

			FileMetaInfo fmi = dataset.getFileMetaInfo();
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();

			//Make sure this is an image
            if (parser.getReadTag() != Tags.PixelData) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Not an image");
			}

			//Make sure the encoding is supported
			if (fmi != null) {
				String transferSyntaxUID = fmi.getTransferSyntaxUID();
				DcmEncodeParam encoding = DcmEncodeParam.valueOf(transferSyntaxUID);
				if (encoding.encapsulated && !transferSyntaxUID.equals(JPEGBaseline)) {
					close(in);
					logger.debug("Unsupported TransferSyntaxUID: "+transferSyntaxUID);
					return AnonymizerStatus.SKIP(inFile, "Unsupported TransferSyntaxUID: "+transferSyntaxUID);
				}
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
				logger.debug("Unable to get the rows and columns");
				return AnonymizerStatus.SKIP(inFile, "Unable to get the rows and columns");
			}
			if ((bitsAllocated % 8) != 0) {
				close(in);
				logger.debug("Unsupported BitsAllocated: "+bitsAllocated);
 				return AnonymizerStatus.SKIP(inFile, "Unsupported BitsAllocated: "+bitsAllocated);
			}

			//Set the encoding
        	String prefEncodingUID = UIDs.ImplicitVRLittleEndian;
            if (fmi != null) prefEncodingUID = fmi.getTransferSyntaxUID();
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(prefEncodingUID);
			boolean swap = fileParam.byteOrder != encoding.byteOrder;

			//Save the dataset to a temporary file, and rename at the end.
			File tempDir = outFile.getParentFile();
			tempFile = File.createTempFile("DCMtemp-", ".anon", tempDir);
            out = new FileOutputStream(tempFile);

            //Create and write the metainfo for the encoding we are using
			logger.debug("About to create and write the metadata");
			fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

			//Set the BurnedInAnnotation element if necessary
			if (setBurnedInAnnotation) {
				dataset.putCS(Tags.BurnedInAnnotation, "NO");
			}

			//Write the dataset as far as was parsed
			logger.debug("About to write the dataset up to the pixels");
			dataset.writeDataset(out, encoding);
			
			//Process the pixels
			if (parser.getReadTag() == Tags.PixelData) {
				logger.debug("Processing the Pixels element: " + Integer.toHexString(parser.getReadTag()));
				logger.debug("Stream position before the Pixels element: "
								+parser.getStreamPosition()+" ["+Long.toHexString(parser.getStreamPosition())+"]");
				dataset.writeHeader(
					out,
					encoding,
					parser.getReadTag(),
					parser.getReadVR(),
					parser.getReadLength());
				logger.debug("Stream position after writing the Pixels element header: "
								+parser.getStreamPosition()+" ["+Long.toHexString(parser.getStreamPosition())+"]");
				if (!encoding.encapsulated) {
					//Handle the non-encapsulated case
					processUnencapsulatedPixels(parser,
												out,
												swap && (parser.getReadVR() == VRs.OW),
												numberOfFrames, samplesPerPixel, planarConfiguration, photometricInterpretation,
												rows, columns, bitsAllocated,
												regions, test);
					}
				else {
					//Handle the encapsulated case
					processEncapsulatedPixels(parser,
											  dataset,
											  out,
											  encoding,
											  regions);
				}
			}
			logger.debug("Finished writing the pixels");
			logger.debug("Stream position after processing the Pixels element: "
								+parser.getStreamPosition()+" ["+Long.toHexString(parser.getStreamPosition())+"]");

			
			boolean processPostPixels = true;
			if (processPostPixels) {
				try {
					if (parser.getStreamPosition() < fileLength) parser.parseHeader(); //get ready for the next element
					//Now do any elements after the pixels one at a time.
					//This is done to allow streaming of large raw data elements
					//that occur above Tags.PixelData.
					int tag;
					while (!parser.hasSeenEOF()
							&& (parser.getStreamPosition() < fileLength)
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
				}
				catch (Exception e) {
					//Log the Exception, but allow the process to finish up.
					logger.warn("Exception caught while processing post-pixels elements", e);
				}
			}
			
			logger.debug("Done");
			out.flush();
			out.close();
			in.close();
			outFile.delete();
			tempFile.renameTo(outFile);
			logger.debug("Returning AnonymizerStatus.OK");
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

	private static void processEncapsulatedPixels(
							DcmParser parser,
							Dataset dataset,
							OutputStream out,
							DcmEncodeParam encoding,
							Regions regions) throws Exception {

		logger.debug("Process Encapsulated Pixels:");

		int rows = getInt(dataset, Tags.Rows, 0);
		int columns = getInt(dataset, Tags.Columns, 0);
		Vector<Shape> shapes = regions.getRegionsVector(rows, columns);
		if (logger.isDebugEnabled()) {
			for (Shape shape : shapes) {
				Rectangle rect = shape.getBounds();
				logger.debug("Shape: "+rect.toString());
			}
		}

		//Skip the Basic Offset Table item and write an empty one
		parser.parseHeader();
		byte[] itemBytes = getItemBytes(parser);
		dataset.writeHeader(out, encoding, Tags.Item, VRs.NONE, 0);
		logger.debug("Wrote empty Basic OffsetTable item");

		int frameNumber = 0;

		//Process frames
		ByteArrayOutputStream frame = new ByteArrayOutputStream();
		parser.parseHeader();
		while (parser.getReadTag() == Tags.Item) {
			itemBytes = getItemBytes(parser);
			frame.write(itemBytes);
			if (isFrameEnd(itemBytes)) {
				//Process the frame
				ByteArrayInputStream inFrame = new ByteArrayInputStream(frame.toByteArray());
				ByteArrayOutputStream outFrame = new ByteArrayOutputStream();
				Parse.parse(inFrame, outFrame, shapes);

				//Pad the frame if necessary
				if ((outFrame.size() & 1) != 0) outFrame.write(0);

				//Write the frame
				int size = outFrame.size();
				dataset.writeHeader(out, encoding, Tags.Item, VRs.NONE, size);
				out.write(outFrame.toByteArray());
				logger.debug("Processed frame " + frameNumber++ + "; item length = " + size);

				//Reset for the next frame
				frame.reset();
			}
			parser.parseHeader();
		}

		//End the sequence
		dataset.writeHeader(out, encoding, Tags.SeqDelimitationItem, VRs.NONE, 0);
	}

	private static byte[] getItemBytes(DcmParser parser) throws Exception {
		if (parser.getReadTag() == Tags.Item) {
			int len = parser.getReadLength();
			if (len > 0) {
				InputStream in = parser.getInputStream();
				byte[] b = new byte[len];
				in.read(b, 0, len);
				parser.setStreamPosition(parser.getStreamPosition() + len);
				return b;
			}
		}
		return new byte[0];
	}

	private static boolean isFrameEnd(byte[] b) {
		int len = b.length;
		byte ff = (byte) 0xFF;
		byte d9 = (byte) 0xD9;
		if ( (b[len-2] == ff) && (b[len-1] == d9) ) return true;
		if ( (b[len-3] == ff) && (b[len-2] == d9) && (b[len-1] == 0) ) return true;
		return false;
	}

    private static void processUnencapsulatedPixels(
							DcmParser parser,
							OutputStream out,
							boolean swap,
							int numberOfFrames,
							int samplesPerPixel,
							int planarConfiguration,
							String photometricInterpretation,
							int rows,
							int columns,
							int bitsAllocated,
							Regions regions,
							boolean test) throws Exception {

		int len = parser.getReadLength();
		logger.debug("Process Unencapsulated Pixels:");
		logger.debug("Read length = "+len);

		String pi = photometricInterpretation.toUpperCase();
		boolean isYBR_FULL = pi.equals("YBR_FULL");
		boolean isYBR_FULL_422 = pi.equals("YBR_FULL_422");
		boolean isM1 = pi.equals("MONOCHROME1");
		boolean isM2 = pi.equals("MONOCHROME2");
		
		int bytesPerPixel = bitsAllocated/8;
		
		if ((isYBR_FULL_422) && (samplesPerPixel == 3)) {
			/*
			Apparently, in this case, if samplesPerPixel is 3, it is really 2.
			Downsampled chrominance planes of a color Photometric Interpretation 
			are a special case, e.g., for a Photometric Interpretation (0028,0004) 
			of YBR_FULL_422. In such cases, Samples per Pixel (0028,0002) describes 
			the nominal number of channels (i.e., 3), and does not reflect that two 
			chrominance samples are shared between two luminance samples. 
			For YBR_FULL_422, Rows (0028,0010) and Columns (0028,0011) describe
			the size of the luminance plane, not the downsampled chrominance planes.
			*/
			samplesPerPixel = 2;
		}

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
				//read the row
				int n = in.read(buffer, 0, buffer.length);
				if ((n == -1) || (n != bytesPerRow)) throw new EOFException("Unable to read all the pixels");
				
				//blank the regions for the row
				if (isYBR_FULL_422 && (planarConfiguration==0)) {
					byte y = (byte)(test ? 128 : 0);
					byte c = (byte)(test ? 128 : 128);
					blankRegions(buffer, row, rows, columns, bytesPerPixel, regions, y, c);
				}
				else if (isYBR_FULL && (planarConfiguration==0)) {
					//for now, do the same
					byte y = (byte)(test ? 128 : 0);
					byte c = (byte)(test ? 128 : 128);
					blankRegions(buffer, row, rows, columns, bytesPerPixel, regions, y, c);
				}
				else if (isYBR_FULL && (planarConfiguration==1)) {
					byte x = ((frame%3)==0) ? ((byte)(test ? 127 : 0)) : 127;
					blankRegions(buffer, row, rows, columns, bytesPerPixel, regions, x);					
				}
				else {
					byte x = (byte)(test ? 127 : 0);
					if (isM1 && (bytesPerPixel==2)) x = (byte)(test ? 8 : 15);
					else if (isM1 && (bytesPerPixel==1)) x = (byte)(test ? 127 : 255);
					else if (isM2 && (bytesPerPixel==2)) x = (byte)(test ? 8 : 0);
					blankRegions(buffer, row, rows, columns, bytesPerPixel, regions, x);
				}
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

	private static void blankRegions(byte[] bytes, int row, int rows, int columns, int bytesPerPixel, Regions regions, byte value) {
		int[] ranges = regions.getRangesFor(row, rows, columns);
		for (int i=0; i<ranges.length; i+=2) {
			int left = bytesPerPixel * ranges[i];
			int right = Math.min( bytesPerPixel * (ranges[i+1] + 1), bytes.length );
			for (int k=left; k<right; k++) bytes[k] = value;
		}
	}

	private static void blankRegions(byte[] bytes, int row, int rows, int columns, int bytesPerPixel, Regions regions, byte y, byte c) {
		int[] ranges = regions.getRangesFor(row, rows, columns);
		for (int i=0; i<ranges.length; i+=2) {
			int leftIndex = ranges[i] & 0xfffffffe; //make it even
			int rightIndex = (ranges[i+1] + 1) & 0xfffffffe;
			for (int k=leftIndex; k<rightIndex & k<columns-1; k+=2) {
				int x = bytesPerPixel * k;
				bytes[x++] = y;
				bytes[x++] = y;
				bytes[x++] = c;
				bytes[x++] = c;
			}
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
