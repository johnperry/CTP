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
 * A class representing a set of values that are acceptable.
 */
public class WhiteList {

	HashSet<String> values = null;
	boolean empty = true;

	/**
	 * Construct a WhiteList object from a configuration element.
	 */
	public WhiteList(Element element, String attributeName) {
		values = new HashSet<String>();
		Node child = element.getFirstChild();
		while (child != null) {
			if ((child instanceof Element) && child.getNodeName().equals("accept")) {
				String value = ((Element)child).getAttribute(attributeName).trim();
				if (!value.equals("")) values.add(value);
			}
			child = child.getNextSibling();
		}
		empty = (values.size() == 0);
	}

	/**
	 * Test whether a string is contained in the white list.
	 * @return the true if the white list is empty or
	 * if the string is contained in the white list.
	 */
	public boolean contains(String value) {
		return empty || values.contains(value.trim());
	}

}