/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdplugins;

import org.apache.log4j.Logger;
import org.rsna.ctp.plugin.AbstractPlugin;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.service.HttpService;
import org.rsna.service.Service;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

/**
 * A Plugin to monitor an HTTP port and redirect connections to an HTTPS port.
 */
public class Redirector extends AbstractPlugin {

	static final Logger logger = Logger.getLogger(Redirector.class);

	int httpPort;
	String httpsHost;
	int httpsPort;
	HttpService monitor = null;
	Service handler = null;

	/**
	 * Construct a plugin implementing a Redirector.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the plugin.
	 */
	public Redirector(Element element) {
		super(element);
		httpPort = StringUtil.getInt(element.getAttribute("httpPort"), 80);
		httpsHost = element.getAttribute("httpsHost").trim();
		httpsPort = StringUtil.getInt(element.getAttribute("httpsPort"), 443);
		try {
			handler = new RedirectionHandler();
			monitor = new HttpService(false, httpPort, handler);
			logger.info("Redirector Plugin instantiated");
		}
		catch (Exception ex) {
			logger.warn("Unable to instantiate the Redirector plugin on port "+httpPort);
		}
	}

	/**
	 * Start the plugin.
	 */
	public void start() {
		if (monitor != null) {
			monitor.start();
			logger.info("Redirector Plugin started on port "+httpPort+"; target port: "+httpsPort);
		}
	}

	/**
	 * Stop the plugin.
	 */
	public void shutdown() {
		if (monitor != null) {
			monitor.stopServer();
			logger.info("Redirector Plugin stopped");
		}
		stop = true;
	}

	class RedirectionHandler implements Service {

		public RedirectionHandler() { }

		public void process(HttpRequest req, HttpResponse res) {
			String host = httpsHost;
			if (host.equals("")) {
				host = req.getHost();
				int k = host.indexOf(":");
				if (k >= 0) host = host.substring(0,k);
			}
			host += ":" + httpsPort;
			String query = req.getQueryString();
			if (!query.equals("")) query = "?" + query;
			String url = "https://" + host + req.getPath() + query;
			res.redirect(url);
		}
	}

}