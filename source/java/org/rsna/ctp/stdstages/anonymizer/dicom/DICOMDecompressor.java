/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.awt.Graphics2D;
import java.awt.image.*;
import java.awt.geom.AffineTransform;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.security.*;
import javax.imageio.*;
import javax.imageio.stream.FileImageInputStream;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.DcmParser;
import org.dcm4che.data.DcmParserFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.dict.VRs;

import org.rsna.ctp.stdstages.anonymizer.AnonymizerFunctions;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.util.FileUtil;

import org.apache.log4j.Logger;

/**
 * The CTP DICOM image decompressor. This class contains one method
 * which changes the transfer syntax of images to EVRLE.
 */
public class DICOMDecompressor {

	static final Logger logger = Logger.getLogger(DICOMDecompressor.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

   /**
     * Convert the transfer syntax of the input file to EVRLE, writing the
     * result to the output file. The input and output files are allowed
     * to be the same.
     * @param inFile the file to anonymize.
     * @param outFile the output file, which may be same as inFile you if want
     * to decompress in place.
     * @return the static status result
     */
    public static AnonymizerStatus decompress(File inFile, File outFile) {

		long fileLength = inFile.length();
		logger.debug("File length      = "+fileLength);

		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		ImageReader reader = null;
		FileImageInputStream fiis = null;
		
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

			//Make sure this is an image
            if (parser.getReadTag() != Tags.PixelData) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Not an image");
			}

			//Get the required parameters and make sure they are okay
			int numberOfFrames = getInt(dataset, Tags.NumberOfFrames, 1);
			int rows = getInt(dataset, Tags.Rows, 0);
			int columns = getInt(dataset, Tags.Columns, 0);
			int samplesPerPixel = getInt(dataset, Tags.SamplesPerPixel, 1);
			int bitsAllocated = getInt(dataset, Tags.BitsAllocated, 16);
			if ((rows == 0) || (columns == 0)) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unable to get the rows and columns");
			}
			if ((bitsAllocated % 8) != 0) {
				close(in);
				return AnonymizerStatus.SKIP(inFile, "Unsupported BitsAllocated: "+bitsAllocated);
			}
			int planarConfig = getInt(dataset, Tags.PlanarConfiguration, 0);
			boolean isPlanarConfig0 = (planarConfig == 0);

			String photometricInterpretation = getString(dataset, Tags.PhotometricInterpretation, "").toUpperCase();
			boolean isMonochrome = photometricInterpretation.contains("MONOCHROME");
			boolean isPalette = photometricInterpretation.contains("PALETTE");
			boolean isRGB = photometricInterpretation.contains("RGB");

			//Set the encoding of the output file
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
        	String prefEncodingUID = UIDs.ExplicitVRLittleEndian;
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(prefEncodingUID);
			boolean swap = (fileParam.byteOrder != encoding.byteOrder);

			//Save the dataset to a temporary file in a temporary directory
			//on the same file system root, and rename at the end.
			File tempDir = outFile.getParentFile();
			tempFile = File.createTempFile("DCMtemp-",".decomp",tempDir);
            out = new BufferedOutputStream(new FileOutputStream(tempFile));

            //Create and write the metainfo for the encoding we are using
			FileMetaInfo fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

            //********************************************************************************
            //Convert everything but monochrome to RGB with PlanarConfiguration 0 and
            //force SamplesPerPixel to 3.
            if (!isMonochrome) {
				dataset.putXX(Tags.PhotometricInterpretation, "RGB");
				dataset.putUS(Tags.BitsAllocated, 8);
				dataset.putUS(Tags.BitsStored, 8);
				dataset.putUS(Tags.HighBit, 7);
				samplesPerPixel = 3;
				dataset.putUS(Tags.SamplesPerPixel, samplesPerPixel);
				dataset.putUS(Tags.PlanarConfiguration, 0);
			}
			//Remove the palette elements if we are converting from palette to RGB
			if (isPalette) {
				dataset.remove(Tags.PaletteColorLookupTableSeq);
				dataset.remove(Tags.PaletteColorLUTUID);
				dataset.remove(Tags.RedPaletteColorLUTData);
				dataset.remove(Tags.RedPaletteColorLUTDescriptor);
				dataset.remove(Tags.GreenPaletteColorLUTData);
				dataset.remove(Tags.GreenPaletteColorLUTDescriptor);
				dataset.remove(Tags.BluePaletteColorLUTData);
				dataset.remove(Tags.BluePaletteColorLUTDescriptor);
			}
            //********************************************************************************

			//Write the dataset as far as was parsed
			dataset.writeDataset(out, encoding);

			//Process the pixels
            if (parser.getReadTag() == Tags.PixelData) {

                //Get the number of bytes for all the pixels to be written
                int bytesPerSample = bitsAllocated / 8;
                int nPixelBytes = numberOfFrames * rows * columns * samplesPerPixel * bytesPerSample;
                int pixelBytesLength = nPixelBytes + (nPixelBytes & 1);
                int pixelsVR = ((bytesPerSample == 1) && (samplesPerPixel == 1)) ? VRs.OB : VRs.OW;
                logger.debug("planarConfig     = "+planarConfig);
                logger.debug("photometricInt   = "+photometricInterpretation);
                logger.debug("numberOfFrames   = "+numberOfFrames);
                logger.debug("rows             = "+rows);
                logger.debug("columns          = "+columns);
                logger.debug("samplesPerPixel  = "+samplesPerPixel);
                logger.debug("bytesPerSample   = "+bytesPerSample);
                logger.debug("pixelsVR         = "+VRs.toString(pixelsVR));
                logger.debug("nPixelBytes      = "+nPixelBytes);
                logger.debug("pixelBytesLength = "+pixelBytesLength);

                //Write the element header
                dataset.writeHeader(
                    out,
                    encoding,
                    parser.getReadTag(),
                    pixelsVR,
                    pixelBytesLength);

                //Now put in the decompressed frames
				fiis = new FileImageInputStream(inFile);
				reader = (ImageReader)ImageIO.getImageReadersByFormatName("DICOM").next();
				reader.setInput(fiis);
				for (int i=0; i<numberOfFrames; i++) {
					logger.debug("Decompressing frame "+i);
					BufferedImage bi = reader.read(i);
					if (!isMonochrome /*&& !isRGB*/) bi = convertToRGB(bi);
					WritableRaster wr = bi.getRaster();
					DataBuffer b = wr.getDataBuffer();
					int numBanks = b.getNumBanks();
					logger.debug("Number of banks = "+numBanks);
					for (int bank=0; bank<numBanks; bank++) {
						logger.debug("  Reading bank "+bank);
						if (b.getDataType() == DataBuffer.TYPE_USHORT) {
							logger.debug("  Datatype: DataBuffer.TYPE_USHORT");
							DataBufferUShort bus = (DataBufferUShort)b;
							short[] data = bus.getData(bank);
							logger.debug("    Buffer length = "+data.length);
							for (int k=0; k<data.length; k++) {
								int p = data[k] & 0xffff;
								out.write(p & 0xff);
								out.write(p >> 8);
							}
						}
						else if (b.getDataType() == DataBuffer.TYPE_SHORT) {
							logger.debug("    Datatype: DataBuffer.TYPE_SHORT");
							DataBufferShort bs = (DataBufferShort)b;
							short[] data = bs.getData(bank);
							logger.debug("    Buffer length = "+data.length);
							for (int k=0; k<data.length; k++) {
								int p = data[k] & 0xffff;
								out.write(p & 0xff);
								out.write(p >> 8);
							}
						}
						else if (b.getDataType() == DataBuffer.TYPE_BYTE) {
							logger.debug("    Datatype: DataBuffer.TYPE_BYTE");
							DataBufferByte bb = (DataBufferByte)b;
							byte[] data = bb.getData(bank);
							logger.debug("    Buffer length = "+data.length);
							out.write(data);
						}
						else if (b.getDataType() == DataBuffer.TYPE_INT) {
							logger.debug("    Datatype: DataBuffer.TYPE_INT");
							DataBufferInt bb = (DataBufferInt)b;
							int[] data = bb.getData(bank);
							logger.debug("    Buffer length = "+data.length);
							for (int k=0; k<data.length; k++) {
								int red = (data[k] & 0xff0000) >> 16;
								int green = (data[k] & 0xff00) >> 8;
								int blue = data[k] & 0xff;
								out.write(red & 0xff);
								out.write(green & 0xff);
								out.write(blue & 0xff);
							}
						}
						else {
							logger.warn("Unsupported DataBuffer type ("+b.getDataType()+") in "+inFile);
							throw new Exception("Unsupported DataBuffer type: "+b.getDataType());
						}
					}
					logger.debug("  Done decompressing frame "+i);
				}
				//Pad the pixels if necessary
				if ((nPixelBytes & 1) != 0) {
					logger.debug("Adding pixel data pad");
					out.write(0);
				}

				fiis.close();
				reader.dispose();

                //Skip the pixel data in the input stream
                if (fileParam.encapsulated) {
                    parser.parseHeader();
                    while (parser.getReadTag() == Tags.Item) {
						skip(parser);
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
                }
                else skip(parser);
				if (parser.getStreamPosition() < fileLength) parser.parseHeader(); //get ready for the next element
			}
			//Now do any elements after the pixels one at a time.
			//This is done to allow streaming of large raw data elements
			//that occur above Tags.PixelData.
			while (!parser.hasSeenEOF() 
//					&& (parser.getStreamPosition() < fileLength) 
					&& parser.getReadTag() != -1) {
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
			
			//Close the file image input stream, if possible
			try { if (fiis != null) fiis.close(); }
			catch (Exception ex) { }
			
			//Dispose of any resources held by the Reader
			try { if (reader != null) reader.dispose(); }
			catch (Exception ex) { }

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

    private static BufferedImage convertToRGB(BufferedImage bi) {
		// Make a destination image
		BufferedImage rgbImage =
						new BufferedImage(
								bi.getWidth(),
								bi.getHeight(),
								BufferedImage.TYPE_INT_RGB);

		//Make an identity transform op
		AffineTransformOp atop = new AffineTransformOp(
										new AffineTransform(),
										AffineTransformOp.TYPE_NEAREST_NEIGHBOR );

		// Paint the transformed image.
		Graphics2D g2d = rgbImage.createGraphics();
		g2d.drawImage(bi, atop, 0, 0);
		g2d.dispose();
		return rgbImage;
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

	private static void swapBytes(byte[] bytes) {
		int len = bytes.length & 0xffffFFFE;
		byte b;
		for (int i=0; i<len; i+=2) {
			b = bytes[i];
			bytes[i] = bytes[i+1];
			bytes[i+1] = b;
		}
	}

	private static void skip(DcmParser parser) throws Exception {
		InputStream in = parser.getInputStream();
		int len = parser.getReadLength();
		for (int i=0; i<len; ++i) in.read();
		long pos = parser.getStreamPosition();
		parser.setStreamPosition(pos + len);
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
