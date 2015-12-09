/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.ImportService;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A Processor stage that logs objects as they flow by.
 */
public class PerformanceLogger extends AbstractPipelineStage implements Processor {

	static final Logger logger = Logger.getLogger(PerformanceLogger.class);

	long startTime = 0;

	int count = 0;
	int interval = 1;
	long prevStageTime = 0;
	final String margin = "\n                              ";

	/**
	 * Construct the PerformanceLogger PipelineStage.
	 * This stage summarizes the times taken by each of the stages
	 * in its pipeline to process the current file. This stage
	 * should normally be the last stage in the pipe.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public PerformanceLogger(Element element) {
		super(element);
		interval = Math.max( 1, StringUtil.getInt(element.getAttribute("interval").trim(), 1) );
		count = 0;
	}

	/**
	 * Log objects as they are received by the stage.
	 * @param fileObject the object to log.
	 * @return the same FileObject.
	 */
	public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if ((count % interval) == 0) {
			Pipeline pipe = getPipeline();
			StringBuffer sb = new StringBuffer();
			for (PipelineStage stage : pipe.getStages()) {
				long timeOut = stage.getLastFileOutTime();
				long processTime = 0;
				if (stage instanceof ImportService) {
					if ((prevStageTime > 0) && (timeOut > prevStageTime)) {
						processTime = timeOut - prevStageTime;
					}
					prevStageTime = timeOut;
				}
				else if (stage.equals(this)) {
					long currentTime = System.currentTimeMillis();
					processTime = currentTime - prevStageTime;
					prevStageTime = currentTime;
				}
				else {
					processTime = timeOut - prevStageTime;
					prevStageTime = timeOut;
				}
				sb.append(margin + String.format("%6d",processTime) + " ms: " + stage.getName());
			}

			logger.info(
				name
				+ ": (" + (count+1) + ") "
				+ fileObject.getClassName()
				+ String.format(" [%,d bytes]", fileObject.getFile().length())
				+ sb.toString()
			);
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