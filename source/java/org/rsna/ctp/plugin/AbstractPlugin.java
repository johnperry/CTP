/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.plugin;

import java.io.File;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.rsna.util.StringUtil;
import org.rsna.ctp.objects.*;

/**
 * An abstract class implementing the Plugin interface.
 * This class captures the root attribute and makes the directory if necessary.
 * It also supplies an implementation of the getName, getID, and getConfigHTML
 * methods. Subclasses can depend on those methods but must
 * override the getStatusHTML method to provide status relevant
 * to the Plugin being implemented.
 */
public abstract class AbstractPlugin implements Plugin {

	protected final Element element;
	protected final String name;
	protected String id; //Note: cannot be final because some plugins change the id
	protected File root = null;
	protected volatile boolean stop = false;

	/**
	 * Construct a base plugin which does nothing.
	 * IMPORTANT: When the constructor is called, neither the
	 * pipelines nor the HttpServer have necessarily been
	 * instantiated. Any actions that depend on those objects
	 * must be deferred until the start method is called.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the plugin.
	 */
	public AbstractPlugin(Element element) {
		this.element = element;
		name = element.getAttribute("name");
		id = element.getAttribute("id").trim();
		String rPath = element.getAttribute("root").trim();
		if (!rPath.equals("")) {
			root = new File(rPath);
			root.mkdirs();
		}
	}

	/**
	 * Start the plugin. This method can be overridden by plugins
	 * which can use it to start subordinate threads created in their constructors.
	 * This method is called by the Configuration after the pipelines, plugins, and
	 * the HttpServer have been constructed.
	 */
	public void start() {
	}

	/**
	 * Stop the plugin.
	 */
	public void shutdown() {
		stop = true;
	}

	/**
	 * Determine whether the plugin has shut down.
	 */
	public boolean isDown() {
		return stop;
	}

	/**
	 * Get the name of this plugin as specified in the
	 * configuration element.
	 * @return the name of the plugin.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the root directory of this plugin.
	 * @return the root directory of the plugin.
	 */
	public File getRoot() {
		return root;
	}

	/**
	 * Get the ID of this plugin as specified in the
	 * configuration element.
	 * @return the ID of the plugin.
	 */
	public String getID() {
		return id;
	}

	/**
	 * Get the config file element for this plugin.
	 * @return the config file element of the plugin.
	 */
	public Element getConfigElement() {
		return element;
	}

	/**
	 * Get HTML text describing the configuration of the plugin,
	 * consisting of a header element containing the
	 * plugin's name and a table containing the rest of the
	 * plugin's configuration element's attributes.
	 * @param admin true if the configuration is allowed to display
	 * the values of username and password attributes.
	 * @return HTML text describing the configuration of the stage.
	 */
	public String getConfigHTML(boolean admin) {
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
	 * Get HTML text displaying the current status of the plugin.
	 * This method must be overridden in real Plugin implementations.
	 * @return HTML text displaying the current status of the plugin.
	 */
	public abstract String getStatusHTML();

	/**
	 * Get HTML text displaying the current status of the plugin.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML(String pluginUniqueStatus) {
		StringBuffer sb = new StringBuffer();
		sb.append("<h3>"+name+"</h3>");
		sb.append("<table border=\"1\" width=\"100%\">");
		sb.append(pluginUniqueStatus);
		sb.append("</table>");
		return sb.toString();
	}

}