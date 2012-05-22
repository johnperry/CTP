/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer;

import java.math.BigInteger;
import java.security.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.regex.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.apache.log4j.Logger;
import org.rsna.util.Base64;
import org.rsna.util.DateUtil;
import org.rsna.util.DigestUtil;

/**
 * MIRC anonymizer functions. These static methods provide the
 * data modification functions used by the MIRC anonymizers.
 * See the <a href="http://mircwiki.rsna.org">MIRC wiki</a> for more more information.
 */
public class AnonymizerFunctions {

	static final Logger logger = Logger.getLogger(AnonymizerFunctions.class);

	/**
	 * Look up a String in a local unencrypted Properties object.
	 * The Properties table keys must be of the form <b>keyType/key</b>.
	 * @param table the lookup table
	 * @param keyType any name relating keys of a particular category,
	 * for example "ptid".
	 * @param key the value of the keyType to be looked up in the table.
	 * @return the value in the table corresponding to keyType/key.
	 * @throws Exception if the requested value cannot be found in the table.
	 */
	public static String lookup(
					Properties table,
					String keyType,
					String key) throws Exception {
		if (table == null) throw new Exception("missing lookup table");
		if (table.size() == 0) throw new Exception("empty lookup table");
		if (key == null) throw new Exception("lookup key missing");
		key = keyType + "/" + key.trim();
		String value = table.getProperty(key);
		if (value == null) throw new Exception("missing key ("+key+") in lookup table");
		return value.trim();
	}

	/**
	 * Generate a replacement string for a text string using an IntegerTable.
	 * The replacement string is an integer numerical string. Replacement
	 * strings are based on the keyType, so text strings of different
	 * keyTypes are handled independently.
	 * @param table the IntegerTable to be used in assigining replacement strings.
	 * @param keyType any name relating text strings of a particular category,
	 * for example "ptid".
	 * @param text the value for which a replacement string is to be created.
	 * @param width the minimum width of the replacement string.
	 * @return the replacement string for keyType/text.
	 * @throws Exception if the replacement string cannot be created.
	 */
	public static String integer(
					IntegerTable table,
					String keyType,
					String text,
					int width) throws Exception {
		if (table == null) throw new Exception("missing IntegerTable");
		if (text == null) throw new Exception("lookup key missing");
		return table.getInteger(keyType, text, width);
	}

	/**
	 * Generate the initials of a patient from the contents of a
	 * string formatted as "last^first^middle".
	 * @param name the name in the format of a DICOM PatientName element.
	 * @return the patient's initials in upper case (e.g., FML), or "X" if
	 * the name is null or empty.
	 * @throws Exception if the requested value cannot be found in the table.
	 */
	public static String initials(String name) {
		if (name == null) return "X";
		String s = name.replace('^',' ');
		s = s.replaceAll("\\s+"," ").trim();
		if (s.equals("")) return "X";
		String ss = "";
		int i = 0;
		do {
			ss += s.charAt(i);
			i = s.indexOf(" ",i) + 1;
		} while (i != 0);
		//Now move the last name initial to the end
		if (ss.length() > 1) ss = ss.substring(1) + ss.charAt(0);
		return ss.toUpperCase();
	}

	/**
	 * Generate a numeric replacement for a patient name from the contents
	 * string formatted as "last^first^middle".
	 * @param string the name to hash.
	 * @param length the number of characters (maximum) in the output
	 * string.
	 * @param wordCount the number of words to include in the hash. This
	 * can be used to exclude, for example, the middle name or initial.
	 * @return a numeric string consisting of length characters, the
	 * result of hashing the first wordCount words of the string.
	 */
	public static String hashName(
							String string,
							int length,
							int wordCount) throws Exception {
		if (wordCount <= 0) wordCount = Integer.MAX_VALUE;
		String[] words = string.split("\\^");
		string = "";
		for (int i=0; i<words.length && i<wordCount; i++) {
			string += words[i];
		}
		string = string.replaceAll("[\\s,'\\^\\.]","").toUpperCase();
		return hash(string, length);
	}

	/**
	 * Round an age field into groups of a specified size.
	 * @param ageString the age string to round.
	 * @param width the width of each bin
	 * @return the age rounded to bins of size width, where the
	 * first bin is centered on zero. If the input string ends with
	 * alphabetic characters, they are appended to the result. Thus,
	 * if the function call is ("27y",5), the result String is 25y.
	 * If the length of the result string (with suffix characters)
	 * is odd, a leading "0" is prepended.
	 * @throws Exception if the age does not parse.
	 */
	public static String round(String ageString, int width) throws Exception {
		if (ageString == null) return "";
		ageString = ageString.trim();
		if (ageString.length() == 0) return "";
		float ageFloat = Float.parseFloat(ageString.replaceAll("\\D",""));
		ageFloat /= (float)width;
		int age = Math.round(ageFloat);
		age *= width;
		String result = age + ageString.replaceAll("\\d","");
		if ((result.length() & 1) != 0) result = "0" + result;
		return result;
	}

	/**
	 * Re-identify a patient by hashing the combination of a
	 * site ID and a patient ID for the site.
	 * @param siteid the ID of the site which generated the ptid.
	 * @param ptid the ID of the patient.
	 * @return the MD5 hash of the combination of the siteid and ptid.
	 * @throws Exception if the hash fails.
	 */
	public static String hashPtID(
					String siteid,
					String ptid) throws Exception {
		return hashPtID(siteid, ptid, Integer.MAX_VALUE);
	}

	/**
	 * Re-identify a patient by hashing the combination of a
	 * site ID and a patient ID for the site.
	 * @param siteid the ID of the site which generated the ptid.
	 * @param ptid the ID of the patient.
	 * @param maxlen the maximum number of characters to return.
	 * @return the MD5 hash of the combination of the siteid and ptid
	 * as a numeric string with the .
	 * @throws Exception if the hash fails.
	 */
	public static String hashPtID(
					String siteid,
					String ptid,
					int maxlen) throws Exception {
		if (siteid == null) siteid = "";
		else siteid = siteid.trim();
		if (ptid == null) ptid = "null";
		else ptid = ptid.trim();
		return hash("[" + siteid + "]" + ptid, maxlen);
	}

	/**
	 * Generate an MD5 hash of an element text,
	 * producing a base-10 digit string.
	 * @param string the string to hash.
	 * @return the MD5 hash of the string.
	 * @throws Exception if the hash fails.
	 */
	public static String hash(String string) throws Exception {
		return hash(string, Integer.MAX_VALUE);
	}

	/**
	 * Generate an MD5 hash of an element text,
	 * producing a base-10 digit string.
	 * @param string the string to hash.
	 * @param maxlen the maximum number of characters to return.
	 * @return the MD5 hash of the string.
	 * @throws Exception if the hash fails.
	 */
	public static String hash(String string, int maxlen) throws Exception {
		if (string == null) string = "null";
		if (maxlen < 1) maxlen = Integer.MAX_VALUE;
		String result = DigestUtil.getUSMD5(string);
		if (result.length() <= maxlen) return result;
		return result.substring(0,maxlen);
	}

	/**
	 * Encrypt a string using a specified key..
	 * @param string the string to encrypt.
	 * @param keyText the text of the key.
	 * @return the base64 string containing the encrypted text.
	 * @throws Exception if the encryption fails.
	 */
	public static String encrypt(String string, String keyText) throws Exception {
		if (string == null) string = "null";
		Cipher enCipher = getCipher(keyText);
		byte[] encrypted = enCipher.doFinal(string.getBytes("UTF-8"));
		return Base64.encodeToString(encrypted);
	}

	//Get a Cipher initialized with the specified key.
	private static Cipher getCipher(String keyText) {
		try {
			Provider sunJce = new com.sun.crypto.provider.SunJCE();
			Security.addProvider(sunJce);
			byte[] key = getEncryptionKey(keyText,128);
			SecretKeySpec skeySpec = new SecretKeySpec(key,"Blowfish");

			SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
			byte[] seed = random.generateSeed(8);
			random.setSeed(seed);

			Cipher enCipher = Cipher.getInstance("Blowfish");
			enCipher.init(Cipher.ENCRYPT_MODE, skeySpec, random);
			return enCipher;
		}
		catch (Exception ex) {
			logger.error("Unable to initialize the Cipher using \""+keyText+"\"",ex);
			return null;
		}
	}

	//Make an encryption key from a string
	static String nonce = "tszyihnnphlyeaglle";
	static String pad = "===";
	private static byte[] getEncryptionKey(String keyText, int size) throws Exception {
		if (keyText == null) keyText = "";
		keyText = keyText.trim();

		//Now make it into a base-64 string encoding the right number of bits.
		keyText = keyText.replaceAll("[^a-zA-Z0-9+/]","");

		//Figure out the number of characters we need.
		int requiredChars = (size + 5) / 6;
		int requiredGroups = (requiredChars + 3) / 4;
		int requiredGroupChars = 4 * requiredGroups;

		//If we didn't get enough characters, then throw some junk on the end.
		while (keyText.length() < requiredChars) keyText += nonce;

		//Take just the right number of characters we need for the size.
		keyText = keyText.substring(0,requiredChars);

		//And return the string padded to a full group.
		keyText = (keyText + pad).substring(0,requiredGroupChars);
		return Base64.decode(keyText);
	}

	/**
	 * Add a constant to a date string.
	 * @param date the date in DICOM format.
	 * @param increment the number of days to increment the date.
	 * Positive increments make the date later; negative increments
	 * make it earlier.
	 * @return the incremented date string.
	 * @throws Exception if the date is in an illegal format.
	 */
	public static String incrementDate(String date, long increment) throws Exception {
		long inc = increment * 24 * 3600 * 1000;
		GregorianCalendar dateCal = DateUtil.getCalendar(date);
		dateCal.setTimeInMillis(dateCal.getTimeInMillis() + inc);
		return  intToString(dateCal.get(Calendar.YEAR), 4) +
				intToString(dateCal.get(Calendar.MONTH) + 1, 2) +
				intToString(dateCal.get(Calendar.DAY_OF_MONTH), 2) +
				((date.length() > 8) ? date.substring(8) : "");
	}

	/**
	 * Modify fields in a date string.
	 * @param date the date in DICOM format.
	 * @param y the value to which to reset the year field (-1 means to leave it alone).
	 * @param m the value to which to reset the month field (-1 means to leave it alone).
	 * @param d the value to which to reset the day field (-1 means to leave it alone).
	 * @return the modified date string.
	 * @throws Exception if the date is in an illegal format.
	 */
	public static String modifyDate(String date, int y, int m, int d) throws Exception {
		GregorianCalendar dateCal = DateUtil.getCalendar(date);

		if (y < 0) y = dateCal.get(Calendar.YEAR);

		if (m < 0) m = dateCal.get(Calendar.MONTH);
		else m--;

		if (d < 0) d = dateCal.get(Calendar.DAY_OF_MONTH);

		dateCal.set(y, m, d);

		return  intToString(dateCal.get(Calendar.YEAR), 4) +
				intToString(dateCal.get(Calendar.MONTH) + 1, 2) +
				intToString(dateCal.get(Calendar.DAY_OF_MONTH), 2) +
				((date.length() > 8) ? date.substring(8) : "");
	}

	// A static integer for preventing two new uids created
	// in the same millisecond from being identical
	private static int disambiguator = 0;
	// Keep track of the last time a UID was generated so
	// we can reset the disambiguator and keep it small.
	private static long lasttime = -1;

	/**
	 * Create a new UID from the current time and an
	 * incrementing value, prepending a prefix.
	 * @param prefix the prefix for the new UID.
	 * @return the new UID string.
	 * @throws Exception if the hash fails.
	 */
	public static String newUID(String prefix) throws Exception {
		//Make sure the prefix is okay.
		prefix = prefix.trim();
		if (!prefix.equals("") && !prefix.endsWith(".")) prefix += ".";
		//Get the current time in ms
		long time = System.currentTimeMillis();
		//Fix the disambiguator if the time has changed;
		if (time != lasttime) {
			lasttime = time;
			disambiguator = 0;
		}
		//Now make the UID
		return prefix
				+ Long.toString(time)
					+ "."
						+ Integer.toString(disambiguator++);
	}

	/**
	 * Create a new UID by hashing an old UID
	 * and prepending a prefix to the hashed value.
	 * @param prefix the prefix for the new UID.
	 * @param uid the UID to hash.
	 * @return the hashed UID string.
	 * @throws Exception if the hash fails.
	 */
	public static String hashUID(String prefix, String uid) throws Exception {
		//Make sure the prefix is okay.
		prefix = prefix.trim();
		if (!prefix.equals("") && !prefix.endsWith(".")) prefix += ".";
		//Create the replacement UID
		String hashString = DigestUtil.getUSMD5(uid);
		String extra = hashString.startsWith("0") ? "9" : "";
		String newuid = prefix + extra + hashString;
		if (newuid.length() > 64) newuid = newuid.substring(0,64);
		return newuid;
	}

	/**
	 * Get a string containing the current time,
	 * with a specified separator.
	 * @param separator the separator for hours, minutes, and seconds.
	 * @return the current time.
	 */
	public static String time(String separator) {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.HOUR_OF_DAY), 2)
				 + separator
				 + intToString(now.get(Calendar.MINUTE), 2)
				 + separator
				 + intToString(now.get(Calendar.SECOND), 2);
	}

	/**
	 * Get a string containing the current date,
	 * with a specified separator.
	 * @param separator the separator for year, month, and day.
	 * @return the current date.
	 */
	public static String date(String separator) {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.YEAR), 4)
				 + separator
				 + intToString(now.get(Calendar.MONTH) + 1, 2)
				 + separator
				 + intToString(now.get(Calendar.DAY_OF_MONTH), 2);
	}

	//Convert an int to a String of a specified number of digits,
	//prepending zeroes if necessary to fill the width
	private static String intToString(int n, int digits) {
		String s = Integer.toString(n);
		int k = digits - s.length();
		for (int i=0; i<k; i++) s = "0" + s;
		return s;
	}

}
