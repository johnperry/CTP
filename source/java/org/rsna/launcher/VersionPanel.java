/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import javax.swing.*;

public class VersionPanel extends BasePanel {

	public VersionPanel() {
		super();
		Configuration config = Configuration.getInstance();
		JEditorPane vp = new JEditorPane( "text/html", getPage(config) );
		vp.setEditable(false);

		JScrollPane jsp = new JScrollPane();
		jsp.setViewportView(vp);
		add(jsp, BorderLayout.CENTER);
	}

	private String getPage(Configuration config) {
		String page =
				"<html>"
			+	"<head></head>"
			+	"<body style=\"background:#c6d8f9;font-family:sans-serif;\">"
			+	"<center>"
			+	"<h1 style=\"color:blue\">" + config.programName + "</h1>"
			+	"Copyright 2012: RSNA<br><br>"
			+	"<p>"
			+	"<table>"
			+	"<tr><td>This computer's Java Version:</td><td>"+config.thisJava+"</td></tr>"
			+	"<tr><td>This computer's Java Data Model:</td><td>"+config.thisJavaBits+"</td></tr>"
			+	"<tr><td>CTP Java Version:</td><td>"+config.ctpJava+"</td></tr>"
			+	"<tr><td>CTP Date:</td><td>"+config.ctpDate+"</td></tr>"
			+	"<tr><td>Utility Library Java Version:</td><td>"+config.utilJava+"</td></tr>"
			+	"<tr><td>Utility Library Date:</td><td>"+config.utilDate+"</td></tr>";

		if (config.isMIRC) {

			page +=
				 (!config.mircJava.equals("") ? "<tr><td>MIRC Plugin Java Version:</td><td>"+config.mircJava+"</td></tr>" : "")
				+(!config.mircDate.equals("") ? "<tr><td>MIRC Plugin Date:</td><td>"+config.mircDate+"</td></tr>" : "")
				+(!config.mircVersion.equals("") ? "<tr><td>MIRC Plugin Version:</td><td>"+config.mircVersion+"</td></tr>" : "");
		}

		if (config.isISN) {

			page +=
				 (!config.isnJava.equals("") ? "<tr><td>ISN Java Version:</td><td>"+config.isnJava+"</td></tr>" : "")
				+(!config.isnDate.equals("") ? "<tr><td>ISN Date:</td><td>"+config.isnDate+"</td></tr>" : "")
				+(!config.isnVersion.equals("") ? "<tr><td>ISN Version:</td><td>"+config.isnVersion+"</td></tr>" : "");
		}

		page +=
				"<tr><td>ImageIO Tools Version:</td><td>"+config.imageIOVersion+"</td></tr>"
			+	"</table>"
			+	"</p>"
			+	"</center>"
			+	"</body>"
			+	"</html>";

		return page;
	}
}
