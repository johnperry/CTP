/*---------------------------------------------------------------
 *  Copyright 2015 by the Radiological Society of North America
 *
 *  This source software is released under the terms of the
 *  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
 *----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.util.LinkedList;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;

/**
 * Encapsulate one function call, providing the parsing for the
 * function name and the argument list.
 */
public class FnCall {

	static final Logger logger = Logger.getLogger(FnCall.class);

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
	
	/** the parsed function call. */
	public String fnCall = "";
	
	LinkedList<String> arglist;
	int currentIndex = 0;

	/**
	 * Constructor; decodes one function call.
	 * @param call the script of the function call.
	 * @param context the context of the call.
	 * @param thisTag the tag of the element currently being processed
	 */
	public FnCall(String call, DICOMAnonymizerContext context, int thisTag) {
		this.context = context;
		this.thisTag = thisTag;
		this.arglist = new LinkedList<String>();

		logger.debug("FnCall: \""+call+"\"");

		//find the function name
		currentIndex = call.indexOf("(");
		if (currentIndex == -1) length = -1;
		name = call.substring(0, currentIndex).replaceAll("\\s", "");
		
		//skip the initial '('
		currentIndex++;
		
		//get the arguments
		String arg;
		while ( (arg=getArg(call)) != null) {
			arglist.add(arg);
		}
		
		//see if there was no closure
		if (currentIndex >= call.length()) { 
			length = -1; 
			return;
		}

		//okay, we have the arguments; save them
		args = new String[arglist.size()];
		arglist.toArray(args);
		
		//skip the, closing paren
		currentIndex++;
		length = currentIndex;
		fnCall = call.substring(0, currentIndex);

		if (logger.isDebugEnabled()) {
			logger.debug("...function: "+fnCall);
			for (int i=0; i<args.length; i++) {
				logger.debug("...arg["+i+"]: "+args[i]);
			}
		}

		//if this is an ifFn or selectFn call, get the conditional code
		if (name.equals(ifFn) || name.equals(selectFn)) {
			//get the true code
			trueCode = getClause(call);
			if (trueCode == null) {
				length = call.length();
				return;
			}
			//skip the closing brace
			currentIndex++;
			
			//get the false code
			falseCode = getClause(call);
			if (falseCode == null) {
				length = call.length();
				return;
			}
			//skip the closing brace
			currentIndex++;
			length = currentIndex;
		}

		//if not an if, maybe an appendFn
		else if (name.equals(appendFn)) {
			//get the clause and store it in the trueCode
			trueCode = getClause(call);
			if (trueCode == null) {
				length = call.length();
				return;
			}
			//skip the closing brace
			currentIndex++;
			length = currentIndex;
		}
	}
	
	//Get and argument, returning with currentIndex pointing to the closing ')'
	private String getArg(String call) {
		//skip initial whitespace
		while ((currentIndex < call.length()) && Character.isWhitespace(call.charAt(currentIndex))) {
			currentIndex++;
		}
		
		//see if there are arguments
		if (currentIndex >= call.length()) return null;
		char c = call.charAt(currentIndex);
		if (c == ')') return null;
		
		//we have an argument, get it, checking for opening quotes, brackets, or parens
		boolean inEscape = false;
		boolean inQuote = false;
		boolean inBracket = false;
		boolean inParen = false;
		
		StringBuffer arg = new StringBuffer();
		while (currentIndex < call.length()) {
			c = call.charAt(currentIndex);
			if (!inEscape && !inQuote && !inBracket && !inParen) {
				if ( (c == ',') || (c == ')') ) break;
			}
			if (!inEscape && (c == escapeChar)) inEscape = true;
			else {
				arg.append(c);
				if (c == '"') {
					inQuote = !inQuote;
				}
				else if (c == '[') {
					inBracket = true;
				}
				else if (inBracket && (c == ']')) {
					inBracket = false;
				}
				else if (c == '(') {
					inParen = true;
				}
				else if (inParen && (c == ')')) {
					inParen = false;
				}
				inEscape = false;
			}
			currentIndex++;
		}
		
		//abort if we fell off the end
		if (currentIndex >= call.length()) return null;
		
		//skip the comma delimiter
		if (c == ',') currentIndex++;
		
		return arg.toString().trim();
	}
	
	//Get a clause, if present, returning with currentIndex pointing to the closing '}'
	private String getClause(String call) {
		//skip initial whitespace
		while ((currentIndex < call.length()) && Character.isWhitespace(call.charAt(currentIndex))) {
			currentIndex++;
		}
		
		//see if there is actually a clause
		if (currentIndex >= call.length()) return null;
		char c = call.charAt(currentIndex);
		if (c != '{') return null;
		currentIndex++;
		
		//we have a clause, get it, checking for nested clauses
		int count = 1;
		StringBuffer clause = new StringBuffer();
		while (currentIndex < call.length()) {
			c = call.charAt(currentIndex);
			if (c == '{') count++;
			if (c == '}') count--;
			if (count == 0) break;
			clause.append(c);
			currentIndex++;
		}
		
		//abort if we fell off the end
		if (currentIndex >= call.length()) return null;
		
		return clause.toString().trim();
	}
	
	/**
	 * Get the tag corresponding to a tag name, allowing for the "this" keyword.
	 * @param tagName the name of the tag.
	 * @return the tag.
	 */
	public int getTag(String tagName) {
		tagName = (tagName != null) ? tagName.trim() : "";
		if (tagName.equals("") || tagName.equals("this")) return thisTag;
		else return context.getElementTag(tagName);
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
}
