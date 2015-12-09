/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

public class EnvironmentPanel extends BasePanel {

	public EnvironmentPanel() {
		super();
		JEditorPane pane = new JEditorPane( "text/html", getPage() );
		pane.setEditable(false);
		pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

		JScrollPane jsp = new JScrollPane();
		jsp.setViewportView(pane);
		add(jsp, BorderLayout.CENTER);
	}

	//Create an HTML page containing the data.
	private String getPage() {
		String page =
				"<html>\n"
			+	" <head></head>\n"
			+	"<body style=\"background:#b9d0ed;font-family:sans-serif;\">"
			+	" <center>\n"
			+ 	"  <h1 style=\"color:#2977b9\">Environment Variables</h1>\n"
			+	"   <table border=\"1\" style=\"background:white\">\n"
			+	     displayEnvironment()
			+	"   </table>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return page;
	}

	//Return a String containing the HTML rows of a table
	//displaying all the environment variables.
	private String displayEnvironment() {
		String v;
		String sep = System.getProperty("path.separator",";");
		StringBuffer sb = new StringBuffer();

		Map<String,String> env = System.getenv();
		String[] n = new String[env.size()];
		n = env.keySet().toArray(n);
		Arrays.sort(n);

		for (int i=0; i<n.length; i++) {
			v = env.get(n[i]);

			//Make path and dirs variables more readable by
			//putting each element on a separate line.
			if (n[i].toLowerCase().contains("path"))
				v = v.replace(sep, sep+"<br/>");

			sb.append( "<tr><td>" + n[i] + "</td><td>" + v + "</td></tr>\n" );
		}
		return sb.toString();
	}

}
