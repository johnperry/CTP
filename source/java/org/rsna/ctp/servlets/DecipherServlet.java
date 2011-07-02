/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.servlets.Servlet;
import org.rsna.util.CipherUtil;

/**
 * The Decipher servlet.
 * This servlet provides an AJAX service for deciphering
 * encrypted elements in DicomObjects. It requires that
 * the user have the admin or qadmin roles.
 */
public class DecipherServlet extends Servlet {

	static final Logger logger = Logger.getLogger(DecipherServlet.class);

	/**
	 * Construct a DecipherServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public DecipherServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * This method returns an XML object containing the deciphered
	 * value of the selected element.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
			HttpRequest req,
			HttpResponse res) {

		//Make sure the user is authorized to do this.
		if (req.userHasRole("admin") || req.userHasRole("qadmin")) {

			//OK, it's allowed, get the parameters.
			String file = req.getParameter("file");
			String elem = req.getParameter("elem");
			String key = req.getParameter("key");

			//Get the object, find the element, and decipher it
			try {
				DicomObject dob = new DicomObject(new File(file));
				elem = elem.replaceAll("[^a-fA-F0-9]","");
				int tag = Integer.parseInt(elem,16);
				String value = new String(dob.getElementByteBuffer(tag).array());
				String result = CipherUtil.decipher(value, key);
				res.write(result);
			}
			catch (Exception ex) { res.setResponseCode(404); }
		}
		else { res.setResponseCode(403); } //Not authorized

		//Send the response
		res.disableCaching();
		res.setContentType("txt");
		res.send();
	}

}











