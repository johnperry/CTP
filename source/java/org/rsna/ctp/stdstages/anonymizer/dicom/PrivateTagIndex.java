/*---------------------------------------------------------------
*  Copyright 2014 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.io.File;
import java.io.InputStream;
import java.util.Hashtable;
import org.apache.log4j.Logger;
import org.rsna.util.Cache;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Encapsulates an index of private DICOM elements.
 */
public class PrivateTagIndex {

	static final Logger logger = Logger.getLogger(PrivateTagIndex.class);
	static final String xmlResource = "PrivateTags.xml";
	static PrivateTagIndex privateTagIndex = null;
	Hashtable<PrivateTagKey,PrivateTag> index = null;

	/**
	 * Get the singleton instance of the PrivateTagIndex.
	 * @return the PrivateTagIndex.
	 */
	public static PrivateTagIndex getInstance() {
		if (privateTagIndex == null) {
			privateTagIndex = new PrivateTagIndex();
		}
		return privateTagIndex;
	}

	public String getVR(String owner, int group, int element) {
		PrivateTag tag = index.get( new PrivateTagKey(owner, group, element) );
		if (tag != null) return tag.getVR();
		return "";
	}

	//The protected constructor of the singleton.
	protected PrivateTagIndex() {
		index = new Hashtable<PrivateTagKey,PrivateTag>();
		File xmlFile = Cache.getInstance().getFile(xmlResource);
		InputStream is = FileUtil.getStream( xmlFile, xmlResource );
		if (is == null) {
			logger.warn("Unable to get InputStream for "+xmlResource);
		}
		try {
			Document doc = XmlUtil.getDocument( is );
			Element root = doc.getDocumentElement();
			//********************** under construction *********************
		}
		catch (Exception unable) { }
	}

	class PrivateTagKey {
		String owner;
		int group;
		int element;

		public PrivateTagKey(String owner, int group, int element) {
			this.owner = owner.toLowerCase();
			this.group = group;
			this.element = element;
		}

		public boolean equals(Object obj) {
			if (obj instanceof PrivateTagKey) {
				PrivateTagKey p = (PrivateTagKey)obj;
				return (p.group == group) && (p.element == element) && p.owner.equals(owner);
			}
			return false;
		}
	}

	class PrivateTag {
		String vr;

		public PrivateTag(String vr) {
			this.vr = vr;
		}

		public String getVR() {
			return vr;
		}
	}
}
