/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Util {

	public static int getInt(String theString, int defaultValue) {
		if (theString == null) return defaultValue;
		theString = theString.trim();
		if (theString.equals("")) return defaultValue;
		try { return Integer.parseInt(theString); }
		catch (NumberFormatException e) { return defaultValue; }
	}

	public static String getText(File file) throws Exception {
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

	public static void setText(File file, String text) throws Exception {
		BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter(
					new FileOutputStream(file), "UTF-8"));
		bw.write(text, 0, text.length());
		bw.flush();
		bw.close();
	}

	public static Document getDocument(File file) {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			DocumentBuilder db = dbf.newDocumentBuilder();
			return db.parse(file);
		}
		catch (Exception ex) { return null; }
	}

	public static Document getDocument() {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			DocumentBuilder db = dbf.newDocumentBuilder();
			return db.newDocument();
		}
		catch (Exception ex) { return null; }
	}

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

	public static Hashtable<String,String> getManifestAttributes(String path) {
		return getManifestAttributes( new File(path) );
	}

	public static Hashtable<String,String> getManifestAttributes(File jarFile) {
		Hashtable<String,String> h = new Hashtable<String,String>();
		JarFile jar = null;
		try {
			jar = new JarFile(jarFile);
			Manifest manifest = jar.getManifest();
			h = getManifestAttributes(manifest);
		}
		catch (Exception ex) { h = null; }
		if (jar != null) {
			try { jar.close(); }
			catch (Exception ignore) { }
		}
		return h;
	}

	public static Hashtable<String,String> getManifestAttributes(Manifest manifest) {
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

	public static HttpURLConnection getConnection(URL url) throws Exception {
		String protocol = url.getProtocol().toLowerCase();
		if (!protocol.startsWith("https") && !protocol.startsWith("http")) {
			throw new Exception("Unsupported protocol ("+protocol+")");
		}

		HttpURLConnection conn;
		if (protocol.startsWith("https")) {
			HttpsURLConnection httpsConn = (HttpsURLConnection)url.openConnection();
			httpsConn.setHostnameVerifier(new AcceptAllHostnameVerifier());
			httpsConn.setUseCaches(false);
			httpsConn.setDefaultUseCaches(false);

			//Set the socket factory
			TrustManager[] trustAllCerts = new TrustManager[] { new AcceptAllX509TrustManager() };
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new SecureRandom());
			httpsConn.setSSLSocketFactory(sc.getSocketFactory());

			conn = httpsConn;
		}
		else conn = (HttpURLConnection)url.openConnection();

		conn.setDoOutput(true);
		conn.setDoInput(true);
		return conn;
	}

	static class AcceptAllX509TrustManager implements X509TrustManager {

		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}

		public void checkClientTrusted(X509Certificate[] certs, String authType) { }

		public void checkServerTrusted(X509Certificate[] certs, String authType) { }
	}

	static class AcceptAllHostnameVerifier implements HostnameVerifier {
		public boolean verify(String urlHost, SSLSession ssls) {
			return true;
		}
	}

	static final String[] browsers = { "google-chrome", "firefox", "opera",
		"epiphany", "konqueror", "conkeror", "midori", "kazehakase", "mozilla" };
	static final String errMsg = "Error attempting to launch web browser";

	/**
	 * Opens the specified web page in the user's default browser
	 * @param url A web address (URL) of a web page (ex: "http://www.google.com/")
	 */
	public static void openURL(String url) {
		try {  //attempt to use Desktop library from JDK 1.6+
			Class<?> d = Class.forName("java.awt.Desktop");
			d.getDeclaredMethod( "browse",  new Class[] { java.net.URI.class } )
				.invoke(
					d.getDeclaredMethod("getDesktop").invoke(null),
					new Object[] { java.net.URI.create(url) });
			//above code mimicks:  java.awt.Desktop.getDesktop().browse()
		}
		catch (Exception ignore) {  //library not available or failed
			String osName = System.getProperty("os.name");
			try {
				if (osName.startsWith("Mac OS")) {
					Class.forName("com.apple.eio.FileManager")
						.getDeclaredMethod(
							"openURL",
							new Class[] { String.class })
								.invoke(
									null,
									new Object[] { url });
				}
				else if (osName.startsWith("Windows")) {
					Runtime.getRuntime().exec( "rundll32 url.dll,FileProtocolHandler " + url);
				}
				else { //assume Unix or Linux
					String browser = null;
					for (String b : browsers) {
						if (browser == null &&
							Runtime.getRuntime().exec(new String[] {"which", b})
								.getInputStream().read() != -1) {
							Runtime.getRuntime().exec(new String[] { browser = b, url });
						}
					}
					if (browser == null) throw new Exception(Arrays.toString(browsers));
				}
			}
			catch (Exception e) {
				JOptionPane.showMessageDialog(null, errMsg + "\n" + e.toString());
			}
		}
	}

	static final String def = "127.0.0.1";

	/**
	 * Get the IP address of the host computer, or the loopback address
	 * (127.0.0.1) if the operation fails.
	 * @return the IP Address string.
	 */
	public static String getIPAddress() {
		try {
			//Get all the network interfaces
			Enumeration<NetworkInterface> nwEnum = NetworkInterface.getNetworkInterfaces();

			//Return the first IPv4 address that is not a loopback address.
			while (nwEnum.hasMoreElements()) {
				NetworkInterface nw = nwEnum.nextElement();
				Enumeration<InetAddress> ipEnum = nw.getInetAddresses();
				while (ipEnum.hasMoreElements()) {
					InetAddress ina = ipEnum.nextElement();
					if ((ina instanceof Inet4Address) && !ina.isLoopbackAddress()) {
						return ina.getHostAddress();
					}
				}
			}
		}
		catch (Exception ex) { }
		return def;
	}

    public static void shutdown() {
		try {
			Configuration config = Configuration.getInstance();
			String protocol = "http" + (config.ssl?"s":"");
			URL url = new URL( protocol, "127.0.0.1", config.port, "/shutdown");

			HttpURLConnection conn = getConnection( url );
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
			HttpURLConnection conn = getConnection(url);
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
				String ext = System.getProperty("java.ext.dirs");
				String sep = System.getProperty("path.separator");
				String user = System.getProperty("user.dir");
				File dir = new File(user);

				Properties props = Configuration.getInstance().props;

				ArrayList<String> command = new ArrayList<String>();

				command.add("java");

				//Set the maximum memory pool
				String maxHeap = props.getProperty("mx", "512");
				int mx = getInt(maxHeap, 512);
				mx = Math.max(mx, 128);
				command.add("-Xmx"+mx+"m");

				//Set the starting memory pool
				String minHeap = props.getProperty("ms", "128");
				int ms = getInt(minHeap, 128);
				ms = Math.max(ms, 128);
				command.add("-Xms"+ms+"m");

				//Set the Thread stack size, if defined in the props
				String stackSize = props.getProperty("ss", "");
				if (!stackSize.equals("")) {
					int ss = getInt(stackSize, 0);
					if (ss > 32) command.add("-Xss"+ss+"k");
				}

				//Enable SSL debugging, if required
				if (props.getProperty("ssl", "no").equals("yes")) {
					command.add("-Djavax.net.debug=ssl");
				}

				//Enable Java monitoring, if required
				if (props.getProperty("mon", "no").equals("yes")) {
					command.add("-Dcom.sun.management.jmxremote ");
				}

				//Set the extensions directories
				String extDirs = props.getProperty("ext", "").trim();
				if (!extDirs.equals("")) {
					extDirs += sep;
					ext = "-Djava.ext.dirs=" + extDirs + ext;
					if (ext.contains(" ") || ext.contains("\t")) ext = "\"" + ext + "\"";
					command.add(ext);
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

	/**
	 * Make a pretty String from an XML DOM Node. Note: this method
	 * inserts leading and trailing whitespace in text nodes. Thus,
	 * the result of this method may be functionally different from
	 * the original XML. It should be used when such whitespace is
	 * not important or when the objective is just to make a string
	 * for printing.
	 * @param node the node at the top of the tree.
	 * @return the XML string for the node and its children.
	 */
	public static String toPrettyString(Node node) {
		StringBuffer sb = new StringBuffer();
		renderNode(sb, node, "", "    ", "<", ">", "\n");
		return sb.toString();
	}

	//Recursively walk the tree and write the nodes to a StringWriter.
	private static void renderNode(StringBuffer sb,
									Node node,
									String margin,
									String indent,
									String lab,
									String rab,
									String nl) {
		if (node == null) { sb.append("null"); return; }
		switch (node.getNodeType()) {

			case Node.DOCUMENT_NODE:
				//sb.append(margin + lab +"?xml version=\"1.0\" encoding=\"UTF-8\"?" + rab + nl);
				Node root = ((Document)node).getDocumentElement();
				renderNode(sb, root, margin, indent, lab, rab, nl);
				break;

			case Node.ELEMENT_NODE:
				String name = node.getNodeName();
				NodeList children = node.getChildNodes();
				int nChildren = children.getLength();
				NamedNodeMap attributes = node.getAttributes();
				int nAttrs = attributes.getLength();

				boolean singleShortTextChild = (nAttrs == 0) && (nChildren == 1)
											&& (children.item(0).getNodeType() == Node.TEXT_NODE)
											&& (children.item(0).getTextContent().length() < 70)
											&& (!children.item(0).getTextContent().contains("\n"));

				if (singleShortTextChild) {
					sb.append(margin + lab + name + ((nChildren == 0) ? "/" : "") + rab);
				}
				else if (nAttrs == 0 && !singleShortTextChild) {
					sb.append(margin + lab + name + ((nChildren == 0) ? "/" : "") + rab + nl);
				}
				else if (nAttrs == 1) {
					Node attr = attributes.item(0);
					sb.append(margin + lab + name +  " "
								+ attr.getNodeName() + "=\"" + escapeChars(attr.getNodeValue()) + "\""
								+ ((nChildren == 0) ? "/" : "")
								+ rab + nl);
				}
				else {
					sb.append(margin + lab + name + nl);
					for (int i=0; i<nAttrs; i++) {
						Node attr = attributes.item(i);
						sb.append(margin + indent + attr.getNodeName()
							+ "=\"" + escapeChars(attr.getNodeValue()));
						if (i < nAttrs - 1)
							sb.append("\"" + nl);
						else
							sb.append("\"" + ((nChildren == 0) ? "/" : "") + rab + nl);
					}
				}
				if (singleShortTextChild) {
					String text = escapeChars(node.getTextContent());
					sb.append(text.trim());
					sb.append(lab + "/" + name + rab + nl);				}
				else {
					for (int i=0; i<nChildren; i++) {
						renderNode(sb, children.item(i), margin+indent, indent, lab, rab, nl);
					}
				}
				if (nChildren != 0 && !singleShortTextChild) sb.append(margin + lab + "/" + name + rab + nl);
				break;

			case Node.TEXT_NODE:
			case Node.CDATA_SECTION_NODE:
				String text = escapeChars(node.getNodeValue());
				String[] lines = text.split("\n");
				for (int i=0; i<lines.length; i++) {
					if (!lines[i].trim().equals("")) sb.append(margin + lines[i].trim() + nl);
				}
				break;

			case Node.PROCESSING_INSTRUCTION_NODE:
				sb.append(margin + lab + "?" + node.getNodeName() + " " +
					escapeChars(node.getNodeValue()) + "?" + rab + nl);
				break;

			case Node.ENTITY_REFERENCE_NODE:
				sb.append("&" + node.getNodeName() + ";");
				break;

			case Node.DOCUMENT_TYPE_NODE:
				// Ignore document type nodes
				break;

			case Node.COMMENT_NODE:
				sb.append(margin + lab + "!--" + node.getNodeValue() + "--" + rab + nl);
				break;
		}
		return;
	}

	/**
	 * Escape the ampersand, less-than, greater-than, single and double quote
	 * characters in a string, replacing with their XML entities.
	 * @param theString the string to escape.
	 * @return the modified string.
	 */
	public static String escapeChars(String theString) {
		return theString.replace("&","&amp;")
						.replace(">","&gt;")
						.replace("<","&lt;")
						.replace("\"","&quot;")
						.replace("'","&apos;");
	}

	/**
	 * Get the first child element with a specified name.
	 * If the starting node is a Document, use the document element
	 * as the starting point. Only first-generation children of the
	 * starting node are searched.
	 * @param node the starting node.
	 * @param name the name of the child to find.
	 * @return the first child element with the specified name, or null
	 * if the starting node is null or if no child with the name exists.
	 */
	public static Element getFirstNamedChild(Node node, String name) {
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

	/**
	 * Delete a file or a directory. If the file is a directory, delete
	 * the contents of the directory and all its child directories, then
	 * delete the directory itself.
	 * @param file the file to delete.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean deleteAll(File file) {
		boolean b = true;
		if (file.exists()) {
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

}

