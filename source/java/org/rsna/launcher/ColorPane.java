/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;

public class ColorPane extends JTextPane {

	public int lineHeight;
	boolean trackWidth = true;

	public ColorPane() {
		super();
		Font font = new Font("Monospaced",Font.PLAIN,12);
		FontMetrics fm = getFontMetrics(font);
		lineHeight = fm.getHeight();
		setFont(font);
		setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		setScrollableTracksViewportWidth(false);
	}

	public boolean getScrollableTracksViewportWidth() {
		return trackWidth;
	}

	public void setScrollableTracksViewportWidth(boolean trackWidth) {
		this.trackWidth = trackWidth;
	}

	public void clear() {
		setText("");
	}

	public void appendln(String s) {
		append(s + "\n");
	}

	public void appendln(Color c, String s) {
		append(c, s + "\n");
	}

	public void append(String s) {
		int len = getDocument().getLength(); // same value as getText().length();
		setCaretPosition(len);  // place caret at the end (with no selection)
		replaceSelection(s); // there is no selection, so inserts at caret
	}

	public void append(Color c, String s) {
		StyleContext sc = StyleContext.getDefaultStyleContext();
		AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);
		int len = getDocument().getLength();
		setCaretPosition(len);
		setCharacterAttributes(aset, false);
		replaceSelection(s);
	}

	public void print(String s) {
		final String ss = s;
		Runnable display = new Runnable() {
			public void run() {
				append(ss);
			}
		};
		SwingUtilities.invokeLater(display);
	}

	public void println(String s) {
		final String ss = s;
		Runnable display = new Runnable() {
			public void run() {
				appendln(ss);
			}
		};
		SwingUtilities.invokeLater(display);
	}

}
