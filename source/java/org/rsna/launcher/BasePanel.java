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

	static Color bgColor = new Color(0xb9d0ed);
	static Color titleColor = new Color(0x2977b9);

	public BasePanel() {
		super();
		setLayout(new BorderLayout());
		setBackground(bgColor);
	}

}
