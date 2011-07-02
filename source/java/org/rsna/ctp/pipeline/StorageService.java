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
 * be implemented by a pipeline Stage that is a StorageService.
 */
public interface StorageService {

	/**
	 * Store a FileObject.
	 * @param fileObject the object to be stored.
	 * @return either the original object or the object placed in storage.
	 */
	public FileObject store(FileObject fileObject);

}