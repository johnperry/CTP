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

	/** The array of SR SOP Classe UIDs. */
	static final String[] srSopClassUIDs = {
		"1.2.840.10008.5.1.4.1.1.88.11",		//basic
		"1.2.840.10008.5.1.4.1.1.88.22",		//enhanced
		"1.2.840.10008.5.1.4.1.1.88.33"			//comprehensive
	};

	/** The KIN SOP Class UID. */
	static final String kosSopClass = "1.2.840.10008.5.1.4.1.1.88.59";

	/** The DICOMDIR SOP Classx UID. */
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

}

