/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
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
import org.rsna.util.Base64;
import org.rsna.util.DateUtil;
import org.rsna.util.DigestUtil;

/**
 * MIRC anonymizer functions. These static methods provide the
 * data modification functions used by the MIRC anonymizers.
 * See the <a href="http://mircwiki.rsna.org">MIRC wiki</a> for more more information.
 */
public class AnonymizerFunctions {

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
		key = keyType.trim() + "/" + key.trim();
		String value = table.getProperty(key);
		if (value == null) throw new Exception("missing key ("+key+") in lookup table");

		value = value.trim();
		int maxIndirects = 10;
		int indirects = maxIndirects;
		while (value.startsWith("@") && value.contains("/") && (indirects > 0)) {
			//This is an indirection
			key = value.substring(1).trim();
			value = table.getProperty(key);
			if (value == null) throw new Exception("missing key ("+key+") in lookup table");
			value = value.trim();
			indirects--;
		}
		if (indirects <= 0) throw new Exception("more than "+maxIndirects+" indirects");
		return value;
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
	 */
	public static String initials(String name) {
		if (name == null) return "X";
		String s = name.replace('^',' ').replace(',',' ');
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
	 * @throws Exception if the string cannot be hashed.
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
	 * as a numeric string.
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
	 * Decrypt a string using a specified key.
	 * @param string the base64 string to decrypt.
	 * @param keyText the text of the key.
	 * @return the decrypted string.
	 * @throws Exception if the encryption fails.
	 */
	public static String decrypt(String string, String keyText) throws Exception {
		if (string == null) string = "null";
		Cipher cipher = getCipher(keyText, Cipher.DECRYPT_MODE);
		byte[] encrypted = Base64.decode(string);
		//System.out.println("decrypt: string: \""+string+"\"");
		//System.out.println("decrypt: encrypted.length = "+encrypted.length);
		//printBytes("decrypt: encrypted bytes", encrypted);
		//System.out.println("----------------");
		byte[] decrypted = cipher.doFinal(encrypted);
		return new String(decrypted, "UTF-8");
	}

	/**
	 * Encrypt a string using a specified key.
	 * @param string the string to encrypt.
	 * @param keyText the text of the key.
	 * @return the base64 string containing the encrypted text.
	 * @throws Exception if the encryption fails.
	 */
	public static String encrypt(String string, String keyText) throws Exception {
		if (string == null) string = "null";
		Cipher enCipher = getCipher(keyText, Cipher.ENCRYPT_MODE);
		byte[] encrypted = enCipher.doFinal(string.getBytes("UTF-8"));
		String result = Base64.encodeToString(encrypted);
		//System.out.println("encrypt: encrypted.length = "+encrypted.length);
		//printBytes("encrypt: encrypted bytes", encrypted);
		//System.out.println("encrypt: string: \""+result+"\"");
		//System.out.println("----------------");
		return result;
	}
	
	private static void printBytes(String s, byte[] bytes) {
		System.out.println(s);
		try { System.out.println(new String(bytes, "UTF-8")); }
		catch (Exception skip) { }
		for (int i=0; i<bytes.length; i++) {
			System.out.println(String.format("%6d: %02x", i, bytes[i]));
		}
	}

	//Get a Cipher initialized with the specified key.
	static SecureRandom secureRandom = null;
	private static Cipher getCipher(String keyText, int mode) {
		try {
			Provider sunJce = new com.sun.crypto.provider.SunJCE();
			Security.addProvider(sunJce);
			byte[] key = getEncryptionKey(keyText,128);
			SecretKeySpec skeySpec = new SecretKeySpec(key,"Blowfish");

			if (secureRandom == null) {
				String osname = System.getProperty("os.name");
				if ((osname != null) && osname.toLowerCase().contains("windows")) {
					secureRandom = SecureRandom.getInstance("SHA1PRNG", "SUN");
				}
				else secureRandom = new SecureRandom();
				byte[] seed = secureRandom.generateSeed(8);
				secureRandom.setSeed(seed);
			}
			Cipher cipher = Cipher.getInstance("Blowfish");
			cipher.init(mode, skeySpec, secureRandom);
			return cipher;
		}
		catch (Exception ex) { return null; }
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
	 * Encrypt a string using a Caesar cipher with a specified offset.
	 * This method offsets characters within their group (lower case,
	 * upper case, numeric). All other characters are not modified.
	 * @param string the string to encrypt.
	 * @param offset the offset (positive for alphabetic order, negative
	 * for reverse alphabetic order.
	 * @return the encrypted string
	 */
	public static String encrypt(String string, int offset) {
		char[] chars = string.toCharArray();
		for (int i=0; i<chars.length; i++) {
			char c = chars[i];
			if ((c >= 'a') && (c <= 'z')) {
				int k = ((c - 'a') + offset) % 26;
				if (k < 0) k += 26;
				chars[i] = (char)('a' + k);
			}
			else if ((c >= 'A') && (c <= 'Z')) {
				int k = ((c - 'A') + offset) % 26;
				if (k < 0) k += 26;
				chars[i] = (char)('A' + k);
			}
			else if ((c >= '0') && (c <= '9')) {
				int k = ((c - '0') + offset) % 10;
				if (k < 0) k += 10;
				chars[i] = (char)('0' + k);
			}
		}
		return new String(chars);
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
		StringBuffer sb = new StringBuffer();
		String[] dates = date.split("\\\\");
		for (String aDate : dates) {
			if (sb.length() != 0) sb.append("\\");
			sb.append(incDate(aDate, increment));
		}
		return sb.toString();
	}
	
	private static String incDate(String date, long increment) throws Exception {
		boolean iso = date.contains("-");
		int dlen = (iso ? 10 : 8);
		GregorianCalendar dateCal = DateUtil.getCalendar(date);
		dateCal.add(GregorianCalendar.DATE, (int)increment);
		return  intToString(dateCal.get(Calendar.YEAR), 4) +
				(iso ? "-" : "") +
				intToString(dateCal.get(Calendar.MONTH) + 1, 2) +
				(iso ? "-" : "") +
				intToString(dateCal.get(Calendar.DAY_OF_MONTH), 2) +
				((date.length() > dlen) ? date.substring(dlen) : "");
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
		StringBuffer sb = new StringBuffer();
		String[] dates = date.split("\\\\");
		for (String aDate : dates) {
			if (sb.length() != 0) sb.append("\\");
			sb.append(modDate(aDate, y, m, d));
		}
		return sb.toString();
	}
	
	private static String modDate(String date, int y, int m, int d) throws Exception {
		boolean iso = date.contains("-");
		int dlen = (iso ? 10 : 8);
		GregorianCalendar dateCal = DateUtil.getCalendar(date);
		if (y < 0) y = dateCal.get(Calendar.YEAR);
		if (m < 0) m = dateCal.get(Calendar.MONTH);
		else m--;
		if (d < 0) d = dateCal.get(Calendar.DAY_OF_MONTH);
		dateCal.set(y, m, d);
		return  intToString(dateCal.get(Calendar.YEAR), 4) +
				(iso ? "-" : "") +
				intToString(dateCal.get(Calendar.MONTH) + 1, 2) +
				(iso ? "-" : "") +
				intToString(dateCal.get(Calendar.DAY_OF_MONTH), 2) +
				((date.length() > dlen) ? date.substring(dlen) : "");
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
