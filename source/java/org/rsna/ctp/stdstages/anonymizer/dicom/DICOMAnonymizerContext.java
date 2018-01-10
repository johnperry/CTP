/*---------------------------------------------------------------
 *  Copyright 2016 by the Radiological Society of North America
 *
 *  This source software is released under the terms of the
 *  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
 *----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.data.SpecificCharacterSet;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.VRs;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.PrivateNameIndex;
import org.rsna.ctp.objects.PrivateTagIndex;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.util.StringUtil;

import org.apache.log4j.Logger;

/**
 * Encapsulate the context of the anonymizer, including
 * the script, the lookup table, the integer table,
 * the root dataset, and the current dataset.
 */
public class DICOMAnonymizerContext {

	static final Logger logger = Logger.getLogger(DICOMAnonymizerContext.class);

	public Properties cmds;
	public Properties lkup;
	public IntegerTable intTable;
	public FileMetaInfo inFMI;
	public Dataset inDS;
	public Dataset outDS;

	public boolean rpg; //remove private groups
	public boolean rue; //remove unscripted elements
	public boolean rol;	//remove overlays
	public boolean rc;	//remove curves
	public boolean kspe; //keep safe private elements

	public int[] keepGroups;
	public Hashtable<Integer,String> scriptTable;

	LinkedList<Dataset> inStack;
	LinkedList<Dataset> outStack;
	PrivateGroupsIndex pgIndex;
	PrivateNameIndex privateElementNames;

   /**
	 * Organize all the data required for anonymization.
	 * @param cmds the complete set of scripts.
	 * @param lkup the local lookup table.
     * @param intTable the integer table containing integer remappings for elements.
	 * @param inDS the input dataset.
	 * @param outDS the dataset to be modified
	 */
    public DICOMAnonymizerContext(
				Properties cmds,
				Properties lkup,
				IntegerTable intTable,
				Dataset inDS,
				Dataset outDS) {

		this.cmds = cmds;
		this.lkup = lkup;
		this.intTable = intTable;
		this.inDS = inDS;
		this.inFMI = inDS.getFileMetaInfo();
		this.outDS = outDS;

		//Build the index of private groups
		pgIndex = new PrivateGroupsIndex(inDS);

		//Make a table to hold locally defined names for private elements
		privateElementNames = new PrivateNameIndex();

		//Set up the booleans to handle the global cases
		rpg = (cmds.getProperty("remove.privategroups") != null);
		rue = (cmds.getProperty("remove.unspecifiedelements") != null);
		rol = (cmds.getProperty("remove.overlays") != null);
		rc  = (cmds.getProperty("remove.curves") != null);
		kspe = (cmds.getProperty("keep.safeprivateelements") != null);

		//Set up the keepGroups and the script Hashtable
		LinkedList<String> list = new LinkedList<String>();
		scriptTable = new Hashtable<Integer,String>();

		for (Enumeration it=cmds.keys(); it.hasMoreElements(); ) {
			String key = (String)it.nextElement();

			if (key.startsWith("set.[")) {
				int k = findClosingBracket(key, 4);
				if (k > 0) {
					int tag = getElementTag(key.substring(5, k));
					if (tag != 0) {
						//Store the script
						Integer tagInteger = new Integer(tag);
						scriptTable.put( tagInteger, cmds.getProperty(key) );

						//If this is a private group element with a name, index the name.
						if ((tag & 0x10000) != 0) {
							String name = key.substring(k+1).trim();
							if (!name.equals("")) privateElementNames.putTag(name, tag);
						}
					}
				}
			}
			else if (key.startsWith("keep.group")) {
				list.add(key.substring("keep.group".length()).trim());
			}
		}

		//Convert the list to an int[]
		Iterator<String> iter = list.iterator();
		keepGroups = new int[list.size()];
		for (int i=0; i<keepGroups.length; i++) {
			try { keepGroups[i] = Integer.parseInt(iter.next(),16); }
			catch (Exception ex) { keepGroups[i] = 0; }
		}
		Arrays.sort(keepGroups);

		inStack = new LinkedList<Dataset>();
		outStack = new LinkedList<Dataset>();
	}

	private int findClosingBracket(String s, int start) {
		int count = 0;
		for (int i=start; i<s.length(); i++) {
			char c = s.charAt(i);
			if (c == '[') count++;
			else if (c == ']') count--;
			if (count == 0) return i;
		}
		return -1;
	}

	/*
	 * Push the current datasets on their respective stacks
	 * and set new datasets in place.
	 * @param inDS the new input dataset.
	 * @param outDS the new dataset to be modified
	 */
	public synchronized void push(Dataset inDS, Dataset outDS) {
		inStack.addFirst(this.inDS);
		outStack.addFirst(this.outDS);
		this.inDS = inDS;
		this.outDS = outDS;
	}

	/*
	 * Pop the dataset stacks, replacing the current datasets
	 * with the values from the stacks. If the stacks are empty,
	 * do nothing.
	 */
	public synchronized void pop() {
		if (inStack.size() > 0) {
			this.inDS = inStack.removeFirst();
			this.outDS = outStack.removeFirst();
		}
	}

	/*
	 * Test whether the context is at the root dataset.
	 */
	public boolean isRootDataset() {
		return (inStack.size() == 0);
	}

	/*
	 * Get the root dataset.
	 */
	public Dataset getRootDataset() {
		logger.debug("inStack.size() = "+inStack.size());
		if (inStack.size() == 0) return inDS;
		return inStack.peekFirst();
	}

	/*
	 * Get the replacement script for a tag
	 * @param tag the tag value
	 * @return the text of the replacement script, or null if
	 * no script is available for the specified tag.
	 */
	public String getScriptFor(int tag) {
		return scriptTable.get( new Integer(tag) );
	}

	/*
	 * Get the replacement script for a tag Integer
	 * @param tag the tag value
	 * @return the text of the replacement script, or null if
	 * no script is available for the specified tag.
	 */
	public String getScriptFor(Integer tag) {
		return scriptTable.get(tag);
	}

	/*
	 * Get the value of a parameter.
	 * This method removes a leading '@' or '$' from the name, if present.
	 * @param param the name of the parameter
	 * @return the text of the value of the parameter, or null if
	 * the parameter is undefined.
	 */
	public String getParam(String param) {
		if (param == null) return null;
		param = param.trim();
		if (param.startsWith("@") || param.startsWith("$")) {
			return cmds.getProperty("param." + param.substring(1));
		}
		return param;
	}

	/*
	 * See if a group is included in the keep group global commands.
	 * @param group the group number, specified as 0xGGGG
	 * @return true if the group is included in the keep group commands; false otherwise.
	 */
	public boolean containsKeepGroup(int group) {
		return (Arrays.binarySearch(keepGroups, group) >= 0);
	}

	/*
	 * Get an element from the input dataset.
	 * @param tag the element to get from the input dataset
	 * @return the element identified by the tag.
	 */
	public DcmElement get(int tag) {
		return inDS.get(tag);
	}

	/*
	 * Get the SpecificCharacterSet of the input dataset.
	 * @return the SpecificCharacterSet of the input dataset.
	 */
	public SpecificCharacterSet getSpecificCharacterSet() {
		return inDS.getSpecificCharacterSet();
	}

	/*
	 * Get the contents of an input dataset element by tagName.
	 * @param tagName the dcm4che name of the element
	 * @param thisTag the tag to be used for the "this" keyword
	 * @return the value of the specified element in the current dataset,
	 * or the empty string if the element is missing.
	 */
	public String contents(String tagName, int thisTag) {
		return contents(tagName, thisTag, inDS);
	}

	/*
	 * Get the contents of an element of a specified dataset by tagName.
	 * @param tagName the dcm4che name of the element
	 * @param thisTag the tag to be used for the "this" keyword
	 * @param ds the dataset from which to obtain the element
	 * @return the value of the specified element in the current dataset,
	 * or the empty string if the element is missing.
	 */
	public String contents(String tagName, int thisTag, Dataset ds) {
		String value = contentsNull(tagName, thisTag, ds);
		if (value == null) value = "";
		return value;
	}

	/*
	 * Get the contents of an input dataset element by tagName.
	 * @param tagName the dcm4che name of the element
	 * @param thisTag the tag to be used for the "this" keyword
	 * @return the value of the specified element in the current dataset,
	 * or null if the element is missing.
	 */
	public String contentsNull(String tagName, int thisTag) {
		return contentsNull(tagName, thisTag, inDS);
	}
	
	/*
	 * Get the contents of an element of a specified dataset by tagName.
	 * @param tagName the dcm4che name of the element
	 * @param thisTag the tag to be used for the "this" keyword
	 * @param ds the dataset from which to obtain the element
	 * @return the value of the specified element in the current dataset,
	 * or null if the element is missing.
	 */
	public String contentsNull(String tagName, int thisTag, Dataset ds) {
		if (tagName.equals("this")) return contents(thisTag);
		return DicomObject.getElementValue(inFMI, ds, tagName, privateElementNames);
	}
	
	/*
	 * Get the contents of an input dataset element by tag,
	 * handling CTP elements specially.
	 * @param tag the element tag
	 * @return the value of the specified element in the current dataset,
	 * or null if the element is missing.
	 */
	public String contents(int tag) {
		DcmElement de = null;
		if ((tag & 0x7FFFFFFF) < 0x80000) {
			de = inFMI.get(tag);
		}
		else de = inDS.get(tag);
		return DicomObject.getElementValue(de, inDS);
	}
	
	/*
	 * See if the input dataset contains a specific element.
	 * @param tag the tag to test for in the input dataset.
	 * @return true if the element is present in the input dataset; false otherwise.
	 */
	public boolean contains(int tag) {
		return inDS.contains(tag);
	}

	/*
	 * Store a value in the output dataset.
	 * If the output dataset is null, this method does nothing.
	 *
	 * This method works around the bug in dcm4che which inserts the wrong
	 * VR (SH) when storing an empty element of VR = PN. It also handles the
	 * problem in older dcm4che versions which threw an exception when an
	 * empty DA element was created. It also forces the VR of private
	 * elements to LO. And finally, it handles multi-valued elements.
	 * @param tag the element tag.
	 * @param vr the value representation.
	 * @param value the value to store.
	 */
	public void putXX(int tag, int vrInt, String value) throws Exception {
		if (outDS == null) return;
		if (value == null) value = "";
		if ((tag & 0x10000) != 0) {
			if ((tag & 0xffff) < 0x100) outDS.putLO(tag, value);
			else {
				String creator = getPrivateCreator(tag);
				if (!creator.equals("")) {
					PrivateTagIndex ptIndex = PrivateTagIndex.getInstance();
					String vr = ptIndex.getVR(creator, tag);
					if (!vr.equals("")) {
						try { 
							outDS.putXX(tag, VRs.valueOf(vr), value);
							return;
						}
						catch (Exception useDefault) { }
					}
				}				
				if (value.length() <= 64) outDS.putLO(tag, value);
				else outDS.putLT(tag, value);
			}
		}
		else if (value.equals("")) {
			if (vrInt == VRs.PN) outDS.putXX(tag, vrInt, " ");
			else if (vrInt == VRs.SQ) {
				if (!outDS.contains(tag)) outDS.putSQ(tag);
			}
			else outDS.putXX(tag, vrInt);
		}
		else {
			//Do this in such a way that we handle multivalued elements.
			String[] s = value.split("\\\\");
			outDS.putXX(tag, vrInt, s);
		}
	}
	
	/*
	 * Get the private creator string for a private data element.
	 * @param tag the tag.
	 * @return the the block owner or the tag, or the empoty string if the element
	 * is not a private data element.
	 */
	public String getPrivateCreator(int tag) {
		if ((tag & 0x10000) != 0) {
			int block = (tag & 0xFF00) >> 8;
			if (block != 0) {
				String owner;
				int pce = (tag & 0xFFFF0000) | block;
				//First see if the element is in outDS.
				try { 
					owner = outDS.getString(pce);
					if (owner != null) return owner;
				}
				catch (Exception notThere) { }
				//See if it is in inDS;
				try { 
					owner = inDS.getString(pce);
					if (owner != null) return owner;
				}
				catch (Exception notThere) { }
			}
		}
		return "";
	}
	
	/**
	 * Get the tag for an element name, using the current dataset
	 * (inDS) if the name specifies a private element.
	 * @param name the dcm4che element name or coded hex value.
	 * @return the tag, or zero if the name cannot be parsed as
	 * an element specification
	 */
	public int getElementTag(String name) {
		return getElementTag(name, inDS);
	}
	
	/**
	 * Get the tag for a DICOM element. This
	 * method supports dcm4che names as well as hex strings,
	 * with or without enclosing parentheses or square brackets
	 * and with or without a comma separating the group and the
	 * element numbers. This method also supports private group
	 * names defined in the script. This method differs from the
	 * DicomObject method of the same name in that it also supports
	 * names for private group elements in the form 0009[ID]02,
	 * where ID is the block owner ID. Examples of element
	 * specifications containing block owner IDs are:
	 *<ul>
	 *<li>9,[blockID]02
	 *<li>(9,[blockID]02)
	 *<li>[9,[blockID]02]
	 *<li>0009[blockID]02
	 *</ul>
	 * @param name the dcm4che element name or coded hex value.
	 * @param ds the dataset to use for finding a private element.
	 * @return the tag, or zero if the name cannot be parsed as
	 * an element specification
	 */
	public int getElementTag(String name, Dataset ds) {
		if (name == null) return 0;
		name = name.trim();
		int k = name.length() - 1;
		if (name.startsWith("[") && name.endsWith("]")) name = name.substring(1, k).trim();
		else if (name.startsWith("(") && name.endsWith(")")) name = name.substring(1, k).trim();
		
		//Try it as a standard element specification
		int tag = DicomObject.getElementTag(name);
		if (tag != 0) return tag;
		
		//Try it as a private element name
		tag = privateElementNames.getTag(name);
		if (tag != 0) return tag;

		//Try to match it as a private group element with a block specification
		return DicomObject.getPrivateElementTag(ds, name);
	}
	
	/**
	 * Get the name of the creator block associated with a private element.
	 * @param tag the element.
	 * @return the value of the creator element claiming the block in which
	 * the tag resides, or the empty string if the tag is not a private element
	 * or the creator element does not exist.
	 */
	public String getCreator(int tag) {
		if ((tag & 0x10000) == 0) return "";
		int group = tag & 0xffff0000;
		int block = (tag >> 8) & 0xff;
		int creatorTag = group | block;
		return inDS.getString(creatorTag, "");
	}

	class PrivateGroupsIndex {

		Hashtable<Integer,PrivateGroupIndex> index;
		Dataset ds;
		SpecificCharacterSet cs;

		public PrivateGroupsIndex( Dataset ds ) {
			this.ds = ds;
			cs = ds.getSpecificCharacterSet();
			index = new Hashtable<Integer,PrivateGroupIndex>();

			for (Iterator it=ds.iterator(); it.hasNext(); ) {
				DcmElement el = (DcmElement)it.next();
				int tag = el.tag();
				int group = (tag >> 16) & 0xFFFF;
				int element = tag & 0xFFFF;
				if ( ((group & 1) != 0) && (element < 0x100)) {

					//This is a private group element that claims a block.
					try {
						String blockOwnerID = el.getString(cs);
						if ((blockOwnerID != null) && !(blockOwnerID=blockOwnerID.trim()).equals("")) {

							//Get the index of this group
							Integer gpInteger = new Integer(group);
							PrivateGroupIndex idx = index.get( gpInteger );
							if (idx == null) {
								idx = new PrivateGroupIndex();
								index.put( gpInteger, idx );
							}

							//Store the mapping for this block.
							//Note: this implementation requires that all blocks
							//within a single private group be claimed with unique IDs.
							//The standard doesn't seem to require this constraint, but
							//objects in practice seem to observe it. There is a CP
							//inprocess to require it.
							idx.put( blockOwnerID, new Integer(tag) );
						}
					}
					catch (Exception skip) { }
				}
			}
		}

		public int getTagForID(int group, String id) {
			if (id == null) return 0;
			PrivateGroupIndex idx = index.get( new Integer(group) );
			if (idx == null) return 0;
			Integer tagInteger = idx.get(id.trim());
			if (tagInteger == null) return 0;
			return tagInteger.intValue();
		}

		class PrivateGroupIndex {
			Hashtable<String,Integer> index;
			public PrivateGroupIndex() {
				index = new Hashtable<String,Integer>();
			}
			public void put(String id, Integer tag) {
				if (id == null) return;
				index.put(id.trim().toUpperCase(), tag);
			}
			public Integer get(String id) {
				if (id == null) return null;
				return index.get(id.trim().toUpperCase());
			}
		}
	}
}
