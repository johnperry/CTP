/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.xml;

/**
 * An encapsulation of a command. The first character of
 * a command can be "$" (indicating an assignment command), a "/" (indicating
 * a path command), or a "#" (indicating a comment). Anything else is treated
 * as a comment. Assignment and path commands consist of a left side, an equal
 * sign, and a right side.
 */
class XmlCommand {
	/** Value indicating that the XmlCommand is an assignment command. */
	public static final int ASSIGN = 0;

	/** Value indicating that the XmlCommand is a path command. */
	public static final int PATH = 1;

	/** Value indicating that the XmlCommand is a comment. */
	public static final int COMMENT = -1;

	/** The type of the XmlCommand (ASSIGN, PATH, or COMMENT). */
	public int type = -1;

	/** The full text of the XmlCommand. */
	public String text = "";

	/** The left side of an assignment or path command. */
	public String left = "";

	/** The right side of an assignment or path command. */
	public String right = "";

	/**

	 * Construct a new XmlCommand, parsing it by type.
	 * @param text the text of the command.
	 */
	public XmlCommand(String text) throws Exception {
		this.text = text;
		//see if it's an assignment statement
		if (text.startsWith("$")) {
			type = ASSIGN;
			split(text);
		}
		//no, see if it's a path statement
		else if (text.startsWith("/")) {
			type = PATH;
			split(text);
		}
		//if we get here, then its go to be a comment
		else type = COMMENT;
	}

	//Split a string at the first equal sign, throwing
	//an exception if there isn't one. Populate the
	//left and right fields.
	private void split(String s) throws Exception {
		int k = s.indexOf("=");
		if (k == -1) throw new Exception("no equal sign");
		left = s.substring(0,k).trim();
		right = s.substring(k+1).trim();
	}
}

