package com.codeminders.demo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class DICOMGoogleClientHttpRequest {
	private static final String BOUNDARY = "DICOMwebBoundary";

	private URLConnection connection;
	private OutputStream os = null;
	private Map<String, String> cookies = new HashMap<String, String>();

	protected void connect() throws IOException {
		if (os == null)
			os = connection.getOutputStream();
	}

	protected void write(char c) throws IOException {
		connect();
		os.write(c);
	}

	protected void write(String s) throws IOException {
		connect();
		os.write(s.getBytes());
	}

	protected void newline() throws IOException {
		connect();
		write("\r\n");
	}

	protected void writeln(String s) throws IOException {
		connect();
		write(s);
		newline();
	}

	private static Random random = new Random();

	protected static String randomString() {
		return Long.toString(random.nextLong(), 36);
	}

	private void boundary() throws IOException {
		write("--");
		write(BOUNDARY);
	}

	/**
	 * Create a new multipart POST HTTP request on a freshly opened URLConnection
	 * 
	 * @param connection
	 *            an already open URL connection
	 * @param reqContentType
	 *            the Content-Type of the request (ending in ';')
	 * @throws IOException
	 *             on any error
	 */
	public DICOMGoogleClientHttpRequest(URLConnection connection, String reqContentType) throws IOException {
		this.connection = connection;
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", reqContentType + " boundary=" + BOUNDARY);
	}

	/**
	 * Create a new multipart POST HTTP request on a freshly opened URLConnection
	 * 
	 * @param connection
	 *            an already open URL connection
	 * @throws IOException
	 *             on any error
	 */
	public DICOMGoogleClientHttpRequest(URLConnection connection) throws IOException {
		this(connection, "multipart/form-data;");
	}

	/**
	 * Create a new multipart POST HTTP request for a specified URL
	 * 
	 * @param url
	 *            the URL to send request to
	 * @throws IOException
	 *             on any error
	 */
	public DICOMGoogleClientHttpRequest(URL url) throws IOException {
		this(url.openConnection());
	}

	/**
	 * Create a new multipart POST HTTP request for a specified URL string
	 * 
	 * @param urlString
	 *            the string representation of the URL to send request to
	 * @throws IOException
	 *             on any error
	 */
	public DICOMGoogleClientHttpRequest(String urlString) throws IOException {
		this(new URL(urlString));
	}

	private void postCookies() {
		StringBuffer cookieList = new StringBuffer();

		for (Iterator i = cookies.entrySet().iterator(); i.hasNext();) {
			Map.Entry entry = (Map.Entry) (i.next());
			cookieList.append(entry.getKey().toString() + "=" + entry.getValue());
			if (i.hasNext()) {
				cookieList.append("; ");
			}
		}
		if (cookieList.length() > 0) {
			connection.setRequestProperty("Cookie", cookieList.toString());
		}
	}

	/**
	 * Add a cookie to the request
	 * 
	 * @param name
	 *            cookie name
	 * @param value
	 *            cookie value
	 * @throws IOException
	 *             on any error
	 */
	public void setCookie(String name, String value) throws IOException {
		cookies.put(name, value);
	}

	private void writeName(String name) throws IOException {
		newline();
		write("Content-Disposition: form-data; name=\"");
		write(name);
		write('"');
	}

	/**
	 * Add a string parameter to the request
	 * 
	 * @param name
	 *            parameter name
	 * @param value
	 *            parameter value
	 * @throws IOException
	 *             on any error
	 */
	public void setParameter(String name, String value) throws IOException {
		boundary();
		writeName(name);
		newline();
		newline();
		writeln(value);
	}

	private static void pipe(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[4096];
		int nread;
		int total = 0;
		synchronized (in) {
			while ((nread = in.read(buf, 0, buf.length)) >= 0) {
				out.write(buf, 0, nread);
				total += nread;
			}
		}
		out.flush();
		buf = null;
	}

	/**
	 * Add a file parameter to the request
	 * 
	 * @param name
	 *            parameter name
	 * @param filename
	 *            the name of the file
	 * @param is
	 *            input stream to read the contents of the file from
	 * @throws IOException
	 *             on any error
	 */
	public void setParameter(String name, String filename, InputStream is) throws IOException {
		boundary();
		writeName(name);
		write("; filename=\"");
		write(filename);
		write('"');
		newline();
		write("Content-Type: ");
		String type = connection.guessContentTypeFromName(filename);
		if (type == null)
			type = "application/octet-stream";
		writeln(type);
		newline();
		pipe(is, os);
		newline();
	}

	/**
	 * Add a file parameter to the request
	 * 
	 * @param name
	 *            parameter name
	 * @param file
	 *            the file to upload
	 * @throws IOException
	 *             on any error
	 */
	public void setParameter(String name, File file) throws IOException {
		InputStream in = new FileInputStream(file);
		setParameter(name, file.getName(), in);
		in.close();
	}

	/**
	 * Add a file part to the request
	 * 
	 * @param file
	 *            the file to upload
	 * @param partContentType
	 *            the ContentType to be used for this part
	 * @throws IOException
	 *             on any error
	 */
	public void addFilePart(File file, String partContentType) throws IOException {
		InputStream in = new FileInputStream(file);
		boundary();
		newline();
		writeln("Content-Type: " + partContentType);
		newline();
		pipe(in, os);
		newline();
		in.close();
	}

	/**
	 * Add a file part to the request
	 * 
	 * @param file
	 *            the file to upload
	 * @param partHeaders
	 *            the headers to be used for this part (without \r\n at the end)
	 * @throws IOException
	 *             on any error
	 */
	public void addFilePart(File file, String[] partHeaders) throws IOException {
		InputStream in = new FileInputStream(file);
		boundary();
		newline();
		for (String header : partHeaders)
			writeln(header);
		newline();
		pipe(in, os);
		newline();
		in.close();
	}

	/**
	 * Post the request to the server
	 * 
	 * @return input stream with the server response
	 * @throws IOException
	 *             on any error
	 */
	public InputStream post() throws IOException {
		boundary();
		writeln("--");
		os.close();
		return connection.getInputStream();
	}

}