/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import org.rsna.ctp.objects.FileObject;

/**
 * The interface specifying the additional methods that must
 * be implemented by a pipeline Stage that is a generic
 * processor.
 */
public interface Processor {

	/**
	 * Process a FileObject.
	 * @param fileObject the object to be processed.
	 * @return the processed object.
	 */
	public FileObject process(FileObject fileObject);

}