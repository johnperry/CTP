/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.installer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * The ClinicalTrialProcessor program installer.
 * This class unpacks files from its own jar file into a
 * directory selected by the user.
 */
public class Installer extends JFrame implements KeyListener {

	JPanel			mainPanel;
	JEditorPane		textPane;
	JFileChooser	chooser;
	File			installer;
	File			directory;
	boolean 		suppressFirstPathElement = false;
	ColorPane		cp;
	Color			bgColor = new Color(0xb9d0ed);

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
	
	static boolean test = false;

	public static void main(String args[]) {
		test = (args.length > 0) && args[0].trim().equals("test");
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
			public void windowClosing( WindowEvent evt ) { 
				if (!test && !programName.equals("ISN")) {
					startLauncher(new File(directory, "CTP"));
				};
				exit(); 
			}
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
		File javaDir = new File(javaHome);
		File extDir = new File(javaDir, "lib");
		extDir = new File(extDir, "ext");
		File clib = getFile(extDir, "clibwrapper_jiio", ".jar");
		File jai = getFile(extDir, "jai_imageio", ".jar");
		imageIOTools = (clib != null) && (jai != null);
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
		textPane.setBackground(bgColor);
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

		//Now install the files and report the results.
		int count = unpackZipFile( installer, "CTP", directory.getAbsolutePath(), suppressFirstPathElement );
		if (count > 0) {
			//Create the service installer batch files.
			updateWindowsServiceInstaller();
			updateLinuxServiceInstaller();

			//If this was a new installation, set up the config file and set the port
			installConfigFile(port);

			//Make any necessary changes in the config file to reflect schema evolution
			fixConfigSchema();
			
			//Set up the ImageIO Tools if they weren't already installed
			installImageIOTools(directory);
			
			cp.append("Installation complete.");

			JOptionPane.showMessageDialog(this,
					programName+" has been installed successfully.\n"
					+ count + " files were installed.",
					"Installation Complete",
					JOptionPane.INFORMATION_MESSAGE);
			addKeyListener(this);
			cp.setEditable(false);
			this.requestFocus();
		}
		else {
			cp.append("Installation failed.");

			JOptionPane.showMessageDialog(this,
					programName+" could not be fully installed.",
					"Installation Failed",
					JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	//KeyListener to listen for the exit
	public void keyPressed(KeyEvent e) { }
	public void keyReleased(KeyEvent e) {  }
	public void keyTyped(KeyEvent e) {
		this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
	}

	//Get the installer program file by looking in the user.dir for [programName]-installer.jar.
	private File getInstallerProgramFile() {
		cp.appendln(Color.black, "Looking for the installer program file");
		File programFile;
		try { programFile = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()); }
		catch (Exception ex) {
			String name = getProgramName();
			programFile = new File(name+"-installer.jar");
		}
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
	
	private File getFile(File dir, String nameStart, String nameEnd) {
		if ((dir == null) || !dir.exists()) return null;
		File[] files = dir.listFiles( new NameFilter(nameStart, nameEnd) );
		if (files.length == 0) return null;
		return files[0];
	}
	
	class NameFilter implements FileFilter {
		String nameStart;
		String nameEnd;
		public NameFilter(String nameStart, String nameEnd) {
			this.nameStart = nameStart;
			this.nameEnd = nameEnd;
		}
		public boolean accept(File file) {
			String name = file.getName();
			return name.startsWith(nameStart) && name.endsWith(nameEnd);
		}
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
	
	private void installImageIOTools(File directory) {
		String javaHome = System.getProperty("java.home");
		File javaDir = new File(javaHome);
		File extDir = new File(javaHome, "lib");
		extDir = new File(extDir, "ext");

		File clib = getFile(extDir, "clibwrapper_jiio", ".jar");
		File jai = getFile(extDir, "jai_imageio", ".jar");
		boolean imageIOTools = (clib != null) && (jai != null);
		
		File ctpDir = new File(directory, "CTP");
		File libDir = new File(ctpDir, "libraries");
		File toolsDir = new File(libDir, "imageio");
		
		if (imageIOTools) {
			deleteAll(toolsDir);
			cp.appendln("ImageIO Tools are already installed, deleting "+toolsDir);
		}
		else cp.appendln("ImageIO Tools are not installed, tools files copied to "+toolsDir);
	}
	
	private boolean moveFile(File inFile, File outDir) {
		try {
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(new File(outDir, inFile.getName())));
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(inFile));
			int size = 1024;
			int n = 0;
			byte[] b = new byte[size];
			while ((n = in.read(b,0,size)) != -1) out.write(b,0,n);
			in.close();
			out.flush();
			out.close();
			inFile.delete();
			return true;
		}
		catch (Exception ex) {
			cp.appendln("Unable to move "+inFile.getName());
			return false;
		}
	}
	
	private void cleanup(File directory) {
		//Clean up from old installations, removing or renaming files.
		//Note that directory is the parent of the CTP directory
		//unless the original installation was done by Bill Weadock's
		//all-in-one installer for Windows.

		//Get a file pointing to the CTP directory.
		//This might be the current directory, or
		//it might be the CTP child.
		File dir;
		if (directory.getName().equals("RSNA")) dir = directory;
		else dir = new File(directory, "CTP");

		//If CTP.jar exists in this directory, it is a really
		//old CTP main file - not used anymore
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

		//clean up the libraries directory
		File libraries = new File(dir, "libraries");
		if (libraries.exists()) {
			//remove obsolete versions of the slf4j libraries
			//and the dcm4che-imageio libraries
			File[] files = libraries.listFiles();
			for (File file : files) {
				if (file.isFile()) {
					String name = file.getName();
					if (name.startsWith("slf4j-") || name.startsWith("dcm4che-imageio-rle")) {
						file.delete();
					}
				}
			}
			//remove the imageio subdirectory
			File imageio = new File(libraries, "imageio");
			deleteAll(imageio);
			//remove the email subdirectory
			File email = new File(libraries, "email");
			deleteAll(email);
			//remove the xml subdirectory
			File xml = new File(libraries, "xml");
			deleteAll(xml);
			//remove the sftp subdirectory
			File sftp = new File(libraries, "sftp");
			deleteAll(xml);
			//move edtftpj.jar to the ftp directory
			File edtftpj = new File(libraries, "edtftpj.jar");
			if (edtftpj.exists()) {
				File ftp = new File(libraries, "ftp");
				ftp.mkdirs();
				File ftpedtftpj = new File(ftp, "edtftpj.jar");
				edtftpj.renameTo(ftpedtftpj );
			}
		}

		//remove the obsolete xml library under dir
		File xml = new File(dir, "xml");
		deleteAll(xml);

		//remove the dicom profiles so any
		//obsolete files will disappear
		File profiles = new File(dir, "profiles");
		File dicom = new File(profiles, "dicom");
		deleteAll(dicom);
		dicom.mkdirs();

		//Remove the index.html file so it will be rebuilt from
		//example-index.html when the system next starts.
		File root = new File(dir, "ROOT");
		if (root.exists()) {
			File index = new File(root, "index.html");
			index.delete();
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
	//port for the server; otherwise, return the negative
	//of the configured port. If the user selects an illegal
	//port, return zero.
	private int getPort() {
		//Note: directory points to the parent of the CTP directory.
		File ctp = new File(directory, "CTP");
		if (suppressFirstPathElement) ctp = ctp.getParentFile();
		File config = new File(ctp, "config.xml");
		if (!config.exists()) {
			//No config file - must be a new installation.
			//Figure out whether this is Windows or something else.
			String os = System.getProperty("os.name").toLowerCase();
			int defPort = ((os.contains("windows") && !programName.equals("ISN")) ? 80 : 1080);
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
				Document doc = getDocument(config);
				Element root = doc.getDocumentElement();
				Element server = getFirstNamedChild(root, "Server");
				String port = server.getAttribute("port");
				return -Integer.parseInt(port);
			}
			catch (Exception ex) { }
		}
		return 0;
	}

	private void installConfigFile(int port) {
		if (port > 0) {
			cp.appendln(Color.black, "Looking for /config/config.xml");
			InputStream is = getClass().getResourceAsStream("/config/config.xml");
			if (is != null) {
				try {
					File ctp = new File(directory, "CTP");
					if (suppressFirstPathElement) ctp = ctp.getParentFile();
					File config = new File(ctp, "config.xml");
					Document doc = getDocument(is);
					Element root = doc.getDocumentElement();
					Element server = getFirstNamedChild(root, "Server");
					cp.appendln("...setting the port to "+port);
					server.setAttribute("port", Integer.toString(port));
					adjustConfiguration(root, ctp);
					setFileText(config, toString(doc));
				}
				catch (Exception ex) {
					cp.appendln(Color.red, "...Error: unable to install the config.xml file");
				}
			}
			else cp.appendln(Color.red, "...could not find it.");
		}
	}

	private void adjustConfiguration(Element root, File dir) {
		//If this is an ISN installation and the Edge Server
		//keystore and truststore files do not exist, then set the configuration
		//to use the keystore.jks and truststore.jks files instead of the ones
		//in the default installation. If the Edge Server files do exist, then
		//delete the keystore.jks and truststore.jks files, just to avoid
		//confusion.
		if (programName.equals("ISN")) {
			Element server = getFirstNamedChild(root, "Server");
			Element ssl = getFirstNamedChild(server, "SSL");
			String rsnaroot = System.getenv("RSNA_ROOT");
			rsnaroot = (rsnaroot == null) ? "/usr/local/edgeserver" : rsnaroot.trim();
			String keystore = rsnaroot + "/conf/keystore.jks";
			String truststore = rsnaroot + "/conf/truststore.jks";
			File keystoreFile = new File(keystore);
			File truststoreFile = new File(truststore);
			cp.appendln(Color.black, "Looking for "+keystore);
			if (keystoreFile.exists() || truststoreFile.exists()) {
				cp.appendln(Color.black, "...found it [This is an EdgeServer installation]");
				//Delete the default files, just to avoid confusion
				File ks = new File(dir, "keystore.jks");
				File ts = new File(dir, "truststore.jks");
				boolean ksok = ks.delete();
				boolean tsok = ts.delete();
				if (ksok && tsok) cp.appendln(Color.black, "...Unused default SSL files were removed");
				else {
					if (!ksok) cp.appendln(Color.black, "...Unable to delete "+ks);
					if (!tsok) cp.appendln(Color.black, "...Unable to delete "+ts);
				}
			}
			else {
				cp.appendln(Color.black, "...not found [OK, this is a non-EdgeServer installation]");
				ssl.setAttribute("keystore", "keystore.jks");
				ssl.setAttribute("keystorePassword", "edge1234");
				ssl.setAttribute("truststore", "truststore.jks");
				ssl.setAttribute("truststorePassword", "edge1234");
				cp.appendln(Color.black, "...SSL attributes were updated for a non-EdgeServer installation");
			}
		}
	}

	private void updateWindowsServiceInstaller() {
		try {
			File dir = new File(directory, "CTP");
			if (suppressFirstPathElement) dir = dir.getParentFile();
			File windows = new File(dir, "windows");
			File install = new File(windows, "install.bat");
			cp.appendln(Color.black, "Windows service installer:");
			cp.appendln(Color.black, "...file: "+install.getAbsolutePath());
			String bat = getFileText(install);
			Properties props = new Properties();
			String home = dir.getAbsolutePath();
			cp.appendln(Color.black, "...home: "+home);
			home = home.replaceAll("\\\\", "\\\\\\\\");
			props.put("home", home);
			bat = replace(bat, props);
			setFileText(install, bat);
			
			//Choose the correct CTP-xx.exe for this Java
			String thisJavaBits = System.getProperty("sun.arch.data.model");
			File ctp = new File(windows, "CTP.exe");
			File procrun = new File(windows, "CTP-"+thisJavaBits+".exe");
			if (copy(procrun, ctp)) cp.appendln("...Service runner copied for "+thisJavaBits+" bits.");
			else cp.appendln("...Service runner could not be copied for "+thisJavaBits+" bits.");
		}
		catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this,
					"Unable to update the windows service install.bat file.",
					"Windows Service Installer",
					JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void updateLinuxServiceInstaller() {
		try {
			File dir = new File(directory, "CTP");
			if (suppressFirstPathElement) dir = dir.getParentFile();
			Properties props = new Properties();
			String ctpHome = dir.getAbsolutePath();
			cp.appendln(Color.black, "...CTP_HOME: "+ctpHome);
			ctpHome = ctpHome.replaceAll("\\\\", "\\\\\\\\");
			props.put("CTP_HOME", ctpHome);
			File javaHome = new File(System.getProperty("java.home"));
			String javaBin = (new File(javaHome, "bin")).getAbsolutePath();
			cp.appendln(Color.black, "...JAVA_BIN: "+javaBin);
			javaBin = javaBin.replaceAll("\\\\", "\\\\\\\\");
			props.put("JAVA_BIN", javaBin);
			
			File linux = new File(dir, "linux");
			File install = new File(linux, "ctpService-ubuntu.sh");
			cp.appendln(Color.black, "Linux service installer:");
			cp.appendln(Color.black, "...file: "+install.getAbsolutePath());
			String bat = getFileText(install);
			bat = replace(bat, props); //do the substitutions
			bat = bat.replace("\r", "");
			setFileText(install, bat);

			//If this is an ISN installation, put the script in the correct place.
			String osName = System.getProperty("os.name").toLowerCase();
			if (programName.equals("ISN") && !osName.contains("windows")) {
				install = new File(linux, "ctpService-red.sh");
				cp.appendln(Color.black, "ISN service installer:");
				cp.appendln(Color.black, "...file: "+install.getAbsolutePath());
				bat = getFileText(install);
				bat = replace(bat, props); //do the substitutions
				bat = bat.replace("\r", "");
				File initDir = new File("/etc/init.d");
				File initFile = new File(initDir, "ctpService");
				if (initDir.exists()) {
					setOwnership(initDir, "edge", "edge");
					setFileText(initFile, bat);
					initFile.setReadable(true, false); //everybody can read //Java 1.6
					initFile.setWritable(true); //only the owner can write //Java 1.6
					initFile.setExecutable(true, false); //everybody can execute //Java 1.6
					
				}
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this,
					"Unable to update the Linux service ctpService.sh file.",
					"Linux Service Installer",
					JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	private void setOwnership(File dir, String group, String owner) {
		try {
			Path path = dir.toPath();
			UserPrincipalLookupService lookupService = FileSystems.getDefault()
						.getUserPrincipalLookupService();
			GroupPrincipal groupPrincipal = lookupService.lookupPrincipalByGroupName(group);
			UserPrincipal userPrincipal = lookupService.lookupPrincipalByName(owner);
			PosixFileAttributeView pfav = Files.getFileAttributeView(
													path, 
													PosixFileAttributeView.class,
													LinkOption.NOFOLLOW_LINKS);
			pfav.setGroup(groupPrincipal);
			pfav.setOwner(userPrincipal);
		}
		catch (Exception ex) {
			cp.appendln("Unable to set the file group and owner for\n   "+dir);
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

	//Copy a file.
	public boolean copy(File inFile, File outFile) {
		try { 
			BufferedInputStream in = new BufferedInputStream( new FileInputStream(inFile));
			BufferedOutputStream out = new BufferedOutputStream( new FileOutputStream(outFile) );
			int bufferSize = 1024 * 64;
			byte[] b = new byte[bufferSize];
			int n;
			while ( (n = in.read(b, 0, b.length)) != -1 ) {
				out.write(b, 0, n);
			}
			out.flush();
			out.close();
			return true;
		}
		catch (Exception ex) { return false; }
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

	public boolean deleteAll(File file) {
		boolean b = true;
		if ((file != null) && file.exists()) {
			if (file.isDirectory()) {
				try {
					File[] files = file.listFiles();
					for (File f : files) b &= deleteAll(f);
				}
				catch (Exception e) { return false; }
			}
			b &= file.delete();
		}
		return b;
	}

	private String getWelcomePage() {
		return
				"<html>"
			+	"<head></head>"
			+	"<body style=\"background:#b9d0ed;font-family:sans-serif;\">"
			+	"<center>"
			+	"<h1 style=\"color:#2977b9\">" + windowTitle + "</h1>"
			+	"Version: " + programDate + "<br>"
			+	"Copyright 2014: RSNA<br><br>"

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
					cp.appendln(Color.black, "...could not find it. [OK, this is a "+programName+" installation]");
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
			isr.close();
			if (programName.equals("ISN")) return !shutdown(port, ssl);
			return true;
		}
		catch (Exception ex) { return false; }
	}
	
    public boolean shutdown(int port, boolean ssl) {
        try {
            String protocol = "http" + (ssl?"s":"");
            URL url = new URL( protocol, "127.0.0.1", port, "shutdown");
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("servicemanager", "shutdown");
            conn.connect();

            StringBuffer sb = new StringBuffer();
            BufferedReader br = new BufferedReader( new InputStreamReader(conn.getInputStream(), "UTF-8") );
            int n; char[] cbuf = new char[1024];
            while ((n=br.read(cbuf, 0, cbuf.length)) != -1) sb.append(cbuf,0,n);
            br.close();
            String message = sb.toString().replace("<br>","\n");
            if (message.contains("Goodbye")) {
				cp.appendln("Shutting down the server:");
				String[] lines = message.split("\n");
				for (String line : lines) {
					cp.append("...");
            		cp.appendln(line);
				}
            	return true;
			}
        }
        catch (Exception ex) { }
        cp.appendln("Unable to shutdown CTP");
        return false;
    }

	private void logSubstring(String info, String text, String search) {
		System.out.println(info);
		System.out.println("...String: \""+text+"\"");
		if (search != null) {
			System.out.println("...Search: \""+search+"\"");
		}
	}

	private boolean startLauncher(File dir) {
		try {
			Runtime rt = Runtime.getRuntime();
			ArrayList<String> command = new ArrayList<String>();
			command.add("java");
			command.add("-jar");
			command.add("Launcher.jar");
			String[] cmdarray = command.toArray( new String[command.size()] );
			Process proc = rt.exec(cmdarray, null, dir);
			return true;
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this,
					"Unable to start the Launcher program.\n"+ex.getMessage(),
					"Start Failed",
					JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
	}

	String[] sslAttrs = {
		"keystore",
		"keystorePassword",
		"truststore",
		"truststorePassword"
	};
	String[] proxyAttrs = {
		"proxyIPAddress",
		"proxyPort",
		"proxyUsername",
		"proxyPassword"
	};
	String[] ldapAttrs = {
		"initialContextFactory",
		"providerURL",
		"securityAuthentication",
		"securityPrincipal",
		"ldapAdmin"
	};
	private void fixConfigSchema() {
		File configFile;
		File ctpDir = new File(directory, "CTP");
		if (ctpDir.exists()) configFile = new File(ctpDir, "config.xml");
		else configFile = new File(directory, "config.xml");
		if (configFile.exists()) {
			try {
				Document doc = getDocument(configFile);
				Element root = doc.getDocumentElement();
				Element server = getFirstNamedChild(root, "Server");
				moveAttributes(server, sslAttrs, "SSL");
				moveAttributes(server, proxyAttrs, "ProxyServer");
				moveAttributes(server, ldapAttrs, "LDAP");
				if (programName.equals("ISN")) fixRSNAROOT(server);
				if (isMIRC(root)) fixFileServiceAnonymizerID(root);
				setFileText(configFile, toString(doc));
			}
			catch (Exception ex) {
				cp.appendln(Color.red, "\nUnable to convert the config file schema.");
				cp.appendln(Color.black, "");
			}
		}
		else {
			cp.appendln(Color.red, "\nUnable to find the config file to check the schema.");
			cp.appendln(Color.black, "");
		}
	}
	private boolean isMIRC(Element root) {
		NodeList nl = root.getElementsByTagName("Plugin");
		for (int i=0; i<nl.getLength(); i++) {
			Element e = (Element)nl.item(i);
			if (e.getAttribute("class").equals("mirc.MIRC")) return true;
		}
		return false;
	}
	private void fixFileServiceAnonymizerID(Element root) {
		Node child = root.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				Element e = (Element)child;
				if (e.getAttribute("class").equals("org.rsna.ctp.stdstages.DicomAnonymizer")) {
					if (e.getAttribute("root").contains("FileService")) {
						e.setAttribute("id", "FileServiceAnonymizer");
					}
				}
				else fixFileServiceAnonymizerID(e);
			}
			child = child.getNextSibling();
		}		
	}

	private static DocumentBuilder getDocumentBuilder() throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		return dbf.newDocumentBuilder();
	}

	private static Document getDocument(File file) throws Exception {
		DocumentBuilder db = getDocumentBuilder();
		return db.parse(file);
	}

	private static Document getDocument(InputStream inputStream) throws Exception {
		DocumentBuilder db = getDocumentBuilder();
		return db.parse(new InputSource(inputStream));
	}

	private static Element getFirstNamedChild(Node node, String name) {
		if (node == null) return null;
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if ( !(node instanceof Element) ) return null;
		Node child = node.getFirstChild();
		while (child != null) {
			if ((child instanceof Element) && child.getNodeName().equals(name)) {
				return (Element)child;
			}
			child = child.getNextSibling();
		}
		return null;
	}

	private void moveAttributes(Element el, String[] attrNames, String childName) {
		String[] values = new String[attrNames.length];
		boolean nonBlank = false;
		for (int k=0; k<attrNames.length; k++) {
			values[k] = el.getAttribute(attrNames[k]).trim();
			nonBlank |= !values[k].equals("");
			el.removeAttribute(attrNames[k]);
		}
		if (nonBlank) {
			Element child = el.getOwnerDocument().createElement(childName);
			for (int k=0; k<attrNames.length; k++) {
				if (!values[k].equals("")) child.setAttribute(attrNames[k], values[k]);
			}
			el.appendChild(child);
		}
	}

	private void fixRSNAROOT(Element server) {
		if (programName.equals("ISN")) {
			Element ssl = getFirstNamedChild(server, "SSL");
			if (ssl != null) {
				if (System.getProperty("os.name").contains("Windows")) {
					ssl.setAttribute("keystore", ssl.getAttribute("keystore").replace("RSNA_HOME", "RSNA_ROOT"));
					ssl.setAttribute("truststore", ssl.getAttribute("truststore").replace("RSNA_HOME", "RSNA_ROOT"));
				}
				else {
					ssl.setAttribute("keystore", "${RSNA_ROOT}/conf/keystore.jks");
					ssl.setAttribute("truststore", "${RSNA_ROOT}/conf/truststore.jks");
				}
			}
		}
	}

	private static String toString(Node node) {
		StringBuffer sb = new StringBuffer();
		renderNode(sb, node);
		return sb.toString();
	}

	//Recursively walk the tree and write the nodes to a StringWriter.
	private static void renderNode(StringBuffer sb, Node node) {
		if (node == null) { sb.append("null"); return; }
		switch (node.getNodeType()) {

			case Node.DOCUMENT_NODE:
				sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				Node root = ((Document)node).getDocumentElement();
				renderNode(sb, root);
				break;

			case Node.ELEMENT_NODE:
				String name = getNodeNameWithNamespace(node);
				NamedNodeMap attributes = node.getAttributes();
				if (attributes.getLength() == 0) {
					sb.append("<" + name + ">");
				}
				else {
					sb.append("<" + name + " ");
					int attrlen = attributes.getLength();
					for (int i=0; i<attrlen; i++) {
						Node attr = attributes.item(i);
						String attrName = getNodeNameWithNamespace(attr);
						sb.append(attrName + "=\"" + escapeChars(attr.getNodeValue()));
						if (i < attrlen-1)
							sb.append("\" ");
						else
							sb.append("\">");
					}
				}
				NodeList children = node.getChildNodes();
				if (children != null) {
					for (int i=0; i<children.getLength(); i++) {
						renderNode(sb,children.item(i));
					}
				}
				sb.append("</" + name + ">");
				break;

			case Node.TEXT_NODE:
				sb.append(escapeChars(node.getNodeValue()));
				break;

			case Node.CDATA_SECTION_NODE:
				sb.append("<![CDATA[" + node.getNodeValue() + "]]>");
				break;

			case Node.PROCESSING_INSTRUCTION_NODE:
				sb.append("<?" + node.getNodeName() + " " +
					escapeChars(node.getNodeValue()) + "?>");
				break;

			case Node.ENTITY_REFERENCE_NODE:
				sb.append("&" + node.getNodeName() + ";");
				break;

			case Node.DOCUMENT_TYPE_NODE:
				// Ignore document type nodes
				break;

			case Node.COMMENT_NODE:
				sb.append("<!--" + node.getNodeValue() + "-->");
				break;
		}
		return;
	}

	private static String getNodeNameWithNamespace(Node node) {
		String name = node.getNodeName();
		String ns = node.getNamespaceURI();
		String prefix = (ns != null) ? node.lookupPrefix(ns) : null;
		if ((prefix != null) && !name.startsWith(prefix+":")) {
			name = prefix + ":" + name;
		}
		return name;
	}

	private static String escapeChars(String theString) {
		return theString.replace("&","&amp;")
						.replace(">","&gt;")
						.replace("<","&lt;")
						.replace("\"","&quot;")
						.replace("'","&apos;");
	}
}
