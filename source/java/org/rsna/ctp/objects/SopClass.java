/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.objects;

/**
  * A class to encapsulate static methods to identify types of
  * DICOM objects from their SOPClassUIDs.
  */
public class SopClass {

	/** The array of SR SOP Class UIDs. */
	static final String[] srSopClassUIDs = {
		"1.2.840.10008.5.1.4.1.1.88.11",	//BasicTextSR
		"1.2.840.10008.5.1.4.1.1.88.22",	//EnhancedSR
		"1.2.840.10008.5.1.4.1.1.88.33",	//ComprehensiveSR
		"1.2.840.10008.5.1.4.1.1.88.1",		//StructuredReportTextStorageRetired
		"1.2.840.10008.5.1.4.1.1.88.2",		//StructuredReportAudioStorageRetired
		"1.2.840.10008.5.1.4.1.1.88.3",		//StructuredReportDetailStorageRetired
		"1.2.840.10008.5.1.4.1.1.88.4",		//StructuredComprehensiveStorageRetired
		"1.2.840.10008.5.1.4.1.1.88.34",	//Comprehensive3DSRStorage
		"1.2.840.10008.5.1.4.1.1.88.35",	//ExtensibleSRStorage
		"1.2.840.10008.5.1.4.1.1.88.50",	//MammographyCADSR
		"1.2.840.10008.5.1.4.1.1.88.65",	//ChestCADSR
		"1.2.840.10008.5.1.4.1.1.88.67",	//XRayRadiationDoseSR
		"1.2.840.10008.5.1.4.1.1.88.68",	//RadiopharmaceuticalRadiationDoseSRStorage
		"1.2.840.10008.5.1.4.1.1.88.69",	//ColonCADSR
		"1.2.840.10008.5.1.4.1.1.88.70",	//ImplantationPlanSRStorage
		"1.2.840.10008.5.1.4.1.1.88.71",	//AcquisitionContextSRStorage
		"1.2.840.10008.5.1.4.1.1.88.72",	//SimplifiedAdultEchoSRStorage
		"1.2.840.10008.5.1.4.1.1.88.73",	//PatientRadiationDoseSRStorage
		"1.2.840.10008.5.1.4.1.1.88.74",	//PlannedImagingAgentAdministrationSRStorage
		"1.2.840.10008.5.1.4.1.1.88.75"		//PerformedImagingAgentAdministrationSRStorage
	};
	
	/** The start of all PresentationState SOP Class UIDs. */
	static final String psSopClassPrefix = "1.2.840.10008.5.1.4.1.1.11";

	/** The KIN SOP Class UID. */
	static final String kosSopClass = "1.2.840.10008.5.1.4.1.1.88.59";

	/** The DICOMDIR SOP Class UID. */
	static final String dicomdirClass = "1.2.840.10008.1.3.10";

	/**
	 * Tests whether a SOP Class corresponds to an SR.
	 * @param sop the SOPClassUID
	 * @return true if sop corresponds to an SR; false otherwise.
	 */
	public static boolean isSR(String sop) {
		if (sop == null) return false;
		sop = sop.trim();
		for (int i=0; i<srSopClassUIDs.length; i++) {
			if (sop.equals(srSopClassUIDs[i])) return true;
		}
		return false;
	}

	/**
	 * Tests whether a SOP Class corresponds to a KIN.
	 * @param sop the SOPClassUID
	 * @return true if sop corresponds to a KIN; false otherwise.
	 */
	public static boolean isKIN(String sop) {
		if (sop == null) return false;
		sop = sop.trim();
		return sop.equals(kosSopClass);
	}

	/**
	 * Tests whether a SOP Class corresponds to a dicomdir.
	 * @param sop the SOPClassUID
	 * @return true if sop corresponds to a DICOMDIR; false otherwise.
	 */
	public static boolean isDICOMDIR(String sop) {
		if (sop == null) return false;
		sop = sop.trim();
		return sop.equals(dicomdirClass);
	}

	/**
	 * Tests whether a SOP Class corresponds to a Presentation State object
	 * @param sop the SOPClassUID
	 * @return true if sop corresponds to a DICOMDIR; false otherwise.
	 */
	public static boolean isPS(String sop) {
		return sop.startsWith(psSopClassPrefix);
	}
}

