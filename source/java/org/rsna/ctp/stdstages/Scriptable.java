/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import java.io.File;

/**
 * The interface specifying the requirements for a PipelineStage
 * that contains one or more scripts that can be edited by the
 * ScriptServlet.
 */
public interface Scriptable {

	/**
	 * Get the script file.
	 */
	public File[] getScriptFiles();

}