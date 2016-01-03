/*---------------------------------------------------------------
*  Copyright 2016 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.logger;

import java.io.Serializable;
import java.util.LinkedList;
import org.rsna.ctp.objects.DicomObject;

public class LoggedElement implements Serializable {
	
	public final int tag;
	public final String name;
	public final String currentValue;
	public final String cachedValue;
	
	public LoggedElement(Integer tagInteger, DicomObject currentObject, DicomObject cachedObject) {
		this.tag = tagInteger.intValue();
		this.name = getElementName(tag);
		this.currentValue = currentObject.getElementValue(tag, "");
		this.cachedValue = cachedObject.getElementValue(tag, "");
	}
	
	private String getElementName(int tag) {
		String elementName = DicomObject.getElementName(tag);
		if (elementName != null) {
			elementName = elementName.replaceAll("\\s", "");
		}
		else {
			int g = (tag >> 16) & 0xFFFF;
			int e = tag &0xFFFF;
			elementName = String.format("g%04Xe%04X", g, e);
		}
		return elementName;
	}
	
	public String getElementTag() {
		int g = (tag >> 16) & 0xFFFF;
		int e = tag &0xFFFF;
		return String.format("(%04X,%04X)", g, e);
	}
}
