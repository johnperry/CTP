/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer;

import java.io.BufferedReader;
import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
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
	static final String prefix = "..";
	boolean isCSV = false;
	String defaultKeyType = null;

	/**
	 * Protected constructor; create a LookupTable from a properties file.
	 * @param file the file containing the lookup table.
	 */
	protected LookupTable(File file, String defaultKeyType) {
		this.file = file;
		this.defaultKeyType = defaultKeyType;
		this.isCSV = file.getName().toLowerCase().endsWith(".csv");
		this.properties = getProps();
		this.lastVersionLoaded = file.lastModified();
	}

	/**
	 * Get the singleton instance of a LookupTable, loading a new instance
	 * only if the properties file has changed.
	 * @param file the file containing the lookup table properties.
	 */
	public static LookupTable getInstance(File file) {
		return getInstance(file, null);
	}

	/**
	 * Get the singleton instance of a LookupTable, loading a new instance
	 * only if the properties file has changed.
	 * @param file the file containing the lookup table properties.
	 * @param defaultKeyType the KeyType to be used for loading a CSV file.
	 */
	public static synchronized LookupTable getInstance(File file, String defaultKeyType) {
		//If there is no file, then return null.
		if (file == null) return null;

		//We have a file, see if we already have an instance in the table
		String path = file.getAbsolutePath().replaceAll("\\\\","/");
		LookupTable lut = tables.get(path);
		if (lut != null){
			if (lut.isCurrent()) return lut;
			else {
				//We got an instance, but it isn't current;
				//reload it, reuse the defaultKeyType from
				//the initial instantiation.
				lut = new LookupTable(file, lut.defaultKeyType);
			}
		}
		else {
			//We didn't get a current instance from the table; create one.
			lut = new LookupTable(file, defaultKeyType);
		}

		//Put this instance in the table and then return it.
		tables.put(path, lut);
		return lut;
	}

	/**
	 * Determine whether the file has changed since it was last loaded.
	 * If the file has changed in the last 5 seconds, it is ignored
	 * to ensure that we don't jump on a file that is still being modified.
	 * @return true if this LookupTable instance is up-to-date; false if the file has changed.
	 */
	public boolean isCurrent() {
		long lastModified = file.lastModified();
		long age = System.currentTimeMillis() - lastModified;
		return ( (lastVersionLoaded >= lastModified) || (age < 1000) );
	}

	/**
	 * Get the Properties object for this instance.
	 * @return the Properties object, or null if it does not exist.
	 */
	public Properties getProperties() {
		return properties;
	}

	/**
	 * Get the Properties object for a file.
	 * This method returns null if a LookupTable object does not exist
	 * or cannot be instantiated for the file.
	 * @param file the file containing the lookup table properties.
	 * @return the Properties object for a file, or null if it cannot be obtained.
	 */
	public static Properties getProperties(File file) {
		return getProperties(file, null);
	}

	/**
	 * Get the Properties object for a file.
	 * This method returns null if a LookupTable object does not exist
	 * or cannot be instantiated for the file.
	 * @param file the file containing the lookup table properties.
	 * @param defaultKeyType the KeyType to be used for loading a CSV file.
	 * @return the Properties object for a file, or null if it cannot be obtained.
	 */
	public static Properties getProperties(File file, String defaultKeyType) {
		LookupTable lut = getInstance(file, defaultKeyType);
		if (lut == null) return null;
		return lut.properties;
	}

	//Get a Properties object for the current file,
	//loading it as either a properties file or a CSV file.
	private Properties getProps() {
		Properties props = new Properties();
		BufferedReader br = null;
		try {
			br = new BufferedReader(
					new InputStreamReader(
						new FileInputStream(file), "UTF-8") );
			if (!isCSV) {
				try { props.load(br); }
				catch (Exception returnEmptyProps) { }
			}
			else {
				String defKeyType = "";
				boolean hasDefaultKeyType = (defaultKeyType != null);
				if (hasDefaultKeyType) defKeyType = defaultKeyType.trim() + "/";
				String line;
				while ( (line=br.readLine()) != null ) {
					String[] s = line.split(",");
					if (s.length == 2) {
						String key = s[0].trim();
						if (hasDefaultKeyType && !key.startsWith(prefix) && !key.startsWith(defaultKeyType)) {
							key = defaultKeyType + key;
						}
					}
					else if (s.length == 1) {
						String key = s[0].trim();
						if (key.startsWith(prefix)) {
							props.setProperty(key, "");
						}
					}
				}
			}
		}
		catch (Exception returnWhatWeHaveSoFar) { }
		finally { FileUtil.close(br); }
		return props;
	}

	/**
	 * Save the Properties object for this instance,
	 * saving it in the format of the original file.
	 */
	public void save() {
		if (!isCSV) {
			BufferedWriter bw = null;
			try {
				bw = new BufferedWriter(
						new OutputStreamWriter(
							new FileOutputStream(file), "UTF-8") );
				properties.store( bw, file.getName() );
				bw.flush();
			}
			catch (Exception e) { }
			finally { FileUtil.close(bw); }
		}
		else {
			StringBuffer sb = new StringBuffer();
			String[] names = new String[properties.size()];
			names = properties.stringPropertyNames().toArray(names);
			Arrays.sort(names);
			for (String name : names) {
				String value = properties.getProperty(name, "");
				sb.append( name + "," + value );
			}
			FileUtil.setText(file, sb.toString());
		}
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
