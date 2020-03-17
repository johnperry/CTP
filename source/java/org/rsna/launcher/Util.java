/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.io.*;
import java.net.*;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;
import java.util.*;
import java.util.jar.*;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.rsna.util.HttpUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Util {

	public static String getAttribute( Document doc, String eName, String aName ) {
		Element root = doc.getDocumentElement();
		NodeList nl = root.getElementsByTagName( eName );
		for (int i=0; i<nl.getLength(); i++) {
			String attr = ((Element)nl.item(i)).getAttribute( aName ).trim();
			if (!attr.equals("")) return attr;
		}
		return "";
	}

	public static boolean containsAttribute( Document doc, String eName, String aName, String value ) {
		value = value.trim();
		Element root = doc.getDocumentElement();
		NodeList nl = root.getElementsByTagName( eName );
		for (int i=0; i<nl.getLength(); i++) {
			String attr = ((Element)nl.item(i)).getAttribute( aName ).trim();
			if (attr.equals(value)) return true;
		}
		return false;
	}

    public static void shutdown() {
		try {
			Configuration config = Configuration.getInstance();
			String protocol = "http" + (config.ssl?"s":"");
			URL url = new URL( protocol, "127.0.0.1", config.port, "/shutdown");

			HttpURLConnection conn = HttpUtil.getConnection( url );
			conn.setRequestMethod("GET");
			conn.setRequestProperty("servicemanager","shutdown");
			conn.connect();

			StringBuffer sb = new StringBuffer();
			BufferedReader br = new BufferedReader( new InputStreamReader(conn.getInputStream(), "UTF-8") );
			int n; char[] cbuf = new char[1024];
			while ((n=br.read(cbuf, 0, cbuf.length)) != -1) sb.append(cbuf,0,n);
			br.close();

			IOPanel.out.println( sb.toString().replace("<br>","\n") );
		}
		catch (Exception ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			IOPanel.out.println("\n\n"+sw.toString());
		}
	}

	public static boolean isRunning() {
		try {
			Configuration config = Configuration.getInstance();
			URL url = new URL("http" + (config.ssl?"s":"") + "://127.0.0.1:"+config.port);
			HttpURLConnection conn = HttpUtil.getConnection(url);
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(500);
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

	public static void wait(int ms) {
		try { Thread.sleep(ms); }
		catch (Exception ex) { }
	}

	public static Thread startup() {
		Runner runner = new Runner();
		runner.start();
		return runner;
	}

	static class Runner extends Thread {
		public Runner() {
			super("CTP Launcher Runner");
		}
		public void run() {
			Runtime rt = Runtime.getRuntime();
			try {
				boolean osIsWindows = System.getProperty("os.name").toLowerCase().contains("windows");
				String ver = System.getProperty("java.version");
				String ext = System.getProperty("java.ext.dirs", "");
				String sep = System.getProperty("path.separator");
				String user = System.getProperty("user.dir");
				File dir = new File(user);

				Properties props = Configuration.getInstance().props;

				ArrayList<String> command = new ArrayList<String>();

				command.add("java");

				//Set the maximum memory pool
				String maxHeap = props.getProperty("mx", "512");
				int mx = StringUtil.getInt(maxHeap, 512);
				mx = Math.max(mx, 128);
				command.add("-Xmx"+mx+"m");

				//Set the starting memory pool
				String minHeap = props.getProperty("ms", "128");
				int ms = StringUtil.getInt(minHeap, 128);
				ms = Math.max(ms, 128);
				command.add("-Xms"+ms+"m");

				//Set the Thread stack size, if defined in the props
				String stackSize = props.getProperty("ss", "");
				if (!stackSize.equals("")) {
					int ss = StringUtil.getInt(stackSize, 0);
					if (ss > 32) command.add("-Xss"+ss+"k");
				}

				//Enable SSL debugging, if required
				if (props.getProperty("ssl", "no").equals("yes")) {
					command.add("-Djavax.net.debug=ssl");
				}

				//Enable Java monitoring, if required
				if (props.getProperty("mon", "no").equals("yes")) {
					command.add("-Dcom.sun.management.jmxremote");
				}

				//Force 32-bit data model, if required
				if (props.getProperty("d32", "no").equals("yes")) {
					command.add("-d32");
				}

				//Set the extensions directories if Java 8 or less
				int n = ver.indexOf(".");
				if (n > 0) ver = ver.substring(0,n);
				n = Integer.parseInt(ver);
				if (n < 9) {
					String extDirs = props.getProperty("ext", "").trim();
					if (!extDirs.equals("")) {
						if (!ext.equals("")) extDirs += sep;
						extDirs += ext;
						if (!extDirs.equals("")) {
							ext = "-Djava.ext.dirs=" + extDirs;
							if (ext.contains(" ") || ext.contains("\t")) ext = "\"" + ext + "\"";
							command.add(ext);
						}
					}
				}

				//Set the program name
				command.add("-jar");
				command.add("libraries" + File.separator + "CTP.jar");

				String[] cmdarray = command.toArray( new String[command.size()] );

				for (String s : cmdarray) IOPanel.out.print(s + " ");
				IOPanel.out.println("");

				Process proc = rt.exec(cmdarray, null, dir);
				(new Streamer(proc.getErrorStream(), "stderr")).start();
				(new Streamer(proc.getInputStream(), "stdout")).start();

				int exitVal = proc.waitFor();
				IOPanel.out.println("Exit: " + ((exitVal==0) ? "normal" : "code "+exitVal));
			}
			catch (Exception ex) { ex.printStackTrace(); }
		}
	}

	static class Streamer extends Thread {
		InputStream is;
		String name;

		public Streamer(InputStream is, String name) {
			super("CTP Launcher Runner Streamer");
			this.is = is;
			this.name = name;
		}

		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ( (line = br.readLine()) != null) {
					IOPanel.out.println(name + ": " + line);
				}
				IOPanel.out.println(name + ": " + "exit");
			}
			catch (Exception ignore) { }
		}
	}

}

