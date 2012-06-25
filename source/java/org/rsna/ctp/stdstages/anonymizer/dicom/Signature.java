/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import org.apache.log4j.Logger;
import org.rsna.util.StringUtil;

/**
 * A DICOMPixelAnonymizer signature.
 */
public class Signature {

	static final Logger logger = Logger.getLogger(Signature.class);

	public String script;
	public Regions regions;

	public Signature(String script) {
		this.script = script;
		this.regions = new Regions();
	}

	public void addRegion(int x, int y, int width, int length) {
		this.regions.addRegion(x, y, width, length);
	}

	//add a region from a String in the form "( 1, 2, 3, 4 )"
	public void addRegion(String s) {
		s = s.substring(1, s.length()-1);
		String[] ss = s.split(",");
		if (ss.length == 4) {
			this.regions.addRegion(
					StringUtil.getInt(ss[0]),
					StringUtil.getInt(ss[1]),
					StringUtil.getInt(ss[2]),
					StringUtil.getInt(ss[3])
			);
		}
		else logger.warn("Skipping pixel region \""+s+"\"");
	}
}
