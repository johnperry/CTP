/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.plugin;

import java.util.LinkedList;
import org.rsna.ctp.servlets.SummaryLink;
import org.rsna.server.User;

public interface Plugin {

	public void start();

	public void shutdown();

	public boolean isDown();

	public String getName();

	public String getID();

	public String getConfigHTML(User user);

	public String getStatusHTML();

	public LinkedList<SummaryLink> getLinks(User user);

}