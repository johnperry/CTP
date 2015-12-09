/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import javax.swing.*;

/**
 * The base JPanel for all launcher tabs.
 */
public class BasePanel extends JPanel implements Scrollable{

	static Color bgColor = new Color(0xb9d0ed);
	static Color titleColor = new Color(0x2977b9);
	private boolean trackWidth = true;

	public BasePanel() {
		super();
		setLayout(new BorderLayout());
		setBackground(bgColor);
	}

	public void setTrackWidth(boolean trackWidth) { this.trackWidth = trackWidth; }
	public boolean getScrollableTracksViewportHeight() { return false; }
	public boolean getScrollableTracksViewportWidth() { return trackWidth; }
	public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 30; }
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 30; }
}
