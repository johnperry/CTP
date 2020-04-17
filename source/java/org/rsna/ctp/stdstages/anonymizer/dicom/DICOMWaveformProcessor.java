/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
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
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.awt.*;
import java.awt.image.*;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmDecodeParam;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmEncodeParam;
import org.dcm4che.data.DcmObject;
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

import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;

import org.apache.log4j.Logger;

/**
 * The MIRC DICOM WaveForm Processor. The stage creates waveform
 * images for files that contain waveforms but no images.
 */
public class DICOMWaveformProcessor {

	static final Logger logger = Logger.getLogger(DICOMWaveformProcessor.class);
	static final DcmParserFactory pFact = DcmParserFactory.getInstance();
	static final DcmObjectFactory oFact = DcmObjectFactory.getInstance();
	static final DictionaryFactory dFact = DictionaryFactory.getInstance();
	static final TagDictionary tagDictionary = dFact.getDefaultTagDictionary();

   /**
     * Anonymizes the input file, writing the result to the output file.
     * The input and output files are allowed to be the same.
     * <p>
     * Important note: if the result is a AnonymizerStatus.SKIP or 
     * AnonymizerStatus.QUARANTINE, the output file is not written and the 
     * input file is unmodified, even if it is the same as the output file.
     * @param inFile the file to anonymize.
     * @param outFile the output file.  It may be same as inFile if you want
     * to anonymize in place.
     * @return the static status result
     */
    public static AnonymizerStatus process(File inFile, File outFile) {

		String exceptions = "";
		BufferedInputStream in = null;
		BufferedOutputStream out = null;
		File tempFile = null;
		byte[] buffer = new byte[4096];
		try {
			//Get the full dataset and leave the input stream open.
			in = new BufferedInputStream(new FileInputStream(inFile));
			DcmParser parser = pFact.newDcmParser(in);
			FileFormat fileFormat = parser.detectFileFormat();
			if (fileFormat == null) throw new IOException("Unrecognized file format: "+inFile);
			Dataset dataset = oFact.newDataset();
			parser.setDcmHandler(dataset.getDcmHandler());
			parser.parseDcmFile(fileFormat, -1);
			
			//Set a default for the SpecificCharacterSet, if necessary.
			SpecificCharacterSet cs = dataset.getSpecificCharacterSet();
			if (cs == null) dataset.putCS(Tags.SpecificCharacterSet, "ISO_IR 100");

			//Create the waveform image
			byte[] imageBytes = createWaveformImage(dataset);
			
			//Set the encoding
			DcmDecodeParam fileParam = parser.getDcmDecodeParam();
        	String prefEncodingUID = UIDs.ExplicitVRLittleEndian;
			FileMetaInfo fmi = dataset.getFileMetaInfo();
            if (fmi != null) prefEncodingUID = fmi.getTransferSyntaxUID();
			else prefEncodingUID = UIDs.ExplicitVRLittleEndian;
			DcmEncodeParam encoding = (DcmEncodeParam)DcmDecodeParam.valueOf(prefEncodingUID);

			//Write the dataset to a temporary file in the same directory
			File tempDir = outFile.getParentFile();
			tempFile = File.createTempFile("DCMtemp-", ".ecg", tempDir);
            out = new BufferedOutputStream(new FileOutputStream(tempFile));

            //Create and write the metainfo for the encoding we are using
			fmi = oFact.newFileMetaInfo(dataset, prefEncodingUID);
            dataset.setFileMetaInfo(fmi);
            fmi.write(out);

			//Write the dataset as far as was parsed
			dataset.writeDataset(out, encoding);
			
			//Write the PixelData element
			dataset.writeHeader(
				out,
				encoding,
				Tags.PixelData,
				VRs.OW,
				imageBytes.length);
			out.write(imageBytes);

			out.flush();
			out.close();
			in.close();

			//Rename the temp file to the specified outFile.
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
				logger.info("Error call from "+inFile, e);
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
    
    private static byte[] createWaveformImage(Dataset ds) throws Exception {
		//Find a WaveformSeq item dataset with WaveformOriginality == ORIGINAL
		DcmElement waveformSeq = ds.get(Tags.WaveformSeq);
		if (waveformSeq == null) {
			logger.warn("No WaveformSeq element");
			return null;
		}
		Dataset item = null;
		for (int i=0; (item = waveformSeq.getItem(i)) != null; i++ ) {
			String originality = item.getString(Tags.WaveformOriginality);
			if (originality.equals("ORIGINAL")) break; //pick the first one
		}
		if (item == null) {
			logger.warn("No  ORIGINAL waveform found");
			return null;
		}
		
		//Get the channel definitions
		int nChannels = item.getInt(Tags.NumberOfWaveformChannels, 0);
		int nSamples = item.getInt(Tags.NumberOfWaveformSamples, 0);
		double samplingFrequency = item.getFloat(Tags.SamplingFrequency, 0);
		Channel[] channels = new Channel[nChannels];
		DcmElement defs = item.get(Tags.ChannelDefinitionSeq);
		for (int i=0; i<nChannels; i++) {
			Dataset ch = defs.getItem(i);
			if (ch == null) {
				logger.warn("Unable to get the channels");
				return null;
			}
			channels[i] = new Channel(ch);
		}
		if (logger.isDebugEnabled()) {
			for (int i=0; i<nChannels; i++) {
				logger.debug("Channel "+i+"\n"+channels[i].toString());
			}
		}
				
		//Get the WaveformData
		DcmElement wd = item.get(Tags.WaveformData);
		int length = wd.length();
		int bytesPerSample = length/nChannels/nSamples;
		if (bytesPerSample != channels[0].bitsStored/8) {
			logger.warn("bytes/sample does not match what is calculated from the length");
			return null;
		}
		ByteBuffer bb = wd.getByteBuffer();
		ShortBuffer sb = bb.asShortBuffer();
		int[][] data = new int[nChannels][nSamples];
		int k=0;
		for (int x=0; x<nSamples; x++) {
			for (int c=0; c<nChannels; c++) {
				int y = sb.get(k++);
				data[c][x] = y;
			}
		}
		
		//Determine the x and y scales
		int pixelsPerMM = 4;
		int headerHeight = 40;
		int leftMarginInPixels = 5 * pixelsPerMM; //1 box (5mm) indent for waveform graphs
		int verticalMMPerChannel = 20;
		int channelVerticalOriginInMM = 15;
		int channelVerticalOriginInPixels = channelVerticalOriginInMM * pixelsPerMM;
		int verticalPixelsPerChannel = verticalMMPerChannel * pixelsPerMM;
		int totalVerticalPixelsForChannels = (nChannels+1) * verticalPixelsPerChannel; //one extra channel bar
		double samplesPerMM = samplingFrequency/25;
		double samplePixelsPerUnit = 10 * pixelsPerMM * channels[0].sensitivity / 1000.0;
		double channelTimeInSeconds = nSamples/samplingFrequency;
		double channelWidthInMM = nSamples/samplesPerMM;
		double channelWidthInPixels = channelWidthInMM * pixelsPerMM;
		
		int width = (int)(leftMarginInPixels + channelWidthInPixels);
		int height = headerHeight + totalVerticalPixelsForChannels;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		//Draw the graph paper
		Graphics2D g2d = (Graphics2D)image.getGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0, 0, width, height);
		g2d.setColor(Color.PINK);
		int boxSize = 5 * pixelsPerMM; //5mm boxes
		//Draw grid dots
		for (int x=0; x<width; x+=pixelsPerMM) {
			for (int y=headerHeight; y<height; y+=pixelsPerMM) {
				g2d.fillRect(x, y, 1, 1);
			}
		}
		//Draw grid lines
		for (int x=0; x<width; x+=boxSize) {
			g2d.drawLine(x, headerHeight, x, height-1);
		}
		for (int y=headerHeight; y<height; y+=boxSize) {
			g2d.drawLine(0, y, width-1, y);
		}
		
		//Draw the labels
		g2d.setColor(Color.BLACK);
		Font font = new Font( "SansSerif", java.awt.Font.BOLD, 16 );
		g2d.setFont(font);
		for (int i=0; i<nChannels; i++) {
			int yOrigin = (verticalPixelsPerChannel * i) + channelVerticalOriginInPixels + headerHeight;
			float x = leftMarginInPixels + 2*pixelsPerMM;
			float y = yOrigin - 6*pixelsPerMM;
			g2d.drawString(channels[i].label, x, y);
		}
		
		//Draw the metadata
		g2d.setColor(Color.BLACK);
		g2d.drawString(ds.getString(Tags.PatientName), 10, 18);
		g2d.drawString(ds.getString(Tags.PatientID), 10, 36);
		FontMetrics fm = g2d.getFontMetrics(font);
		String institutionName = ds.getString(Tags.InstitutionName);
		int adv = fm.stringWidth(institutionName);
		g2d.drawString(institutionName, width - adv -10, 18);
		String studyDate = ds.getString(Tags.StudyDate);
		if (studyDate.length() == 8) {
			studyDate = studyDate.substring(0,4) 
							+ "." + studyDate.substring(4,6) 
								+ "." + studyDate.substring(6);
		}
		adv = fm.stringWidth(studyDate);
		g2d.drawString(studyDate, width - adv -10, 36);
		
		//Draw the starting pulses and scale
		for (int i=0; i<nChannels; i++) {
			int yOrigin = (verticalPixelsPerChannel * i) + channelVerticalOriginInPixels + headerHeight;
			g2d.drawLine(0, yOrigin, 
						 	0, yOrigin-10*pixelsPerMM);
			g2d.drawLine(0, yOrigin-10*pixelsPerMM, 
						 	5*pixelsPerMM, yOrigin-10*pixelsPerMM);
			g2d.drawLine(5*pixelsPerMM, yOrigin-10*pixelsPerMM, 
						 	5*pixelsPerMM, yOrigin);
		}
		int yLineBottom = height - 10*pixelsPerMM;
		int yLineTop = yLineBottom - 100*pixelsPerMM;
		int xLine = width - 2*pixelsPerMM;
		g2d.drawLine(xLine, yLineTop, xLine, yLineBottom);
		for (int i=0; i<11; i++) {
			int y = yLineTop + i*10*pixelsPerMM;
			g2d.drawLine( xLine - 6*pixelsPerMM, y, xLine, y);
			y += 5*pixelsPerMM;
			if (i < 10) {
				g2d.drawLine(xLine - 3*pixelsPerMM, y, xLine, y);
			}
		}
		adv = fm.stringWidth("10 cm");
		g2d.drawString("10 cm", xLine-adv, yLineBottom+5*pixelsPerMM);
		
		//Plot the waveforms
		g2d.setColor(Color.BLUE);
		g2d.setStroke(new BasicStroke(2));
		double xScale = samplesPerMM / pixelsPerMM;
		double yScale = samplePixelsPerUnit;
		for (int c=0; c<nChannels; c++) {
			double yOrigin = (double)(verticalPixelsPerChannel * c) + channelVerticalOriginInPixels + headerHeight;
			for (int x=0; x<width-1; x++) {
				int x1 = (int)(x*xScale);
				int x2 = (int)((x+1)*xScale);
				x1 = Math.min(x1, nSamples-1);
				x2 = Math.min(x2, nSamples-1);
				int y1 = (int)(yOrigin - yScale * data[c][x1]);
				int y2 = (int)(yOrigin - yScale * data[c][x2]);
				g2d.drawLine(x+leftMarginInPixels, y1, x+1+leftMarginInPixels, y2);
			}
		}
		
		//Add the image to the dataset
		ds.putCS(Tags.PhotometricInterpretation, "RGB");
		ds.putUS(Tags.Rows, height);
		ds.putUS(Tags.Columns, width);
		ds.putUS(Tags.BitsAllocated, 8);
		ds.putUS(Tags.BitsStored, 8);
		ds.putUS(Tags.HighBit, 7);
		ds.putUS(Tags.SamplesPerPixel, 3);
		ds.putUS(Tags.PixelRepresentation, 0);
		ds.putUS(Tags.PlanarConfiguration, 0);
		
		//Return the array of pixels
		int[] pixels = new int[width * height * 3];
		pixels = image.getRaster().getPixels(0, 0, width, height, pixels);
		byte[] bytes = new byte[pixels.length];
		for (int i=0; i<bytes.length; i++) {
			bytes[i] = (byte)(pixels[i] & 0xff);
		}
		return bytes;
	}
	
	static class Channel {
		String label;
		String meaning;
		double sensitivity;
		String sensitivityUnits;
		double baseline;
		double skew;
		double offset;
		int bitsStored;
		
		public Channel(Dataset ds) {
			label = ds.getString(Tags.ChannelLabel);
			meaning = ds.get(Tags.ChannelSourceSeq).getItem(0).getString(Tags.CodeMeaning);
			sensitivity = ds.getFloat(Tags.ChannelSensitivity, 0);
			sensitivityUnits = ds.get(Tags.ChannelSensitivityUnitsSeq).getItem(0).getString(Tags.CodeValue);
			baseline = ds.getFloat(Tags.ChannelBaseline, 0);
			skew = ds.getFloat(Tags.ChannelSampleSkew, 0);
			offset = ds.getFloat(Tags.ChannelOffset, 0);
			bitsStored = ds.getInt(Tags.WaveformBitsStored, 16);
		}
		
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append("label:       "+label+"\n");
			sb.append("meaning:     "+meaning+"\n");
			sb.append("sensitivity: "+sensitivity+"\n");
			sb.append("units:       "+sensitivityUnits+"\n");
			sb.append("baseline:    "+baseline+"\n");
			sb.append("skew:        "+skew+"\n");
			sb.append("offset:      "+offset+"\n");
			sb.append("bitsStored:  "+bitsStored+"\n");
			return sb.toString();
		}
	}
    
}
