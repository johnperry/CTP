/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.objects;

import java.io.File;
import java.io.InputStream;
import java.util.Hashtable;
import org.apache.log4j.Logger;
import org.rsna.util.Cache;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Encapsulates an index of private DICOM elements.
 */
public class PrivateTagIndex {

	static final Logger logger = Logger.getLogger(PrivateTagIndex.class);
	static final String xmlResource = "PrivateTagDictionary.xml";
	static PrivateTagIndex privateTagIndex = null;
	Hashtable<String,PrivateTag> index = null;

	//The protected constructor of the singleton.
	protected PrivateTagIndex() {
		index = new Hashtable<String,PrivateTag>();
		try {
			InputStream is = FileUtil.getStream( xmlResource );
			Document doc = XmlUtil.getDocument( is );
			indexElements(doc);
		}
		catch (Exception unable) {
			logger.warn("Unable to load the standard private tag index");
		}
		//See if there is a local dictionary in the base directory
		File localDictionary = new File(xmlResource);
		if (localDictionary.exists()) {
			try {
				Document doc = XmlUtil.getDocument( localDictionary );
				indexElements(doc);
			}
			catch (Exception unable) {
				logger.warn("Unable to load the local private tag index");
			}
		}
		logger.debug("Size = "+index.size());
	}
	
	private void indexElements(Document doc) {
		Element root = doc.getDocumentElement();
		NodeList nl = root.getElementsByTagName("element");
		for (int i=0; i<nl.getLength(); i++) {
			Element el = (Element)nl.item(i);
			int group = StringUtil.getHexInt(el.getAttribute("gp"));
			String creator = el.getAttribute("cr");
			int element = StringUtil.getHexInt(el.getAttribute("el"));
			String code = el.getAttribute("code");
			String vr = el.getAttribute("vr");
			String vm = el.getAttribute("vm");
			PrivateTagKey key = new PrivateTagKey(group, creator, element);
			PrivateTag tag = new PrivateTag(vr, vm, code);
			index.put(key.toString(), tag);
		}
	}

	/**
	 * Get the singleton instance of the PrivateTagIndex.
	 * @return the PrivateTagIndex.
	 */
	public static synchronized PrivateTagIndex getInstance() {
		if (privateTagIndex == null) {
			privateTagIndex = new PrivateTagIndex();
		}
		return privateTagIndex;
	}
	
	/**
	 * Get the size of the index.
	 * @return the size of the index.
	 */
	public int size() {
		return index.size();
	}

	/**
	 * Get the VR of a private attribute data element.
	 * @param owner the string declaring the owner of the block.
	 * @param group the group number.
	 * @param element the element number within the block.
	 * @return the VR, or the empty string if the element is unknown.
	 */
	public String getVR(int group, String owner, int element) {
		if (owner != null) {
			return getVR(new PrivateTagKey(group, owner, element));
		}
		return "";
	}

	/**
	 * Get the VR of a private attribute data element.
	 * @param owner the string declaring the owner of the block.
	 * @param element the tag of the element (ggggeeee).
	 * @return the VR, or the empty string if the element is unknown.
	 */
	public String getVR(String owner, int element) {
		if (owner != null) {
			return getVR(new PrivateTagKey(owner, element));
		}
		return "";
	}

	private String getVR(PrivateTagKey key) {
		PrivateTag tag = index.get(key.toString());
		if (tag != null) return tag.getVR();
		return "";
	}
	
	/**
	 * Get the VM of a private attribute data element.
	 * @param owner the string declaring the owner of the block.
	 * @param group the group number.
	 * @param element the element number within the block.
	 * @return the VM, or the empty string if the element is unknown.
	 */
	public String getVM(int group, String owner, int element) {
		if (owner != null) {
			return getVM(new PrivateTagKey(group, owner, element));
		}
		return "";
	}

	/**
	 * Get the VM of a private attribute data element.
	 * @param owner the string declaring the owner of the block.
	 * @param element the tag of the element (ggggeeee).
	 * @return the VM, or the empty string if the element is unknown.
	 */
	public String getVM(String owner, int element) {
		if (owner != null) {
			return getVM(new PrivateTagKey(owner, element));
		}
		return "";
	}

	private String getVM(PrivateTagKey key) {
		PrivateTag tag = index.get(key.toString());
		if (tag != null) return tag.getVM();
		return "";
	}
	
	/**
	 * Get the code of a private attribute data element.
	 * @param owner the string declaring the owner of the block.
	 * @param group the group number.
	 * @param element the element number within the block.
	 * @return the code, or the empty string if the element is unknown.
	 */
	public String getCode(int group, String owner, int element) {
		if (owner != null) {
			return getCode(new PrivateTagKey(group, owner, element));
		}
		return "";
	}

	/**
	 * Get the code of a private attribute data element.
	 * @param owner the string declaring the owner of the block.
	 * @param element the tag of the element (ggggeeee).
	 * @return the code, or the empty string if the element is unknown.
	 */
	public String getCode(String owner, int element) {
		if (owner != null) {
			return getCode(new PrivateTagKey(owner, element));
		}
		return "";
	}
	
	private String getCode(PrivateTagKey key) {
		PrivateTag tag = index.get(key.toString());
		if (tag != null) return tag.getCode();
		return "";
	}
	
	class PrivateTagKey {
		String owner;
		int group;
		int element;

		public PrivateTagKey(int group, String owner, int element) {
			this.owner = owner.toUpperCase();
			this.group = group;
			this.element = element & 0xFF;
		}
		
		public PrivateTagKey(String owner, int tag) {
			this.owner = owner.toUpperCase();
			this.group = (tag >> 16) & 0xffff;
			this.element = tag & 0xff;
		}

		public String toString() {
			return String.format("(%04x[%s]%02x)", group, owner, element);
		}
	}

	class PrivateTag {
		String vr;
		String vm;
		String code;

		public PrivateTag(String vr, String vm, String code) {
			this.vr = vr;
			this.vm = vm;
			this.code = code;
		}

		public String getVR() {
			return vr;
		}

		public String getVM() {
			return vm;
		}

		public String getCode() {
			return code;
		}
		
		public String toString() {
			return "vr=\""+vr+"\"; vm=\""+vm+"\"; "+code;
		}
	}
}
