package org.rsna.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.text.DefaultCaret;

import org.rsna.ctp.stdstages.ReportService;
import org.rsna.ui.ColorPane;
import org.rsna.util.FileUtil;

public class ReportPanel extends BasePanel implements ActionListener {

	private JEditorPane out;
	private JScrollPane jsp;

	private static ReportPanel panel;

	public final static int INTERVAL = 1000;

	public static synchronized ReportPanel getInstance() {
		if (panel == null) {
			panel = new ReportPanel();
		}
		return panel;
	}

	private ReportPanel() {
		super();

		out = new JEditorPane();
		out.setContentType("text/html");
		BasePanel bp = new BasePanel();
		bp.add(out, BorderLayout.CENTER);

		DefaultCaret caret = (DefaultCaret)out.getCaret();
		caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		
		jsp = new JScrollPane();
		jsp.getVerticalScrollBar().setUnitIncrement(10);
		jsp.setViewportView(bp);
		jsp.getViewport().setBackground(Color.white);
		add(jsp, BorderLayout.CENTER);

		Timer timer = new Timer(INTERVAL, new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				panel.reload();
				panel.revalidate();
			}
		});
		timer.start();
	}

	public void reload() {
		String report = ReportService.getInstance().loadReport();
		String prevRep = out.getText();
		if (!report.equals(prevRep)) {
			out.setText(report);
			
		}
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {

	}

}
