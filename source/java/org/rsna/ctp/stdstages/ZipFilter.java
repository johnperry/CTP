/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.server.User;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * A script-based filter for ZipObjects.
 */
public class ZipFilter extends AbstractPipelineStage implements Processor, Scriptable {

	static final Logger logger = Logger.getLogger(ZipFilter.class);

	public File scriptFile = null;

	/**
	 * Construct the XmlFilter PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public ZipFilter(Element element) {
		super(element);
		scriptFile = FileUtil.getFile(element.getAttribute("script").trim(), "examples/example-filter.script");
	}

	/**
	 * Evaluate the script for the object and quarantine the object if
	 * the result is false.
	 * @param fileObject the object to process.
	 * @return the same FileObject if the result is true; otherwise null.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if (fileObject instanceof ZipObject) {
			String script = FileUtil.getText(scriptFile);
			if (!((ZipObject)fileObject).matches(script)) {
				if (quarantine != null) quarantine.insert(fileObject);
				lastFileOut = null;
				lastTimeOut = System.currentTimeMillis();
				return null;
			}
		}
		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Get the script file.
	 * @return the script file used by this stage.
	 */
	public File[] getScriptFiles() {
		return new File[] {scriptFile};
	}

	/**
	 * Get the list of links for display on the summary page.
	 * @param user the requesting user.
	 * @return the list of links for display on the summary page.
	 */
	public LinkedList<SummaryLink> getLinks(User user) {
		LinkedList<SummaryLink> links = super.getLinks(user);
		boolean admin = (user != null) && user.hasRole("admin");
		if (admin) {
			String qs = "?p="+pipeline.getPipelineIndex()+"&s="+stageIndex+"&f=0";
			if (scriptFile != null) {
				links.addFirst( new SummaryLink("/script"+qs, null, "Edit the Script File", false) );
			}
		}
		return links;
	}

}