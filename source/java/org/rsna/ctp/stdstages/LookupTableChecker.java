/*---------------------------------------------------------------
*  Copyright 2013 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.util.List;
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
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizerContext;
import org.rsna.server.ServletSelector;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.JdbmUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
    File dcmScriptFile = null;
    File lutFile = null;

    static final Pattern pattern = Pattern.compile("@\\s*lookup\\s*\\(([^,]+),([^),]+)");

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
		//Set stop so the isDown method will return the correct value.
		stop = true;
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
			if (dcmScriptFile != null) {
				DAScript daScript = DAScript.getInstance(dcmScriptFile);
				Document scriptXML = daScript.toXML();
				Element scriptRoot = scriptXML.getDocumentElement();
				Properties lutProps = LookupTable.getProperties(lutFile);
				synchronized(this) {
					boolean ok = true;
					Node child = scriptRoot.getFirstChild();
					while (child != null) {
						if ( (child instanceof Element) && child.getNodeName().equals("e") ) {
							Element eChild = (Element)child;
							if (eChild.getAttribute("en").equals("T")) {
								String thisTag = eChild.getAttribute("t");
								String command = eChild.getTextContent();;
								Matcher matcher = pattern.matcher(command);
								while (matcher.find()) {
									String element = matcher.group(1).trim();
									String keyType = matcher.group(2).trim() + "/";

									//logger.info("keyType: \""+keyType+"\"; element: \""+element+"\"");

									if (element.equals("this")) element = thisTag;
									String elementValue = dob.getElementValue(element);
									String key = keyType + elementValue;
									if (lutProps.getProperty(key) == null) {
										try { index.insert(key, keyType, true); }
										catch (Exception unable) { }
										ok = false;
									}
								}
							}
						}
						child = child.getNextSibling();
					}
					if (!ok) {
						try { recman.commit(); }
						catch (Exception unable) { };
						if (quarantine != null) quarantine.insert(fileObject);
						lastFileOut = null;
						lastTimeOut = System.currentTimeMillis();
						return null;
					}
				}
			}
		}
		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Get an XML object containing all the terms in the database.
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
	 */
	public File getLookupTableFile() {
		return lutFile;
	}

	/**
	 * Get the pipeline.
	 */
	public Pipeline getPipeline() {
		return pipe;
	}

	/**
	 * Get the pipeline.
	 */
	public DicomAnonymizer getAnonymizer() {
		return anonymizer;
	}

	/**
	 * Update the lookup table and the database.
	 */
	public synchronized void update(Document doc) {
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
	}

	//Find the next anonymizer stage and get its script and lookup table files.
	private void getContext() {
		if (dcmScriptFile == null) {
			pipe = Configuration.getInstance().getPipeline(this);
			if (pipe != null) {
				List<PipelineStage> stageList = pipe.getStages();
				int thisStage = stageList.indexOf(this);
				if (thisStage != -1) {
					PipelineStage[] stages = new PipelineStage[stageList.size()];
					stages = stageList.toArray(stages);
					//Find the next DicomAnonymizer
					for (int i=thisStage+1; i<stages.length; i++) {
						if (stages[i] instanceof DicomAnonymizer) {
							anonymizer = ((DicomAnonymizer)stages[i]);
							dcmScriptFile = anonymizer.getScriptFile();
							lutFile = anonymizer.getLookupTableFile();
							break;
						}
					}
				}
			}
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
}

