/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.objects;

/**
  * A class to encapsulate static methods to identify types of
  * DICOM objects from their SOPClassUIDs.
  */
public class SopClass {

	/** The array of supported image SOP Classe UIDs. */
	static final String[] sopClassUIDs = {
		"1.2.840.10008.5.1.4.1.1.1",			//CR
		"1.2.840.10008.5.1.4.1.1.2",			//CT
		"1.2.840.10008.5.1.1.30",				//Hardcopy color
		"1.2.840.10008.5.1.1.29",				//Hardcopy grayscale
		"1.2.840.10008.5.1.4.1.1.4",			//MR
		"1.2.840.10008.5.1.4.1.1.20",			//NM
		"1.2.840.10008.5.1.4.1.1.128",			//PET
		"1.2.840.10008.5.1.4.1.1.7",			//Secondary capture
		"1.2.840.10008.5.1.4.1.1.6.1",			//US
		"1.2.840.10008.5.1.4.1.1.12.1",			//X-ray angio
		"1.2.840.10008.5.1.4.1.1.12.2",			//X-ray fluoro
		"1.2.840.10008.5.1.4.1.1.1.1",			//DR
		"1.2.840.10008.5.1.4.1.1.1.2",			//Digital mammo
		"1.2.840.10008.5.1.4.1.1.1.3",			//Digital intra-oral X-ray
		"1.2.840.10008.5.1.4.1.1.77.1.1",		//VL endoscope
		"1.2.840.10008.5.1.4.1.1.77.1.2",		//VL microscope
		"1.2.840.10008.5.1.4.1.1.77.1.3",		//VL slide-coordinates microscope
		"1.2.840.10008.5.1.4.1.1.77.1.4"		//VL photograph
	};

	/** The array of SR SOP Classe UIDs. */
	static final String[] srSopClassUIDs = {
		"1.2.840.10008.5.1.4.1.1.88.11",		//basic
		"1.2.840.10008.5.1.4.1.1.88.22",		//enhanced
		"1.2.840.10008.5.1.4.1.1.88.33"			//comprehensive
	};

	/** The KIN SOP Class UID. */
	static final String kosSopClass = "1.2.840.10008.5.1.4.1.1.88.59";

	/**
	 * Tests whether a SOP Class corresponds to a supported image.
	 * @param sop the SOPClassUID
	 * @return true if sop corresponds to a supported image; false otherwise.
	 */
	public static boolean isImage(String sop) {
		if (sop == null) return false;
		sop = sop.trim();
		for (int i=0; i<sopClassUIDs.length; i++) {
			if (sop.equals(sopClassUIDs[i])) return true;
		}
		return false;
	}

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

}

