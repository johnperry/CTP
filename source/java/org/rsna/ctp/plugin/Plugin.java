/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.plugin;

public interface Plugin {

	public void start();

	public void shutdown();

	public boolean isDown();

	public String getName();

	public String getID();

	public String getConfigHTML(boolean local);

	public String getStatusHTML();

}