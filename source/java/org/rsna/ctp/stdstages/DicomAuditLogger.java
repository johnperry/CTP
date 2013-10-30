/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.ctp.stdplugins.AuditLog;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A Processor stage that logs the differences between two DicomObjects,
 * the current object and an object cached by an earlier ObjectCache stage.
 */
public class DicomAuditLogger extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(DicomAuditLogger.class);

	String verbosity;
	String objectCacheID;
	String auditLogID;
	AuditLog auditLog = null;
	ObjectCache objectCache = null;
	LinkedList<Integer> auditLogTags = null;

	/**
	 * Construct the DicomDifferenceLogger PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public DicomAuditLogger(Element element) {
		super(element);
		verbosity = element.getAttribute("verbosity").trim();
		objectCacheID = element.getAttribute("cacheID").trim();
		auditLogID = element.getAttribute("auditLogID").trim();

		String[] alts = element.getAttribute("auditLogTags").split(";");
		auditLogTags = new LinkedList<Integer>();
		for (String alt : alts) {
			alt = alt.trim();
			if (!alt.equals("")) {
				int tag = DicomObject.getElementTag(alt);
				if (tag != 0) auditLogTags.add(new Integer(tag));
				else logger.warn(name+": Unknown DICOM element tag: \""+alt+"\"");
			}
		}
	}

	/**
	 * Start the pipeline stage. When this method is called, all the
	 * stages have been instantiated. We have to get the ObjectCache
	 * and AuditLog stages here to ensure that the Configuration
	 * has been instantiated. (Note: The Configuration constructor has
	 * not finished when the stages are constructed.)
	 */
	public void start() {
		Configuration config = Configuration.getInstance();
		if (!objectCacheID.equals("")) {
			PipelineStage stage = config.getRegisteredStage(objectCacheID);
			if (stage != null) {
				if (stage instanceof ObjectCache) {
					objectCache = (ObjectCache)stage;
				}
				else logger.warn(name+": cacheID \""+objectCacheID+"\" does not reference an ObjectCache");
			}
			else logger.warn(name+": cacheID \""+objectCacheID+"\" does not reference any PipelineStage");
		}

		Plugin plugin = config.getRegisteredPlugin(auditLogID);
		if ((plugin != null) && (plugin instanceof AuditLog)) {
			auditLog = (AuditLog)plugin;
		}
		else logger.warn(name+": auditLogID \""+auditLogID+"\" does not reference an AuditLog");
	}

	/**
	 * Log objects as they are received by the stage.
	 * @param fileObject the object to log.
	 * @return the same FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if ((auditLog != null) && (fileObject instanceof DicomObject)) {

			//Get the cached object, if possible
			DicomObject cachedObject = null;
			if (objectCache != null) {
				FileObject fob = objectCache.getCachedObject();
				if ( (fob instanceof DicomObject) ) cachedObject = (DicomObject)fob;
			}

			if (cachedObject != null) makeAuditLogEntry( (DicomObject)fileObject, cachedObject );
			else makeAuditLogEntry( (DicomObject)fileObject );
		}

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	private void makeAuditLogEntry( DicomObject dicomObject, DicomObject cachedObject ) {
		String patientID = cachedObject.getPatientID();
		String studyInstanceUID = cachedObject.getStudyInstanceUID();
		String sopInstanceUID = cachedObject.getSOPInstanceUID();
		String sopClassName = cachedObject.getSOPClassName();

		try {
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("DicomObject");
			root.setAttribute("SOPClassName", sopClassName);
			Element sources = doc.createElement("Sources");
			sources.setAttribute("a", objectCache.getName());
			sources.setAttribute("b", this.getName());
			root.appendChild(sources);
			Element els = doc.createElement("Elements");
			root.appendChild(els);

			for (Integer tag : auditLogTags) {
				int tagint = tag.intValue();
				String elementName = DicomObject.getElementName(tagint);
				if (elementName != null) {
					elementName = elementName.replaceAll("\\s", "");
				}
				else {
					int g = (tagint >> 16) & 0xFFFF;
					int e = tagint &0xFFFF;
					elementName = String.format("g%04Xe%04X", g, e);
				}
				Element el = doc.createElement(elementName);
				el.setAttribute("a", cachedObject.getElementValue(tagint, ""));
				el.setAttribute("b", dicomObject.getElementValue(tagint, ""));
				el.setAttribute("tag", DicomObject.getElementNumber(tagint));
				els.appendChild(el);
			}
			String entry = XmlUtil.toPrettyString(root);
			logger.debug("AuditLog entry:\n"+entry);
			try { auditLog.addEntry(entry, "xml", patientID, studyInstanceUID, sopInstanceUID); }
			catch (Exception ex) { logger.warn("Unable to insert the AuditLog entry"); }
		}
		catch (Exception ex) {
			logger.warn("Unable to construct the AuditLog entry", ex);
		}

	}

	private void makeAuditLogEntry( DicomObject dicomObject ) {
		String patientID = dicomObject.getPatientID();
		String studyInstanceUID = dicomObject.getStudyInstanceUID();
		String sopInstanceUID = dicomObject.getSOPInstanceUID();
		String sopClassName = dicomObject.getSOPClassName();

		try {
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("DicomObject");
			root.setAttribute("StageName", this.getName());
			root.setAttribute("SOPClassName", sopClassName);
			Element els = doc.createElement("Elements");
			root.appendChild(els);

			for (Integer tag : auditLogTags) {
				int tagint = tag.intValue();
				String elementName = DicomObject.getElementName(tagint);
				if (elementName != null) {
					elementName = elementName.replaceAll("\\s", "");
				}
				else {
					int g = (tagint >> 16) & 0xFFFF;
					int e = tagint &0xFFFF;
					elementName = String.format("g%04Xe%04X", g, e);
				}
				els.setAttribute(elementName, dicomObject.getElementValue(tagint, ""));
			}
			String entry = XmlUtil.toPrettyString(root);
			logger.debug("AuditLog entry:\n"+entry);
			try { auditLog.addEntry(entry, "xml", patientID, studyInstanceUID, sopInstanceUID); }
			catch (Exception ex) { logger.warn("Unable to insert the AuditLog entry"); }
		}
		catch (Exception ex) {
			logger.warn("Unable to construct the AuditLog entry", ex);
		}
	}
}