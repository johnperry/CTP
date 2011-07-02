/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.Path;
import org.rsna.server.User;
import org.rsna.server.Users;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;

/**
 * A Servlet which provides web access to the studies stored in a FileStorageService.
 */
public class GuestListServlet extends Servlet {

	static final Logger logger = Logger.getLogger(GuestListServlet.class);

	/**
	 * Static init method to set up the root directory..
	 * This method copies the CSS and JS files needed
	 * by the viewer into the root directory.
	 */
	public static void init(File root) {
		root.mkdirs();
	}

	/**
	 * Construct a GuestListServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public GuestListServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page with a form displaying
	 * the guest list for the current user and providing editing
	 * capability. If the user has the proxy privilege, allow
	 * the user to manage a different user's guest list.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {

		//Get the user
		User user = req.getUser();
		if (user == null) {
			res.setResponseCode(403);
			res.send();
			return;
		}

		//Get the Users instance.
		Users users = Users.getInstance();

		//See if this is a proxy.
		Path path = new Path(req.getPath());
		if ((path.length() > 1) && user.hasRole("proxy")) {
			user = users.getUser(path.element(1));
			//If the proxied user doesn't exist, stay with the current user.
			if (user == null) user = req.getUser();
		}

		//Get the FileSystemManager.
		FileSystemManager fsm = FileSystemManager.getInstance(root);
		if (fsm == null) {
			//There is no FileSystemManager for this root, return a 404.
			res.setResponseCode(404);
			res.send();
		}

		//Get the FileSystem corresponding to the user
		FileSystem fs = fsm.getFileSystem(user.getUsername());
		if (fs == null) {
			//There is no FileSystem for this user, return a 404.
			res.setResponseCode(404);
			res.send();
		}

		try {
			//Get the guest list for this FileSystem
			Document guestsXML = fs.getGuestList().getXML();

			//Get a String containing the usernames of users who have the guest role and
			//another String containing all the usernames.
			//
			//It would be nicer to pass an XML document containing all the users
			//and let the XSL do all the work, but it turns out that the XSL processor
			//in Java has a bug which causes it to get a conversion error when a document
			//is passed as a param. It would be possible to get around this by including
			//the Xalan release as a class library with CTP, but that would triple the size
			//of the download, so I decided to simply provide Strings instead.
			String[] usernames = users.getUsernames();
			StringBuffer allUsers = new StringBuffer();
			StringBuffer guestUsers = new StringBuffer();
			for (int i=0; i<usernames.length; i++) {
				User u = users.getUser(usernames[i]);
				allUsers.append(u.getUsername() + ";");
				if ((u != null) && u.hasRole("guest")) {
					guestUsers.append(u.getUsername() + ";");
				}
			}

			//Generate the page
			File xslFile = new File("pages/guest-manager.xsl");
			Object[] params = new Object[] {
				"context",		context,
				"all-users",	allUsers.toString(),
				"guest-users",	guestUsers.toString(),
				"user",			user.getUsername(),
				"proxy",		(req.getUser().hasRole("proxy") ? "yes" : "no")
			};
			String page = XmlUtil.getTransformedText(guestsXML, xslFile, params);
			res.write(page);
			res.setContentType("html");
			res.disableCaching();
			res.send();
		}
		catch (Exception e) {
			logger.warn("Unable to make the page",e);
			res.setResponseCode(500);
			res.send();
		}
	}

	/**
	 * The POST handler: update the guest list and return a page
	 * allowing further updates.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doPost(HttpRequest req, HttpResponse res) {
		//Update the guest list from the form

		//Get the user
		User user = req.getUser();
		if (user == null) {
			res.setResponseCode(403);
			res.send();
			return;
		}

		//Get the Users instance.
		Users users = Users.getInstance();

		//See if this is a proxy.
		Path path = new Path(req.getPath());
		if ((path.length() > 1) && user.hasRole("proxy")) {
			user = users.getUser(path.element(1));
			//If the proxied user doesn't exist, stay with the current user.
			if (user == null) user = req.getUser();
		}

		//Get the FileSystemManager.
		FileSystemManager fsm = FileSystemManager.getInstance(root);
		if (fsm == null) {
			//There is no FileSystemManager for this root, return a 404.
			res.setResponseCode(404);
			res.send();
		}

		//Get the FileSystem corresponding to the user
		FileSystem fs = fsm.getFileSystem(user.getUsername());
		if (fs == null) {
			//There is no FileSystem for this user, return a 404.
			res.setResponseCode(404);
			res.send();
		}

		//Get the guest list for this FileSystem
		GuestList guestList = fs.getGuestList();

		//Walk through the parameters and update the guestList.
		int i = 1;
		String username;
		String checkbox;
		while ( (username = req.getParameter("un"+i)) != null) {
			checkbox = req.getParameter("cb"+i);
			if ((checkbox == null) || !checkbox.equals("yes")) guestList.remove(username);
			i++;
		}
		username = req.getParameter("addguest");
		if ( (username != null) && !username.trim().equals("")) {
			User guestUser = users.getUser(username);
			if ((guestUser != null) && guestUser.hasRole("guest")) {
				guestList.add(username);
			}
		}

		//Return the current page
		doGet(req,res);
	}

}

