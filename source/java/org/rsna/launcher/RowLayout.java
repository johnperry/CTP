/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import javax.swing.JComponent;

public class RowLayout implements LayoutManager {
	private int horizontalGap = 10;
	private int verticalGap = 5;

	public RowLayout() { }

	public static JComponent crlf() {
		return new CRLF();
	}

	public void addLayoutComponent(String name,Component component) { }
	public void removeLayoutComponent(Component component) { }

	public Dimension preferredLayoutSize(Container parent) {
		return getLayoutSize(parent,horizontalGap,verticalGap,false);
	}

	public Dimension minimumLayoutSize(Container parent) {
		return getLayoutSize(parent,horizontalGap,verticalGap,false);
	}

	public void layoutContainer(Container parent) {
		getLayoutSize(parent,horizontalGap,verticalGap,true);
	}

	private Dimension getLayoutSize(Container parent, int hGap, int vGap, boolean layout) {
		Dimension d;
		Component[] components = parent.getComponents();
		Insets insets = parent.getInsets();

		//First find the maximum number of components in a row.
		int maxRowLength = 0;
		int x = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				maxRowLength = Math.max(maxRowLength, x);
				x = 0;
			}
			else x++;
		}

		//Now find the maximum width required for each column
		int[] columnWidth = new int[maxRowLength];
		for (int i=0; i<columnWidth.length; i++) columnWidth[i] = 0;
		x = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				x = 0;
			}
			else {
				d = components[i].getPreferredSize();
				columnWidth[x] = Math.max(columnWidth[x], d.width);
				x++;
			}
		}

		//Now lay out the container
		int currentX = insets.left;
		int currentY = insets.top;
		int lineHeight = 0;
		int maxX = 0;
		x = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				maxX = Math.max(maxX, currentX + insets.right);
				currentX = insets.left;
				currentY += lineHeight + vGap;
				lineHeight = 0;
				x = 0;
			}
			else {
				//It's not a CRLF, lay it out.
				d = components[i].getPreferredSize();
				lineHeight = Math.max(lineHeight, d.height);
				if (layout) {
					components[i].setBounds(currentX, currentY, d.width, d.height);
				}
				currentX += columnWidth[x] + hGap;
				x++;
			}
		}
		return new Dimension(maxX - hGap, currentY + insets.bottom - vGap);
	}

	static class CRLF extends JComponent {
		public CRLF() {
			super();
			setVisible(false);
		}
	}

}
