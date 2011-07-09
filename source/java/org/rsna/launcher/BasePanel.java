/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import javax.swing.*;

/**
 * The base JPanel for all launcher tabs.
 */
public class BasePanel extends JPanel {

	static Color bgColor = new Color(0xc6d8f9);

	public BasePanel() {
		super();
		setLayout(new BorderLayout());
		setBackground(bgColor);
	}

}
