/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Hashtable;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerFunctions;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.apache.log4j.Logger;

/**
 * A utility for executing script commands.
 */
class XmlScript {

	static final Logger logger = Logger.getLogger(XmlScript.class);

	public String script = null;
	public boolean hasMore = true;
	Document document = null;
	Hashtable<String,String> table = null;
	int k = 0; //the current parsing position.

	/**
	 * Construct a new XmlScript.
	 * @param document the DOM document.
	 * @param table the table of variable values.
	 * @param script the text of the script command.
	 */
	public XmlScript(Document document,
					 Hashtable<String,String> table,
					 String script) {
		this.document = document;
		this.table = table;
		if (script != null) script = script.trim();
		this.script = script;
		this.k = 0;
	}

	/**
	 * Determine whether the script contains a $require()
	 * function call.
	 * @return true if the require function is present in
	 * the script; false otherwise.
	 */
	public boolean isRequired() {
		return script.startsWith("$require(");
	}

	/**
	 * Determine whether the script contains a $remove()
	 * function call.
	 * @return true if the remove function is present in
	 * the script; false otherwise.
	 */
	public boolean isRemoved() {
		return script.startsWith("$remove(");
	}

	/**
	 * Get the first value of the script.
	 * @param nodeValue the current value of the node whose value
	 * is to be replaced.
	 * @return the value computed from the script and the current
	 * node value.
	 * @throws Exception if it is impossible to execute the script.
	 */
	public String getValue(String nodeValue) throws Exception {
		k = 0;
		hasMore = true;
		return getNextValue(nodeValue);
	}

	/**
	 * Get the value of the script.
	 * @param nodeValue the current value of the node whose value
	 * is to be replaced.
	 * @return the value computed from the script and the current
	 * node value.
	 * @throws Exception if it is impossible to execute the script.
	 */
	public String getNextValue(String nodeValue) throws Exception {
		if (script == null) return "null";
		String value = "";
		String name;
		String temp;
		ArrayList<String> params;
		XmlScript paramsScript;
		int kk;
		int kp;
		while (k < script.length()) {
			if (script.charAt(k) =='\"') {
				//it's a literal
				k++;
				kk = script.indexOf("\"",k);
				if (kk == -1)  {
					value += script.substring(k);
					k = script.length();
				}
				else if (script.charAt(kk-1) == '\\') {
					value += script.substring (k,kk-1) + "\"";
					k = kk;
				}
				else {
					value += script.substring(k,kk);
					k = kk + 1;
				}
			}
			else if (script.charAt(k) == '$') {
				//it's either a function call or a variable reference
				kk = findDelimiter(script,k);
				if ((kk < script.length()) && (script.charAt(kk) == '(')) {
					//it's a function call
					name = script.substring(k,kk);
					kp = findParamsEnd(script,kk);
					temp = script.substring(kk+1,kp);
					paramsScript = new XmlScript(document,table,temp);
					params = new ArrayList<String>();
					while (paramsScript.hasMore) params.add(paramsScript.getNextValue(nodeValue));
					k = kp + 1;

					if (name.equals("$require")) {
						check(params,1,"$require");
						value += params.get(0);
					}

					else if (name.equals("$remove")) {
						return "";
					}

					else if (name.equals("$uid")) {
						check(params,1,"$uid");
						String uidroot = params.get(0).trim();
						value += AnonymizerFunctions.newUID(uidroot);
					}

					else if (name.equals("$hashuid")) {
						check(params,2,"$hashuid");
						String uidroot = params.get(0).trim();
						String uid = params.get(1).trim();
						value += AnonymizerFunctions.hashUID(uidroot, uid);
					}

					else if (name.equals("$hash")) {
						check(params,1,"$hash");
						String string = params.get(0);
						String maxlen = (params.size()>1) ? params.get(1).trim() : "";
						int len = Integer.MAX_VALUE;
						if (!maxlen.equals("")) {
							try { len = Integer.parseInt(maxlen); }
							catch (Exception keepDefault) { len = Integer.MAX_VALUE; }
						}
						value += AnonymizerFunctions.hash(string, len);
					}

					else if (name.equals("$hashname")) {
						check(params,1,"$hashname");
						String string = params.get(0);
						String maxlen = (params.size()>1) ? params.get(1).trim() : "";
						String wdsin = (params.size()>2) ? params.get(2).trim() : "";
						int len = Integer.MAX_VALUE;
						if (!maxlen.equals("")) {
							try { len = Integer.parseInt(maxlen); }
							catch (Exception keepDefault) { len = Integer.MAX_VALUE; }
						}
						int wds = Integer.MAX_VALUE;
						if (!wdsin.equals("")) {
							try { wds = Integer.parseInt(wdsin); }
							catch (Exception keepDefault) { wds = Integer.MAX_VALUE; }
						}
						value += AnonymizerFunctions.hashName(string, len, wds);
					}

					else if (name.equals("$hashptid")) {
						check(params,2,"$hashptid");
						String siteID = params.get(0).trim();
						String string = params.get(1).trim();
						String maxlen = (params.size()>2) ? params.get(2).trim() : "";
						int len = Integer.MAX_VALUE;
						if (!maxlen.equals("")) {
							try { len = Integer.parseInt(maxlen); }
							catch (Exception keepDefault) { len = Integer.MAX_VALUE; }
						}
						value += AnonymizerFunctions.hashPtID(string, siteID, len);
					}

					else if (name.equals("$round")) {
						check(params,2,"$round");
						String string = params.get(0);
						String grpString = params.get(1);
						try {
							int grp = Integer.parseInt(grpString);
							value += AnonymizerFunctions.round(string, grp);
						}
						catch (Exception ex) {
							logger.warn("Non-parsing group size (\""+grpString+"\") in $round script");
							throw ex;
						}
					}

					else if (name.equals("$encrypt")) {
						check(params,2,"$encrypt");
						String string = params.get(0);
						String key = params.get(1);
						value += AnonymizerFunctions.encrypt(string, key);
					}

					else if (name.equals("$incrementdate")) {
						check(params,2,"$incrementdate");
						String string = params.get(0).trim();
						String incString = params.get(1).trim();
						try {
							long inc = Long.parseLong(incString);
							value += AnonymizerFunctions.incrementDate(string, inc);
						}
						catch (Exception ex) {
							logger.warn("Non-parsing increment (\""+incString+"\") in $incrementdate script");
							throw ex;
						}
					}

					else if (name.equals("$modifydate")) {
						check(params,4,"$modifydate");
						String string = params.get(0).trim();
						int y = getReplacementValue(params.get(1).trim());
						int m = getReplacementValue(params.get(2).trim());
						int d = getReplacementValue(params.get(3).trim());
						value += AnonymizerFunctions.modifyDate(string, y, m, d);
					}

					else if (name.equals("$initials")) {
						check(params,1,"$initials");
						String string = params.get(0).trim();
						value += AnonymizerFunctions.initials(string);
					}

					else if (name.equals("$time")) {
						String sep = (params.size()>0) ? params.get(0) : "";
						value += AnonymizerFunctions.time(sep);
					}

					else if (name.equals("$date")) {
						String sep = (params.size()>0) ? params.get(0) : "";
						value += AnonymizerFunctions.date(sep);
					}
				}
				else {
					//it's a variable reference
					name = script.substring(k,kk);
					temp = (String)table.get(name);
					if (temp == null) temp = "null";
					value += temp;
					k = kk;
				}
			}
			else if (script.charAt(k) == 't') {
				//see if it is a reserved word
				//now, the only reserved word is "this"
				kk = findDelimiter(script,k);
				name = script.substring(k,kk);
				if (name.equals("this")) value += nodeValue;
				k = kk;
			}
			else if (script.charAt(k) == '/') {
					//it's a path expression
					kk = findDelimiter(script,k);
					String path = script.substring(k,kk).trim();
					value += getPathValue(new XmlPathElement(document,path));
					k = kk;
			}
			else if (script.charAt(k) == ',') {
				k++;
				return value;
			}
			else k++;
		}
		hasMore = false;
		return value;
	}

	//Check whether a function call has enough arguments.
	//Throw an exception if it does not.
	private void check(List params, int n, String name) throws Exception {
		if (params.size() < n) {
			String msg = "Insufficient arguments for "+name;
			logger.warn(msg);
			throw new Exception(msg);
		}
	}

	//Get an integer from a string, returning -1 if the string does not parse.
	private static int getReplacementValue(String s) {
		try { return Integer.parseInt(s); }
		catch (Exception ex) { return -1; }
	}

	//Get the index of the delimiter of a name. Delimiters are:
	//   whitespace
	//   the end of the string
	//   ( or , or )
	private int findDelimiter(String s, int k) {
		char c;
		while (k < s.length()) {
			c = s.charAt(k);
			if (!Character.isWhitespace(c)
				&& (c != '(') && (c != ',') && (c != ')')) k++;
			else return k;
		}
		return k;
	}

	//Get the index of the end parenthesis in parameter
	//list in a function call, allowing for nesting.
	//This method is called with k pointing to the
	//the opening parenthesis character.
	private int findParamsEnd(String s, int k) {
		int count = 0;
		char c;
		while (k < s.length()) {
			c = s.charAt(k++);
			if (c == '(') count++;
			else if (c == '"') k = skipQuote(s,k);
			else if (c == ')') count--;
			if (count == 0) return k-1;;
		}
		return k;
	}

	//Get the index of the character after the end of
	//a quoted string. This method is called with k
	//pointing to the character after the starting quote.
	private int skipQuote(String s, int k) {
		boolean esc = false;
		while (k < s.length()) {
			if (esc) esc = false;
			else if (s.charAt(k) == '\\') esc = true;
			else if (s.charAt(k) == '"') return k+1;
			k++;
		}
		return k;
	}

	//Get the value from the end of a path,
	//always choosing the first node if a segment
	//produces multiple nodes.
	private String getPathValue(XmlPathElement pe) {

		//If the next segment is an attribute, get the value.
		if (pe.segmentIsAttribute()) {
			return pe.getValue();
		}

		//No, see if the next segment is empty, meaning that
		//the parent node of this XmlPathElement is the end of
		//the path. If so, get the value of the parent node.
		if (pe.isEndSegment()) {
			return pe.getValue();
		}

		//This is not the end of the path; get the NodeList;
		//then see if there is an element available for the segment.
		NodeList nl = pe.getNodeList();
		if ((nl == null) || (nl.getLength() == 0)) {
			//The element identified by the segment is missing.
			return "null";
		}

		//If we get here, one or more elements identified by
		//the segment are present and this is not the end of
		//the path; pick the first child segment.
		String remainingPath = pe.getRemainingPath();
		return getPathValue(new XmlPathElement(nl.item(0),remainingPath));
	}
}

