/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.xml;

import java.util.LinkedList;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.w3c.dom.NodeList;

/**
 * A class to assist in walking down an XML path, with support for wildcards.
 * A path consists of a starting node and a series of path segments coded as
 * a text string in the format of an XPath expression, except that instead of
 * predicates, path segments can have wildcards or predicate-like qualifiers
 * as described in the documentation for the segment field below.
 * <p>
 * An XmlPathElement is parsed into:
 * <ul>
 * <li>node: the parent node from which the path proceeds</li>
 * <li>path: the full path from the parent node to the end</li>
 * <li>segment: the first step along the path after the parent node</li>
 * </l>remainingPath: the rest of the path after the segment.</li>
 * </ul>
 */
public class XmlPathElement {

	/** The parent node of the segment (always a real Node). */
	public Node node;

	/** The path from the parent node (not including the parent node) to the end. */
	public String path;

	/** The name of the next path segment (may include modifiers):
	 *<ul>
	 *<li>attributes start with @</li>
	 *<li>wildcard segment names are *</li>
	 *<li>wildcard selections are name[*]</li>
	 *<li>specific selections are name[n] for n an integer starting at 0</li>
	 *</ul>
	 */
	public String segment;

	/** The path from the segment to the end, not including the segment. */
	public String remainingPath;

	/**
	 * Construct an XmlPathElement that starts from a node.
	 * @param node the current node - the parent of the path. This node
	 * can be a Document, and if so, the path will still start from the root
	 * Element.
	 * @param path the path from the node forward.
	 */
	public XmlPathElement(Node node, String path) {
		this.node = node;
		path = path.replaceAll("\\s","");
		this.path = path;
		segment = path;
		if (segment.startsWith("/")) segment = segment.substring(1);
		int k = segment.indexOf("/",1);
		if (k != -1) segment = segment.substring(0,k);
	}

	/**
	 * Get an XmlPathElement whose parent node is the segment name for
	 * this XmlPathElement. This method is used to create a node in a path
	 * when that node is not present in the Document.
	 * @return a new XmlPathElement child of the current XmlPathElement.
	 */
	public XmlPathElement createPathElement() {
		Element el = node.getOwnerDocument().createElement(getSegmentName());
		node.appendChild(el);
		return new XmlPathElement(el,getRemainingPath());
	}

	/**
	 * Get the sum of all the text child nodes for the parent node of this
	 * XmlPathElement. If the child segment of the parent node is an attribute,
	 * it returns the value of the attribute named by the segment.
	 * @return the text value of the parent node.
	 */
	public String getValue() {
		if (segmentIsAttribute())
			return (node instanceof Element) ?
						((Element)node).getAttribute(segment.substring(1)) :
						null;
		else {
			String value = "";
			Node n = node.getFirstChild();
			while (n != null) {
				if (n instanceof Text) value += n.getNodeValue();
				n = n.getNextSibling();
			}
			return value;
		}
	}

	/**
	 * If the child segment of the parent node is an attribute, set its value.
	 * Otherwise, remove all the text nodes for the parent node and then append
	 * a text node with the supplied value.
	 * @param value the text value to assign.
	 */
	public void setValue(String value) {
		//Attributes are easy; try that first.
		if (segmentIsAttribute()) {
			if (node instanceof Element)
				((Element)node).setAttribute(segment.substring(1),value);
			return;
		}
		//If we get here, we are setting the value of an element.
		//First remove all the text nodes of the element and then
		//append a text node. This will leave all the child elements
		//intact, if the parent element is mixed.
		Node n = node.getFirstChild();

		while (n != null) {
			Node nn = n.getNextSibling();
			if (n instanceof Text) node.removeChild(n);
			n = nn;
		}
		Text text = node.getOwnerDocument().createTextNode(value);
		node.appendChild(text);
	}

	/**
	 * See if the attribute identified by the current segment exists.
	 */
	public boolean attributeExists() {
		if (segmentIsAttribute() && (node instanceof Element)) {
			String name = segment.substring(1);
			return ( ((Element)node).getAttributeNode(name) != null );
		}
		return false;
	}

	/**
	 * Remove the attribute identified by the current segment.
	 */
	public void removeAttribute() {
		if (segmentIsAttribute() && (node instanceof Element))
			((Element)node).removeAttribute(segment.substring(1));
	}

	/**
	 * Remove the parent node.
	 */
	public void removeElement() {
		if (segmentIsAttribute()) return;
		node.getParentNode().removeChild(node);
	}

	/**
	 * Get the list of Element nodes that match the current segment,
	 * with support for wildcards in the segment.
	 * @return the list of children of the parent node that match
	 * the segment.
	 */
	public NodeList getNodeList() {
		XmlPathNodeList pnl = new XmlPathNodeList();
		NodeList nl;
		String name;
		Node node = this.node;
		Node n;
		if (node instanceof Document) {
			node = ((Document)node).getDocumentElement();
			if (segmentIsWildcardElement() || segment.equals(node.getNodeName()))
				pnl.add(node);
		}

		else if ((node instanceof Element) && !segmentIsAttribute()) {
			nl = ((Element)node).getChildNodes();
			if (segmentIsWildcardElement()) {
				for (int i=0; i<nl.getLength(); i++) {
					n = nl.item(i);
					if (n instanceof Element) pnl.add(n);
				}
			}
			else if (segment.startsWith("/")) {
				name = getSegmentName();
				return ((Element)node).getElementsByTagName(name);
			}
			else {
				name = getSegmentName();
				for (int i=0; i<nl.getLength(); i++) {
					n = nl.item(i);
					if ((n instanceof Element) && n.getNodeName().equals(name)) pnl.add(n);
				}
				int index = 0;
				if (segmentContainsQualifier()) {
					String indexString = getSegmentQualifier();
					if (containsWildcard(indexString)) return pnl;
					try { index = Integer.parseInt(indexString); }
					catch (Exception ex) { return new XmlPathNodeList(); }
				}
				n = pnl.item(index);
				pnl = new XmlPathNodeList();
				if (n != null) pnl.add(n);
			}
		}
		return pnl;
	}

	/**
	 * Get the name of the segment, with any qualifiers removed.
	 * @return the name of the segment.
	 */
	public String getSegmentName() {
		String name = segment;
		if (name.startsWith("/")) name = name.substring(1);
		int k = name.indexOf("[");
		if (k == -1) return name.replaceAll("\\]","");
		return name.substring(0,k);
	}

	/**
	 * Get the path following the segment.
	 * @return the remaining path after the segment.
	 */
	public String getRemainingPath() {
		String remPath = path;
		if (remPath.startsWith("//")) remPath = remPath.substring(2);
		else if (remPath.startsWith("/")) remPath = remPath.substring(1);
		int k = remPath.indexOf("/");
		if (k != -1) return remPath.substring(k);
		return "";
	}

	/**
	 * Determine whether the segment is the last one on the path.
	 * @return true if the segment is the last one on the path; false otherwise.
	 */
	public boolean isEndSegment() {
		return segment.equals("");
	}

	/**
	 * Determine whether the segment names an attribute.
	 * @return true if the segment is an attribute; false otherwise.
	 */
	public boolean segmentIsAttribute() {
		return segment.startsWith("@");
	}

	/**
	 * Determine whether the segment is a wildcard, defined as "*".
	 * @return true if the segment is a wildcard; false otherwise.
	 */
	public boolean segmentIsWildcardElement() {
		return segment.equals("*");
	}

	/**
	 * Determine whether the segment contains a wildcard qualifier,
	 * defined as a name followed by [*].
	 * @return true if the segment contains a wildcard qualifier; false otherwise.
	 */
	public boolean segmentContainsWildcard() {
		return containsWildcard(getSegmentQualifier());
	}

	/**
	 * Determine whether the segment contains a qualifier.
	 * @return true if the segment contains a qualifier; false otherwise.
	 */
	public boolean segmentContainsQualifier() {
		return !getSegmentQualifier().equals("");
	}

	/**
	 * Get the text of the qualifier, not including the "[" and "]".
	 * @return the text of the qualifier; otherwise, the empty string.
	 */
	public String getSegmentQualifier() {
		int k = segment.indexOf("[");
		if (k == -1) return "";
		int kk = segment.indexOf("]");
		if (kk < k) return "";
		return segment.substring(k+1,kk).trim();
	}

	//Look for a wildcard anywhere in some text

	private boolean containsWildcard(String text) {
		return (text.indexOf("*") != -1);
	}
}

