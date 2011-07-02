/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.util.*;
import javax.swing.*;

public class SystemPanel extends BasePanel {

	public SystemPanel() {
		super();
		JEditorPane vp = new JEditorPane( "text/html", getPage() );
		vp.setEditable(false);

		JScrollPane jsp = new JScrollPane();
		jsp.setViewportView(vp);
		add(jsp, BorderLayout.CENTER);
	}

	//Create an HTML page containing the data.
	private String getPage() {
		String page =
				"<html>\n"
			+	" <head></head>\n"
			+	"<body style=\"background:#c6d8f9;font-family:sans-serif;\">"
			+	" <center>\n"
			+ 	"  <h1 style=\"color:blue\">System Properties</h1>\n"
			+	"   <table border=\"1\">\n"
			+	     displayProperties()
			+	"   </table>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return page;
	}

	//Return a String containing the HTML rows of a table
	//displaying all the Java System properties.
	private String displayProperties() {
		String v;
		String sep = System.getProperty("path.separator",";");
		StringBuffer sb = new StringBuffer();

		Properties p = System.getProperties();
		String[] n = new String[p.size()];
		Enumeration e = p.propertyNames();
		for (int i=0; i< n.length; i++) n[i] = (String)e.nextElement();
		Arrays.sort(n);
		for (int i=0; i<n.length; i++) {
			v = p.getProperty(n[i]);

			//Make path and dirs properties more readable by
			//putting each element on a separate line.
			if (n[i].endsWith(".path") ||
				n[i].endsWith(".dirs"))
					v = v.replace(sep, sep+"<br/>");

			//Make definition, access, and loader properties more
			//readable by putting each element on a separate line.
			if (n[i].endsWith(".definition") ||
				n[i].endsWith(".access") ||
				n[i].endsWith(".loader"))
					v = v.replace(",", ",<br>");

			sb.append( "<tr><td>" + n[i] + "</td><td>" + v + "</td></tr>\n" );
		}
		return sb.toString();
	}

}
