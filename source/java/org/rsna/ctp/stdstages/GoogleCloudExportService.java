/*---------------------------------------------------------------
 *  Copyright 2015 by the Radiological Society of North America
 *
 *  This source software is released under the terms of the
 *  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
 *----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import org.apache.log4j.Logger;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractExportService;
import org.rsna.ctp.pipeline.Status;
import org.rsna.server.HttpResponse;
import org.rsna.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * An ExportService that exports files via the DICOM STOW-RS protocol.
 */
public class GoogleCloudExportService extends DicomSTOWRSExportService {

    static final Logger logger = Logger.getLogger(GoogleCloudExportService.class);

    static final int oneSecond = 1000;
    final int connectionTimeout = 20 * oneSecond;
    final int readTimeout = 120 * oneSecond;

    URL url;
    String protocol;
    String username = null;
    String password = null;
    boolean authenticate = false;
    String authHeader = null;
    boolean includeContentDispositionHeader = false;
    boolean logUnauthorizedResponses = true;
    boolean logDuplicates = false;

    /**/ LinkedList<String> recentUIDs = new LinkedList<String>();
    /**/ LinkedList<Long> recentTimes = new LinkedList<Long>();
    /**/static final int maxQueueSize = 10;

    /**
     * Class constructor; creates a new instance of the ExportService.
     *
     * @param element the configuration element.
     * @throws Exception on any error
     */
    public GoogleCloudExportService(Element element) throws Exception {
        super(element);

        //See if we are to log duplicate transmissions
        logDuplicates = element.getAttribute("logDuplicates").equals("yes");

        //Get the destination url
        url = new URL(element.getAttribute("url").trim());

        //Get the flag for including the Content-Disposition header in requests
        includeContentDispositionHeader =
                element.getAttribute("includeContentDispositionHeader")
                        .trim().toLowerCase().equals("yes");

        //Get the credentials attributes, if they are present.
        //Note: the credentials might be included in the username and password
        //attributes or embedded in the URL's userinfo. The username and password
        //attributes take precedence.
        username = element.getAttribute("username").trim();
        password = element.getAttribute("password").trim();
        String userinfo = url.getUserInfo();
        if (username.equals("") && (userinfo != null)) {
            String[] creds = userinfo.split(":");
            if (creds.length == 2) {
                username = creds[0];
                password = creds[1];
            }
        }
        authenticate = !username.equals("");
        if (authenticate) {
            authHeader = "Basic " + Base64.encodeToString((username + ":" + password).getBytes());
        }

        //Validate the destination url
        protocol = url.getProtocol().toLowerCase();
        if (!protocol.startsWith("https") && !protocol.startsWith("http")) {
            logger.error(name + ": Illegal protocol (" + protocol + ")");
            throw new Exception();
        }
        if (url.getPort() == -1) {
            logger.error(name + ": No port specified: " + element.getAttribute("url"));
            throw new Exception();
        }
        logger.info(name + ": " + url.getProtocol() + " protocol; port " + url.getPort());
    }

    /**
     * Export a file.
     *
     * @param fileToExport the file to export.
     * @return the status of the attempt to export the file.
     */
    public Status export(File fileToExport) {

        //Do not export zero-length files
        long fileLength = fileToExport.length();
        if (fileLength == 0) return Status.FAIL;

        HttpURLConnection conn = null;
        OutputStream svros = null;
        try {
            FileObject fileObject = FileObject.getInstance(fileToExport);

            //Establish the connection
            conn = HttpUtil.getConnection(url);
            conn.setReadTimeout(connectionTimeout);
            conn.setConnectTimeout(readTimeout);
            if (authenticate) conn.setRequestProperty("Authorization", authHeader);
            //if (logger.isDebugEnabled()) logConnection(conn);

            if (logDuplicates) {
                //*********************************************************************************************
                //See if this object has the same UID as a recent one.
                String currentUID = fileObject.getUID();
                if (recentUIDs.contains(currentUID)) {
                    logger.warn("----------------------------------------------------------------");
                    logger.warn(name);
                    logger.warn("Duplicate UID in last " + maxQueueSize + " objects: " + currentUID);
                    String s = "";
                    long time = 0;
                    for (int i = 0; i < recentUIDs.size(); i++) {
                        String uid = recentUIDs.get(i);
                        s += uid.equals(currentUID) ? "!" : "*";
                        time = recentTimes.get(i).longValue();
                    }
                    long deltaT = System.currentTimeMillis() - time;
                    logger.warn("[oldest] " + s + "! [newest]  deltaT = " + deltaT + "ms");
                    logger.warn("----------------------------------------------------------------");
                }
                recentUIDs.add(currentUID);
                recentTimes.add(new Long(System.currentTimeMillis()));
                if (recentUIDs.size() > maxQueueSize) {
                    recentUIDs.remove();
                    recentTimes.remove();
                }
                //*********************************************************************************************
            }

            //Send the file to the server
            ClientHttpRequest req = new ClientHttpRequest(conn, "multipart/related; type=application/dicom;");
            if (!includeContentDispositionHeader) req.addFilePart(fileToExport, "application/dicom");
            else {
                String ctHeader = "Content-Type: application/dicom";
                String cdHeader = "Content-Disposition: form-data; name=\"stowrs\"; filename=\"" + fileToExport.getName() + "\";";
                String[] headers = {cdHeader, ctHeader};
                req.addFilePart(fileToExport, headers);
            }
            InputStream is = req.post();
            String response = FileUtil.getText(is, "UTF-8");
            conn.disconnect();

            //Get the response code and log Unauthorized responses
            int responseCode = conn.getResponseCode();
            if (logger.isDebugEnabled()) {
                try {
                    Document doc = XmlUtil.getDocument(response);
                    response = XmlUtil.toPrettyString(doc);
                } catch (Exception ex) {
                }
                logger.debug(name + ": Response code: " + responseCode);
                logger.debug(name + ": XML Response Message:\n" + response);
            }

            if (responseCode == HttpResponse.unauthorized) {
                if (logUnauthorizedResponses) {
                    logger.warn(name + ": Credentials for " + username + " were not accepted by " + url);
                    logUnauthorizedResponses = false;
                }
                conn.disconnect();
                enableExport = false;
                return failOrRetry();
            } else if (responseCode == HttpResponse.forbidden) {
                if (logUnauthorizedResponses) {
                    logger.warn(name + ": User " + username + " was not accepted by " + url);
                    logUnauthorizedResponses = false;
                }
                conn.disconnect();
                enableExport = false;
                return failOrRetry();
            } else if (!logUnauthorizedResponses) {
                logger.warn(name + ": Credentials for " + username + " have been accepted by " + url);
                logUnauthorizedResponses = true;
            }

            if (responseCode == HttpResponse.ok) {
                makeAuditLogEntry(fileObject, Status.OK, getName(), url.toString());
                return Status.OK;
            } else return Status.FAIL;
        } catch (Exception e) {
            if (logger.isDebugEnabled()) logger.debug(name + ": export failed: " + e.getMessage(), e);
            else logger.warn(name + ": export failed: " + e.getMessage());
            return failOrRetry();
        }
    }

    private Status failOrRetry() {
        return logger.isDebugEnabled() ? Status.FAIL : Status.RETRY;
    }

    private void logConnection(HttpURLConnection conn) {
        StringBuffer sb = new StringBuffer();
        sb.append(name + ": Connection parameters:\n");
        sb.append("Request method: " + conn.getRequestMethod() + "\n");
        sb.append("URL: " + conn.getURL() + "\n");
        sb.append("Request properties:\n");
        Map<String, List<String>> props = conn.getRequestProperties();
        for (String prop : props.keySet()) {
            List<String> list = props.get(prop);
            for (String value : list) {
                sb.append(prop + " : " + value + "\n");
            }
        }
        logger.debug(sb.toString());
    }

}