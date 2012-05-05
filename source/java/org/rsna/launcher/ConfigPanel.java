/*---------------------------------------------------------------
*  Copyright 2012 by the Radiological Society of North America
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
import javax.swing.border.*;
import javax.swing.TransferHandler;
import javax.swing.event.*;
import javax.swing.tree.*;
import org.w3c.dom.*;

public class ConfigPanel extends BasePanel implements ActionListener {

	static final String templateFilename = "config-editor.xml";

	MenuPane menuPane;
	TreePane treePane;
	DataPane dataPane;
	JSplitPane split;
	boolean loaded = false;

	Document configXML = null;
	Document templateXML = null;
	Hashtable<String,Element> templateTable = null;
	Element server = null;
	Element pipeline = null;
	LinkedList<Element> plugins = null;
	LinkedList<Element> importServices = null;
	LinkedList<Element> processors = null;
	LinkedList<Element> storageServices = null;
	LinkedList<Element> exportServices = null;

	public ConfigPanel() {
		super();

		menuPane = new MenuPane();
		this.add(menuPane, BorderLayout.NORTH);

		//Parse the editor template file and construct the indexes of elements
		templateXML = Util.getDocument( new File(templateFilename) );
		templateTable = new Hashtable<String,Element>();
		plugins = new LinkedList<Element>();
		importServices = new LinkedList<Element>();
		processors = new LinkedList<Element>();
		storageServices = new LinkedList<Element>();
		exportServices = new LinkedList<Element>();

		if (templateXML != null) {
			Element root = templateXML.getDocumentElement();
			Node child = root.getFirstChild();
			while (child != null) {
				if (child instanceof Element) {
					Element e = (Element)child;
					String name = e.getTagName();
					if (name.equals("Server")) server = e;
					else if (name.equals("Pipeline")) pipeline = e;
					else if (name.equals("Plugin")) plugins.add(e);
					else if (name.equals("ImportService")) importServices.add(e);
					else if (name.equals("Processor")) processors.add(e);
					else if (name.equals("StorageService")) storageServices.add(e);
					else if (name.equals("ExportService")) exportServices.add(e);

					Node attrChild = e.getFirstChild();
					while (attrChild != null) {
						if (attrChild instanceof Element) {
							Element ch = (Element)attrChild;
							if (ch.getTagName().equals("attr") && ch.getAttribute("name").equals("class")) {
								String className = ch.getAttribute("default").trim();
								if (!className.equals("")) {
									templateTable.put(className, e);
									break;
								}
							}
						}
						attrChild = attrChild.getNextSibling();
					}
				}
				child = child.getNextSibling();
			}
		}

		treePane = new TreePane();
		JScrollPane jspTree = new JScrollPane();
		jspTree.getVerticalScrollBar().setUnitIncrement(12);
		jspTree.setViewportView(treePane);
		jspTree.getViewport().setBackground(Color.white);
		jspTree.getVerticalScrollBar().setUnitIncrement(30);

		dataPane = new DataPane();
		JScrollPane jspData = new JScrollPane();
		jspData.getVerticalScrollBar().setUnitIncrement(12);
		jspData.setViewportView(dataPane);
		jspData.getViewport().setBackground(Color.white);
		jspData.getVerticalScrollBar().setUnitIncrement(30);

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
				loaded = true;
			}
			catch (Exception ex) { }
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

			JMenu viewMenu = new JMenu("View");
			JMenuItem formItem = new JMenuItem("Form");
			formItem.setAccelerator( KeyStroke.getKeyStroke('F', InputEvent.CTRL_MASK) );
			formItem.addActionListener( new FormImpl() );
			viewMenu.add(formItem);

			JMenuItem xmlItem = new JMenuItem("XML");
			xmlItem.setAccelerator( KeyStroke.getKeyStroke('D', InputEvent.CTRL_MASK) );
			xmlItem.addActionListener( new XmlImpl() );
			viewMenu.add(xmlItem);

			JMenu pluginMenu = new JMenu("Plugin");
			pluginMenu.add( new JMenuItem("AuditLog") );
			pluginMenu.add( new JMenuItem("MIRC") );

			JMenu pipeMenu = new JMenu("Pipeline");
			pipeMenu.add( new JMenuItem("New") );

			JMenu stageMenu = new JMenu("Stage");
			stageMenu.add( new JMenuItem("New") );

			menuBar.add(fileMenu);
			menuBar.add(viewMenu);
			menuBar.add(pluginMenu);
			menuBar.add(pipeMenu);
			menuBar.add(stageMenu);

			this.add( menuBar );
		}

		class SaveImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				treePane.save();
			}
		}

		class FormImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				dataPane.setView(false);
			}
		}

		class XmlImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				dataPane.setView(true);
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
					XMLUserObject userObject = (XMLUserObject)object;
					dataPane.edit( userObject );
				}
			}
		}

		public void save() {
			Document doc = tree.getDocument();
			//TODO: save the configuration
			tree.clearSelection();
		}
	}

	class DataPane extends ScrollableJPanel {

		boolean viewAsXML = false;
		XMLUserObject currentObject = null;

		public DataPane() {
			super();
			setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
			setBackground(Color.white);
		}

		public void setView(boolean viewAsXML) {
			this.viewAsXML = viewAsXML;
			if (currentObject != null) edit(currentObject);
		}

		public void edit(XMLUserObject userObject) {
			currentObject = userObject;
			for (Component c : getComponents()) remove(c);
			if (viewAsXML) displayXML(userObject);
			else displayForm(userObject);
		}

		private void displayForm(XMLUserObject userObject) {
			setTrackWidth(true);
			setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
			Element template = userObject.getTemplate();
			if (template != null) {
				Node child = template.getFirstChild();
				while (child != null) {
					if (child instanceof Element) {
						Element ch = (Element)child;
						if (ch.getTagName().equals("attr")) {
							String name = ch.getAttribute("name");
							String defValue = ch.getAttribute("default");
							String options = ch.getAttribute("options").trim();
							boolean editable = !ch.getAttribute("editable").equals("no");
							String helpText = "";
							NodeList nl = ch.getElementsByTagName("comment");
							if (nl.getLength() > 0) helpText = nl.item(0).getTextContent();
							String configValue = userObject.getAttribute(name);
							if (configValue.equals("")) configValue = defValue;

							if (options.equals("")) {
								add( new TextAttrPanel(name, configValue, helpText, editable) );
							}
							else {
								//add( new ComboAttrPanel(name, configValue, options, helpText) );
								add( new ButtonAttrPanel(name, configValue, options, helpText) );
							}
							add( Box.createVerticalStrut(10) );
						}
					}
					child = child.getNextSibling();
				}
			}
			revalidate();
		}

		private void displayXML(XMLUserObject userObject) {
			setTrackWidth(false);
			setLayout(new BorderLayout());
			ColorPane cp = new ColorPane();
			cp.setEditable(false);
			cp.setScrollableTracksViewportWidth(false);
			String xml = Util.toPrettyString(userObject.element);
			cp.setText(xml);
			add(cp, BorderLayout.CENTER);
			revalidate();
		}
	}

	class ScrollableJPanel extends JPanel implements Scrollable {
		private boolean trackWidth = true;
		public ScrollableJPanel() {
			super();
		}
		public void setTrackWidth(boolean trackWidth) { this.trackWidth = trackWidth; }
		public boolean getScrollableTracksViewportHeight() { return false; }
		public boolean getScrollableTracksViewportWidth() { return trackWidth; }
		public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 10; }
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 10; }
	}

	class AttrPanel extends ScrollableJPanel implements Scrollable {
		String name;
		public AttrPanel(String name) {
			super();
			setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
			this.name = name;
			Border empty = BorderFactory.createEmptyBorder(5,30,5,5);
			Border line = BorderFactory.createLineBorder(Color.GRAY);
			TitledBorder title = BorderFactory.createTitledBorder(line, name);
			title.setTitleFont( new Font( "Monospaced", Font.BOLD, 18 ) );
			Border compound = BorderFactory.createCompoundBorder(title, empty);
			setBorder(compound);
			setBackground(Color.white);
		}
	}

	class TextAttrPanel extends AttrPanel {
		public ConfigTextField text;
		HelpPane help = null;
		public TextAttrPanel(String name, String value, String comment, boolean editable) {
			super(name);
			text = new ConfigTextField(value);
			text.setEditable(editable);
			this.add(text);
			if ((comment != null) && !comment.trim().equals("")) {
				this.add( Box.createVerticalStrut(5) );
				comment = comment.trim().replaceAll("\\s+", " ");
				help = new HelpPane(comment);
				this.add(help);
			}
			else this.add( Box.createVerticalStrut(5) );
		}
	}

	class ComboAttrPanel extends AttrPanel {
		public ConfigComboBox text;
		HelpPane help = null;
		public ComboAttrPanel(String name, String value, String options, String comment) {
			super(name);
			String[] ops = options.split("\\|");
			int idx = 0;
			for (int i=0; i<ops.length; i++) {
				if (value.equals(ops[i])) {
					idx = i;
					break;
				}
			}
			text = new ConfigComboBox(ops, idx);
			this.add(text);
			if ((comment != null) && !comment.trim().equals("")) {
				this.add( Box.createVerticalStrut(5) );
				comment = comment.trim().replaceAll("\\s+", " ");
				help = new HelpPane(comment);
				this.add(help);
			}
		}
	}

	class ButtonAttrPanel extends AttrPanel {
		public ConfigButtonGroup text;
		HelpPane help = null;
		public ButtonAttrPanel(String name, String value, String options, String comment) {
			super(name);
			String[] ops = options.split("\\|");
			int idx = 0;
			for (int i=0; i<ops.length; i++) {
				if (value.equals(ops[i])) {
					idx = i;
					break;
				}
			}
			text = new ConfigButtonGroup(ops, idx);
			this.add(text);
			if ((comment != null) && !comment.trim().equals("")) {
				this.add( Box.createVerticalStrut(5) );
				comment = comment.trim().replaceAll("\\s+", " ");
				help = new HelpPane(comment);
				this.add(help);
			}
		}
	}

	class ConfigTextField extends JTextField {
		public ConfigTextField(String s) {
			super();
			setText(s);
			setFont( new Font( "Monospaced", Font.BOLD, 12 ) );
			setBackground(Color.white);
		}
	}

	class ConfigComboBox extends JComboBox implements Scrollable {
		public ConfigComboBox(String[] values, int selectedIndex) {
			super(values);
			setSelectedIndex(selectedIndex);
			setFont( new Font( "Monospaced", Font.BOLD, 12 ) );
			setBackground(Color.white);
			setEditable(false);
		}
		public boolean getScrollableTracksViewportHeight() { return false; }
		public boolean getScrollableTracksViewportWidth() { return true; }
		public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 200; }
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 40; }
	}

	class ConfigButtonGroup extends JPanel {
		ButtonGroup group;
		public ConfigButtonGroup(String[] values, int selectedIndex) {
			super();
			setLayout( new RowLayout(10, 0) );
			setBackground(Color.white);
			group = new ButtonGroup();
			for (int i=0; i<values.length; i++) {
				JRadioButton jrb = new JRadioButton( values[i] );
				jrb.setSelected( (i==selectedIndex) );
				jrb.setFont( new Font( "Monospaced", Font.BOLD, 12 ) );
				jrb.setBackground(Color.white);
				group.add(jrb);
				add(jrb);
				add(RowLayout.crlf());
			}
		}
	}

	class HelpPane extends JTextPane implements Scrollable {
		public HelpPane(String s) {
			super();
			setText(s);
			setFont( new Font( "SansSerif", Font.PLAIN, 12 ) );
			setEditable(false);
			setForeground(Color.gray);
		}
		public boolean getScrollableTracksViewportHeight() { return false; }
		public boolean getScrollableTracksViewportWidth() { return true; }
		public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 200; }
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 40; }
	}


	//The User Object that encapsulates the CTP configuration elements
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

		public Element getTemplate() {
			return (isServer ? server : (isPipeline ? pipeline : templateTable.get(className)));
		}

		public String getAttribute(String name) {
			return element.getAttribute(name);
		}

		public boolean isDragable() {
			return !isConfiguration && !isServer && !isPipeline && !isChild;
		}

		public String toString() {
			return tag;
		}
	}

	//*****************************************************************
	//The rest of the source code implements the tree for the left pane
	//*****************************************************************
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
