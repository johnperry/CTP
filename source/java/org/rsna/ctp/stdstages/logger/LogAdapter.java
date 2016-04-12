/*---------------------------------------------------------------
*  Copyright 2016 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.logger;

import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.pipeline.Status;

public interface LogAdapter {
	
	/**
	 * Get the name of the cohort to which the current object belongs.
	 * @param currentObject the object that has madve it down the pipeline
	 * to the calling stage.
	 * @param cachedObject the object that was cached at the head end of the pipe.
	 * @return the name of the cohort to which the objects belong.
	 */
	public String getCohortName(DicomObject currentObject, DicomObject cachedObject);
	
	/**
	 * Connect to the external logging database.
	 * This method is called whenever the exporter starts a sequence of exports.
	 * It is not called again until the exporter empties the export queue,
	 * disconnects, and then receives another QueueEntry to export.
	 * @return Status.OK or Status.FAIL
	 */
	public Status connect();
	
	/**
	 * Disconnect from the external logging database.
	 * This method is called when the exporter empties the export queue
	 * or when the pipeline tells the stage to shut down.
	 * This method should commit the database and then disconnect.
	 * @return Status.OK or Status.FAIL
	 */
	public Status disconnect();
	
	/**
	 * Export one QueueEntry to the external logging database.
	 * This method is called from the exporter thread.
	 * @param queueEntry the entry to export
	 * @return Status.OK, Status.RETRY, or Status.FAIL
	 */
	public Status export(QueueEntry queueEntry);
	
}
