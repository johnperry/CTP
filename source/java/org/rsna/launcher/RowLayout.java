/*---------------------------------------------------------------
*  Copyright 2012 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.util.LinkedList;
import javax.swing.JComponent;

public class RowLayout implements LayoutManager {
	private int horizontalGap = 10;
	private int verticalGap = 5;

	public RowLayout() { }

	public RowLayout(int horizontalGap, int verticalGap) {
		this.horizontalGap = horizontalGap;
		this.verticalGap = verticalGap;
	}

	public static JComponent crlf() {
		return new CRLF();
	}

	static boolean isCRLF(Component c) {
		return (c instanceof CRLF);
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

	public LinkedList<LinkedList<Component>> getRows(Container parent) {
		Component[] components = parent.getComponents();
		LinkedList<LinkedList<Component>> rows = new LinkedList<LinkedList<Component>>();
		LinkedList<Component> row = null;
		for (int i=0; i<components.length; i++) {
			if (row == null) {
				row = new LinkedList<Component>();
				rows.add( row );
			}
			Component c = components[i];
			row.add( c );
			if (c instanceof CRLF) row = null;
		}
		return rows;
	}

	private Dimension getLayoutSize(Container parent, int hGap, int vGap, boolean layout) {
		Dimension d;
		Component[] components = parent.getComponents();
		Insets insets = parent.getInsets();

		//First find the number of rows and columns.
		int maxRowLength = 0;
		int x = 0;
		int y = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				maxRowLength = Math.max(maxRowLength, x);
				x = 0;
				y++;
			}
			else x++;
		}

		//Now find the maximum width required for each column
		//and the maximum height required for each row.
		int[] columnWidth = new int[maxRowLength];
		int[] rowHeight = new int[y+1];
		for (int i=0; i<columnWidth.length; i++) columnWidth[i] = 0;
		for (int i=0; i<rowHeight.length; i++) rowHeight[i] = 0;
		x = 0;
		y = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				x = 0;
				y++;
			}
			else {
				d = components[i].getPreferredSize();
				columnWidth[x] = Math.max(columnWidth[x], d.width);
				rowHeight[y] = Math.max(rowHeight[y], d.height);
				x++;
			}
		}

		//Now lay out the container
		int currentX = insets.left;
		int currentY = insets.top;
		int maxX = 0;
		x = 0;
		y = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				maxX = Math.max(maxX, currentX + insets.right);
				currentX = insets.left;
				currentY += rowHeight[y] + vGap;
				x = 0;
				y++;
			}
			else {
				//It's not a CRLF, lay it out.
				d = components[i].getPreferredSize();
				float xAlign = components[i].getAlignmentX();
				float yAlign = components[i].getAlignmentY();
				int leftMargin = (int) ((columnWidth[x] - d.width) * xAlign);
				int topMargin = (int) ((rowHeight[y] - d.height) * yAlign);
				if (layout) {
					components[i].setBounds(currentX + leftMargin, currentY + topMargin, d.width, d.height);
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
