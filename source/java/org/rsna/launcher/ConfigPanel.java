/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import org.w3c.dom.*;

public class ConfigPanel extends BasePanel implements ActionListener {

	MenuPane menuPane;
	TreePane treePane;
	DataPane dataPane;
	JSplitPane split;
	boolean loaded = false;

	Document configXML = null;

	public ConfigPanel() {
		super();

		menuPane = new MenuPane();
		this.add(menuPane, BorderLayout.NORTH);

		treePane = new TreePane();
		JScrollPane jspTree = new JScrollPane();
		jspTree.getVerticalScrollBar().setUnitIncrement(12);
		jspTree.setViewportView(treePane);

		dataPane = new DataPane();
		JScrollPane jspData = new JScrollPane();
		jspData.getVerticalScrollBar().setUnitIncrement(12);
		jspData.setViewportView(dataPane);

		split = new JSplitPane();
		split.setContinuousLayout(true);
		split.setResizeWeight(0.1D);
		split.setLeftComponent(jspTree);
		split.setRightComponent(jspData);

		this.add(split, BorderLayout.CENTER);
	}

	public void load() {
		if (!loaded) {
			try {
				configXML = Util.getDocument( new File("config.xml") );
				treePane.load(configXML);
			}
			catch (Exception ex) { }
			loaded = true;
		}
	}

	public void actionPerformed(ActionEvent event) {
		/*
		if (event.getSource().equals(wrap)) {
			out.setScrollableTracksViewportWidth( wrap.isSelected() );
			jsp.invalidate();
			jsp.validate();
		}
		else if (event.getSource().equals(refresh)) {
			reload();
		}
		else if (event.getSource().equals(delete)) {
			File logs = new File("logs");
			Util.deleteAll(logs);
			reload();
		}
		*/
	}

	class MenuPane extends BasePanel {

		public MenuPane() {
			super();
			setLayout( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );

			JMenuBar menuBar = new JMenuBar();
			menuBar.setBackground( bgColor );

			JMenu fileMenu = new JMenu("File");
			JMenuItem saveItem = new JMenuItem("Save");
			saveItem.setAccelerator( KeyStroke.getKeyStroke('S', InputEvent.CTRL_MASK) );
			saveItem.addActionListener( new SaveImpl() );
			fileMenu.add(saveItem);

			JMenu pluginMenu = new JMenu("Plugin");
			pluginMenu.add( new JMenuItem("AuditLog") );
			pluginMenu.add( new JMenuItem("MIRC") );

			JMenu pipeMenu = new JMenu("Pipeline");
			pipeMenu.add( new JMenuItem("New") );

			JMenu stageMenu = new JMenu("Stage");
			stageMenu.add( new JMenuItem("New") );

			menuBar.add(fileMenu);
			menuBar.add(pluginMenu);
			menuBar.add(pipeMenu);
			menuBar.add(stageMenu);

			this.add( menuBar );
		}

		class SaveImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				treePane.setText();
			}
		}
	}

	class TreePane extends BasePanel implements TreeSelectionListener {

		Document doc = null;
		Element root =  null;
		JTree tree = null;
		XMLTreeNode troot = null;
		TreeModel model = null;

		public TreePane() {
			super();
		}

		public void load(Document doc) {
			this.doc = doc;
			root = doc.getDocumentElement();
			troot = new XMLTreeNode(root);
			model = new DefaultTreeModel(troot);
			tree = new JTree(model);
			addChildren(troot, root);
			for (Component c : getComponents()) remove(c);
			this.add(tree);
			expandAll();
			TreeSelectionModel tsm = tree.getSelectionModel();
			tsm.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
			tsm.addTreeSelectionListener(this);
			tree.setSelectionModel(tsm);
		}

		public void valueChanged(TreeSelectionEvent event) {
			XMLTreeNode tn = (XMLTreeNode)tree.getLastSelectedPathComponent();
			String name = (String)tn.getUserObject();
			dataPane.setText(name);
		}

		public void setText() {
			Document doc = Util.getDocument();
			appendTreeNode(doc, troot);
			dataPane.setText( Util.toPrettyString( doc.getDocumentElement() ) );
			tree.clearSelection();
		}

		private void appendTreeNode(Node node, XMLTreeNode tnode) {
			Document doc;
			if (node instanceof Document) doc = (Document)node;
			else doc = node.getOwnerDocument();
			Element el = (Element)doc.importNode( tnode.element, false );
			node.appendChild(el);
			try {
				TreeNode child = tnode.getFirstChild();
				while (child != null) {
					XMLTreeNode tchild = (XMLTreeNode)child;
					appendTreeNode( el, tchild );
					child = tchild.getNextSibling();
				}
			}
			catch (Exception noKids) {  }
		}

		private void expandAll() {
			for (int i=0; i<tree.getRowCount(); i++) {
				if (tree.isCollapsed(i)) tree.expandRow(i);
			}
		}

		private void addChildren(XMLTreeNode tnode, Element el) {
			Node child = el.getFirstChild();
			while (child != null) {
				if (child instanceof Element) {
					Element cel = (Element)child;
					XMLTreeNode cnode = new XMLTreeNode( (Element)child );
					tnode.add(cnode);
					addChildren(cnode, cel);
				}
				child = child.getNextSibling();
			}
		}

		class XMLTreeNode extends DefaultMutableTreeNode {
			public Element element;
			public String className;

			public XMLTreeNode(Element element) {
				super();
				this.element = element;
				this.className = element.getAttribute("class");
				String tag = element.getTagName();
				if (tag.equals("Plugin")) {
					String name = className.substring(className.lastIndexOf(".")+1);
					tag += " ["+name+"]";
				}
				else if (tag.equals("Pipeline")) {
					tag += " ["+element.getAttribute("name")+"]";
				}
				setUserObject(tag);
			}
		}
	}

	class DataPane extends JPanel {
		ColorPane cp;

		public DataPane() {
			super();
			setLayout( new BorderLayout() );
			cp = new ColorPane();
			add( cp, BorderLayout.CENTER );
			cp.setText("quack");
		}

		public void setText(String text) {
			for (Component c : getComponents()) remove(c);
			setLayout( new BorderLayout() );
			add( cp, BorderLayout.CENTER );
			cp.setText(text);
			cp.setCaretPosition(0);
		}
	}


}
