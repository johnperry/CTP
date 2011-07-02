/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.Path;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A Servlet which provides web access to the index data in a FileStorageService.
 */
public class AjaxServlet extends Servlet {

	static final Logger logger = Logger.getLogger(AjaxServlet.class);

	/**
	 * Construct an AjaxServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public AjaxServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: service queries for data about stored objects.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {

		//Get the FileSystemManager.
		FileSystemManager fsm = FileSystemManager.getInstance(root);
		if (fsm == null) {
			//There is no FileSystemManager for this root, return a 404.
			res.setResponseCode(404);
			res.send();
		}
		//Figure out what was requested from the path.
		//The format of the path is:
		//  /ajax/[query]/[data]?modifiers
		//
		//The following paths are supported:
		//  /ajax/listFileSystems
		//  /ajax/listStudies/[FileSystemName]
		//  /ajax/listObjects/[FileSystemName]/[StudyUID]
		//  /ajax/findObject/[UID]
		//
		//All calls return XML objects.
		Path path = new Path(req.path);
		String query = path.element(1);

		if (query.equals("listFileSystems")) listFileSystems(res, fsm);
		else if (query.equals("listStudies")) listStudies(res, path, fsm);
		else if (query.equals("listObjects")) listObjects(res, path, fsm);
		else if (query.equals("findObject")) findObject(res, path, fsm);
		else {
			//Not one of those; return NotFound
			res.setResponseCode(404);
			res.send();
		}
	}

	//List all the file systems.
	private void listFileSystems(HttpResponse res, FileSystemManager fsm) {
		try {
			List<String> fsList = fsm.getFileSystems();
			Document doc = XmlUtil.getDocument();
			Element docRoot = doc.createElement("FileSystemList");
			doc.appendChild(docRoot);
			Iterator<String> lit = fsList.iterator();
			while (lit.hasNext()) {
				String fsName = lit.next();
				Element fsElement = doc.createElement("FileSystem");
				fsElement.setAttribute("name", fsName);
				docRoot.appendChild(fsElement);
			}
			res.write(XmlUtil.toString(doc));
			res.setContentType("xml");
			res.disableCaching();
			res.send();
		}
		catch (Exception ex) {
			res.setResponseCode(404);
			res.send();
		}
	}

	//List all studies in one file system.
	private void listStudies(HttpResponse res, Path path, FileSystemManager fsm) {
		try {
			String fsName = path.element(2);
			FileSystem fs = fsm.getFileSystem(fsName, false);
			Document doc = fs.getIndex();
			res.write(XmlUtil.toString(doc));
			res.setContentType("xml");
			res.disableCaching();
			res.send();
		}
		catch (Exception ex) {
			res.setResponseCode(404);
			res.send();
		}
	}

	//List all the objects in one study.
	private void listObjects(HttpResponse res, Path path, FileSystemManager fsm) {
		try {
			String fsName = path.element(2);
			String studyUID = path.element(3);
			FileSystem fs = fsm.getFileSystem(fsName, false);
			Study study = fs.getStudyByUID(studyUID);
			Document doc = study.getIndex();
			res.write(XmlUtil.toString(doc));
			res.setContentType("xml");
			res.disableCaching();
			res.send();
		}
		catch (Exception ex) {
			res.setResponseCode(404);
			res.send();
		}
	}

	//Get an object.
	private void findObject(HttpResponse res, Path path, FileSystemManager fsm) {
		try {
			String uid = path.element(2);

			Document doc = XmlUtil.getDocument();
			Element docRoot = doc.createElement("ObjectList");
			docRoot.setAttribute("uid", uid);
			doc.appendChild(docRoot);

			//Look in all the FileSystems
			List<String> fsList = fsm.getFileSystems();
			Iterator<String> lit = fsList.iterator();
			while (lit.hasNext()) {
				String fsName = lit.next();
				FileSystem fs = fsm.getFileSystem(fsName, false);

				//Look in all the studies for this FileSystem
				Study[] studies = fs.getStudies();
				for (int i=0; i<studies.length; i++) {
					Document index = studies[i].getIndex();
					Element studyRoot = index.getDocumentElement();
					Node child = studyRoot.getFirstChild();
					while (child != null) {
						if (child.getNodeType() == Node.ELEMENT_NODE) {
							if (getValue(child, "uid").equals(uid)) {
								Element obj = doc.createElement("Object");
								String url =
									"/storage/"
										+fsName+"/"
											+studyRoot.getAttribute("studyName")
												+"/" + getValue(child, "file");
								obj.setAttribute("url", url);
								obj.setAttribute("patientName", studyRoot.getAttribute("patientName"));
								obj.setAttribute("patientID", studyRoot.getAttribute("patientID"));
								obj.setAttribute("studyName", studyRoot.getAttribute("studyName"));
								obj.setAttribute("fileSystemName", studyRoot.getAttribute("fileSystemName"));
								Node importedChild = doc.importNode(child, true);
								obj.appendChild(importedChild);
								docRoot.appendChild(obj);
							}
						}
						child = child.getNextSibling();
					}
				}
			}
			res.write(XmlUtil.toString(doc));
			res.setContentType("xml");
			res.disableCaching();
			res.send();
		}
		catch (Exception ex) {
			res.setResponseCode(404);
			res.send();
		}
	}

	//Get the text content of a named child of a node.
	private String getValue(Node node, String name) {
		Node child = node.getFirstChild();
		while (child != null) {
			if (child.getNodeName().equals(name)) {
				return child.getTextContent();
			}
			child = child.getNextSibling();
		}
		return "";
	}

}

