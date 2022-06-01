/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer;

import java.io.File;
import java.util.Properties;
import jdbm.RecordManager;
import jdbm.htree.HTree;
import org.apache.log4j.Logger;
import org.rsna.util.JdbmUtil;

/**
 * A database for tracking assigned integer replacements for text strings.
 */
public class IntegerTable {

	static final Logger logger = Logger.getLogger(IntegerTable.class);

	File dir;
    public RecordManager recman = null;
    public HTree index = null;

	/**
	 * Constructor; create an IntegerTable from a database file.
	 * @param dir the directory in which the database is to be created.
	 * @throws Exception if the table cannot be loaded.
	 */
	public IntegerTable(File dir) throws Exception {
		this.dir = dir;
		File indexFile = new File(dir, "integers");
		recman = JdbmUtil.getRecordManager( indexFile.getAbsolutePath() );
		index = JdbmUtil.getHTree( recman, "index" );
		if (index == null) {
			logger.warn("Unable to load the integer database.");
			throw new Exception("Unable to load the integer database.");
		}
	}

	/**
	 * Commit and close the IntegerTable.
	 */
	public void close() {
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
			}
			catch (Exception ignore) { }
		}
	}

	/**
	 * Get a String containing an integer replacement for text of a specified type.
	 * @param type any String identifying the category of text being replaced, for example "ptid".
	 * @param text the text string to be replaced by an integer string.
	 * @param width the minimum width of the replacement string. If the width parameter
	 * is negative or zero, no padding is provided.
	 * @return the replacement string, with leading zeroes if necessary to pad the
	 * replacement string to the required width.
	 */
	public synchronized String getInteger(String type, String text, int width) {
		try {
			text = text.trim();
			type = type.trim();
			logger.debug("getInteger: \""+type+"\", \""+text+"\", "+width);
			String key = type + "/" + text;
			logger.debug("...searching for "+key);
			Integer value = (Integer)index.get(key);
			if (value == null) {
				logger.debug("...got null");
				String lastIntKey = "__" + type + "__";
				logger.debug("...searching for "+lastIntKey);
				Integer lastInt = (Integer)index.get(lastIntKey);
				logger.debug("...got "+lastInt);
				if (lastInt == null) lastInt = new Integer(0);
				value = new Integer( lastInt.intValue() + 1 );
				logger.debug("...storing "+value+" for "+lastIntKey);
				index.put(lastIntKey, value);
				logger.debug("...storing "+value+" for "+key);
				index.put(key, value);
				logger.debug("...success");
				recman.commit();
			}
			else {
				logger.debug("...got "+value);
				logger.debug("...success");
			}
			int intValue = value.intValue();
			String format = (width > 0) ? ("%0"+width+"d") : ("%d");
			return String.format(format, intValue);
		}
		catch (Exception e) {
			logger.warn("Unable to create integer for (\""+type+"\",\""+text+"\","+width+")", e);
			return "error";
		}
	}

}
