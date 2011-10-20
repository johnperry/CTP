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

public class IOPanel extends BasePanel implements ActionListener {

	public static ColorPane out;

	JScrollPane jsp;
	JCheckBox wrap;

	public IOPanel() {
		super();

		out = new ColorPane();
		out.setScrollableTracksViewportWidth(false);

		jsp = new JScrollPane();
		jsp.setViewportView(out);
		jsp.getViewport().setBackground(Color.white);
		add(jsp, BorderLayout.CENTER);

		Box footer = Box.createHorizontalBox();
		wrap = new JCheckBox("Wrap lines");
		wrap.setBackground(bgColor);
		wrap.addActionListener(this);

		footer.add(wrap);
		footer.add(Box.createHorizontalGlue());
		add(footer, BorderLayout.SOUTH);
	}

	public void actionPerformed(ActionEvent event) {
		if (event.getSource().equals(wrap)) {
			out.setScrollableTracksViewportWidth( wrap.isSelected() );
			jsp.invalidate();
			jsp.validate();
		}
	}

}
