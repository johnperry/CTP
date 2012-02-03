/*---------------------------------------------------------------
*  Copyright 2012 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.objects;

import java.util.Arrays;
import java.util.Properties;

/**
 * A class to encapsulate a match result, including
 * the true/false result of the execution of the script
 * and the Properties.object containing all the operand
 * values.
 */
public class MatchResult {
	public final boolean result;
	public final Properties props;
	static final String margin  = "         ";

	/**
	 * Construct the MatchResult object
	 * @param result true if the result of executing the
	 * script produced true; false otherwise.
	 * @param props the set of operand values used in
	 * executing the script.
	 */
	public MatchResult(boolean result, Properties props) {
		this.result = result;
		this.props = props;
	}

	/**
	 * Get the result of the execution of the script.
	 */
	public boolean getResult() {
		return result;
	}

	/**
	 * Get the operand values in a form for logging.
	 */
	public String getOperandValues() {
		if (props != null) {
			String[] keys = props.keySet().toArray(new String[props.size()]);
			Arrays.sort(keys);
			StringBuffer sb = new StringBuffer();
			for (String key : keys) {
				sb.append(margin + key + " = " +props.getProperty(key) + "\n");
			}
			//Get rid of the last newline, just to make the log entry look nicer.
			if (sb.length() > 0) sb.setLength( sb.length()-1 );
			return sb.toString();
		}
		else return "";
	}
}