/*---------------------------------------------------------------
 *  Copyright 2005 by the Radiological Society of North America
 *
 *  This source software is released under the terms of the
 *  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
 *----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.util.Properties;
import org.dcm4che.data.Dataset;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import java.util.LinkedList;
import org.dcm4che.dict.Tags;

/**
 * Encapsulate one function call, providing the parsing for the
 * function name and the argument list.
 */
public class FnCall {

	static final char escapeChar = '\\';
	static final String ifFn	 = "if";
	static final String appendFn = "append";
	static final String selectFn = "select";
	static final String alwaysFn = "always";

	/** the context. */
	public DICOMAnonymizerContext context;

	/** the function name. */
	public String name = "";

	/** the current element. */
	public int thisTag = 0;

	/** the decoded list of function arguments. */
	public String[] args = null;

	/** the script to be executed if the function generates true. */
	public String trueCode = "";

	/** the script to be executed if the function generates false. */
	public String falseCode = "";

	/** the length of the script text occupied by this function call. */
	public int length = -1;

   /**
	 * Constructor; decodes one function call.
	 * @param call the script of the function call.
	 * @param context the context of the call.
	 */
	public FnCall(String call, DICOMAnonymizerContext context, int thisTag) {
		this.context = context;
		this.thisTag = thisTag;

		//find the function name
		int k = call.indexOf("(");
		if (k == -1) length = -1;
		name = call.substring(0,k).replaceAll("\\s","");

		//now get the arguments
		int kk = k;
		LinkedList<String> arglist = new LinkedList<String>();
		while ((kk = getDelimiter(call, kk+1, ",)", '(')) != -1) {
			arglist.add(call.substring(k+1,kk).trim());
			k = kk;
			if (call.charAt(kk) == ')') break;
		}
		//if there was no clean end to the arguments, bail out
		if (kk == -1) { length = -1; return; }

		//okay, we have arguments; save them
		args = new String[arglist.size()];
		arglist.toArray(args);
		length = kk + 1;

		//if this is an ifFn or selectFn call, get the conditional code
		if (name.equals(ifFn) || name.equals(selectFn)) {
			//get the true code
			if ( ((k = call.indexOf("{",kk+1)) == -1) ||
				 ((kk = getDelimiter(call, k+1, "}", '{'))  == -1) ) {
				//this call is not coded correctly; return with
				//a length set to ignore the rest of the line
				length = call.length();
				return;
			}
			//the true code was present, save it
			trueCode = call.substring(k+1,kk);

			//now get the false code
			if ( ((k = call.indexOf("{",kk+1)) == -1) ||
				 ((kk = getDelimiter(call, k+1, "}", '{')) == -1) ) {
				//either this call is not coded correctly or there
				//is no false code; return with a length set to
				//ignore the rest of the line
				length = call.length();
				return;
			}
			//the false code was present, save it
			falseCode = call.substring(k+1,kk);
			length = kk + 1;
		}
		//if not an if, maybe an appendFn
		else if (name.equals(appendFn)) {
			//get the clause and store it in the trueCode
			if ( ((k = call.indexOf("{",kk+1)) == -1) ||
				 ((kk = getDelimiter(call, k+1, "}", '{'))  == -1) ) {
				//this call is not coded correctly; return with
				//a length set to ignore the rest of the line
				length = call.length();
				return;
			}
			//the clause was present, save it
			trueCode = call.substring(k+1,kk);
			length = kk + 1;
		}
	}

   /**
	 * Get the tag corresponding to a tag name, allowing for the "this" keyword.
	 * Allowed formats are:
	 * <ul>
	 * <li>this</li>
	 * <li>ggggeeee</li>
	 * <li>[ggggeeee]</li>
	 * </ul>
	 * Note: the underlying translation also supports wrapping in parentheses and
	 * separating the group and element numbers with a comma, but the parser in
	 * the FnCall constructor does not allow these formats. Maybe someday...
	 * @param tagName the name of the tag.
	 * @return the tag.
	 */
	public int getTag(String tagName) {
		tagName = (tagName != null) ? tagName.trim() : "";
		if (tagName.equals("") || tagName.equals("this")) return thisTag;
		else return DicomObject.getElementTag(tagName);
	}

   /**
	 * Get a specific argument.
	 * @param arg the argument to get, counting from zero.
	 * @return the String value of the argument, or ""
	 * if the argument does not exist.
	 */
	public String getArg(int arg) {
		if (args == null) return "";
		if (arg >= args.length) return "";
		String argString = args[arg].trim();
		if (argString.startsWith("\"") && argString.endsWith("\"")) {
			argString = argString.substring(1,argString.length()-1);
		}
		return argString;
	}

   /**
	 * Regenerate the script of this function call, not including
	 * any conditional clauses.
	 * @return the function name and arguments.
	 */
	public String getCall() {
		return name + getArgs();
	}

   /**
	 * Regenerate the list of arguments in this function call.
	 * @return the function arguments.
	 */
	public String getArgs() {
		String s = "";
		if (args != null) {
			for (int i=0; i<args.length; i++) {
				s += args[i];
				if (i != args.length-1) s += ",";
			}
		}
		return "(" + s + ")";
	}

   /**
	 * Search a string for a delimiter, handling escape characters
	 * and double-quoted substrings.
	 * Note: this method only works for function parameter lists
	 * and un-nested if clauses. if statements within conditional
	 * clauses are not supported.
	 * @param s the string to search.
	 * @param k the index of the starting point in the string.
	 * @param delims the list of delimiter characters.
	 * @return the index of the delimiter.
	 */
	public int getDelimiter(String s, int k, String delims, char open) {
		int count = 1;
		boolean inQuote = false;
		boolean inEscape = false;
		while (k < s.length()) {
			char c = s.charAt(k);
			if (inEscape) inEscape = false;
			else if (c == escapeChar) inEscape = true;
			else if (inQuote) {
				if (c == '"') inQuote = false;
			}
			else if (c == '"') inQuote = true;
			else if (c == open) count++;
			else if (delims.indexOf(c) != -1) {
				count--;
				if (count == 0) return k;
			}
			k++;
		}
		return -1;
	}
}
