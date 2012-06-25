/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;

/**
 * A DICOMPixelAnonymizer script.
 */
public class PixelScript {

	static final Logger logger = Logger.getLogger(PixelScript.class);

	List<Signature> signatures = null;

   /**
	* Constructor; create a PixelScript from a file.
	* @param file the file containing the script.
	*/
	public PixelScript(File file) {
		signatures = getSignatures(FileUtil.getText(file, FileUtil.utf8));
	}

   /**
	* Get the Regions for a specific DicomObject. This method tests
	* the supplied object against the individual signatures and returns the
	* list of Regions associated with the first matching signature.
	* @param dicomObject the DicomObject for which to find the Regions.
	* @return the regions associated with the DicomObject.
	*/
	public Signature getMatchingSignature(DicomObject dicomObject) {
		if (signatures != null) {
			for (Signature sig : signatures) {
				if (dicomObject.matches(sig.script).getResult()) return sig;
			}
		}
		return null;
	}

	//Parse a text string and return a list of Signatures
	private List<Signature> getSignatures(String s) {
		LinkedList<Signature> signatures = new LinkedList<Signature>();
		Pattern scriptPattern = Pattern.compile("\\{[^\\}]*\\}");
		Pattern regionPattern = Pattern.compile("\\([^\\)]*\\)");

		Matcher scriptMatcher = scriptPattern.matcher(s);
		boolean foundScript = scriptMatcher.find();
		while (foundScript) {
			//capture the script and create the Signature
			String script = scriptMatcher.group();
			Signature signature = new Signature(script.substring(1, script.length()-1));
			signatures.add(signature);
			int scriptEnd = scriptMatcher.end();

			//find the next script
			foundScript = scriptMatcher.find();

			//get the text between the last script and this one
			int scriptStart = s.length();
			if (foundScript) scriptStart = scriptMatcher.start();
			String regionString = s.substring(scriptEnd, scriptStart);

			//find all the regions in the regionString and add them to the signature
			Matcher regionMatcher = regionPattern.matcher(regionString);
			while (regionMatcher.find()) {
				String region = regionMatcher.group();
				signature.addRegion(region);
			}
		}
		return signatures;
	}
}
