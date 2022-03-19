package org.rsna.ctp.stdstages.email;

import java.io.File;
import java.util.Date;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class EmailSender {

	Session session = null;
	Transport transport = null;
	String smtpServer = null;
	String senderUsername = null;
	String senderPassword = null;

	public EmailSender(String smtpServer, 
					   String smtpPort, 
					   String senderUsername, 
					   String senderPassword,
					   boolean tls) {
		this.smtpServer = smtpServer;
		this.senderUsername = senderUsername;
		this.senderPassword = senderPassword;

		Properties props = System.getProperties();
		props.put("mail.smtp.host", smtpServer);
		if (smtpPort != null) props.put("mail.smtp.port", smtpPort);
		props.put("mail.smtp.starttls.enable", Boolean.toString(tls));
		if ( (senderUsername != null) && (senderPassword != null) &&
				!senderUsername.trim().equals("") && !senderPassword.trim().equals("") ) {
			props.put("mail.smtp.auth", "true");
			final String un = senderUsername;
			final String pw = senderPassword;
			session = Session.getDefaultInstance(
						props, 
						new Authenticator() {
            				protected PasswordAuthentication getPasswordAuthentication() {
                				return new PasswordAuthentication(un, pw);
            				}
          				}
          			);
		}
		else session = Session.getDefaultInstance(props, null);
	}
	
	public boolean connect() {
		try {
			if (transport == null) transport = session.getTransport("smtp");
			transport.connect();
			return true;
		}
		catch (Exception ex) { }
		return false;
	}
	
	public boolean close() {
		try {
			if (transport != null) {
				transport.close();
				transport = null;
				return true;
			}
		}
		catch (Exception ex) { }
		return false;
	}
	
	/**
	 * Send a message in plain text.
	 * @param to the recipients
	 * @param from the sender
	 * @param cc the copy recipients
	 * @param subject the subject of the message
	 * @param body the body of the message
	 * @return true of the message was successfully sent
	 */
	public boolean sendPlainText(String to,
								 String from,
								 String cc,
								 String subject,
								 String body) {
		try {
			// Create the message
			Message msg = new MimeMessage(session);
			msg.setFrom( new InternetAddress(from) );
			addRecipients(msg, Message.RecipientType.TO, to);
			addRecipients(msg, Message.RecipientType.CC, cc);
			msg.setSubject(subject);
			msg.setText(body);

			// Set additional header information
			msg.setSentDate(new Date());
			
			transport.sendMessage(msg, msg.getAllRecipients());
			return true;
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}

	/**
	 * Send a message with a plain text part and
	 * an HTML alternative.
	 * @param to the recipients
	 * @param from the sender
	 * @param cc the copy recipients
	 * @param subject the subject of the message
	 * @param textBody the plain text of the message
	 * @param htmlBody the HTML text of the message
	 * @return true of the message was successfully sent
	 */
	public boolean sendHTML(String to,
							String from,
							String cc,
							String subject,
							String textBody,
							String htmlBody) {
		try {
			// Create the message
			Message msg = new MimeMessage(session);
			msg.setFrom( new InternetAddress(from) );
			addRecipients(msg, Message.RecipientType.TO, to);
			addRecipients(msg, Message.RecipientType.CC, cc);
			msg.setSubject(subject);

			// Set additional header information
			msg.setSentDate(new Date());

			// Create the parts
			MimeMultipart mp = new MimeMultipart("alternative");
			MimeBodyPart bp1 = new MimeBodyPart();
			bp1.setText(textBody, "UTF-8", "plain");
			mp.addBodyPart(bp1);
			MimeBodyPart bp2 = new MimeBodyPart();
			bp2.setText(htmlBody, "UTF-8", "html");
			mp.addBodyPart(bp2);

			// Put the parts in the message.
			msg.setContent(mp);

			// Send the message
			transport.sendMessage(msg, msg.getAllRecipients());
			return true;
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}

	/**
	 * Send a message with attachments.
	 * @param to the recipients
	 * @param from the sender
	 * @param cc the copy recipients
	 * @param subject the subject of the message
	 * @param textBody the plain text of the message
	 * @param subtype "html" or "plain"
	 * @param files the array of attachments
	 * @return true of the message was successfully sent
	 */
	public boolean sendTextWithAttachments(
							String to,
							String from,
							String cc,
							String subject,
							String textBody,
							String subtype, //"html" or "plain"
							File[] files) {
		try {
			// Create the message
			Message msg = new MimeMessage(session);
			msg.setFrom( new InternetAddress(from) );
			addRecipients(msg, Message.RecipientType.TO, to);
			addRecipients(msg, Message.RecipientType.CC, cc);
			msg.setSubject(subject);

			// Set additional header information
			msg.setSentDate(new Date());

			// Create the parts
			MimeMultipart mp = new MimeMultipart("mixed");
			MimeBodyPart bp1 = new MimeBodyPart();
			bp1.setText(textBody, "UTF-8", subtype);
			mp.addBodyPart(bp1);
			if (files != null) {
				for (int i=0; i<files.length; i++) {
					MimeBodyPart bp = new MimeBodyPart();
					DataSource source = new FileDataSource(files[i]);
					bp.setDataHandler(new DataHandler(source));
					bp.setFileName(files[i].getName());
					mp.addBodyPart(bp);
				}
			}

			// Put the parts in the message.
			msg.setContent(mp);

			// Send the message
			transport.sendMessage(msg, msg.getAllRecipients());
			return true;
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}
	
	private void addRecipients(Message msg, Message.RecipientType type, String addrs) {
		if (addrs != null) {
			String[] recs = addrs.split(",");
			for (String rec : recs) {
				rec = rec.trim();
				if (!rec.equals("")) {
					try { msg.addRecipient(type, new InternetAddress(rec)); }
					catch (Exception ignore) {
						System.out.println("Unable to add recipient \""+rec+"\"");
					}
				}
			}
		}
	}

}
