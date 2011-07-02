/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import org.rsna.server.HttpRequest;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A class encapsulating the selection of frames
 * as well as size and quality parameters of images..
 */
public class ImageQualifiers {

	String maxWidthString;
	String minWidthString;
	String qualityString;
	String frameString;

	public int maxWidth;
	public int minWidth;
	public int quality;

	public ImageQualifiers(HttpRequest req) {
		maxWidthString = req.getParameter("wmax");
		minWidthString = req.getParameter("wmin");
		qualityString = req.getParameter("q");
		frameString = req.getParameter("frame");
		setIntegers();
	}

	public ImageQualifiers(Element el) {
		maxWidthString = el.getAttribute("wmax");
		minWidthString = el.getAttribute("wmin");
		qualityString = el.getAttribute("q");
		frameString = el.getAttribute("frame");
		setIntegers();
	}

	/**
	 * Get an integer indicating which frame or frames have
	 * been specified.
	 * @param nFrames the number of frames in the object
	 * @return the frame number selected (the first frame is zero),
	 * or -1, indicating all frames.
	 */
	public int getSelectedFrames(int nFrames) {
		if (frameString != null) {
			if (frameString.equals("first")) return 0;
			if (frameString.equals("middle")) return nFrames/2;
			if (frameString.equals("last")) return nFrames - 1;
			if (frameString.equals("all")) return -1;
		}
		return 0;
	}

	//Set up the integers from the string parameters.
	private void setIntegers() {
		maxWidth = StringUtil.getInt(maxWidthString, 10000);
		minWidth = StringUtil.getInt(minWidthString, 96);
		quality = StringUtil.getInt(qualityString, -1);
	}

	/**
	 * Get a String in the form "[maxWidth; minWidth; quality]"
	 * with no frame field.
	 */
	public String toString() {
		if ((maxWidthString == null) &&
			(minWidthString == null) &&
			(qualityString == null)) return "";
		return "[" + maxWidth + ";" + minWidth + ";" + quality + "]";
	}

	/**
	 * Same as toString(frame, 0).
	 */
	public String toString(int frame) {
		return toString( frame, 0 );
	}

	/**
	 * Get a String in the form "[maxWidth; minWidth; quality][frame]
	 * where the width of the frame field is set by the length of the
	 * nFrames parameter, with leading zeroes as necessary.
	 */
	public String toString(int frame, int nFrames) {
		int len = Integer.toString(nFrames).length();
		String fString = String.format("[%0"+len+"d]", frame);
		if ((maxWidthString == null) &&
			(minWidthString == null) &&
			(qualityString == null)) return fString;
		return "[" + maxWidth + ";" + minWidth + ";" + quality + "]" + fString;
	}
}

