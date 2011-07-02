/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;

public interface ScriptableDicom {

	/**
	 * Get the script file.
	 */
	public File getScriptFile();

	/**
	 * Get the lookup table file.
	 */
	public File getLookupTableFile();

}