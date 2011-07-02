/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import org.apache.log4j.Logger;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class to encapsulate a set of users who have been invited to access a FileSystem.
 */
public class GuestList {

	static final Logger logger = Logger.getLogger(GuestList.class);

	File guestFile;
	HashSet<String> guests;

	/**
	 * Construct a GuestList
	 * @param dir the root directory of the FileSystem in which to store the GuestList file.
	 */
	public GuestList(File dir) {
		Document doc;
		guestFile = new File(dir, "__guests.xml");
		guests = new HashSet<String>();
		try {
			doc = XmlUtil.getDocument(guestFile);
			Element root = doc.getDocumentElement();
			Node child = root.getFirstChild();
			while (child != null) {
				if ( (child.getNodeType() == Node.ELEMENT_NODE)
						&& child.getNodeName().equals("guest") ) {
					guests.add( ((Element)child).getAttribute("username") );
				}
				child = child.getNextSibling();
			}
		}
		catch (Exception ex) { save(); }
	}

	/**
	 * Get the GuestList as an XML object, where the guest names are alphabetized..
	 * @return the GuestList as an XML object.
	 */
	public Document getXML() {
		try {
			String[] names = new String[guests.size()];
			names = guests.toArray(names);
			Arrays.sort(names);
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("guests");
			doc.appendChild(root);
			for (int i=0; i<names.length; i++) {
				Element guest = doc.createElement("guest");
				guest.setAttribute("username",names[i]);
				root.appendChild(guest);
			}
			return doc;
		}
		catch (Exception ex) { return null; }
	}

	/**
	 * Check whether a user's username appears in the guest list.
	 * @param user the user.
	 * @return true if the user's username appears in the list; false
	 * if the user is null or if the user's username does not appear in the list.
	 */
	public boolean includes(User user) {
		return (user != null) && guests.contains(user.getUsername());
	}

	/**
	 * Add a user's username to the guest list.
	 * @param user the user.
	 */
	public void add(User user) {
		if (user != null) {
			guests.add(user.getUsername());
			save();
		}
	}

	/**
	 * Add a username to the guest list.
	 * @param username the username.
	 */
	public void add(String username) {
		if (username != null) {
			guests.add(username);
			save();
		}
	}

	/**
	 * Remove a user's username from the guest list.
	 * @param user the user.
	 */
	public void remove(User user) {
		if (user != null) {
			guests.remove(user.getUsername());
			save();
		}
	}

	/**
	 * Remove a username from the guest list.
	 * @param username the username.
	 */
	public void remove(String username) {
		if (username != null) {
			guests.remove(username);
			save();
		}
	}

	//Save the current HashSet as an XML File
	private void save() {
		try { FileUtil.setText(guestFile, XmlUtil.toString(getXML())); }
		catch (Exception ex) { logger.warn("Unable to save "+guestFile); }
	}

}
