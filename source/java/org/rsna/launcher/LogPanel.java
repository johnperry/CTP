/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;

public class LogPanel extends BasePanel implements ActionListener {

	public static ColorPane out;

	JScrollPane jsp;
	JCheckBox wrap;
	JButton delete;
	JButton refresh;
	File log = new File("logs/ctp.log");

	public LogPanel() {
		super();

		out = new ColorPane();
		out.setScrollableTracksViewportWidth(false);

		jsp = new JScrollPane();
		jsp.setViewportView(out);
		add(jsp, BorderLayout.CENTER);

		wrap = new JCheckBox("Wrap lines");
		wrap.setBackground(bgColor);
		wrap.addActionListener(this);

		delete = new JButton("Delete");
		delete.setToolTipText("Delete old logs");
		delete.addActionListener(this);

		refresh = new JButton("Refresh");
		refresh.addActionListener(this);

		Box footer = Box.createHorizontalBox();
		footer.add(wrap);
		footer.add(Box.createHorizontalGlue());
		footer.add(delete);
		footer.add(Box.createHorizontalStrut(3));
		footer.add(refresh);
		add(footer, BorderLayout.SOUTH);
	}

	public void reload() {
		out.clear();
		if (log.exists()) {
			try { out.append( Util.getText( log ) ); }
			catch (Exception ignore) { }
		}
	}

	public void actionPerformed(ActionEvent event) {
		if (event.getSource().equals(wrap)) {
			out.setScrollableTracksViewportWidth( wrap.isSelected() );
			jsp.invalidate();
			jsp.validate();
		}
		else if (event.getSource().equals(refresh)) {
			reload();
		}
		else if (event.getSource().equals(delete)) {
			File logs = new File("logs");
			Util.deleteAll(logs);
			reload();
		}
	}

}
