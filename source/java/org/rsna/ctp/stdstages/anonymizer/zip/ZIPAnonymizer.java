/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.zip;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.zip.*;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.xml.XMLAnonymizer;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;

/**
 * The MIRC Zip anonymizer. This anonymizer provides de-identification and
 * re-identification of the manifests of ZipObjects for clinical trials. Each element
 * is scriptable. The script language is documented on the MIRC wiki.
 * See the <a href="http://mircwiki.rsna.org">MIRC wiki</a> for more more information.
 */
public class ZIPAnonymizer {

	static final Logger logger = Logger.getLogger(ZIPAnonymizer.class);

	/**
	 * Anonymizes the input file, writing the result to the output file.
	 * The fields to anonymize are scripted in the properties file.
	 * @param inFile the file to anonymize.
	 * @param outFile the output file. It may be same as inFile you if want
	 * to anonymize in place.
	 * @param cmdFile the file containing the anonymization commands.
	 * @return AnonymizerStatus.OK if successful; AnonymizerStatus.QUARANTINE otherwise,
	 * in which case the input file is not modified.
	 */
	public static AnonymizerStatus anonymize(
			File inFile,
			File outFile,
			File cmdFile) {

		try {
			ZipObject zob = new ZipObject(inFile);
			Document manifestXML = zob.getManifestDocument();
			AnonymizerStatus status
					= XMLAnonymizer.anonymize(manifestXML, cmdFile);
			if (status.isOK()) {
				File tempFile =
					File.createTempFile("TMP-", outFile.getName(), outFile.getParentFile());
				if (copyToNewZipFile(inFile, tempFile, XmlUtil.toString(manifestXML))) {
					inFile.delete();
					tempFile.renameTo(outFile);
					return AnonymizerStatus.OK(outFile,"");
				}
				tempFile.delete();
				return AnonymizerStatus.QUARANTINE(inFile,"Unable to save anonymized file");
			}
			else return AnonymizerStatus.QUARANTINE(inFile,status.getMessage());

		}
		catch (Exception ex) {
			logger.warn("error: ",ex);
			return AnonymizerStatus.QUARANTINE(inFile,ex.getMessage());
		}
	}

	private static boolean copyToNewZipFile(File inFile, File outFile, String manifest) {
		if (!inFile.exists()) return false;

		ZipOutputStream zout = null;
		BufferedInputStream zin = null;
		ZipFile inZipFile = null;
		byte[] buffer = new byte[2048];
		int bytesread;

		try {
			//Set up to write to the output zip file
			outFile.delete();
			outFile = new File(outFile.getAbsolutePath());
			zout = new ZipOutputStream(new FileOutputStream(outFile));

			//Set up to read the input zip file
			inZipFile = new ZipFile(inFile);

			//Get the entries in the input zip file and process them.
			Enumeration<? extends ZipEntry> zipEntries = inZipFile.entries();
			while (zipEntries.hasMoreElements()) {

				ZipEntry entry = zipEntries.nextElement();
				//Only process non-directory entries.
				if (!entry.isDirectory()) {
					String name = entry.getName().replace('/',File.separatorChar);
					if (name.equals(ZipObject.manifestName)) {
						//This is the manifest. Replace it with the new one.
						ZipEntry outze = new ZipEntry(name);
						zout.putNextEntry(outze);
						byte[] manifestBytes = manifest.getBytes("UTF-8");
						zout.write(manifestBytes);
						zout.closeEntry();
					}
					else {
						//Not the manifest, just copy it.
						ZipEntry outze = new ZipEntry(name);
						zout.putNextEntry(outze);
						zin = new BufferedInputStream(inZipFile.getInputStream(entry));
						int n = 0;
						while ((bytesread = zin.read(buffer)) > 0) zout.write(buffer,0,bytesread);
						zout.closeEntry();
						zin.close();
					}
				}
			}
			zout.close();
			inZipFile.close();
			return true;
		}
		catch (Exception e) {
			try {
				if (inZipFile != null) inZipFile.close();
				if (zin != null) zin.close();
				if (zout != null) zout.close();
			}
			catch (Exception ignore) { logger.warn("Unable to close the zip file."); }
			outFile.delete();
			return false;
		}
	}

}
