/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import org.rsna.util.StringUtil;

/**
 * The ClinicalTrialProcessor program launcher.
 * This program provides a GUI for starting and
 * stopping CTP and for configuring the Java
 * launch parameters.
 */
public class Launcher extends JFrame implements ChangeListener {

	JTabbedPane		tp;
	JavaPanel		javaPanel;
	VersionPanel	versionPanel;
	SystemPanel		systemPanel;
	EnvironmentPanel environmentPanel;
	ConfigPanel		configPanel;
	IOPanel			ioPanel;
	LogPanel		logPanel;
	ReportPanel		reportPanel;

	static boolean	autostart = false;

	Configuration	config;

	public static void main(String args[]) {
		if (args.length > 0) autostart = args[0].trim().toLowerCase().equals("start");
		new Launcher();
	}

	/**
	 * Class constructor; creates a new Launcher object, displays a JFrame
	 * providing the GUI for configuring and launching CTP.
	 */
	public Launcher() {
		super();
		setBackground( BasePanel.bgColor );

		//Set the SSL params
		System.setProperty("javax.net.ssl.keyStore", "keystore");
		System.setProperty("javax.net.ssl.keyStorePassword", "ctpstore");

		try {
			config = Configuration.load();
			setTitle(config.windowTitle);

			versionPanel = new VersionPanel();
			javaPanel = JavaPanel.getInstance();
			systemPanel = new SystemPanel();
			environmentPanel = new EnvironmentPanel();
			configPanel = ConfigPanel.getInstance();
			ioPanel = new IOPanel();
			logPanel = LogPanel.getInstance();
			reportPanel = ReportPanel.getInstance();
			tp = new JTabbedPane();
			tp.setBackground( Color.white );
			tp.setForeground( BasePanel.titleColor );

			tp.add("General", javaPanel);
			tp.add("Version", versionPanel);
			tp.add("System", systemPanel);
			tp.add("Environment", environmentPanel);
			tp.add("Configuration", configPanel);
			tp.add("Console", ioPanel);
			tp.add("Log", logPanel);
			tp.add("Report", reportPanel);

			tp.addChangeListener(this);

			this.getContentPane().add( tp, BorderLayout.CENTER );
			this.addWindowListener(new WindowCloser(this));

			pack();

			positionFrame();
			setVisible(true);

			UIManager.put("Button.defaultButtonFollowsFocus", Boolean.TRUE);

			if (autostart) javaPanel.start();
			else {
				javaPanel.setFocusOnStart();
				if (!config.hasPipelines()) {
					tp.setSelectedComponent(configPanel);
					configPanel.showHelp();
				}
			}
		}
		catch (Exception ex) {
			setVisible(true);
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			JOptionPane.showMessageDialog(null, "Unable to start the Launcher program.\n\n" + sw.toString());
			System.exit(0);
		}
	}

	public void stateChanged(ChangeEvent event) {
		Component comp = tp.getSelectedComponent();
		if (comp.equals(logPanel)) {
			logPanel.reload();
		}
		else if (comp.equals(configPanel)) {
			configPanel.load();
		}
	}

	private void positionFrame() {
		Properties props = Configuration.getInstance().props;
		int x = StringUtil.getInt( props.getProperty("x"), 0 );
		int y = StringUtil.getInt( props.getProperty("y"), 0 );
		int w = StringUtil.getInt( props.getProperty("w"), 0 );
		int h = StringUtil.getInt( props.getProperty("h"), 0 );

		boolean noProps = ((w == 0) || (h == 0));

		int wmin = 550;
		int hmin = 600;
		if ((w < wmin) || (h < hmin)) {
			w = wmin;
			h = hmin;
		}

		if ( noProps || !screensCanShow(x, y) || !screensCanShow(x+w-1, y+h-1) ) {
			Toolkit t = getToolkit();
			Dimension scr = t.getScreenSize ();
			x = (scr.width - wmin)/2;
			y = (scr.height - hmin)/2;
			w = wmin;
			h = hmin;
		}
		setSize( w, h );
		setLocation( x, y );
	}

	private boolean screensCanShow(int x, int y) {
		GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = env.getScreenDevices();
		for (GraphicsDevice screen : screens) {
			GraphicsConfiguration[] configs = screen.getConfigurations();
			for (GraphicsConfiguration gc : configs) {
				if (gc.getBounds().contains(x, y)) return true;
			}
		}
		return false;
	}

    //Class to capture a window close event and give the
    //user a chance to change his mind.
    class WindowCloser extends WindowAdapter {
		private Component parent;
		public WindowCloser(JFrame parent) {
			this.parent = parent;
			parent.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		}
		public void windowClosing(WindowEvent evt) {
			save();
			if (Util.isRunning()) {
				int response = JOptionPane.showConfirmDialog(
								parent,
								"Do you want to stop the "+config.programName+" server?\n\n" +
								"If you click YES, the server will be stopped before the Launcher exits.\n" +
								"If you click NO, the server will be left running after the Launcher exits.\n" +
								"To keep both the server and the Launcher running, click CANCEL.\n\n",
								"Are you sure?",
								JOptionPane.YES_NO_CANCEL_OPTION);

				boolean stop = (response == JOptionPane.YES_OPTION);
				boolean exit = (response == JOptionPane.NO_OPTION);

				if (stop && configOK()) {
					Util.shutdown();
					System.exit(0);
				}
				if (exit && configOK()) {
					System.exit(0);
				}
			}
			else if (configOK()) {
				System.exit(0);
			}
		}
		private boolean configOK() {
			if (ConfigPanel.getInstance().hasChanged()) {
				int yesno = JOptionPane.showConfirmDialog(
								parent,
								"The configuration changes have not been saved. Do you wish to proceed?\n\n"
								+ "If you click OK, the program will end without saving the configuration.\n\n",
								"Configuration",
								JOptionPane.OK_CANCEL_OPTION);
				return (yesno == JOptionPane.OK_OPTION);
			}
			return true;
		}
		private void save() {
			Properties props = Configuration.getInstance().props;
			Point p = parent.getLocation();
			props.setProperty("x", Integer.toString(p.x));
			props.setProperty("y", Integer.toString(p.y));

			Toolkit t = getToolkit();
			Dimension d = parent.getSize ();
			props.setProperty("w", Integer.toString(d.width));
			props.setProperty("h", Integer.toString(d.height));

			javaPanel.save();
		}
    }

}
