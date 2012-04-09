/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.dicom;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.log4j.Logger;
import org.dcm4che.dict.UIDs;

public class PCTable extends Hashtable<String,LinkedList<String>> {

    final static Logger logger = Logger.getLogger(PCTable.class);

	protected PCTable() {
		super();
		for (int i=0; i<pcs.length; i++) {
			this.put(pcs[i].asUID, pcs[i].list);
		}
		for (int i=0; i<ext_pcs.length; i++) {
			this.put(ext_pcs[i].asUID, ext_pcs[i].list);
		}
	}

	public static synchronized PCTable getInstance() {
		return pcTable;
	}

	static class PC {
		public String asUID;
		public LinkedList<String> list;

		//This method always looks up asName in the dictionary.
		public PC(String asName, String listString) {
			this.asUID = UIDs.forName(asName);
			this.list = tokenize(listString, new LinkedList<String>());
		}

		//This method is for the ext_pcs, to allow UIDs to be added that
		//are not in the dictionary. If ext is true, use asName as the UID;
		//else look it up in the dictionary.
		public PC(String asName, String listString, boolean ext) {
			this.asUID = ext ? asName : UIDs.forName(asName);
			this.list = tokenize(listString, new LinkedList<String>());
		}

		private static LinkedList<String> tokenize(String s, LinkedList<String> list) {
			StringTokenizer stk = new StringTokenizer(s, ", ");
			while (stk.hasMoreTokens()) {
				String tk = stk.nextToken();
				if (tk.equals("$ts-native")) tokenize(tsNative, list);
				else if (tk.equals("$ts-jpeglossless")) tokenize(tsJPEGLossless, list);
				else if (tk.equals("$ts-epd")) tokenize(tsEPD, list);
				else list.add(UIDs.forName(tk));
			}
			return list;
		}
	}

	static String tsJPEGLossless =
			"JPEGLossless,"+
			"JPEGLossless14";
	static String tsEPD =
			"$ts-jpeglossless,"+
			"JPEG2000Lossless,"+
			"JPEG2000Lossy,"+
			"JPEGExtended,"+
			"JPEGLSLossy,"+
			"RLELossless,"+
			"JPEGBaseline";
	static String tsNative =
			"ExplicitVRLittleEndian,"+
			"ImplicitVRLittleEndian";

	static PC[] pcs = {
		new PC("AmbulatoryECGWaveformStorage","$ts-native"),
		new PC("BasicStudyContentNotification","$ts-native"),
		new PC("BasicTextSR","$ts-native"),
		new PC("BasicVoiceAudioWaveformStorage","$ts-native"),
		new PC("CTImageStorage","$ts-epd,$ts-native"),
		new PC("CardiacElectrophysiologyWaveformStorage","$ts-native"),
		new PC("ComprehensiveSR","$ts-native"),
		new PC("ComputedRadiographyImageStorage","$ts-epd,$ts-native"),
		new PC("DigitalIntraoralXRayImageStorageForPresentation","$ts-jpeglossless,$ts-native"),
		new PC("DigitalIntraoralXRayImageStorageForProcessing","$ts-jpeglossless,$ts-native"),
		new PC("DigitalMammographyXRayImageStorageForPresentation","$ts-jpeglossless,$ts-native"),
		new PC("DigitalMammographyXRayImageStorageForProcessing","$ts-jpeglossless,$ts-native"),
		new PC("DigitalXRayImageStorageForPresentation","$ts-jpeglossless,$ts-native"),
		new PC("DigitalXRayImageStorageForProcessing","$ts-jpeglossless,$ts-native"),
		new PC("EncapsulatedPDFStorage","$ts-native"),
		new PC("EnhancedMRImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("EnhancedSR","$ts-native"),
		new PC("GeneralECGWaveformStorage","$ts-native"),
		new PC("GrayscaleSoftcopyPresentationStateStorage","$ts-native"),
		new PC("HangingProtocolStorage","$ts-native"),
		new PC("HardcopyColorImageStorage","$ts-native"),
		new PC("HardcopyGrayscaleImageStorage","$ts-native"),
		new PC("HemodynamicWaveformStorage","$ts-native"),
		new PC("KeyObjectSelectionDocument","$ts-native"),
		new PC("MRImageStorage","$ts-epd,$ts-native"),
		new PC("MammographyCADSR","$ts-native"),
		new PC("MultiframeColorSecondaryCaptureImageStorage","$ts-jpeglossless,JPEGBaseline,$ts-native"),
		new PC("MultiframeGrayscaleByteSecondaryCaptureImageStorage","$ts-jpeglossless,JPEGBaseline,$ts-native"),
		new PC("MultiframeGrayscaleWordSecondaryCaptureImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("MultiframeSingleBitSecondaryCaptureImageStorage","$ts-native"),
		new PC("NuclearMedicineImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("NuclearMedicineImageStorageRetired","$ts-native"),
		new PC("PositronEmissionTomographyImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("RTBeamsTreatmentRecordStorage","$ts-native"),
		new PC("RTBrachyTreatmentRecordStorage","$ts-native"),
		new PC("RTDoseStorage","$ts-native"),
		new PC("RTImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("RTPlanStorage","$ts-native"),
		new PC("RTStructureSetStorage","$ts-native"),
		new PC("RTTreatmentSummaryRecordStorage","$ts-native"),
		new PC("RawDataStorage","$ts-native"),
		new PC("SecondaryCaptureImageStorage","$ts-epd,$ts-native"),
		new PC("SiemensCSANonImageStorage","$ts-native"),
		new PC("TwelveLeadECGWaveformStorage","$ts-native"),
		new PC("UltrasoundImageStorage","$ts-epd,$ts-native"),
		new PC("UltrasoundImageStorageRetired","$ts-native"),
		new PC("UltrasoundMultiframeImageStorage","$ts-epd,$ts-native"),
		new PC("UltrasoundMultiframeImageStorageRetired","$ts-native"),
		new PC("VLEndoscopicImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("VLImageStorageRetired","$ts-native"),
		new PC("VLMicroscopicImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("VLMultiframeImageStorageRetired","$ts-native"),
		new PC("VLPhotographicImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("VLSlideCoordinatesMicroscopicImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("VLWholeSlideMicroscopyImageStorage","$ts-jpeglossless,JPEGBaseline,$ts-native"),
		new PC("VideoEndoscopicImageStorage","MPEG2"),
		new PC("VideoMicroscopicImageStorage","MPEG2"),
		new PC("VideoPhotographicImageStorage","MPEG2"),
		new PC("XRayAngiographicBiPlaneImageStorageRetired","$ts-native"),
		new PC("XRayAngiographicImageStorage","$ts-jpeglossless,JPEGBaseline,$ts-native"),
		new PC("XRayRadiofluoroscopicImageStorage","$ts-jpeglossless,$ts-native"),
		new PC("XRayRadiationDoseSR","$ts-native")
	};

	//SOP Classes not in the dcm4che UID dictionary
	static PC[] ext_pcs = { };

	private static PCTable pcTable = new PCTable();

}