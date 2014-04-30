package org.rsna.ctp.stdstages.email;

import java.io.File;
import java.util.Date;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;

public class EmailSender {

	Session session;
	String username;
	String password;

    public EmailSender(String smtpServer, String username, String password) throws Exception {
		this.username = check(username);
		this.password = check(password);
		Properties props = System.getProperties();
		props.put("mail.smtp.host", smtpServer);
		session = Session.getDefaultInstance(props, null);
	}

	private String check(String s) {
		return (s != null) ? s.trim() : "";
	}

	/**
	 * Send a message in plain text.
	 */
	public boolean sendPlainText(String to,
								 String from,
								 String cc,
								 String subject,
								 String body) {
		try {
			// Create the message
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(from));
			addRecipients(msg, Message.RecipientType.TO, to);
			addRecipients(msg, Message.RecipientType.CC, cc);
			msg.setSubject(subject);
			msg.setText(body);

			// Set additional header information
			msg.setSentDate(new Date());

			// Send the message
			if (username.equals("")) Transport.send(msg);
			else Transport.send(msg, username, password);
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
			msg.setFrom(new InternetAddress(from));
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
			if (username.equals("")) Transport.send(msg);
			else Transport.send(msg, username, password);
			return true;
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}

	/**
	 * Send a message with attachments.
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
			msg.setFrom(new InternetAddress(from));
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
			if (username.equals("")) Transport.send(msg);
			else Transport.send(msg, username, password);
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
			for (int i=0; i<recs.length; i++) {
				String rec = recs[i].trim();
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
