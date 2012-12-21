/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.util.LinkedList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class representing a set of values that are acceptable.
 */
public class WhiteList {

	HashSet<String> values = null;
	LinkedList<Pattern> patterns = null;
	boolean empty = true;

	/**
	 * Construct a WhiteList object from a configuration element.
	 */
	public WhiteList(Element element, String attributeName) {
		values = new HashSet<String>();
		patterns = new LinkedList<Pattern>();
		Node child = element.getFirstChild();
		while (child != null) {
			if ((child instanceof Element) && child.getNodeName().equals("accept")) {
				String value = ((Element)child).getAttribute(attributeName).trim();
				if (!value.equals("")) values.add(value);
				String regex = ((Element)child).getAttribute("regex").trim();
				if (!regex.equals("")) patterns.add(Pattern.compile(regex));
			}
			child = child.getNextSibling();
		}
		empty = (values.size() == 0) && (patterns.size() == 0);
	}

	/**
	 * Test whether a string is contained in the white list.
	 * @return the true if the white list is empty or
	 * if the string is contained in the white list.
	 */
	public boolean contains(String value) {
		if (empty || values.contains(value.trim())) return true;
		for (Pattern p : patterns) {
			Matcher matcher = p.matcher(value);
			if (matcher.matches()) return true;
		}
		return false;
	}

}