/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.rsna.util.StringUtil;
import org.rsna.ctp.objects.*;

/**
 * An abstract class implementing the PipelineStage interface.
 * This class instantiates the Quarantine if one is specified.
 * It also captures the root attribute and makes the directory if necessary.
 * It also supplies an implementation of the getName and getConfigHTML
 * methods. Subclasses can depend on those methods but must
 * override the getStatusHTML method to provide status relevant
 * to the PipelineStage being implemented.
 */
public abstract class AbstractPipelineStage implements PipelineStage {

	protected final Element element;
	protected final String name;
	protected final String id;
	protected Quarantine quarantine = null;
	protected File root = null;
	protected boolean acceptDicomObjects = true;
	protected boolean acceptXmlObjects = true;
	protected boolean acceptZipObjects = true;
	protected boolean acceptFileObjects = true;
	protected File lastFileIn = null;
	protected long lastTimeIn = 0;
	protected File lastFileOut = null;
	protected long lastTimeOut = 0;
	protected volatile boolean stop = false;

	/**
	 * Construct a base pipeline stage which does no processing.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public AbstractPipelineStage(Element element) {
		this.element = element;
		name = element.getAttribute("name");
		id = element.getAttribute("id").trim();
		acceptDicomObjects	= !element.getAttribute("acceptDicomObjects").equals("no");
		acceptXmlObjects	= !element.getAttribute("acceptXmlObjects")	 .equals("no");
		acceptZipObjects	= !element.getAttribute("acceptZipObjects")	 .equals("no");
		acceptFileObjects	= !element.getAttribute("acceptFileObjects") .equals("no");
		String qPath = element.getAttribute("quarantine").trim();
		if (!qPath.equals("")) quarantine = new Quarantine(qPath);
		String rPath = element.getAttribute("root").trim();
		if (!rPath.equals("")) {
			root = new File(rPath);
			root.mkdirs();
		}
	}

	/**
	 * Start the pipeline stage. This method can be overridden by stages
	 * which can use it to start subordinate threads created in their constructors.
	 * This method is called by the Pipeline after all the stages have been
	 * constructed.
	 */
	public synchronized void start() {
	}

	/**
	 * Stop the pipeline stage.
	 */
	public synchronized void shutdown() {
		stop = true;
	}

	/**
	 * Determine whether the pipeline stage has shut down.
	 */
	public synchronized boolean isDown() {
		return stop;
	}

	/**
	 * Get the name of this pipeline stage as specified in the
	 * configuration element for the stage.
	 * @return the name of the pipeline stage.
	 */
	public synchronized String getName() {
		return name;
	}

	/**
	 * Get the root directory of this pipeline stage.
	 * @return the root directory of the pipeline stage.
	 */
	public synchronized File getRoot() {
		return root;
	}

	/**
	 * Get the ID of this pipeline stage as specified in the
	 * configuration element for the stage.
	 * @return the ID of the pipeline stage.
	 */
	public synchronized String getID() {
		return id;
	}

	/**
	 * Get the quarantine of this pipeline stage as specified in the
	 * configuration element for the stage.
	 * @return the Quarantine, or null if no quarantine was specified.
	 */
	public synchronized Quarantine getQuarantine() {
		return quarantine;
	}

	/**
	 * Determine whether this stage accepts an object type.
	 * @param fileObject the object
	 * @return true if the stage accepts the object; false otherwise.
	 */
	public synchronized boolean acceptable(FileObject fileObject) {
		if (fileObject instanceof DicomObject) return acceptDicomObjects;
		if (fileObject instanceof XmlObject) return acceptXmlObjects;
		if (fileObject instanceof ZipObject) return acceptZipObjects;
		return acceptFileObjects;
	}

	/**
	 * Get HTML text describing the configuration of the stage,
	 * consisting of a header element containing the
	 * stage's name and a table containing the rest of the
	 * stage's configuration element's attributes.
	 * @param admin true if the configuration is allowed to display
	 * the values of username and password attributes.
	 * @return HTML text describing the configuration of the stage.
	 */
	public synchronized String getConfigHTML(boolean admin) {
		StringBuffer sb = new StringBuffer();
		sb.append("<h3>"+name+"</h3>");
		sb.append("<table border=\"1\" width=\"100%\">");
		NamedNodeMap attrs = element.getAttributes();
		for (int i=0; i<attrs.getLength(); i++) {
			Node n = attrs.item(i);
			String attrName = n.getNodeName();
			if (!attrName.equals("name")) {
				sb.append("<tr><td width=\"20%\">"+attrName+":</td>");
				if (admin || (!attrName.equals("username") && !attrName.equals("password")))
					sb.append("<td>"+n.getNodeValue()+"</td></tr>");
				else
					sb.append("<td>[suppressed]</td></tr>");
			}
		}
		Node child = element.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element)child;
				sb.append("<tr><td width=\"20%\">"+e.getNodeName()+":</td>");
				sb.append("<td>"+getTableFor(e)+"</td></tr>");
			}
			child = child.getNextSibling();
		}
		sb.append("</table>");
		return sb.toString();
	}

	//Get a table for a configuration child element
	private String getTableFor(Element element) {
		StringBuffer sb = new StringBuffer();
		sb.append("<table border=\"0\">");
		NamedNodeMap attrs = element.getAttributes();
		for (int i=0; i<attrs.getLength(); i++) {
			Node n = attrs.item(i);
			sb.append("<tr><td width=\"20%\">"+n.getNodeName()+":</td>");
			sb.append("<td>"+n.getNodeValue()+"</td></tr>");
		}
		String text = element.getTextContent().trim();
		if (!text.equals("")) {
			sb.append("<tr><td width=\"20%\"><i>Text content:</i></td>");
			sb.append("<td>"+text+"</td></tr>");
		}
		sb.append("</table>");
		return sb.toString();
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * This method must be overridden in real PipelineStage
	 * implementations.
	 * @return HTML text displaying the current status of the stage.
	 */
	public abstract String getStatusHTML();

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public synchronized String getStatusHTML(String stageUniqueStatus) {
		StringBuffer sb = new StringBuffer();
		sb.append("<h3>"+name+"</h3>");
		sb.append("<table border=\"1\" width=\"100%\">");
		sb.append(stageUniqueStatus);
		sb.append("<tr><td width=\"20%\">Last file received:</td>");
		if (lastTimeIn != 0) {
			sb.append("<td>"+lastFileIn+"</td></tr>");
			sb.append("<tr><td width=\"20%\">Last file received at:</td>");
			sb.append("<td>"+StringUtil.getDateTime(lastTimeIn,"&nbsp;&nbsp;&nbsp;")+"</td></tr>");
		}
		else sb.append("<td>No activity</td></tr>");
		sb.append("<tr><td width=\"20%\">Last file supplied:</td>");
		if (lastTimeOut != 0) {
			sb.append("<td>"+lastFileOut+"</td></tr>");
			sb.append("<tr><td width=\"20%\">Last file supplied at:</td>");
			sb.append("<td>"+StringUtil.getDateTime(lastTimeOut,"&nbsp;&nbsp;&nbsp;")+"</td></tr>");
		}
		else sb.append("<td>No activity</td></tr>");
		sb.append("</table>");
		return sb.toString();
	}

}