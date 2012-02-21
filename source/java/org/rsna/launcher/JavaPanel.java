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
import java.util.Properties;
import javax.swing.*;
import javax.swing.border.*;
import org.w3c.dom.Element;

public class JavaPanel extends BasePanel implements ActionListener {

	Configuration config;

	Row maxMemory;
	Row initMemory;
	Row stackSize;
	Row extDirs;
	Row serverPort;
	CBRow clearLogs;
	CBRow debugSSL;
	CBRow javaMonitor;

	JButton start;
	JButton stop;
	JButton launchBrowser;

	JLabel status = new JLabel("Stopped");
	boolean running;
	Thread runner = null;
	Monitor monitor = null;

	public JavaPanel() {
		super();
		setLayout(new BorderLayout());
		config = Configuration.getInstance();
		Properties props = config.props;
		running = false; Util.isRunning();

		JPanel main = new JPanel();
		main.setLayout(new BorderLayout());
		main.setBackground(bgColor);

		//North Panel
		JPanel np = new JPanel();
		np.setBackground(bgColor);
		JLabel title = new JLabel(config.programName);
		title.setFont( new Font( "SansSerif", Font.BOLD, 24 ) );
		title.setForeground( Color.BLUE );
		np.add(title);
		np.setBorder(BorderFactory.createEmptyBorder(10,0,20,0));
		main.add(np, BorderLayout.NORTH);

		//Center Panel
		RowPanel javaPanel = new RowPanel("Java Parameters");
		javaPanel.setBackground(bgColor);
		javaPanel.addRow( initMemory = new Row("Initial memory pool:", props.getProperty("ms","")) );
		javaPanel.addRow( maxMemory = new Row("Maximum memory pool:", props.getProperty("mx","")) );
		javaPanel.addRow( stackSize = new Row("Thread stack size:", props.getProperty("ss","")) );
		javaPanel.addRow( extDirs = new Row("Extensions directory:", props.getProperty("ext","")) );
		javaPanel.addRow( debugSSL = new CBRow("Enable SSL debugging:", props.getProperty("ssl","").equals("yes")) );
		javaPanel.addRow( javaMonitor = new CBRow("Enable Java monitoring:", props.getProperty("mon","").equals("yes")) );

		RowPanel serverPanel = new RowPanel("Server Parameters");
		serverPanel.setBackground(bgColor);
		serverPanel.addRow( serverPort = new Row("Server port:", Integer.toString(config.port)) );
		serverPanel.addRow( clearLogs = new CBRow("Clear logs on start:", props.getProperty("clr","").equals("yes")) );

		JPanel cp = new JPanel();
		cp.setLayout(new RowLayout());
		cp.setBackground(bgColor);

		cp.add(serverPanel);
		cp.add(RowLayout.crlf());
		cp.add(Box.createVerticalStrut(20));
		cp.add(RowLayout.crlf());
		cp.add(javaPanel);
		cp.add(RowLayout.crlf());

		JPanel ccp = new JPanel(new FlowLayout());
		ccp.setBackground(bgColor);
		ccp.add(cp);
		main.add(ccp, BorderLayout.CENTER);

		//South Panel
		JPanel sp = new JPanel();
		sp.setBorder(BorderFactory.createEmptyBorder(10,0,20,0));
		sp.setBackground(bgColor);

		start = new JButton("Start");
		sp.add(start);
		start.addActionListener(this);
		sp.add(Box.createHorizontalStrut(15));

		stop = new JButton("Stop");
		sp.add(stop);
		stop.addActionListener(this);
		sp.add(Box.createHorizontalStrut(70));

		launchBrowser = new JButton(config.browserButtonName+ " Home Page");
		sp.add(launchBrowser);
		launchBrowser.addActionListener(this);

		main.add(sp, BorderLayout.SOUTH);

		this.add(main, BorderLayout.CENTER);
		this.add(new StatusPanel( status ), BorderLayout.SOUTH);

		running = Util.isRunning();
		setStatus();
	}

	public void start() {
		running = Util.isRunning();
		if (!running) {
			clearLogsDir();
			run();
		}
	}

	public void setFocusOnStart() {
		start.requestFocusInWindow();
	}

	public void actionPerformed(ActionEvent event) {
		if (event.getSource().equals(launchBrowser)) {
			Configuration config = Configuration.getInstance();
			String ip = Util.getIPAddress();
			String protocol = "http" + (config.ssl ? "s" : "");
			String url = protocol + "://" + ip + ":" + config.port;
			Util.openURL( url );
		}
		else if (event.getSource().equals(stop)) {
			Util.shutdown();
			running = Util.isRunning();
			setStatus();
		}
		else if (event.getSource().equals(start)) {
			clearLogsDir();
			run();
			launchBrowser.requestFocusInWindow();
		}
	}

	private void clearLogsDir() {
		if (clearLogs.cb.isSelected()) {
			File logs = new File("logs");
			Util.deleteAll(logs);
			IOPanel.out.clear();
		}
	}

	private void run() {
		int port = Util.getInt( serverPort.tf.getText().trim(), config.port );
		if (port != config.port) {
			try {
				Element server = Util.getFirstNamedChild( config.configXML, "Server" );
				server.setAttribute("port", Integer.toString(port));
				config.saveXML();
				config.port = port;
			}
			catch (Exception unable) { }
			serverPort.tf.setText( Integer.toString( config.port ) );
		}
		save();
		if (!running) {
			runner = Util.startup( );
			Util.wait(500);
			running = runner.isAlive();
			if (running) {
				monitor = new Monitor(runner);
				monitor.start();
			}
		}
		setStatus();
	}

	class Monitor extends Thread {
		Thread runner = null;
		public Monitor(Thread runner) {
			super("CTP Launcher Monitor");
			this.runner = runner;
		}
		public void run() {
			while (runner.isAlive()) {
				try { Thread.sleep(1000); }
				catch (Exception ex) { }
			}
			Runnable update = new Runnable() {
				public void run() {
					running = false;
					setStatus();
				}
			};
			SwingUtilities.invokeLater(update);
		}
	}

	class StatusPanel extends JPanel {
		public StatusPanel(JLabel label) {
			super();
			this.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			this.setLayout(new FlowLayout(FlowLayout.LEFT));
			this.setBackground(bgColor);
			this.add( new JLabel("Status: ") );
			this.add(label);
			label.setFont( new Font( "Monospaced", Font.BOLD, 14 ) );
		}
	}

	public void save() {
		Configuration config = Configuration.getInstance();
		Properties props = config.props;
		props.setProperty("mx", maxMemory.tf.getText().trim());
		props.setProperty("ms", initMemory.tf.getText().trim());
		props.setProperty("ss", stackSize.tf.getText().trim());
		props.setProperty("ext", extDirs.tf.getText().trim());
		props.setProperty("clr", (clearLogs.cb.isSelected()?"yes":"no"));
		props.setProperty("ssl", (debugSSL.cb.isSelected()?"yes":"no"));
		props.setProperty("mon", (javaMonitor.cb.isSelected()?"yes":"no"));
		config.save();
	}

	private void setStatus() {
		//Update the status display
		status.setText( running ? "Running" : "Stopped" );
		status.setForeground( running ? Color.BLACK : Color.RED );
		start.setEnabled( !running );
		stop.setEnabled( running );
		launchBrowser.setEnabled( running );
	}

	class RowPanel extends JPanel {
		public RowPanel() {
			super();
			setBorder(BorderFactory.createEmptyBorder(5,10,5,10));
			setLayout(new RowLayout());
		}
		public RowPanel(String name) {
			super();
			Border empty = BorderFactory.createEmptyBorder(10,10,10,10);
			Border line = BorderFactory.createLineBorder(Color.GRAY);
			Border title = BorderFactory.createTitledBorder(line, name);
			Border compound = BorderFactory.createCompoundBorder(title, empty);
			setBorder(compound);
			setLayout(new RowLayout());
		}
		public void addRow(Row row) {
			add(row.label);
			add(row.tf);
			add(RowLayout.crlf());
		}
		public void addRow(CBRow row) {
			add(row.label);
			add(row.cb);
			add(RowLayout.crlf());
		}
		public void addRow(int height) {
			add(Box.createVerticalStrut(height));
			add(RowLayout.crlf());
		}
		public void addRow(JLabel label) {
			add(label);
			add(RowLayout.crlf());
		}
	}

	class Row {
		public RowLabel label;
		public JTextField tf;
		public Row(String name) {
			label = new RowLabel(name);
			tf = new JTextField(15);
		}
		public Row(String name, String value) {
			label = new RowLabel(name);
			tf = new JTextField(20);
			tf.setText(value);
		}
	}

	class CBRow {
		public RowLabel label;
		public JCheckBox cb;
		public CBRow(String name, boolean checked) {
			label = new RowLabel(name);
			cb = new JCheckBox();
			cb.setBackground(bgColor);
			cb.setSelected(checked);
		}
	}

	class RowLabel extends JLabel {
		public RowLabel(String s) {
			super(s);
			Dimension d = this.getPreferredSize();
			d.width = 140;
			this.setPreferredSize(d);
		}
	}

}
