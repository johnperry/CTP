/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import javax.swing.*;
import javax.swing.TransferHandler;
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
		Element root = null;
		XMLTree tree = null;
		TreeDragSource dragSource;
		TreeDropTarget dropTarget;

		public TreePane() {
			super();
		}

		public void load(Document doc) {
			this.doc = doc;
			root = doc.getDocumentElement();
			tree = new XMLTree(root);
			tree.getSelectionModel().addTreeSelectionListener(this);
			for (Component c : getComponents()) remove(c);
			this.add(tree);
			tree.expandAll();
			dragSource = new TreeDragSource(tree, DnDConstants.ACTION_COPY_OR_MOVE);
			dropTarget = new TreeDropTarget(tree);
		}

		public void valueChanged(TreeSelectionEvent event) {
			DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
			if (treeNode != null) {
				Object object = treeNode.getUserObject();
				if ((object != null) && (object instanceof XMLUserObject)) {
					XMLUserObject xmlUserObject = (XMLUserObject)object;
					Element el = (Element)xmlUserObject.element.cloneNode(false);
					dataPane.setText( Util.toPrettyString( el ) );
				}
			}
		}

		public void setText() {
			Document doc = tree.getDocument();
			dataPane.setText( Util.toPrettyString( doc.getDocumentElement() ) );
			tree.clearSelection();
		}
	}

	class DataPane extends JPanel {
		ColorPane cp;

		public DataPane() {
			super();
			setLayout( new BorderLayout() );
			cp = new ColorPane();
			add( cp, BorderLayout.CENTER );
			cp.setText("");
		}

		public void setText(String text) {
			for (Component c : getComponents()) remove(c);
			setLayout( new BorderLayout() );
			add( cp, BorderLayout.CENTER );
			cp.setText(text);
			cp.setCaretPosition(0);
		}
	}

	//The User Object that encapsultates the CTP configuration elements
	class XMLUserObject {

		public Element element;
		public String className;
		public String tag;

		public boolean isConfiguration = false;
		public boolean isServer = false;
		public boolean isPlugin = false;
		public boolean isPipeline = false;
		public boolean isStage = false;
		public boolean isChild = false;

		public XMLUserObject(Element element) {
			super();
			this.element = element;
			this.className = element.getAttribute("class");
			tag = element.getTagName();

			isConfiguration = tag.equals("Configuration");
			isServer = tag.equals("Server");
			isPlugin = tag.equals("Plugin");
			isPipeline = tag.equals("Pipeline");
			if (!isConfiguration && !isServer && !isPlugin && !isPipeline) {
				isStage = element.getParentNode().getNodeName().equals("Pipeline");
				isChild = !isStage;
			}

			if (tag.equals("Plugin")) {
				String name = className.substring(className.lastIndexOf(".")+1);
				tag += " ["+name+"]";
			}
			else if (tag.equals("Pipeline")) {
				tag += " ["+element.getAttribute("name")+"]";
			}
		}

		public boolean isDragable() {
			return !isConfiguration && !isServer && !isPipeline;
		}

		public String toString() {
			return tag;
		}
	}

	class XMLTree extends JTree {

		DefaultTreeModel model = null;
		DefaultMutableTreeNode troot = null;

		public XMLTree(Element xmlRoot) {
			super();
			XMLUserObject xmlUserObject = new XMLUserObject(xmlRoot);
			troot = new DefaultMutableTreeNode(xmlUserObject);
			model = new DefaultTreeModel(troot);
			setModel(model);
			setScrollsOnExpand(true);
			addChildren(troot, xmlRoot);

			TreeSelectionModel tsm = getSelectionModel();
			tsm.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		}

		private void addChildren(DefaultMutableTreeNode tnode, Element el) {
			Node child = el.getFirstChild();
			while (child != null) {
				if (child instanceof Element) {
					Element cel = (Element)child;
					XMLUserObject xmlUserObject = new XMLUserObject(cel);
					DefaultMutableTreeNode cnode = new DefaultMutableTreeNode(xmlUserObject);
					tnode.add(cnode);
					addChildren(cnode, cel);
				}
				child = child.getNextSibling();
			}
		}

		public Document getDocument() {
			Document doc = Util.getDocument();
			appendTreeNode(doc, troot);
			return doc;
		}

		private void appendTreeNode(Node node, DefaultMutableTreeNode tnode) {
			Document doc;
			if (node instanceof Document) doc = (Document)node;
			else doc = node.getOwnerDocument();
			XMLUserObject xmlUserObject = (XMLUserObject)tnode.getUserObject();
			Element el = (Element)doc.importNode( xmlUserObject.element, false );
			node.appendChild(el);
			try {
				TreeNode child = tnode.getFirstChild();
				while (child != null) {
					DefaultMutableTreeNode tchild = (DefaultMutableTreeNode)child;
					appendTreeNode( el, tchild );
					child = tchild.getNextSibling();
				}
			}
			catch (Exception noKids) { }
		}

		public void expandAll() {
			for (int i=0; i<getRowCount(); i++) {
				if (isCollapsed(i)) expandRow(i);
			}
		}

		public void collapseAll() {
			for (int i=getRowCount(); i>=0; i--) {
				collapseRow(i);
			}
		}
	}

	class TreeDragSource implements DragSourceListener, DragGestureListener {

		DragSource source;
		DragGestureRecognizer recognizer;
		TransferableObject transferable;
		DefaultMutableTreeNode oldNode;
		JTree sourceTree;

		public TreeDragSource(JTree tree, int actions) {
			sourceTree = tree;
			source = new DragSource();
			recognizer = source.createDefaultDragGestureRecognizer(sourceTree, actions, this);
		}

		//----------------------
		//Drag Gesture Handler
		//----------------------
		public void dragGestureRecognized(DragGestureEvent dge) {
			TreePath path = sourceTree.getSelectionPath();
			if ((path == null) || (path.getPathCount() <= 1)) {
				return;
			}
			oldNode = (DefaultMutableTreeNode) path.getLastPathComponent();
			XMLUserObject xmlUserObject = (XMLUserObject)oldNode.getUserObject();
			if (xmlUserObject.isDragable()) {
				transferable = new TransferableObject(xmlUserObject);
				source.startDrag(dge, DragSource.DefaultMoveDrop, transferable, this);
			}
		}

		//----------------------
		//Drag Event Handlers
		//----------------------
		public void dragEnter(DragSourceDragEvent dsde) {
			dragOver(dsde);
		}

		public void dragOver(DragSourceDragEvent dsde) {
			DragSourceContext dsc = dsde.getDragSourceContext();
			dsc.setCursor(DragSource.DefaultMoveDrop);
		}

		public void dragExit(DragSourceEvent dse) {
			DragSourceContext dsc = dse.getDragSourceContext();
			dsc.setCursor(DragSource.DefaultMoveNoDrop);
		}

		public void dropActionChanged(DragSourceDragEvent dsde) { }

		public void dragDropEnd(DragSourceDropEvent dsde) {
			if (dsde.getDropSuccess() && (dsde.getDropAction() == DnDConstants.ACTION_MOVE)) {
				((DefaultTreeModel) sourceTree.getModel()).removeNodeFromParent(oldNode);
			}
		}
	}

	class TreeDropTarget implements DropTargetListener {

		DropTarget target;
		JTree targetTree;

		public TreeDropTarget(JTree tree) {
			targetTree = tree;
			target = new DropTarget(targetTree, this);
		}

		//----------------------
		//Drop Event Handlers
		//----------------------
		private boolean okToDrop(Transferable tr, DefaultMutableTreeNode target) {
			XMLUserObject dest = (XMLUserObject)target.getUserObject();
			DataFlavor[] flavors = tr.getTransferDataFlavors();
			for (int i = 0; i < flavors.length; i++) {
				if (tr.isDataFlavorSupported(flavors[i])) {
					try {
						XMLUserObject src = (XMLUserObject)tr.getTransferData(flavors[i]);
						if (src.isStage && dest.isPipeline) return true;
						if (src.isStage && dest.isStage) return true;
						if (src.isPlugin && dest.isPlugin) return true;
						return false;
					}
					catch (Exception skip) { }
				}
			}
			return false;
		}

		private DefaultMutableTreeNode getNodeForEvent(DropTargetDragEvent dtde) {
			Point p = dtde.getLocation();
			DropTargetContext dtc = dtde.getDropTargetContext();
			JTree tree = (JTree) dtc.getComponent();
			TreePath path = tree.getClosestPathForLocation(p.x, p.y);
			return (DefaultMutableTreeNode) path.getLastPathComponent();
		}

		private DefaultMutableTreeNode getNodeForEvent(DropTargetDropEvent dtde) {
			Point p = dtde.getLocation();
			DropTargetContext dtc = dtde.getDropTargetContext();
			JTree tree = (JTree) dtc.getComponent();
			TreePath path = tree.getClosestPathForLocation(p.x, p.y);
			return (DefaultMutableTreeNode) path.getLastPathComponent();
		}

		public void dragEnter(DropTargetDragEvent dtde) {
			dragOver(dtde);
		}

		public void dragOver(DropTargetDragEvent dtde) {
			DefaultMutableTreeNode node = getNodeForEvent(dtde);
			XMLUserObject xmlUserObject = (XMLUserObject)node.getUserObject();
			if (okToDrop(dtde.getTransferable(), node)) {
				dtde.acceptDrag(dtde.getDropAction());
			}
			else dtde.rejectDrag();
		}

		public void dragExit(DropTargetEvent dte) { }

		public void dropActionChanged(DropTargetDragEvent dtde) { }

		public void drop(DropTargetDropEvent dtde) {
			DefaultMutableTreeNode targetNode = getNodeForEvent(dtde);
			XMLUserObject targetUO = (XMLUserObject)targetNode.getUserObject();
			Transferable tr = dtde.getTransferable();
			DataFlavor[] flavors = tr.getTransferDataFlavors();
			try {
				for (int i = 0; i < flavors.length; i++) {
					if (tr.isDataFlavorSupported(flavors[i])) {
						dtde.acceptDrop(dtde.getDropAction());

						XMLUserObject sourceUO = (XMLUserObject)tr.getTransferData(flavors[i]);
						DefaultMutableTreeNode sourceNode = new DefaultMutableTreeNode(sourceUO);

						DefaultTreeModel model = (DefaultTreeModel) targetTree.getModel();

						int index = 0;
						if (sourceUO.isStage && targetUO.isPipeline) {
							index = model.getChildCount(targetNode);
							model.insertNodeInto(sourceNode, targetNode, index);
						}
						else if (sourceUO.isStage && targetUO.isStage) {
							DefaultMutableTreeNode targetParentNode = (DefaultMutableTreeNode)targetNode.getParent();
							index = targetParentNode.getIndex(targetNode);
							model.insertNodeInto(sourceNode, targetParentNode, index);
						}
						else if (sourceUO.isPlugin && targetUO.isPlugin) {
							DefaultMutableTreeNode targetParentNode = (DefaultMutableTreeNode)targetNode.getParent();
							index = targetParentNode.getIndex(targetNode);
							model.insertNodeInto(sourceNode, targetParentNode, index);
						}
						else {
							dtde.rejectDrop();
							return;
						}
						dtde.dropComplete(true);
						return;
					}
				}
				dtde.rejectDrop();
			}
			catch (Exception e) {
				e.printStackTrace();
				dtde.rejectDrop();
			}
		}
	}

	public static DataFlavor XML_FLAVOR = new DataFlavor(XMLUserObject.class, "XML_FLAVOR");
	class TransferableObject implements Transferable {

		DataFlavor flavors[] = { XML_FLAVOR };
		XMLUserObject object;

		public TransferableObject(XMLUserObject object) {
			this.object = object;
		}

		public synchronized DataFlavor[] getTransferDataFlavors() {
			return flavors;
		}

		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return (flavor.getRepresentationClass() == XMLUserObject.class);
		}

		public synchronized Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (isDataFlavorSupported(flavor)) return object;
			throw new UnsupportedFlavorException(flavor);
		}
	}
}
