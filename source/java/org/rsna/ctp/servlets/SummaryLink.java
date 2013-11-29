/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

public class SummaryLink {

	public final String url;
	public final String icon;
	public final String title;
	public final boolean loadInNewWindow;

	public SummaryLink(String url, String icon, String title, boolean loadInNewWindow) {
		this.url = url;
		this.icon = icon;
		this.title = title;
		this.loadInNewWindow = loadInNewWindow;
	}

	public String getURL() {
		return url;
	}

	public String getIcon() {
		return icon;
	}

	public String getTitle() {
		return title;
	}

	public boolean hasIcon() {
		return (icon != null) && !icon.trim().equals("");
	}

	public boolean needsNewWindow() {
		return loadInNewWindow;
	}
}