/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.installer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;

/**
 * The ClinicalTrialProcessor program installer.
 * This class unpacks files from its own jar file into a
 * directory selected by the user.
 */
public class Installer extends JFrame {

	JPanel			mainPanel;
	JEditorPane		textPane;
	JFileChooser	chooser;
	File			installer;
	File			directory;
	boolean 		suppressFirstPathElement = false;
	ColorPane		cp;

	String windowTitle = "CTP Installer";
	String programName = "CTP";
	String programDate = "";
	String buildJava = "";
	String utilJava = "";
	String mircJava = "";
	String mircDate = "";
	String mircVersion = "";
	String imageIOVersion  = "";
	String thisJava = "";
	String thisJavaBits = "";
	boolean imageIOTools = false;

	public static void main(String args[]) {
		new Installer();
	}

	/**
	 * Class constructor; creates a new Installer object, displays a JFrame
	 * introducing the program, allows the user to select an install directory,
	 * and copies files from the jar into the directory.
	 */
	public Installer() {
		super();
		addWindowListener(new WindowAdapter() {
			public void windowClosing( WindowEvent evt ) { exit(); }
		});

		//Make a text pane to record the details
		cp = new ColorPane();

		//Find the installer program so we can get to the files.
		installer = getInstallerProgramFile();
		String name = installer.getName();
		programName = (name.substring(0, name.indexOf("-"))).toUpperCase();
		windowTitle = programName + " Installer";
		setTitle(windowTitle);

		//Get the installation information
		thisJava = System.getProperty("java.version");
		thisJavaBits = System.getProperty("sun.arch.data.model") + " bits";

		//Find the ImageIO Tools and get the version
		String javaHome = System.getProperty("java.home");
		File extDir = new File(javaHome);
		extDir = new File(extDir, "lib");
		extDir = new File(extDir, "ext");
		File clib = new File(extDir, "clibwrapper_jiio.jar");
		File jai = new File(extDir, "jai_imageio.jar");
		imageIOTools = clib.exists() && jai.exists();
		if (imageIOTools) {
			Hashtable<String,String> jaiManifest = getManifestAttributes(jai);
			imageIOVersion  = jaiManifest.get("Implementation-Version");
		}

		//Get the CTP.jar parameters
		Hashtable<String,String> manifest = getJarManifestAttributes("/CTP/libraries/CTP.jar");
		programDate = manifest.get("Date");
		buildJava = manifest.get("Java-Version");

		//Get the util.jar parameters
		Hashtable<String,String> utilManifest = getJarManifestAttributes("/CTP/libraries/util.jar");
		utilJava = utilManifest.get("Java-Version");

		//Get the MIRC.jar parameters (if the plugin is present)
		Hashtable<String,String> mircManifest = getJarManifestAttributes("/CTP/libraries/MIRC.jar");
		if (mircManifest != null) {
			mircJava = mircManifest.get("Java-Version");
			mircDate = mircManifest.get("Date");
			mircVersion = mircManifest.get("Version");
		}

		//Set up the installation information for display
		if (imageIOVersion .equals("")) {
			imageIOVersion  = "<b><font color=\"red\">not installed</font></b>";
		}
		else if (imageIOVersion .startsWith("1.0")) {
			imageIOVersion  = "<b><font color=\"red\">"+imageIOVersion +"</font></b>";
		}
		if (thisJavaBits.startsWith("64")) {
			thisJavaBits = "<b><font color=\"red\">"+thisJavaBits+"</font></b>";
		}
		boolean javaOK = (thisJava.compareTo(buildJava) >= 0);
		javaOK &= (thisJava.compareTo(utilJava) >= 0);
		javaOK &= (thisJava.compareTo(mircJava) >= 0);
		if (!javaOK) {
			thisJava = "<b><font color=\"red\">"+thisJava+"</font></b>";
		}

		//Set up the UI
		JSplitPane splitPane = new JSplitPane( JSplitPane.VERTICAL_SPLIT);
		splitPane.setContinuousLayout(true);

		textPane = new JEditorPane( "text/html", getWelcomePage() );
		textPane.setEditable(false);
		splitPane.setTopComponent(textPane);

		JScrollPane jsp = new JScrollPane();
		jsp.setViewportView(cp);
		splitPane.setBottomComponent(jsp);

		this.getContentPane().add( splitPane, BorderLayout.CENTER );
		pack();
		positionFrame();
		setVisible(true);
		splitPane.setDividerLocation(0.6f);

		//Get the directory for installing the program
		if ((directory=getDirectory()) == null) exit();

		//Point to the parent of the directory in which to install the program.
		//so the copy process works correctly for directory trees.
		//
		//If the user has selected a directory named "CTP",
		//then assume that this is the directory in which
		//to install the program.
		//
		//If the directory is not CTP, see if it is called "RSNA" and contains
		//the Launcher program, in which case we can assume that it is an
		//installation that was done with Bill Weadock's all-in-one installer for Windows.
		//
		//If neither of those cases is true, then this is already the parent of the
		//directory in which to install the program
		if (directory.getName().equals("CTP")) {
			directory = directory.getParentFile();
		}
		else if (directory.getName().equals("RSNA") && (new File(directory, "Launcher.jar")).exists()) {
			suppressFirstPathElement = true;
		}

		//Cleanup old releases
		cleanup(directory);

		//Get a port for the server.
		int port = getPort();
		if (port < 0) {
			if (checkServer(-port, false)) {
				JOptionPane.showMessageDialog(this,
					"CTP appears to be running.\nPlease stop CTP and run the installer again.",
					"Server is Running",
					JOptionPane.INFORMATION_MESSAGE);
				System.exit(0);
			}
		}

		//Now install the files
		int count = unpackZipFile( installer, "CTP", directory.getAbsolutePath(), suppressFirstPathElement );

		//And report the results.
		if (count > 0) {
			//Create the service installer batch files.
			updateWindowsServiceInstaller();

			//If this was a new installation, set up the config file and set the port
			installConfigFile(port);

			cp.append("Installation complete.");

			JOptionPane.showMessageDialog(this,
					programName+" has been installed successfully.\n"
					+ count + " files were installed.",
					"Installation Complete",
					JOptionPane.INFORMATION_MESSAGE);
		}
		else {
			cp.append("Installation failed.");

			JOptionPane.showMessageDialog(this,
					programName+" could not be fully installed.",
					"Installation Failed",
					JOptionPane.INFORMATION_MESSAGE);
		}
	}

	//Get the installer program file by looking in the user.dir for [programName]-installer.jar.
	private File getInstallerProgramFile() {
		cp.appendln(Color.black, "Looking for the installer program file");
		String name = getProgramName();
		File programFile = new File(name+"-installer.jar");
		programFile = new File( programFile.getAbsolutePath() );

		if (programFile.exists()) cp.appendln(Color.black, "...found "+programFile);
		else {
			cp.appendln(Color.red, "...unable to find the program file "+programFile+"\n...exiting.");
			JOptionPane.showMessageDialog(this,
					"Unable to find the installer program file.\n"+programFile,
					"Installation Failed",
					JOptionPane.INFORMATION_MESSAGE);
			exit();
		}
		return programFile;
	}

	//Take a tree of files starting in a directory in a zip file
	//and copy them to a disk directory, recreating the tree.
	private int unpackZipFile(File inZipFile, String directory, String parent, boolean suppressFirstPathElement) {
		int count = 0;
		if (!inZipFile.exists()) return count;
		parent = parent.trim();
		if (!parent.endsWith(File.separator)) parent += File.separator;
		if (!directory.endsWith(File.separator)) directory += File.separator;
		File outFile = null;
		try {
			ZipFile zipFile = new ZipFile(inZipFile);
			Enumeration zipEntries = zipFile.entries();
			while (zipEntries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry)zipEntries.nextElement();
				String name = entry.getName().replace('/',File.separatorChar);
				if (name.startsWith(directory)) {
					if (suppressFirstPathElement) name = name.substring(directory.length());
					outFile = new File(parent + name);
					//Create the directory, just in case
					if (name.indexOf(File.separatorChar) >= 0) {
						String p = name.substring(0,name.lastIndexOf(File.separatorChar)+1);
						File dirFile = new File(parent + p);
						dirFile.mkdirs();
					}
					if (!entry.isDirectory()) {
						cp.appendln(Color.black, "Installing "+outFile);
						//Copy the file
						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));
						BufferedInputStream in = new BufferedInputStream(zipFile.getInputStream(entry));
						int size = 1024;
						int n = 0;
						byte[] b = new byte[size];
						while ((n = in.read(b,0,size)) != -1) out.write(b,0,n);
						in.close();
						out.flush();
						out.close();
						//Count the file
						count++;
					}
				}
			}
			zipFile.close();
		}
		catch (Exception e) {
			cp.appendln(Color.red, "...an error occurred while installing "+outFile);
			e.printStackTrace();
			JOptionPane.showMessageDialog(this,
					"Error copying " + outFile.getName() + "\n" + e.getMessage(),
					"I/O Error", JOptionPane.INFORMATION_MESSAGE);
			return -count;
		}
		cp.appendln(Color.black, count + " files were installed.");
		return count;
	}

	private void cleanup(File directory) {
		//Clean up from old installations, removing or renaming files.
		//Note that directory is the parent of the CTP directory.
		//Also note that if Bill Weadock's all-in-one installer for
		//Windows was used to create the original installation,
		//the cleanup doesn't work, but it doesn't matter since the
		//cleanup is for installations that pre-date Bill's installer.
		File dir = new File(directory, "CTP");

		//This is a really old CTP main file - not used anymore
		File ctp = new File(dir, "CTP.jar");
		if (ctp.exists()) ctp.delete();

		//These are old names for the Launcher.jar file
		File launcher = new File(dir,"CTP-launcher.jar");
		if (launcher.exists()) launcher.delete();
		launcher = new File(dir,"TFS-launcher.jar");
		if (launcher.exists()) launcher.delete();

		//Delete the obsolete CTP-runner.jar file
		File runner = new File(dir,"CTP-runner.jar");
		if (runner.exists()) runner.delete();

		//Delete the obsolete MIRC-copier.jar file
		File copier = new File(dir,"MIRC-copier.jar");
		if (copier.exists()) copier.delete();

		//Rename the old versions of the properties files
		File oldprops = new File(dir, "CTP-startup.properties");
		File newprops = new File(dir, "CTP-launcher.properties");
		File correctprops = new File(dir, "Launcher.properties");
		if (oldprops.exists()) {
			if (newprops.exists() || correctprops.exists()) oldprops.delete();
			else oldprops.renameTo(correctprops);
		}
		if (newprops.exists()) {
			if (correctprops.exists()) newprops.delete();
			else newprops.renameTo(correctprops);
		}

		//Get rid of obsolete startup and shutdown programs
		File startup = new File(dir, "CTP-startup.jar");
		if (startup.exists()) startup.delete();
		File shutdown = new File(dir, "CTP-shutdown.jar");
		if (shutdown.exists()) shutdown.delete();

		//Get rid of the obsolete linux directory
		File linux = new File(dir, "linux");
		if (linux.exists()) {
			startup = new File(linux, "CTP-startup.jar");
			if (startup.exists()) startup.delete();
			shutdown = new File(linux, "CTP-shutdown.jar");
			if (shutdown.exists()) shutdown.delete();
			linux.delete();
		}

		//remove obsolete versions of the slf4j libraries
		File libraries = new File(dir, "libraries");
		if (libraries.exists()) {
			File[] files = libraries.listFiles();
			for (File file : files) {
				if (file.getName().startsWith("slf4j-")) file.delete();
			}
		}
		//remove the obsolete xml library
		File xml = new File(dir, "xml");
		if (xml.exists()) {
			File[] xmlFiles = xml.listFiles();
			for (File file : xmlFiles) file.delete();
			xml.delete();
		}
	}

	//Let the user select an installation directory.
	private File getDirectory() {
		cp.appendln(Color.black, "Finding a directory in which to install the program");
		//Pick the first of these directories which exists:
		//1. "JavaPrograms" in the root of the current drive.
		//2. programName (e.g., "CTP") in the root of the current drive.
		//3. user.dir in the System properties.
		File currentDirectory = new File( System.getProperty( "user.dir" ) );
		File root = new File(File.separator);
		File programDir = new File(root, "CTP");
		File rsnaDir = new File(root, "RSNA");
		File tfsDir = new File(root, "TFS");
		File javaPrograms = new File(root, "JavaPrograms");
		if (javaPrograms.exists() && javaPrograms.isDirectory()) {
			currentDirectory = javaPrograms;
		}
		else if (rsnaDir.exists() && rsnaDir.isDirectory()) {
			currentDirectory = rsnaDir;
		}
		else if (tfsDir.exists() && tfsDir.isDirectory()) {
			currentDirectory = tfsDir;
		}
		else if (programDir.exists() && programDir.isDirectory()) {
			currentDirectory = programDir;
		}
		cp.appendln(Color.black, "...setting the starting directory to "+currentDirectory);
		//Now make a new chooser and set the current directory.
		chooser = new JFileChooser();
		chooser.setCurrentDirectory(currentDirectory);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Select a directory in which to install the program");
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File dir = chooser.getSelectedFile();
			cp.appendln(Color.black, "...selected directory: "+dir);
			return dir;
		}
		cp.appendln(Color.red, "...NO DIRECTORY WAS SELECTED; exit.");
		exit();
		return null;
	}

	//If this is a new installation, ask the user for a
	//port for the server; otherwise, return -1.
	private int getPort() {
		//Note: directory points to the parent of the CTP directory.
		File ctp = new File(directory, "CTP");
		File config = new File(ctp, "config.xml");
		if (!config.exists()) {
			//No config file - must be a new installation.
			//Figure out whether this is Windows or something else.
			String os = System.getProperty("os.name").toLowerCase();
			int defPort = (os.contains("windows") ? 80 : 1080);
			int userPort = 0;
			while (userPort == 0) {
				String port = JOptionPane.showInputDialog(null,
						"This is a new "+programName+" installation.\n\n" +
						"Select a port number for the web server.\n\n",
						Integer.toString(defPort));
				try { userPort = Integer.parseInt(port.trim()); }
				catch (Exception ex) { userPort = -1; }
				if ((userPort < 80) || (userPort > 32767)) userPort = 0;
			}
			return userPort;
		}
		else {
			try {
				String text = getFileText(config);
				String target = "<Server port=\"";
				int k = text.indexOf(target);
				if (k != -1) {
					k += target.length();
					int kk = text.indexOf("\"", k);
					String portString = text.substring(k, kk);
					return -Integer.parseInt(portString);
				}
			}
			catch (Exception ex) { }
		}
		return 0;
	}

	private void installConfigFile(int port) {
		String target = "<Server port=\"";
		if (port > 0) {
			cp.appendln(Color.black, "Looking for /config/config.xml");
			InputStream is = getClass().getResourceAsStream("/config/config.xml");
			if (is != null) {
				StringBuffer sb = new StringBuffer();
				try {
					BufferedReader br = new BufferedReader( new InputStreamReader(is, "UTF-8") );
					int size = 1024; int n = 0; char[] buf = new char[size];
					while ((n = br.read(buf, 0, size)) != -1) sb.append(buf, 0, n);
					br.close();
					String text = sb.toString();
					cp.appendln("...setting the port to "+port);
					int k = text.indexOf(target);
					if (k == -1) {
						cp.appendln(Color.red, "...unable to find the Server element in the default config.xml file");
						return;
					}
					k += target.length();
					int kk = text.indexOf("\"", k);
					text = text.substring(0, k) + port + text.substring(kk);
					File ctp = new File(directory, "CTP");
					if (suppressFirstPathElement) ctp = ctp.getParentFile();
					File config = new File(ctp, "config.xml");
					setFileText(config, text);
				}
				catch (Exception ex) {
					cp.appendln(Color.red, "...I/O error; unable to install the config.xml file");
				}
			}
			else cp.appendln(Color.red, "...could not find it.");
		}
	}

	private void updateWindowsServiceInstaller() {
		try {
			File dir = new File(directory, "CTP");
			if (suppressFirstPathElement) dir = dir.getParentFile();
			File windows = new File(dir, "windows");
			File install = new File(windows, "install.bat");
			cp.appendln(Color.black, "install.bat: "+install.getAbsolutePath());
			String bat = getFileText(install);
			Properties props = new Properties();
			String home = dir.getAbsolutePath();
			cp.appendln(Color.black, "home: "+home);
			home = home.replaceAll("\\\\", "\\\\\\\\");
			props.put("home", home);

			bat = replace(bat, props);
			setFileText(install, bat);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this,
					"Unable to update the windows service install.bat file.",
					"Windows Service Installer",
					JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private String replace(String string, Properties table) {
		try {
			Pattern pattern = Pattern.compile("\\$\\{\\w+\\}");
			Matcher matcher = pattern.matcher(string);
			StringBuffer sb = new StringBuffer();
			while (matcher.find()) {
				String group = matcher.group();
				String key = group.substring(2, group.length()-1).trim();
				String repl = table.getProperty(key);
				if (repl == null) repl = matcher.quoteReplacement(group);
				matcher.appendReplacement(sb, repl);
			}
			matcher.appendTail(sb);
			return sb.toString();
		}
		catch (Exception ex) { return string; }
	}

	private String getFileText(File file) throws Exception {
		BufferedReader br = new BufferedReader(
				new InputStreamReader(
					new FileInputStream(file), "UTF-8"));
		StringWriter sw = new StringWriter();
		int n;
		char[] cbuf = new char[1024];
		while ((n=br.read(cbuf, 0, cbuf.length)) != -1) sw.write(cbuf,0,n);
		br.close();
		return sw.toString();
	}

	private void setFileText(File file, String text) throws Exception {
		BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(
					new FileOutputStream(file), "UTF-8"));
		bw.write(text, 0, text.length());
		bw.flush();
		bw.close();
	}

	private String getWelcomePage() {
		return
				"<html>"
			+	"<head></head>"
			+	"<body style=\"background:#c6d8f9;font-family:sans-serif;\">"
			+	"<center>"
			+	"<h1 style=\"color:blue\">" + windowTitle + "</h1>"
			+	"Version: " + programDate + "<br>"
			+	"Copyright 2011: RSNA<br><br>"

			+	"<p>"

			+ (programName.equals("TFS") ?
				"<b>"+programName+"</b> is radiological teaching file system.<br>"
				:
				"<b>"+programName+"</b> is a tool for acquiring and managing data for medical imaging.<br>"
			  )
			+	"This program installs and configures all the required software components."
			+	"</p>"

			+	"<p>"
			+	"<table>"
			+	"<tr><td>This computer's Java Version:</td><td>"+thisJava+"</td></tr>"
			+	"<tr><td>This computer's Java Data Model:</td><td>"+thisJavaBits+"</td></tr>"
			+	"<tr><td>CTP Java Version:</td><td>"+buildJava+"</td></tr>"
			+	"<tr><td>Utility library Java Version:</td><td>"+utilJava+"</td></tr>"

			+(!mircJava.equals("") ? "<tr><td>MIRC plugin Java Version:</td><td>"+mircJava+"</td></tr>" : "")
			+(!mircDate.equals("") ? "<tr><td>MIRC plugin Date:</td><td>"+mircDate+"</td></tr>" : "")
			+(!mircJava.equals("") ? "<tr><td>MIRC plugin Version:</td><td>"+mircVersion+"</td></tr>" : "")

			+	"<tr><td>ImageIO Tools Version:</td><td>"+imageIOVersion+"</td></tr>"

			+	"</table>"
			+	"</p>"
			+	"</center>"
			+	"</body>"
			+	"</html>";
	}

	private static void exit() {
		System.exit(0);
	}

	private void positionFrame() {
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize( 650, 800 ); //scr.width*2/5, scr.height/3 );
		int x = (scr.width - getSize().width)/2;
		int y = (scr.height - getSize().height)/2;
		setLocation( new Point(x,y) );
	}

	private String getProgramName() {
		try {
			InputStream mirc = getClass().getResourceAsStream("/CTP/libraries/MIRC.jar");
			if (mirc != null) return "TFS";
			InputStream isn = getClass().getResourceAsStream("/CTP/libraries/isn/ISN.jar");
			if (isn != null) return "ISN";
		}
		catch (Exception ex) { }
		return "CTP";
	}

	private Hashtable<String,String>  getJarManifestAttributes(String path) {
		Hashtable<String,String> h = new Hashtable<String,String>();
		JarInputStream jis = null;
		try {
			cp.appendln(Color.black, "Looking for "+path);
			InputStream is = getClass().getResourceAsStream(path);
			if (is == null) {
				if (!path.endsWith("/MIRC.jar")) {
					cp.appendln(Color.red, "...could not find it.");
				}
				else {
					cp.appendln(Color.black, "...could not find it. [OK, this is a CTP installation.]");
				}
				return null;
			}
			jis = new JarInputStream(is);
			Manifest manifest = jis.getManifest();
			h = getManifestAttributes(manifest);
		}
		catch (Exception ex) { ex.printStackTrace(); }
		if (jis != null) {
			try { jis.close(); }
			catch (Exception ignore) { }
		}
		return h;
	}

	private static Hashtable<String,String> getManifestAttributes(File jarFile) {
		Hashtable<String,String> h = new Hashtable<String,String>();
		JarFile jar = null;
		try {
			jar = new JarFile(jarFile);
			Manifest manifest = jar.getManifest();
			h = getManifestAttributes(manifest);
		}
		catch (Exception ex) { }
		if (jar != null) {
			try { jar.close(); }
			catch (Exception ignore) { }
		}
		return h;
	}

	private static Hashtable<String,String> getManifestAttributes(Manifest manifest) {
		Hashtable<String,String> h = new Hashtable<String,String>();
		try {
			Attributes attrs = manifest.getMainAttributes();
			Iterator it = attrs.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next().toString();
				h.put(key, attrs.getValue(key));
			}
		}
		catch (Exception ignore) { }
		return h;
	}

	class ColorPane extends JTextPane {

		public int lineHeight;

		public ColorPane() {
			super();
			Font font = new Font("Monospaced",Font.PLAIN,12);
			FontMetrics fm = getFontMetrics(font);
			lineHeight = fm.getHeight();
			setFont(font);
			setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		}

		public boolean getScrollableTracksViewportWidth() {
			return true;
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
	}

	private boolean checkServer(int port, boolean ssl) {
		try {
			URL url = new URL("http://127.0.0.1:"+port);
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.setRequestMethod("GET");
			conn.connect();
			int length = conn.getContentLength();
			StringBuffer text = new StringBuffer();
			InputStream is = conn.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			int size = 256; char[] buf = new char[size]; int len;
			while ((len=isr.read(buf,0,size)) != -1) text.append(buf,0,len);
			return true;
		}
		catch (Exception ex) { return false; }
	}

	private void logSubstring(String info, String text, String search) {
		System.out.println(info);
		System.out.println("...String: \""+text+"\"");
		if (search != null) {
			System.out.println("...Search: \""+search+"\"");
		}
	}

}
