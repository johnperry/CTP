/*---------------------------------------------------------------
*  Copyright 2013 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import java.util.LinkedList;
import org.rsna.ctp.objects.*;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.server.User;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

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
	protected volatile File lastFileIn = null;
	protected volatile long lastTimeIn = 0;
	protected volatile File lastFileOut = null;
	protected volatile long lastTimeOut = 0;
	protected volatile boolean stop = false;
	protected volatile Pipeline pipeline = null;
	protected volatile PipelineStage nextStage = null;
	protected volatile PipelineStage previousStage = null;
	public int stageIndex = -1;

	/**
	 * Construct a base pipeline stage which does no processing.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public AbstractPipelineStage(Element element) {
		this.element = element;
		name = element.getAttribute("name");
		id = element.getAttribute("id").trim();
		acceptDicomObjects	= !element.getAttribute("acceptDicomObjects").trim().equals("no");
		acceptXmlObjects	= !element.getAttribute("acceptXmlObjects").trim().equals("no");
		acceptZipObjects	= !element.getAttribute("acceptZipObjects").trim().equals("no");
		acceptFileObjects	= !element.getAttribute("acceptFileObjects").trim().equals("no");
		long quarantineTimeDepth = StringUtil.getLong(element.getAttribute("quarantineTimeDepth"));
		quarantine = Quarantine.getInstance(element.getAttribute("quarantine"), quarantineTimeDepth);
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
	 * Get the pipeline to which this stage belongs.
	 * @return this stage's pipeline.
	 */
	public synchronized Pipeline getPipeline() {
		return pipeline;
	}

	/**
	 * Set the pipeline for this stage.
	 */
	public synchronized void setPipeline(Pipeline pipeline) {
		this.pipeline = pipeline;
	}

	/**
	 * Set the index for this stage.
	 */
	public synchronized void setStageIndex(int index) {
		this.stageIndex = index;
	}

	/**
	 * Get the next stage in the pipeline.
	 * @return the next stage in the pipeline.
	 */
	public synchronized PipelineStage getNextStage() {
		return nextStage;
	}

	/**
	 * Set the next stage in the pipeline.
	 */
	public synchronized void setNextStage(PipelineStage nextStage) {
		this.nextStage = nextStage;
	}

	/**
	 * Get the prevous stage in the pipeline.
	 * @return the prevous stage in the pipeline.
	 */
	public synchronized PipelineStage getPreviousStage() {
		return previousStage;
	}

	/**
	 * Set the prevous stage in the pipeline.
	 */
	public synchronized void setPreviousStage(PipelineStage previousStage) {
		this.previousStage = previousStage;
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
	 * Get the last time the stage supplied a file.
	 * @return the last time the stage supplied a file to the pipeline.
	 */
	public synchronized long getLastFileOutTime() {
		return lastTimeOut;
	}

	/**
	 * Determine whether a user has admin privileges for this stage.
	 * Admin privileges are conferred on users who have the admin role
	 * or who are designated as an admin of the pipeline in which this
	 * stage appears..
	 * @param user the requesting user.
	 * @return true if the user is an admin of this stage.
	 */
	public boolean allowsAdminBy(User user) {
		return getPipeline().allowsAdminBy(user);
	}

	/**
	 * Get the list of links for display on the summary page.
	 * This method returns an empty array. It should be overridden
	 * by stages that provide servlets to access their data.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public LinkedList<SummaryLink> getLinks(User user) {
		LinkedList<SummaryLink> links = new LinkedList<SummaryLink>();
		if ((quarantine != null) && (allowsAdminBy(user) || ((user != null) && user.hasRole("qadmin")))) {
			String qs = "?p="+pipeline.getPipelineIndex()+"&s="+stageIndex;
			links.add( new SummaryLink("/quarantines"+qs, null, "View the Quarantine Contents", false) );
		}
		return links;
	}

	/**
	 * Get HTML text describing the configuration of the stage,
	 * consisting of a header element containing the
	 * stage's name and a table containing the rest of the
	 * stage's configuration element's attributes.
	 * @param user the requesting user.
	 * @return HTML text describing the configuration of the stage.
	 */
	public synchronized String getConfigHTML(User user) {
		boolean admin = (user != null) && user.hasRole("admin");
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
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		return getStatusHTML("");
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @param childUniqueStatus the status of the stage of
	 * which this class is the parent.
	 * @return HTML text displaying the current status of the stage.
	 */
	public synchronized String getStatusHTML(String childUniqueStatus) {
		StringBuffer sb = new StringBuffer();
		sb.append("<h3>"+name+"</h3>");
		sb.append("<table border=\"1\" width=\"100%\">");
		sb.append(childUniqueStatus);
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
