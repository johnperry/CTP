/*---------------------------------------------------------------
*  Copyright 2014 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.event;

/**
 * The interface for listeners to FileEvents.
 */
public interface FileEventListener {

	/**

	 * Notify listeners that a file event has occurred.
	 * @param event the event describing the file that was affected.
	 */
	public void fileEventOccurred(FileEvent event);

}
