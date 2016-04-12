/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;

public interface ScriptableDicom {

	/**
	 * Get the script file.
	 * @return the script file
	 */
	public File getScriptFile();

	/**
	 * Get the lookup table file.
	 * @return the lookup table file
	 */
	public File getLookupTableFile();

}