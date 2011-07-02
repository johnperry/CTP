/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import org.rsna.ctp.objects.FileObject;

/**
 * The interface specifying the additional methods that must
 * be implemented by a pipeline Stage that is an ExportService.
 */
public interface ExportService {

	/**
	 * Export a FileObject. This method is required to queue the
	 * object to be exported and then return immediately.
	 * @param fileObject the object to be exported.
	 */
	public void export(FileObject fileObject);

}