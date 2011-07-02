/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.util.HashSet;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class representing a set of values that are not acceptable.
 */
public class BlackList {

	HashSet<String> values = null;

	/**
	 * Construct a BlackList object from a configuration element.
	 */
	public BlackList(Element element, String attributeName) {
		values = new HashSet<String>();
		Node child = element.getFirstChild();
		while (child != null) {
			if ((child instanceof Element) && child.getNodeName().equals("reject")) {
				String value = ((Element)child).getAttribute(attributeName).trim();
				if (!value.equals("")) values.add(value);
			}
			child = child.getNextSibling();
		}
	}

	/**
	 * Test whether a string is contained in the black list.
	 * @return the true if the string is contained in the black list.
	 */
	public boolean contains(String value) {
		return values.contains(value.trim());
	}

}