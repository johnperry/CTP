/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
	 * Protected constructor; create a LookupTable from a properties file.
	 * @param file the file containing the lookup table.
	 */
	protected LookupTable(File file) {
		this.file = file;
		this.properties = getProps(file);
		this.lastVersionLoaded = file.lastModified();
	}

	/**
	 * Get the singleton instance of a LookupTable, loading a new instance
	 * only if the properties file has changed.
	 * @param file the file containing the lookup table properties.
	 */
	public static LookupTable getInstance(File file) {
		//If there is no file, then return null.
		if (file == null) return null;

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
	 * Save the Properties object for a file.
	 * This method does nothing if a LookupTable object does not exist
	 * or cannot be instantiated for the file.
	 */
	public static void save(File file) {
		LookupTable lut = getInstance(file);
		if (lut != null) {
			saveProps(file, lut.properties);
			lut.properties = getProps(file);
		}
	}

	/**
	 * Get the Properties object for this instance.
	 * This method returns null if a LookupTable object does not exist
	 * or cannot be instantiated for the file.
	 * @return the Properties object, or null if it cannot be obtained.
	 */
	public Properties getProperties() {
		return properties;
	}

	/**
	 * Save the Properties object for this instance.
	 */
	public void save() {
		saveProps(file, properties);
	}

	/**
	 * Determine whether the script file has changed since it was last loaded.
	 * If the script file has changed in the last 5 seconds, it is ignored
	 * to ensure that we don't jump on a file that is still being modified.
	 * @return true if this LookupTable instance is up-to-date; false if the file has changed.
	 */
	public boolean isCurrent() {
		long lastModified = file.lastModified();
		long age = System.currentTimeMillis() - lastModified;
		return ( (lastVersionLoaded >= lastModified) || (age < 1000) );
	}

	/**
	 * Load a properties file.
	 * @param propsFile the file to load
	 * @return the properties, or an empty object if the load failed.
	 */
	public static Properties getProps(File propsFile) {
		Properties props = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(propsFile);
			props.load(fis);
		}
		catch (Exception returnEmptyProps) { }
		finally {
			FileUtil.close(fis);
		}
		return props;
	}

	/**
	 * Save a properties object in a file.
	 * @param propsFile the file to save
	 * @param props the properties object to save
	 */
	public static void saveProps(File propsFile, Properties props) {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream( propsFile );
			props.store( fos, propsFile.getName() );
			fos.flush();
		}
		catch (Exception e) { }
		FileUtil.close(fos);
	}

}
