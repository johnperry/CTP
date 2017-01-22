/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.awt.image.*;
import java.awt.geom.*;
import java.awt.Graphics2D;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.*;
import javax.imageio.stream.*;

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
 * The CTP DICOM Palette Image Converter. This class has one
 * static method that converts PALETTE COLOR images to RGB.
 */
public class DICOMPaletteImageConverter {

	static final Logger logger = Logger.getLogger(DICOMPaletteImageConverter.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

   /**
     * Convert an image to RGB.
     * @param inFile the file to convert.
     * @param outFile the output file, which may be same as inFile.
     * @return the static status result
     */
    public static AnonymizerStatus convert(File inFile, File outFile) {

		long fileLength = inFile.length();
		logger.debug("Entering DICOMPaletteImageConverter.convert");
		logger.debug("File length       = "+fileLength);

		BufferedInputStream in = null;
		BufferedOutputStream out = null;
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
			String photometricInterpretation = getString(dataset, Tags.PhotometricInterpretation, "");
			if ((rows == 0) || (columns == 0)) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unable to get the rows and columns");
			}
			if (!photometricInterpretation.equals("PALETTE COLOR")) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported PhotometricInterpretation: "+photometricInterpretation);
			}
            if (parser.getReadTag() != Tags.PixelData) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "No pixels");
			}


			//Get the encoding
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
			if (fileParam.encapsulated) {
				logger.debug("Encapsulated pixel data found");
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Encapsulated pixel data not supported");
			}			
			
			//Set the parameters
        	String fileEncodingUID = UIDs.ImplicitVRLittleEndian;
			FileMetaInfo fmi = dataset.getFileMetaInfo();
            if (fmi != null) fileEncodingUID = fmi.getTransferSyntaxUID();
            boolean isBigEndian = fileEncodingUID.equals(UIDs.ExplicitVRBigEndian);
            String encodingUID = UIDs.ExplicitVRLittleEndian;
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(encodingUID);
			boolean swap = (fileParam.byteOrder != encoding.byteOrder);

			//Get the LUTs
			LUT red = new LUT(dataset.getInts(Tags.RedPaletteColorLUTDescriptor),
							  dataset.getInts(Tags.RedPaletteColorLUTData));
			LUT green = new LUT(dataset.getInts(Tags.GreenPaletteColorLUTDescriptor),
							  dataset.getInts(Tags.GreenPaletteColorLUTData));
			LUT blue = new LUT(dataset.getInts(Tags.BluePaletteColorLUTDescriptor),
							  dataset.getInts(Tags.BluePaletteColorLUTData));

			//Set the PlanarConfiguration to 0
			dataset.putUS(Tags.PlanarConfiguration, 0);
			
			//Set the PhotometricInterpretation to RGB
			dataset.putCS(Tags.PhotometricInterpretation, "RGB");
			
			//Set the pixel parameters
			dataset.putUS(Tags.SamplesPerPixel, 3);
			dataset.putUS(Tags.BitsAllocated, 8);
			dataset.putUS(Tags.BitsStored, 8);
			dataset.putUS(Tags.HighBit, 7);			
			
			//Remove the lookup tables and their descriptors
			dataset.remove(Tags.RedPaletteColorLUTDescriptor);
			dataset.remove(Tags.GreenPaletteColorLUTDescriptor);
			dataset.remove(Tags.BluePaletteColorLUTDescriptor);
			dataset.remove(Tags.RedPaletteColorLUTData);
			dataset.remove(Tags.GreenPaletteColorLUTData);
			dataset.remove(Tags.BluePaletteColorLUTData);

			//Save the dataset to a temporary file, and rename at the end.
			File tempDir = outFile.getParentFile();
			tempFile = File.createTempFile("DCMtemp-", ".anon", tempDir);
            out = new BufferedOutputStream(new FileOutputStream(tempFile));

            //Create and write the metainfo for the encoding we are using
			fmi = oFact.newFileMetaInfo(dataset, encodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

			//Write the dataset as far as was parsed
			dataset.writeDataset(out, encoding);

			//Process the pixels
			int nPixels = numberOfFrames * rows * columns;
			int nPixelBytes = nPixels * 3 /*samplesPerPixel*/;
			int pad = nPixelBytes & 1;
			dataset.writeHeader(
				out,
				encoding,
				parser.getReadTag(),
				VRs.OB,
				nPixelBytes + pad);

			int pd;
			int b1,b2;
			int bytesPerFrame = rows * columns * 2;
			byte[] frameBytes = new byte[bytesPerFrame];
			for (int frame=0; frame<numberOfFrames; frame++) {
				if (in.read(frameBytes, 0, frameBytes.length) != bytesPerFrame) throw new Exception("End of File");
				for (int p=0; p<bytesPerFrame; ) {
					b1 = frameBytes[p++];
					b2 = frameBytes[p++];
					if (!swap) {
						pd = ((b2 & 0xff)<<8) | (b1 & 0xff);
					}
					else {
						pd = ((b1 & 0xff)<<8) | (b2 & 0xff);
					}
					out.write(red.get(pd));
					out.write(green.get(pd));
					out.write(blue.get(pd));
				}
			}
			if (pad != 0) out.write(0);			
			logger.debug("Finished writing the pixels");

			 //Skip everything after the pixels
			out.flush();
			out.close();
			in.close();
			outFile.delete();
			tempFile.renameTo(outFile);
			return AnonymizerStatus.OK(outFile,"");
		}

		catch (Exception e) {
			logger.debug("Exception while processing image.", e);

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
			logger.debug(e.getMessage());
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
	
	static class LUT {
		byte[] color;
		int nEntries;
		int firstValue;
		int nBits;
		
		public LUT(int[] desc, int[] data) {
			nEntries = desc[0];
			if (nEntries == 0) nEntries = 65536;
			firstValue = desc[1];
			nBits = desc[2];
			color = new byte[nEntries];
			if (nBits == 8) {
				for (int i=0; i<nEntries; i++) color[i] = (byte)data[i];
			}
			else {
				for (int i=0; i<nEntries; i++) {
					color[i] = (byte)((data[i] >> 8) & 0xff);
				}
			}				
			logger.debug("desc = "+nEntries+"; "+firstValue+"; "+nBits);
		}
		
		public byte get(int pd) {
			pd = pd & 0xffff;
			if (pd < firstValue) return color[0];
			if (pd < firstValue + nEntries) {
				return color[pd-firstValue];
			}
			return color[color.length-1];
		}		
	}
}
