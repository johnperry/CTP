/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;

public interface PipelineStage {

	public void start();

	public void shutdown();

	public boolean isDown();

	public String getName();

	public File getRoot();

	public String getID();

	public Quarantine getQuarantine();

	public String getConfigHTML(boolean local);

	public String getStatusHTML();

}