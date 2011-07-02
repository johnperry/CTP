/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A Processor stage that monitors heap space and provides garbage collection and logging.
 */
public class MemoryMonitor extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(MemoryMonitor.class);

	private static final Runtime runtime = Runtime.getRuntime();
	private static long usedMemory() {
		return runtime.totalMemory() - runtime.freeMemory();
	}

	int count = 0;
	int interval = 1;
	boolean collectGarbage = true;
	boolean logMemoryInUse = true;

	/**
	 * Construct the MemoryMonitor PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public MemoryMonitor(Element element) {
		super(element);
		count = 0;
		interval = Math.max( 1, StringUtil.getInt(element.getAttribute("interval"), 1) );
		collectGarbage = !element.getAttribute("collectGarbage").trim().equals("no");
		logMemoryInUse = !element.getAttribute("logMemoryInUse").trim().equals("no");
	}

	/**
	 * Process objects as they are received by the stage.
	 * @param fileObject the object.
	 * @return the same FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if ((count % interval) == 0) {

			if (collectGarbage)  {
				runtime.runFinalization();
				runtime.gc();
			}

			if (logMemoryInUse) {
				logger.info(name + ": (" + (count+1) + "): memory in use: " + usedMemory());
			}
		}
		count++;

		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	public String getStatusHTML() {
		String stageUniqueStatus =
			"<tr><td width=\"20%\">Files processed:</td>"
			+ "<td>" + count + "</td></tr>";
		return super.getStatusHTML(stageUniqueStatus);
	}
}