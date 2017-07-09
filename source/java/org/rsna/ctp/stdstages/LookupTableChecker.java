/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;
import jdbm.RecordManager;
import jdbm.btree.BTree;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.servlets.LookupTableCheckerServlet;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.server.ServletSelector;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.JdbmUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.dcm4che.data.SpecificCharacterSet;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.VRs;

/**
 * A stage for filtering out objects that will fail an @lookup function call
 * in the DicomAnonymizer.
 */
public class LookupTableChecker extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(LookupTableChecker.class);

    RecordManager recman = null;
    public BTree index = null;

    Pipeline pipe = null;
    DicomAnonymizer anonymizer = null;
    File daScriptFile = null;
    File lutFile = null;
	ScriptTable scriptTable = null;
	Properties lutProps = null;
	SpecificCharacterSet charset = null;

    static final Pattern processPattern = Pattern.compile("@\\s*process\\s*\\(\\s*\\)");
    static final Pattern lookupPattern = Pattern.compile("@\\s*lookup\\s*\\(([^,)]+),([^,)]+),?([^,)]*),?([^\\)]*)\\)");
    static final Pattern intervalPattern = Pattern.compile("@\\s*dateinterval\\s*\\(([^,]+),([^,]+),([^)]+)\\s*\\)");

	/**
	 * Construct the LookupTableChecker PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public LookupTableChecker(Element element) {
		super(element);
		if (root != null) {
			File indexFile = new File(root, "__index");
			getIndex(indexFile.getPath());
		}
		else logger.error(name+": No root directory was specified.");
	}

	/**
	 * Start the pipeline stage. This method is called by the Pipeline
	 * after all the stages have been constructed.
	 */
	public synchronized void start() {
		//Find the next DicomAnonymizer and get the files.
		//Important note: this cannot be done in the constructor because
		//at the time the constructor is called, the subsequent stages in
		//the pipeline have not yet been instantiated.
		getContext();

		//Install the servlet
		//Important note: this cannot be done in the constructor because
		//at the time the constructor is called, the configuration is not
		//instantiated, so the servlet selector is not available
		Configuration config = Configuration.getInstance();
		ServletSelector selector = config.getServer().getServletSelector();
		selector.addServlet(id, LookupTableCheckerServlet.class);
	}

	/**
	 * Stop the stage.
	 */
	public void shutdown() {
		//Commit and close the database
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
			}
			catch (Exception ex) {
				logger.debug("Unable to commit and close the database");
			}
		}
		super.shutdown();
	}

	/**
	 * Check a DicomObject and record any failing lookups in the database.
	 * @param fileObject the object to process.
	 * @return the same FileObject if the result is true; otherwise null.
	 */
	public FileObject process(FileObject fileObject) {
		String cmd;
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();
		if (fileObject instanceof DicomObject) {
			DicomObject dob = (DicomObject)fileObject;
			daScriptFile = anonymizer.getDAScriptFile();
			lutFile = anonymizer.getLookupTableFile();
			if (daScriptFile != null) {
				DAScript daScript = DAScript.getInstance(daScriptFile);
				scriptTable = new ScriptTable(daScript);
				lutProps = LookupTable.getProperties(lutFile);
				synchronized(this) {
					Dataset ds = dob.getDataset();
					charset = ds.getSpecificCharacterSet();
					boolean ok = checkDataset(ds) & handleAlwaysCalls(ds);
					if (!ok) {
						try { recman.commit(); }
						catch (Exception unable) { };
						if (quarantine != null) quarantine.insert(fileObject);
						return null;
					}
				}
			}
		}
		logger.debug("Done checking "+fileObject.getType());
		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	private boolean checkDataset(Dataset ds) {		
		boolean ok = true;
		for (Iterator it=ds.iterator(); it.hasNext(); ) {
			DcmElement el = (DcmElement)it.next();
			int tag = 0;
			try { tag = el.tag(); }
			catch (Exception useZero) { }
			String command = scriptTable.get(new Integer(tag));
			if (command != null) {
				if (el.vr() == VRs.SQ) {
					Matcher processMatcher = processPattern.matcher(command);
					if (processMatcher.find()) {
						int i = 0;
						Dataset child;
						while ((child=el.getItem(i++)) != null) {
							ok &= checkDataset(child);
						}
					}
				}
				else {
					//Look for lookup function calls
					ok &= checkLookupCalls(ds, tag, command);
					
					//Look for dateinterval function calls
					ok &= checkDateIntervalCalls(ds, tag, command);
				}
			}
		}
		return ok;
	}
	
	private boolean checkLookupCalls(Dataset ds, int tag, String command) {
		boolean ok = true;
		Matcher lookupMatcher = lookupPattern.matcher(command);
		while (lookupMatcher.find()) {

			int nGroups = lookupMatcher.groupCount();
			String element = lookupMatcher.group(1).trim();
			String keyType = lookupMatcher.group(2).trim() + "/";
			String action = (nGroups > 2) ? lookupMatcher.group(3).trim() : "";
			String regex = (nGroups > 3) ? lookupMatcher.group(4).trim() : "";

			//logger.info("...nGroups  = "+nGroups);
			//logger.info("...element: |"+element+"|");
			//logger.info("...keyType: |"+keyType+"|");
			//logger.info("...action : |"+action+"|");
			//logger.info("...regex:   |"+regex+"|");

			int targetTag = ( element.equals("this") ? tag : DicomObject.getElementTag(element) );
			String targetValue = handleNull( ds.getString(targetTag) );

			if (!targetValue.equals("")) {
				String key = keyType + targetValue;
				if (lutProps.getProperty(key) == null) {
					boolean there = false;
					if (action.equals("keep")   ||
						action.equals("skip")   ||
						action.equals("remove") ||
						action.equals("empty")  ||
						action.equals("default")) there = true;
					else if (action.equals("ignore")) {
						regex = removeQuotes(regex);
						there = targetValue.matches(regex);
					}
					try {
						if (!there) {
							index.insert(key, keyType, true);
							ok = false;
						}
					}
					catch (Exception ignore) { }
				}
			}
		}
		return ok;
	}		

	private boolean checkDateIntervalCalls(Dataset ds, int tag, String command) {
		boolean ok = true;
		Matcher intervalMatcher = intervalPattern.matcher(command);
		while (intervalMatcher.find()) {

			int nGroups = intervalMatcher.groupCount();
			String dateElement = intervalMatcher.group(1).trim();
			String keyType = intervalMatcher.group(2).trim() + "/";
			String keyElement = intervalMatcher.group(3).trim();

			//logger.info("...nGroups  = "+nGroups);
			//logger.info("...dateElement: |"+dateElement+"|");
			//logger.info("...keyType:     |"+keyType+"|");
			//logger.info("...keyElement : |"+keyElement+"|");

			int targetTag = ( keyElement.equals("this") ? tag : DicomObject.getElementTag(keyElement) );
			String targetValue = handleNull( ds.getString(targetTag) );

			if (!targetValue.equals("")) {
				String key = keyType + targetValue;
				if (lutProps.getProperty(key) == null) {
					try {
						index.insert(key, keyType, true);
						ok = false;
					}
					catch (Exception ignore) { }
				}
			}
		}
		return ok;
	}
	
	private boolean handleAlwaysCalls(Dataset ds) {
		boolean ok = true;
		for (Integer tagInteger : scriptTable.keySet()) {
			String command = scriptTable.get(tagInteger);
			if (command.contains("@always")) {
				int tag = tagInteger.intValue();
				ok = ok & checkLookupCalls(ds, tag, command);
				ok = ok & checkDateIntervalCalls(ds, tag, command);
			}
		}
		return ok;
	}

	private String handleNull(String s) {
		if (s != null) return s.trim();
		return "";
	}

	private String removeQuotes(String s) {
		if (s == null) return "";
		s = s.trim();
		if ((s.length() > 1) && s.startsWith("\"") && s.endsWith("\"")) {
			s = s.substring(1, s.length()-1);
		}
		return s;
	}

	/**
	 * Get an XML object containing all the terms in the database.
	 * @return the XML document containing all the terms in the database.
	 */
	public Document getIndexDocument() {
		try {
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("Terms");
			doc.appendChild(root);
			TupleBrowser browser = index.browse();
			Tuple tuple = new Tuple();
			while (browser.getNext(tuple)) {
				Element term = doc.createElement("Term");
				root.appendChild(term);
				String key = (String)tuple.getKey();
				String value = (String)tuple.getValue();
				term.setAttribute("key", key.substring(value.length()));
				term.setAttribute("keyType", value);
			}
			return doc;
		}
		catch (Exception unable) { return null; }
	}

	/**
	 * Get the lookup table file.
	 * @return the lookup table file.
	 */
	public synchronized File getLookupTableFile() {
		return lutFile;
	}

	/**
	 * Get the DicomAnonymizer stage that is being checked.
	 * @return the DicomAnonymizer stage that is being checked.
	 */
	public synchronized DicomAnonymizer getAnonymizer() {
		return anonymizer;
	}

	/**
	 * Update the lookup table and the database.
	 * @param doc the Document containing the values to be
	 * added to the lookup table
	 */
	public synchronized boolean update(Document doc) {
		LookupTable lut = LookupTable.getInstance(lutFile);
		Properties props = lut.getProperties();
		Element root = doc.getDocumentElement();
		boolean changed = false;
		Node child = root.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				Element term = (Element)child;
				String key = term.getAttribute("key");
				String value = term.getAttribute("value");
				props.setProperty(key, value);
				try { index.remove(key); }
				catch (Exception ignore) { }
				changed = true;
			}
			child = child.getNextSibling();
		}
		if (changed) {
			try { recman.commit(); }
			catch (Exception ignore) { }
			lut.save();
		}
		return changed;
	}

	//Find the next anonymizer stage.
	private void getContext() {
		PipelineStage next = getNextStage();
		while (next != null) {
			if (next instanceof DicomAnonymizer) {
				anonymizer = ((DicomAnonymizer)next);
				break;
			}
			next = next.getNextStage();
		}
	}

	//Load the RecordManager and the index BTree
	private void getIndex(String indexPath) {
		try {
			recman = JdbmUtil.getRecordManager( indexPath );
			index = JdbmUtil.getBTree(recman, "index");
		}
		catch (Exception ex) {
			recman = null;
			logger.warn("Unable to load the index.");
		}
	}

	/**
	 * Get the list of links for display on the summary page.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public LinkedList<SummaryLink> getLinks(User user) {
		LinkedList<SummaryLink> links = super.getLinks(user);
		if (allowsAdminBy(user)) {
			String qs = "?p="+pipeline.getPipelineIndex()+"&s="+stageIndex;
			links.addFirst( new SummaryLink("/"+id+qs, null, "View the LookupTableChecker Database", false) );
		}
		return links;
	}

	/**
	 * Get HTML text displaying the active status of the stage.
	 * @param childUniqueStatus the status of the stage of which
	 * this class is the parent.
	 * @return HTML text displaying the active status of the stage.
	 */
	public synchronized String getStatusHTML(String childUniqueStatus) {
		String stageUniqueStatus =
			  "<tr><td width=\"20%\">Database size:</td><td>" + index.size() + "</td></tr>";
		return super.getStatusHTML(childUniqueStatus + stageUniqueStatus);
	}

	//A Hashtable of the script, keeping only the @process and @lookup scripts
	class ScriptTable extends Hashtable<Integer,String> {
		public ScriptTable(DAScript script) {
			super();
			Document scriptXML = script.toXML();
			Element scriptRoot = scriptXML.getDocumentElement();
			Node child = scriptRoot.getFirstChild();
			while (child != null) {
				if ( (child instanceof Element) && child.getNodeName().equals("e") ) {
					Element eChild = (Element)child;
					if (eChild.getAttribute("en").equals("T")) {
						int tag = StringUtil.getHexInt(eChild.getAttribute("t"));
						String command = eChild.getTextContent();
						Matcher processMatcher = processPattern.matcher(command);
						Matcher lookupMatcher = lookupPattern.matcher(command);
						Matcher intervalMatcher = intervalPattern.matcher(command);
						Integer tagInteger = new Integer(tag);
						if (processMatcher.find() || lookupMatcher.find() || intervalMatcher.find()) {
							this.put(tagInteger, command);
							//logger.info("ScriptTable: "+Integer.toHexString(tagInteger.intValue())+" "+command);
						}
					}
				}
				child = child.getNextSibling();
			}
		}
	}

}

