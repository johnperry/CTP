/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class IOPanel extends BasePanel {

	public static ColorPane out;

	JScrollPane jsp;
	JCheckBox wrap;

	public IOPanel() {
		super();

		out = new ColorPane();
		out.setScrollableTracksViewportWidth(false);

		BasePanel bp = new BasePanel();
		bp.add(out, BorderLayout.CENTER);

		jsp = new JScrollPane();
		jsp.setViewportView(bp);
		jsp.getViewport().setBackground(Color.white);
		add(jsp, BorderLayout.CENTER);
	}

}
