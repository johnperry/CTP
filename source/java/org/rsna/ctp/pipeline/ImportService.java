/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import org.rsna.ctp.objects.FileObject;

/**
 * The interface specifying the additional methods that must
 * be implemented by a pipeline Stage that is an ImportService.
 */
public interface ImportService {

	/**
	 * Get the next object available for processing
	 * @return the next object available, or null if no object is available.
	 */
	public FileObject getNextObject();

	/**
	 * Release a file, allowing an ImportService to clean up temporary
	 * storage if necessary. Note that other stages in the pipeline may
	 * have moved the file, so it is possible that the file will no
	 * longer exist. It is therefore important to check the existence of
	 * the file before using it.
	 * @param file the file to be released, which must be the original file
	 * supplied by the ImportService.
	 */
	public void release(File file);

}