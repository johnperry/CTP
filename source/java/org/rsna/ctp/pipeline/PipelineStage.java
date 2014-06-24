/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import java.util.LinkedList;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.server.User;

public interface PipelineStage {

	public void start();

	public void shutdown();

	public boolean isDown();

	public String getName();

	public File getRoot();

	public String getID();

	public Quarantine getQuarantine();

	public String getConfigHTML(User user);

	public String getStatusHTML();

	public long getLastFileOutTime();

	public LinkedList<SummaryLink> getLinks(User user);

	public Pipeline getPipeline();

	public void setPipeline(Pipeline pipeline);

	public void setStageIndex(int index);

	public PipelineStage getNextStage();

	public void setNextStage(PipelineStage nextStage);

	public PipelineStage getPreviousStage();

	public void setPreviousStage(PipelineStage previousStage);

}