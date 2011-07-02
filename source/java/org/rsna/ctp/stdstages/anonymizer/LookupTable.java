/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer;

import java.io.File;
import java.io.FileInputStream;
import java.util.Hashtable;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.rsna.util.FileUtil;

/**
 * An anonymizer lookup table.
 */
public class LookupTable {

	static final Logger logger = Logger.getLogger(LookupTable.class);

	static Hashtable<String,LookupTable> tables = new Hashtable<String,LookupTable>();

	public File file;
	public Properties properties = null;
	public long lastVersionLoaded = 0;

	/**
	 * Get the singleton instance of a LookupTable, loading a new instance
	 * if the properties file has changed.
	 * @param file the file containing the lookup table properties.
	 */
	public static LookupTable getInstance(File file) {
		//If there is no file, then return null.
		if ((file == null) || !file.exists()) return null;

		//We have a file, see if we already have an instance in the table
		String path = file.getAbsolutePath().replaceAll("\\\\","/");
		LookupTable lut = tables.get(path);
		if ( (lut != null) && lut.isCurrent() ) return lut;

		//We didn't get a current instance from the table; create one.
		lut = new LookupTable(file);

		//Put this instance in the table and then return it.
		tables.put(path, lut);
		return lut;
	}

	/**
	 * Get the Properties object for a file.
	 * This method returns null if a LookupTable object does not exist
	 * or cannot be instantiated for the file.
	 * @return the Properties object for a file, or null if it cannot be obtained.
	 */
	public static Properties getProperties(File file) {
		LookupTable lut = getInstance(file);
		if (lut == null) return null;
		return lut.properties;
	}

	/**
	 * Protected constructor; create a LookupTable from a properties file.
	 * @param file the file containing the lookup table.
	 */
	protected LookupTable(File file) {
		this.file = file;
		this.properties = getProps(file);
		this.lastVersionLoaded = file.lastModified();
	}

	/**
	 * Determine whether the script file has changed since it was last loaded.
	 * If the script file has changed in the last 5 seconds, it is ignored
	 * to ensure that we don't jump on a file that is still being modified.
	 * @return true if this DAScript instance is up-to-date; false if the file has changed.
	 */
	public boolean isCurrent() {
		long lastModified = file.lastModified();
		long age = System.currentTimeMillis() - lastModified;
		return ( (lastVersionLoaded >= lastModified) || (age < 5000) );
	}

	//Load a Properties file
	private Properties getProps(File propFile) {
		Properties props = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(propFile);
			props.load(fis);
		}
		catch (Exception ex) {
			logger.warn("Unable to load the properties file: "+propFile);
			props = null;
		}
		finally {
			if (fis != null) {
				try { fis.close(); }
				catch (Exception ex) {
					logger.warn("Unable to close the FileInputStream for loading "+propFile);
				}
			}
		}
		return props;
	}
}
