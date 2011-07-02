/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.xml;

import java.io.BufferedReader;
import java.io.StringReader;

/**
 * A utility for assembling multi-line script commands.
 */
class XmlCommandHandler {
	BufferedReader br = null;
	String line = "";

	/**
	 * Construct a new handler for multi-line script commands.
	 * @param cmds the script commands.
	 */
	public XmlCommandHandler(String cmds) throws Exception {
		br = new BufferedReader(new StringReader(cmds));
		line = br.readLine();
	}

	/**
	 * Get the next command. Commands are always one or more lines long.
	 * Two commands do not occur on the same line. The first character of
	 * a line can be "$" (indicating an assignment command), a "/" (indicating
	 * a path command), or a "#" (indicating a comment). Any other line belongs
	 * to the command line above it.
	 * @return the next command.
	 */
	public XmlCommand getNextCommand() throws Exception {
		if (line == null) return null;
		StringBuffer sb = new StringBuffer(line);
		boolean done = false;
		while ( ((line = br.readLine()) != null)
				&& !line.startsWith("$") && !line.startsWith("/")
				&& !line.startsWith("#"))
			sb.append(line + "\n");
		while ((line != null) && line.startsWith("#"))
			line = br.readLine();
		return new XmlCommand(sb.toString());
	}
}

