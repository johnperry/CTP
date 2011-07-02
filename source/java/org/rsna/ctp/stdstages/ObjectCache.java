/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

/**
 * A Processor stage that caches the current object.
 */
public class ObjectCache extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(ObjectCache.class);

	int count = 0;
	FileObject cachedObject = null;

	/**
	 * Construct the ObjectCache PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public ObjectCache(Element element) {
		super(element);
	}

	/**
	 * Cache the current object.
	 * @param fileObject the object to cache.
	 * @return the same FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		//Delete any objects in the root directory
		for (File file : root.listFiles()) FileUtil.deleteAll(file);

		//Copy the object to the root directory
		File file = new File(root, "object");
		FileUtil.copy( fileObject.getFile(), file );

		//Parse the object and save it for later
		cachedObject = FileObject.getInstance( file );

		//Set the correct extension
		cachedObject.setStandardExtension();

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Get the object currently in the cache.
	 * @return the parsed object in the cache.
	 */
	public FileObject getCachedObject() {
		return cachedObject;
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		return getStatusHTML("");
	}

}