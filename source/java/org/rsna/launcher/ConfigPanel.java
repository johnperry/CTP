/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.launcher;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.TransferHandler;
import javax.swing.tree.*;

import com.codeminders.demo.GoogleAPIClient;
import com.codeminders.demo.GoogleAPIClientFactory;
import org.apache.log4j.Logger;
import org.rsna.ui.ColorPane;
import org.rsna.ui.RowLayout;
import org.rsna.util.BrowserUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.*;

public class ConfigPanel extends BasePanel {

	static final String templateFilename = "ConfigurationTemplates.xml";
	static Logger logger = Logger.getLogger(ConfigPanel.class);
	MenuPane menuPane;
	TreePane treePane;
	JScrollPane jspTree;
	DataPane dataPane;
	JScrollPane jspData;
	JSplitPane split;
	boolean loaded = false;

	String savedXML = "";

	Hashtable<String,Template> templateTable = new Hashtable<String,Template>();
	Template server = null;
	Template pipeline = null;
	LinkedList<Template> plugins = new LinkedList<Template>();
	LinkedList<Template> importServices = new LinkedList<Template>();
	LinkedList<Template> processors = new LinkedList<Template>();
	LinkedList<Template> storageServices = new LinkedList<Template>();
	LinkedList<Template> exportServices = new LinkedList<Template>();
	LinkedList<Element> attachedChildren = new LinkedList<Element>();
	Hashtable<String,Element> standardPipelines = new Hashtable<String,Element>();
	Hashtable<String,String> defaultHelpText = new Hashtable<String,String>();

	static ConfigPanel configPanel = null;

	public static synchronized ConfigPanel getInstance() {
		if (configPanel == null) configPanel = new ConfigPanel();
		return configPanel;
	}

	protected ConfigPanel() {
		super();

		loadTemplates();

		menuPane = new MenuPane();
		this.add(menuPane, BorderLayout.NORTH);

		treePane = new TreePane();
		jspTree = new JScrollPane();
		jspTree.getVerticalScrollBar().setUnitIncrement(12);
		jspTree.setViewportView(treePane);
		jspTree.getViewport().setBackground(Color.white);
		jspTree.getVerticalScrollBar().setUnitIncrement(30);

		jspData = new JScrollPane();
		jspData.getVerticalScrollBar().setUnitIncrement(12);
		jspData.getViewport().setBackground(Color.white);
		jspData.getVerticalScrollBar().setUnitIncrement(30);
		dataPane = new DataPane();
		jspData.setViewportView(dataPane);

		split = new JSplitPane();
		split.setContinuousLayout(true);
		split.setResizeWeight(0.1D);
		split.setLeftComponent(jspTree);
		split.setRightComponent(jspData);

		this.add(split, BorderLayout.CENTER);
		split.setDividerLocation(200);
		menuPane.setEnables();

		load();
	}

	public void load() {
		if (!loaded) {
			try {
				Document configXML = XmlUtil.getDocument( new File("config.xml") );
				loaded = treePane.load(configXML);
				savedXML = XmlUtil.toPrettyString(treePane.getXML());
			}
			catch (Exception ex) { }
		}
	}

	public boolean hasChanged() {
		return !XmlUtil.toPrettyString(treePane.getXML()).equals(savedXML);
	}

	public void showHelp() {
		Configuration config = Configuration.getInstance();
		if (!config.hasPipelines()) {
			JOptionPane.showMessageDialog(
						this,
						"The configuration currently has no pipelines.\n"
						+"You can add a pipeline by selecting a pre-configured\n"
						+"one from the Pipeline menu, or you can construct one\n"
						+"from scratch by typing ctrl-N and then adding pipeline\n"
						+"stages from the other menus.\n",
						"No Pipelines",
						JOptionPane.PLAIN_MESSAGE );
		}
		BrowserUtil.openURL( "http://mircwiki.rsna.org/index.php?title=The_CTP_Launcher_Configuration_Editor" );
	}

	//Class to encapsulate a template element
	class Template implements Comparable<Template> {
		Element template;
		Hashtable<String,Element> attrs;
		Hashtable<String,Template> children;
		public Template(Element template) {
			this.template = template;
			attrs = new Hashtable<String,Element>();
			children = new Hashtable<String,Template>();
			Node n = template.getFirstChild();
			while (n != null) {
				if (n instanceof Element) {
					Element e = (Element)n;
					String name = e.getAttribute("name");
					String tagName = e.getTagName();
					if (tagName.equals("attr")) attrs.put(name, e);
					else if (tagName.equals("child")) children.put(name, new Template(e));
				}
				n = n.getNextSibling();
			}
		}
		public void attachChild(Element child) {
			children.put(child.getAttribute("name"), new Template(child));
		}
		public String getName() {
			return template.getTagName();
		}
		public String getClassName() {
			return getAttrValue("class", "default");
		}
		public Element getTemplateElement() {
			return template;
		}
		public Element getAttrElement(String name) {
			return attrs.get(name);
		}
		public String getAttrValue(String attrName, String attrValueName) {
			Element e = attrs.get(attrName);
			if (e != null) {
				return e.getAttribute(attrValueName).trim();
			}
			return "";
		}
		public String[] getChildNames(){
			String[] names = new String[children.size()];
			return children.keySet().toArray(names);
		}
		public Template getChildTemplate(String name) {
			return children.get(name);
		}
		public Element getXML(String parentName) {
			try {
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement(parentName);

				Element element;
				String tagName = template.getTagName();
				if (tagName.equals("Plugin") || tagName.equals("Pipeline")) {
					element = doc.createElement(tagName);
				}
				else if (tagName.equals("child")) {
					element = doc.createElement(template.getAttribute("name"));
				}
				else {
					element = doc.createElement(getAttrValue("name", "default"));
				}

				root.appendChild(element);
				Node node = template.getFirstChild();
				while (node != null) {
					if ((node instanceof Element) && node.getNodeName().equals("attr")) {
						Element attr = (Element)node;
						String name = attr.getAttribute("name");
						if (!name.equals("name")) {
							String value = attr.getAttribute("default");
							element.setAttribute(name, value);
						}
					}
					node = node.getNextSibling();
				}
				return element;
			}
			catch (Exception ex) { ex.printStackTrace(); return null; }
		}
		public int compareTo(Template t) {
			String thisCName = this.getClassName();
			thisCName = thisCName.substring( thisCName.lastIndexOf(".") + 1 );
			String thatCName = t.getClassName();
			thatCName = thatCName.substring( thatCName.lastIndexOf(".") + 1 );
			return thisCName.compareTo(thatCName);
		}
	}

	private void loadTemplates() {
		File libraries = new File("libraries");
		loadTemplates(libraries);

		//Attach any children to their parents
		for (Element e : attachedChildren) {
			NodeList parentNodes = e.getElementsByTagName("parent");
			NodeList childNodes = e.getElementsByTagName("child");
			for (int p=0; p<parentNodes.getLength(); p++) {
				Element parent = (Element)parentNodes.item(p);
				for (int c=0; c<childNodes.getLength(); c++) {
					Element child = (Element)childNodes.item(c);
					Template t = templateTable.get(parent.getAttribute("class"));
					t.attachChild(child);
				}
			}
		}
	}

	private void loadTemplates(File file) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (File f : files) loadTemplates(f);
		}
		else {
			String name = file.getName().toLowerCase();
			if (name.endsWith(".jar")) {
				InputStream in = null;
				try {
					ZipFile zipFile = new ZipFile(file);
					ZipEntry entry = zipFile.getEntry(templateFilename);
					if (entry != null) {
						in = new BufferedInputStream(zipFile.getInputStream(entry));
						loadTemplates(XmlUtil.getDocument(in));
					}
				}
				catch (Exception skip) { }
				finally {
					try { if (in != null) in.close(); }
					catch (Exception ignore) { }
				}
			}
		}
	}
	
	private void loadTemplates(Document templateXML) throws Exception {
		if (templateXML != null) {
			Element root = templateXML.getDocumentElement();
			Node child = root.getFirstChild();
			while (child != null) {
				if (child instanceof Element) {
					Element e = (Element)child;
					String name = e.getTagName();
					if (name.equals("Components")) loadComponents(e);
					else if (name.equals("StandardPipelines")) loadStandardPipelines(e);
					else if (name.equals("DefaultHelpText")) loadDefaultHelpText(e);
				}
				child = child.getNextSibling();
			}
		}
	}

	private void loadComponents(Element components) {
		Node child = components.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				Element e = (Element)child;
				if (e.getTagName().equals("AttachedChild")) {
					//Attached children have to be attached to
					//their parents after all parents have been
					//loaded, so just save the element for now.
					attachedChildren.add(e);
				}
				else {
					Template template = new Template(e);
					String name = template.getName();

					if (name.equals("Server")) server = template;
					else if (name.equals("Pipeline")) pipeline = template;
					else if (name.equals("Plugin")) plugins.add(template);
					else if (name.equals("ImportService")) importServices.add(template);
					else if (name.equals("Processor")) processors.add(template);
					else if (name.equals("StorageService")) storageServices.add(template);
					else if (name.equals("ExportService")) exportServices.add(template);

					//Store the element in the templateTable, indexed by the class name
					String className = template.getAttrValue("class", "default");
					if (!className.equals("")) {
						templateTable.put(className, template);
					}
				}
			}
			child = child.getNextSibling();
		}
		//Sort the component lists
		plugins = sort(plugins);
		importServices = sort(importServices);
		processors = sort(processors);
		storageServices = sort(storageServices);
		exportServices = sort(exportServices);
	}

	private LinkedList<Template> sort(LinkedList<Template> list) {
		Template[] array = new Template[list.size()];
		array = list.toArray(array);
		Arrays.sort(array);
		list = new LinkedList<Template>();
		for (Template t : array) list.add(t);
		return list;
	}

	private void loadStandardPipelines(Element sp) {
		Node child = sp.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				Element pipe = (Element)child;
				String name = pipe.getAttribute("name");
				if (!name.equals("")) standardPipelines.put(name, pipe);
			}
			child = child.getNextSibling();
		}
	}

	private void loadDefaultHelpText(Element dht) {
		Node child = dht.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				Element attr = (Element)child;
				String attrName = attr.getTagName();
				String attrValue = attr.getTextContent().trim();
				if (!attrValue.equals("")) defaultHelpText.put(attrName, attrValue);
			}
			child = child.getNextSibling();
		}
	}
	//******** End of template loading ********

	class MenuPane extends BasePanel {

		JMenu childrenMenu;
		JMenuItem formItem;
		JMenuItem xmlItem;

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
			JMenuItem deleteBackupsItem = new JMenuItem("Delete backups");
			deleteBackupsItem.addActionListener( new DeleteBackupsImpl() );
			fileMenu.add(deleteBackupsItem);

			JMenu editMenu = new JMenu("Edit");
			JMenuItem deleteItem = new JMenuItem("Remove");
			deleteItem.setAccelerator( KeyStroke.getKeyStroke('R', InputEvent.CTRL_MASK) );
			deleteItem.addActionListener( new DeleteImpl() );
			editMenu.add(deleteItem);

			JMenu viewMenu = new JMenu("View");
			formItem = new JMenuItem("Form");
			formItem.setAccelerator( KeyStroke.getKeyStroke('F', InputEvent.CTRL_MASK) );
			formItem.addActionListener( new FormImpl() );
			viewMenu.add(formItem);
			xmlItem = new JMenuItem("XML");
			xmlItem.setAccelerator( KeyStroke.getKeyStroke('D', InputEvent.CTRL_MASK) );
			xmlItem.addActionListener( new XmlImpl() );
			viewMenu.add(xmlItem);
			JMenuItem expandItem = new JMenuItem("Expand all");
			expandItem.setAccelerator( KeyStroke.getKeyStroke('E', InputEvent.CTRL_MASK) );
			expandItem.addActionListener( new ExpandImpl() );
			viewMenu.add(expandItem);
			JMenuItem collapseItem = new JMenuItem("Collapse all");
			collapseItem.setAccelerator( KeyStroke.getKeyStroke('W', InputEvent.CTRL_MASK) );
			collapseItem.addActionListener( new CollapseImpl() );
			viewMenu.add(collapseItem);

			JMenu pluginMenu = new JMenu("Plugin");
			ComponentImpl impl = new ComponentImpl("Configuration");
			for (Template template : plugins) {
				ComponentMenuItem item = new ComponentMenuItem(template.getClassName());
				item.addActionListener(impl);
				pluginMenu.add(item);
			}

			JMenu pipeMenu = new JMenu("Pipeline");
			JMenuItem pipeItem = new JMenuItem("New Pipeline");
			pipeItem.setAccelerator( KeyStroke.getKeyStroke('N', InputEvent.CTRL_MASK) );
			PipelineImpl pipeImpl = new PipelineImpl();
			pipeItem.addActionListener(pipeImpl);
			pipeMenu.add(pipeItem);

			String[] pipeNames = new String[standardPipelines.size()];
			pipeNames = standardPipelines.keySet().toArray(pipeNames);
			Arrays.sort(pipeNames);
			StandardPipelineImpl spImpl = new StandardPipelineImpl();
			for (String name : pipeNames) {
				JMenuItem item = new JMenuItem(name);
				item.addActionListener(spImpl);
				pipeMenu.add(item);
			}

			JMenu importServiceMenu = new JMenu("ImportService");
			impl = new ComponentImpl("Pipeline");
			for (Template template : importServices) {
				ComponentMenuItem item = new ComponentMenuItem(template.getClassName());
				item.addActionListener(impl);
				importServiceMenu.add(item);
			}

			JMenu processorMenu = new JMenu("Processor");
			for (Template template : processors) {
				ComponentMenuItem item = new ComponentMenuItem(template.getClassName());
				item.addActionListener(impl);
				processorMenu.add(item);
			}

			JMenu storageServiceMenu = new JMenu("StorageService");
			for (Template template : storageServices) {
				ComponentMenuItem item = new ComponentMenuItem(template.getClassName());
				item.addActionListener(impl);
				storageServiceMenu.add(item);
			}

			JMenu exportServiceMenu = new JMenu("ExportService");
			for (Template template : exportServices) {
				ComponentMenuItem item = new ComponentMenuItem(template.getClassName());
				item.addActionListener(impl);
				exportServiceMenu.add(item);
			}

			childrenMenu = new JMenu("Children");

			JMenu authorizationMenu = new JMenu("Authorization");
			JMenuItem googleAuthItem = new JMenuItem("Login with Google");
			googleAuthItem.addActionListener(new AuthImpl());
			authorizationMenu.add(googleAuthItem);

			JMenu helpMenu = new JMenu("Help");
			JMenuItem helpItem = new JMenuItem("Configuration Editor Instructions");
			helpItem.setAccelerator( KeyStroke.getKeyStroke('H', InputEvent.CTRL_MASK) );
			HelpImpl helpImpl = new HelpImpl();
			helpItem.addActionListener(helpImpl);
			helpMenu.add(helpItem);
			JMenuItem ctphelpItem = new JMenuItem("Top-level CTP Article");
			ctphelpItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event) {
					BrowserUtil.openURL( "http://mircwiki.rsna.org/index.php?title=CTP-The_RSNA_Clinical_Trial_Processor" );
				}
			});
			helpMenu.add(ctphelpItem);
			JMenuItem ctpArticlesItem = new JMenuItem("List of all CTP Articles");
			ctpArticlesItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event) {
					BrowserUtil.openURL( "http://mircwiki.rsna.org/index.php?title=CTP_Articles" );
				}
			});
			helpMenu.add(ctpArticlesItem);

			menuBar.add(fileMenu);
			menuBar.add(editMenu);
			menuBar.add(viewMenu);
			menuBar.add(pluginMenu);
			menuBar.add(pipeMenu);
			menuBar.add(importServiceMenu);
			menuBar.add(processorMenu);
			menuBar.add(storageServiceMenu);
			menuBar.add(exportServiceMenu);
			menuBar.add(childrenMenu);
			menuBar.add(authorizationMenu);
			menuBar.add(helpMenu);

			this.add( menuBar );
		}

		public void setEnables() {
			boolean viewIsXML = dataPane.viewIsXML();
			formItem.setEnabled(viewIsXML);
			xmlItem.setEnabled(!viewIsXML);
		}

		class ComponentMenuItem extends JMenuItem {
			String className;
			public ComponentMenuItem(String className) {
				super();
				this.className = className;
				String name = className.substring( className.lastIndexOf(".")+1 );
				setText(name);
			}
			public String getClassName() {
				return className;
			}
		}

		public void setChildrenMenu(String parentName, Template parentTemplate) {
			childrenMenu.removeAll();
			if (parentTemplate != null) {
				ChildImpl impl = new ChildImpl(parentName, parentTemplate);
				String[] childNames = parentTemplate.getChildNames();
				childrenMenu.setEnabled( (childNames.length > 0) );
				for (String name : childNames) {
					ComponentMenuItem item = new ComponentMenuItem(name);
					item.addActionListener(impl);
					childrenMenu.add(item);
				}
			}
		}

		class SaveImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				Element config = treePane.getXML();
				if (checkConfig(config)) {
					String xml = XmlUtil.toPrettyString(config);
					File configFile = new File("config.xml");
					backupTarget(configFile);
					try {
						FileUtil.setText(configFile, xml);
						savedXML = xml;
					}
					catch (Exception ignore) { }
					Configuration.getInstance().reloadXML();
					JavaPanel.getInstance().reloadXML();
				}
			}
		}

		class DeleteBackupsImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				File ctp = new File("config.xml");
				try {
					ctp = ctp.getCanonicalFile().getParentFile();
					File[] files = ctp.listFiles(new ConfigFileFilter());
					if ((files.length > 0) && deleteApproved()) {
						for (File file : files) file.delete();
					}
				}
				catch (Exception skip) { skip.printStackTrace(); }
			}
		}

		class ConfigFileFilter implements FileFilter {
			public boolean accept(File file) {
				return file.getName().matches("config\\[\\d+\\]\\.xml");
			}
		}

		class DeleteImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				treePane.delete();
			}
		}

		class FormImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				dataPane.setView(false);
				setEnables();
			}
		}

		class XmlImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				dataPane.setView(true);
				setEnables();
			}
		}

		class ExpandImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				treePane.expandAll();
			}
		}

		class CollapseImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				treePane.collapseAll();
			}
		}

		class PipelineImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				Element element = pipeline.getXML("Configuration");
				treePane.insert(element);
			}
		}

		class StandardPipelineImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				JMenuItem item = (JMenuItem)event.getSource();
				String name = item.getText();
				Element pipe = standardPipelines.get(name);
				treePane.insertPipeline(pipe);
			}
		}

		class ComponentImpl implements ActionListener {
			String parentName;
			public ComponentImpl(String parentName) {
				this.parentName = parentName;
			}
			public void actionPerformed(ActionEvent event) {
				ComponentMenuItem item = (ComponentMenuItem)event.getSource();
				Template template = templateTable.get(item.getClassName());
				Element element = template.getXML(parentName);
				treePane.insert(element);
			}
		}

		class ChildImpl implements ActionListener {
			String parentClassName;
			Template parentTemplate;
			public ChildImpl(String parentClassName, Template parentTemplate) {
				this.parentClassName = parentClassName;
				this.parentTemplate = parentTemplate;
			}
			public void actionPerformed(ActionEvent event) {
				ComponentMenuItem item = (ComponentMenuItem)event.getSource();
				Template template = parentTemplate.getChildTemplate(item.getClassName());
				Element element = template.getXML(parentClassName);
				Element parent = (Element)element.getParentNode();
				parent.setAttribute("class", parentTemplate.getAttrValue("class", "default"));
				treePane.insert(element);
			}
		}

		class HelpImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				showHelp();
			}
		}

		class AuthImpl implements ActionListener {
			public void actionPerformed(ActionEvent event) {
				logger.info("Creating GoogleAPIClientFactory");
				GoogleAPIClient auth = GoogleAPIClientFactory.getInstance().createGoogleClient();
				logger.info("Create GoogleAPIClientFactory=" + auth);
				try {
					logger.info("Invoking signIn()");
					auth.signIn();
				} catch (Exception e) {
					logger.error("Error invoking signIn()", e);
					e.printStackTrace();
				}
			}
		}
	}

	public boolean deleteApproved() {
		int yesno = JOptionPane.showConfirmDialog(
							this,
							"Are you sure you want to delete\n"
							+ "the backup config.xml files?\n",
							"Are you sure?",
							JOptionPane.YES_NO_OPTION);
		return (yesno == JOptionPane.YES_OPTION);
	}

	//Check the configuration, looking for duplicate ports and root directories.
	private boolean checkConfig(Element config) {
		DupTable portTable = new DupTable("port");
		portTable.addElement(config);
		RootTable rootTable = new RootTable("root");
		rootTable.addElement(config);
		if (portTable.hasDuplicates) {
			String dups = portTable.getDuplicates();
			JOptionPane.showMessageDialog(
				this,
				"The following port values appear multiple times\n"
				+ "in the configuration. Port values must be unique.\n"
				+ "The configuration cannot be saved.\n"
				+dups,
				"Duplicate ports",
				JOptionPane.WARNING_MESSAGE);
			return false;
		}
		if (rootTable.hasDuplicates) {
			String dups = rootTable.getDuplicates();
			int yesno = JOptionPane.showConfirmDialog(
							this,
							"The following root directories appear multiple times\n"
							+ "in the configuration. Except in special situations,\n"
							+ "root directories must be unique.\n"
							+ "If you click OK, the configuration will be saved.\n\n"
							+dups,
							"Duplicate root directories",
							JOptionPane.OK_CANCEL_OPTION);
			return (yesno == JOptionPane.OK_OPTION);
		}
		return true;
	}

	class DupTable extends Hashtable<String,Integer> {
		String attrName;
		public boolean hasDuplicates = false;
		public DupTable(String attrName) {
			super();
			this.attrName = attrName;
		}
		public void addElement(Element el) {
			if (!el.getAttribute("enabled").equals("no")) {
				addAttribute(el);
				Node child = el.getFirstChild();
				while (child != null) {
					if (child instanceof Element) {
						addElement( (Element)child );
					}
					child = child.getNextSibling();
				}
			}
		}
		void addAttribute(Element el) {
			String attrValue = el.getAttribute(attrName).trim();
			if (!attrValue.equals("")) {
				Integer i = get(attrValue);
				if (i == null) {
					put(attrValue, new Integer(1));
				}
				else {
					i = new Integer(i.intValue() + 1);
					put(attrValue, i);
					hasDuplicates = true;
				}
			}
		}
		public String getDuplicates() {
			StringBuffer sb = new StringBuffer();
			for (String attrValue : keySet()) {
				Integer i = get(attrValue);
				if (i.intValue() > 1) {
					sb.append(attrValue + "\n");
				}
			}
			return sb.toString();
		}
	}

	class RootTable extends DupTable {
		public RootTable(String attrName) {
			super(attrName);
		}
		void addAttribute(Element el) {
			String attrValue = el.getAttribute(attrName).trim();
			if (!attrValue.equals("")) {
				Element parent = (Element)el.getParentNode();
				if (parent != null) {
					String parentValue = parent.getAttribute(attrName).trim();
					File root = new File(attrValue);
					if (!root.isAbsolute() && !parentValue.equals("")) {
						File parentRoot = new File(parentValue);
						root = new File(parentRoot, attrValue);
						attrValue = root.getPath();
					}
				}
				Integer i = get(attrValue);
				if (i == null) {
					put(attrValue, new Integer(1));
				}
				else {
					i = new Integer(i.intValue() + 1);
					put(attrValue, i);
					hasDuplicates = true;
				}
			}
		}
	}

	//Backup a target.
	private void backupTarget(File targetFile) {
		targetFile = targetFile.getAbsoluteFile();
		File parent = targetFile.getParentFile();
		if (targetFile.exists()) {
			String name = targetFile.getName();
			int k = name.lastIndexOf(".");
			String target = name.substring(0,k) + "[";
			int tlen = target.length();
			String ext = name.substring(k);

			int n = 0;
			File[] files = parent.listFiles();
			if (files != null) {
				for (File file : files) {
					String fname = file.getName();
					if (fname.startsWith(target)) {
						int kk = fname.indexOf("]", tlen);
						if (kk > tlen) {
							int nn = StringUtil.getInt(fname.substring(tlen, kk), 0);
							if (nn > n) n = nn;
						}
					}
				}
			}
			n++;
			File backup = new File(parent, target + n + "]" + ext);
			backup.delete(); //shouldn't be there, but just in case.
			targetFile.renameTo(backup);
		}
	}

	//******** The left pane ********
	class TreePane extends BasePanel implements TreeSelectionListener {

		Document doc = null;
		Element root = null;
		XMLTree tree = null;
		TreeDragSource dragSource;
		TreeDropTarget dropTarget;

		public TreePane() {
			super();
		}

		public boolean load(Document doc) {
			this.doc = doc;
			root = doc.getDocumentElement();
			fixPipelines(root);
			tree = new XMLTree(root);
			tree.getSelectionModel().addTreeSelectionListener(this);
			removeAll();
			this.add(tree);
			tree.expandAll();
			dragSource = new TreeDragSource(tree, DnDConstants.ACTION_COPY_OR_MOVE);
			dropTarget = new TreeDropTarget(tree);
			return true;
		}

		private void fixPipelines(Element root) {
			Node pipe = root.getFirstChild();
			while (pipe != null) {
				if ((pipe instanceof Element) && pipe.getNodeName().equals("Pipeline")) {
					Node n = pipe.getFirstChild();
					while (n != null) {
						if (n instanceof Element) {
							Element stage = (Element)n;
							String className = stage.getAttribute("class");
							if (className.contains(".")) {
								String name = className.substring( className.lastIndexOf(".")+1 );
								n = XmlUtil.renameElement(stage, name);
							}
						}
						n = n.getNextSibling();
					}
				}
				pipe = pipe.getNextSibling();
			}
		}

		public void valueChanged(TreeSelectionEvent event) {
			DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
			if (treeNode != null) {
				dataPane.edit( treeNode );
			}
		}

		public void expandAll() {
			tree.expandAll();
		}

		public void collapseAll() {
			tree.collapseAll();
			tree.expandRow(0);
		}

		public DefaultMutableTreeNode insert(Element element) {
			DefaultMutableTreeNode targetNode = (DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
			return insert(element, targetNode);
		}

		public DefaultMutableTreeNode insert(Element element, DefaultMutableTreeNode targetNode) {
			DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
			XMLUserObject userObject = new XMLUserObject(element);

			XMLUserObject targetObject = null;
			DefaultMutableTreeNode parentNode = null;
			DefaultMutableTreeNode cnode = null;
			if (targetNode != null) {
				targetObject = (XMLUserObject)targetNode.getUserObject();
				parentNode = (DefaultMutableTreeNode)targetNode.getParent();
			}
			if (userObject.isStage() && (targetNode != null)) {
				if (targetObject.isPipeline()) {
					cnode = tree.addChild(targetNode, userObject);
				}
				else if (targetObject.isStage()) {
					int index = parentNode.getIndex(targetNode);
					cnode = tree.addChild(targetNode, userObject);
					parentNode.insert(cnode, index+1);
				}
			}
			else if (userObject.isChild() && (targetNode != null) && (targetObject.isStage() || targetObject.isServer())) {
				cnode = tree.addChild(targetNode, userObject);
			}
			else if (userObject.isPlugin()) {
				if ((targetObject != null) && targetObject.isPlugin()) {
					int index = parentNode.getIndex(targetNode);
					cnode = tree.addChild(targetNode, userObject);
					parentNode.insert(cnode, index+1);
				}
				else {
					DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
					DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)root.getFirstChild();
					XMLUserObject childObject = null;
					while (childNode != null) {
						childObject = (XMLUserObject)childNode.getUserObject();
						if (childObject.isPipeline()) break;
						childNode = childNode.getNextSibling();
					}
					if (childNode == null) {
						cnode = tree.addChild(root, userObject);
					}
					else {
						int index = root.getIndex(childNode);
						cnode = tree.addChild(root, userObject);
						root.insert(cnode, index);

					}
				}
			}
			else if (userObject.isPipeline()) {
				DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
				cnode = tree.addChild(root, userObject);
			}
			reload(cnode, model);

			return cnode;
		}

		public void delete() {
			DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
			if (treeNode != null) {
				Object object = treeNode.getUserObject();
				if ((object != null) && (object instanceof XMLUserObject)) {
					XMLUserObject userObject = (XMLUserObject)object;
					if (userObject.isDeletable()) {
						DefaultMutableTreeNode next = (DefaultMutableTreeNode)treeNode.getNextSibling();
						if (next == null) next = (DefaultMutableTreeNode)treeNode.getParent();
						DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
						model.removeNodeFromParent(treeNode);
						TreePath path = new TreePath(model.getPathToRoot(next));
						tree.setSelectionPath(path);
					}
				}
			}
		}

		public void insertPipeline(Element pipe) {
			if (pipe !=  null) {
				DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
				DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
				DefaultMutableTreeNode cnode = tree.addChild(root, pipe);
				tree.addChildren(cnode, pipe);
				reload(cnode, model);
			}
		}

		public void reload(DefaultMutableTreeNode cnode) {
			DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
			reload(cnode, model);
		}

		public void reload(DefaultMutableTreeNode cnode, DefaultTreeModel model) {
			if (cnode != null) {
				model.reload();
				tree.expandAll();
				TreePath path = new TreePath(model.getPathToRoot(cnode));
				tree.setSelectionPath(path);
				tree.scrollPathToVisible(path);
			}
		}

		public Element getXML() {
			return tree.getXML();
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
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 30; }
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 30; }
	}

	//******** The right pane ********
	class DataPane extends ScrollableJPanel {

		boolean viewAsXML = false;
		DefaultMutableTreeNode currentNode = null;

		public DataPane() {
			super();
			//setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
			setLayout( new BorderLayout() );
			setView(false);
		}

		public boolean viewIsXML() {
			return viewAsXML;
		}

		public boolean viewIsForm() {
			return !viewAsXML;
		}

		public void setView(boolean viewAsXML) {
			this.viewAsXML = viewAsXML;
			if (viewAsXML) {
				setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
				setBackground(Color.white);
				jspData.getViewport().setBackground(Color.white);
			}
			else {
				setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
				setBackground(BasePanel.bgColor);
				jspData.getViewport().setBackground(BasePanel.bgColor);
			}
			if (currentNode != null) edit(currentNode);
		}

		public void edit(DefaultMutableTreeNode treeNode) {
			Object object = treeNode.getUserObject();
			if ((object != null) && (object instanceof XMLUserObject)) {
				currentNode = treeNode;
				XMLUserObject userObject = (XMLUserObject)object;
				removeAll();
				if (viewAsXML) displayXML(userObject);
				else displayForm(userObject);
				jspData.setViewportView(this);
				jspData.getVerticalScrollBar().setValue(0);
				menuPane.setChildrenMenu(userObject.toString(), userObject.getTemplate());
			}
		}

		private void displayForm(XMLUserObject userObject) {
			setTrackWidth(true);
			setLayout(new BorderLayout());
			add(userObject.getFormPanel(), BorderLayout.CENTER);
		}

		private void displayXML(XMLUserObject userObject) {
			setTrackWidth(false);
			setLayout(new BorderLayout());
			String xml = XmlUtil.toPrettyString(userObject.getXML());
			ColorPane cp = new ColorPane(xml);
			cp.setEditable(false);
			cp.setScrollableTracksViewportWidth(false);
			add(cp, BorderLayout.CENTER);
		}
	}

	//******** The User Object that encapsulates the CTP configuration elements ********
	class XMLUserObject {

		public Element element;
		public String className;
		public String tag;
		public Template template;

		public DefaultMutableTreeNode treeNode = null;

		public boolean isConfiguration = false;
		public boolean isServer = false;
		public boolean isPlugin = false;
		public boolean isPipeline = false;
		public boolean isStage = false;
		public boolean isChild = false;

		public FormPanel formPanel = null;

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
				String name = element.getAttribute("name");
				if (!name.equals("")) tag += " ["+name+"]";
			}
			this.template = null;
			if (isServer) template = server;
			else if (isPlugin) template = templateTable.get(className);
			else if (isPipeline) template = pipeline;
			else if (isStage) template = templateTable.get(className);
			else if (isChild) {
				Element parent = (Element)element.getParentNode();
				Template parentTemplate = null;
				String parentClass = parent.getAttribute("class");
				if (parent.getTagName().equals("Server") && parentClass.equals("")) parentTemplate = server;
				else parentTemplate = templateTable.get(parentClass);
				if (parentTemplate == null) System.out.println("parentTemplate is null");
				template = parentTemplate.getChildTemplate(tag);
			}

			this.formPanel = new FormPanel(element, template);
		}

		public boolean isDeletable() {
			return isPlugin || isPipeline || isStage || isChild;
		}

		public void setTreeNode(DefaultMutableTreeNode treeNode) {
			this.treeNode = treeNode;
		}

		public DefaultMutableTreeNode getTreeNode() {
			return treeNode;
		}

		public Template getTemplate() {
			return template;
		}

		public FormPanel getFormPanel() {
			return formPanel;
		}

		public String getAttribute(String name) {
			return element.getAttribute(name);
		}

		public boolean isDragable() {
			return !isConfiguration && !isServer && !isPipeline && !isChild;
		}

		public boolean isServer() {
			return isServer;
		}

		public boolean isPlugin() {
			return isPlugin;
		}

		public boolean isPipeline() {
			return isPipeline;
		}

		public boolean isStage() {
			return isStage;
		}

		public boolean isChild() {
			return isChild;
		}

		public Element getXML() {
			Element xml = formPanel.getXML();
			if (treeNode != null) {
				Enumeration e = treeNode.children();
				while (e.hasMoreElements()) {
					Object object = e.nextElement();
					if (object instanceof DefaultMutableTreeNode) {
						DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)object;
						Object userObject = childNode.getUserObject();
						XMLUserObject xmlUserObject = (XMLUserObject)userObject;
						Element c = xmlUserObject.getXML();
						Element cImported = (Element)xml.getOwnerDocument().importNode(c, true);
						xml.appendChild(cImported);
					}
				}
			}
			return xml;
		}

		public String toString() {
			return tag;
		}
	}

	//******** The panel that displays the form for a component ********
	class FormPanel extends ScrollableJPanel {

		Element element;
		Template template;

		public FormPanel(Element element, Template template) {
			super();
			this.element = element;
			this.template = template;
			setBackground(BasePanel.bgColor);
			setTrackWidth(true);
			setLayout( new BoxLayout( this, BoxLayout.Y_AXIS ) );
			if (template != null) {
				Node child = template.getTemplateElement().getFirstChild();
				while (child != null) {
					if (child instanceof Element) {
						Element ch = (Element)child;
						if (ch.getTagName().equals("attr")) {
							String name = ch.getAttribute("name");
							String defValue = ch.getAttribute("default");
							String options = ch.getAttribute("options").trim();
							boolean editable = !ch.getAttribute("editable").equals("no");

							//Get the help text if possible
							String helpText = "";
							NodeList nl = ch.getElementsByTagName("helptext");
							if (nl.getLength() > 0) helpText = nl.item(0).getTextContent();
							if (helpText.equals("")) helpText = defaultHelpText.get(name);
							if (helpText == null) helpText = "";

							String configValue = element.getAttribute(name);
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
		}

		public Element getXML() {
			try {
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement(element.getTagName());
				Component[] comps = getComponents();
				for (Component c : comps) {
					if (c instanceof AttrPanel) {
						AttrPanel a = (AttrPanel)c;
						String attrName = a.getName();
						String attrValue = a.getValue();
						String defaultValue = template.getAttrValue(attrName, "default");
						boolean required = template.getAttrValue(attrName, "required").equals("yes");
						if (required || (!attrValue.equals("") && !attrValue.equals(defaultValue))) {
							root.setAttribute(attrName, attrValue);
						}
					}
				}
				return root;
			}
			catch (Exception ex) { return null; }
		}
	}

	//************** UI components for attributes in the right pane **************
	class AttrPanel extends ScrollableJPanel {
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
		public String getName() {
			return name;
		}
		public String getValue() {
			return "";
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
		public String getValue() {
			return text.getText().trim();
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
		public String getValue() {
			return text.getSelectedItem().toString().trim();
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
		public String getValue() {
			return text.getText().trim();
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
			setLayout( new BoxLayout(this, BoxLayout.Y_AXIS) );
			setBackground(Color.white);
			group = new ButtonGroup();
			for (int i=0; i<values.length; i++) {
				Box box = new Box(BoxLayout.X_AXIS);
				JRadioButton jrb = new JRadioButton( values[i] );
				jrb.setSelected( (i==selectedIndex) );
				jrb.setFont( new Font( "Monospaced", Font.BOLD, 12 ) );
				jrb.setBackground(Color.white);
				group.add(jrb);
				box.add(jrb);
				box.add(Box.createHorizontalGlue());
				this.add(box);
			}
		}
		public String getText() {
			Enumeration<AbstractButton> e = group.getElements();
			while (e.hasMoreElements()) {
				AbstractButton b = e.nextElement();
				if (b.isSelected()) return b.getText();
			}
			return "";
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
			xmlUserObject.setTreeNode(troot);
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
					DefaultMutableTreeNode cnode = addChild(tnode, cel);
					addChildren(cnode, cel);
				}
				child = child.getNextSibling();
			}
		}

		public DefaultMutableTreeNode addChild(DefaultMutableTreeNode tnode, Element child) {
			XMLUserObject xmlUserObject = new XMLUserObject(child);
			return addChild(tnode, xmlUserObject);
		}

		public DefaultMutableTreeNode addChild(DefaultMutableTreeNode tnode, XMLUserObject xmlUserObject) {
			DefaultMutableTreeNode cnode = new DefaultMutableTreeNode(xmlUserObject);
			xmlUserObject.setTreeNode(cnode);
			tnode.add(cnode);
			return cnode;
		}

		public Element getXML() {
			Object userObject = troot.getUserObject();
			XMLUserObject xmlUserObject = (XMLUserObject)userObject;
			return xmlUserObject.getXML();
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

						//Make an element to insert, with all the children
						Document doc = XmlUtil.getDocument();
						Element root = doc.createElement( sourceUO.isStage() ? "Pipeline":"Configuration"  );
						doc.appendChild(root);
						Element sourceEl = sourceUO.getXML();
						sourceEl = (Element)doc.importNode(sourceEl, true);
						root.appendChild(sourceEl);

						//Insert the node
						DefaultMutableTreeNode cnode = treePane.insert(sourceEl, targetNode);

						//Insert the children
						Node child = sourceEl.getFirstChild();
						while (child != null) {
							if (child instanceof Element) treePane.insert( (Element)child, cnode);
							child = child.getNextSibling();
						}

						//Select the node
						treePane.reload(cnode);

						//Signal to remove the old node
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
