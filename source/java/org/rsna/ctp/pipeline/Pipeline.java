/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.pipeline;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.FileObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The class that represents a single pipeline.
 */
public class Pipeline extends Thread {

	static final Logger logger = Logger.getLogger(Pipeline.class);

	String name = "";
	List<ImportService> importServices = null;
	List<PipelineStage> stages = null;
	protected volatile boolean stop = false;

	/**
	 * A Thread representing a processing pipeline for FileObjects
	 * (and subclasses of FileObjects - DicomObjects, XmlObjects,
	 * and ZipObjects).
	 * @param pipeline the XML element from the configuration file
	 * specifying the stages in the pipeline.
	 */
	public Pipeline(Element pipeline) {
		super();
		name = pipeline.getAttribute("name");
		setName(name);
		stages = new ArrayList<PipelineStage>();
		importServices = new ArrayList<ImportService>();
		Node child = pipeline.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				Element childElement = (Element)child;
				String className = childElement.getAttribute("class").trim();
				if (!className.equals("")) {
					try {
						Class theClass = Class.forName(className);
						Class[] signature = { Element.class };
						Constructor constructor = theClass.getConstructor(signature);
						Object[] args = { childElement };
						PipelineStage stage = (PipelineStage)constructor.newInstance(args);
						//Put the ImportServices in a special list.
						if (stage instanceof ImportService) importServices.add((ImportService)stage);
						//Put all the stages in the stages list so the servlets can get at them easily.
						stages.add(stage);
					}
					catch (Exception ex) { logger.error(name+": Unable to load "+className,ex); }
				}
				else logger.error(name+": Stage with no class attribute: "+childElement.getTagName());
			}
			child = child.getNextSibling();
		}
	}

	/**
	 * Get the name of this pipeline.
	 * @return the name of this pipeline.
	 */
	public synchronized String getPipelineName() {
		return name;
	}

	/**
	 * Get the list of ImportService objects.
	 * @return the list of ImportService objects.
	 */
	public synchronized List<ImportService> getImportServices() {
		return importServices;
	}

	/**
	 * Get the list of PipelineStage objects.
	 * @return the list of PipelineStage objects.
	 */
	public synchronized List<PipelineStage> getStages() {
		return stages;
	}

	/**
	 * Shut down the pipeline
	 */
	public synchronized void shutdown() {
		stop = true;
	}

	/**
	 * Check whether the pipeline has shut down.
	 * @return true if the pipeline has cleanly shut down; false otherwise.
	 */
	public synchronized boolean isDown() {
		if (!this.getState().equals(Thread.State.TERMINATED)) return false;
		for (PipelineStage stage: stages) {
			if (!stage.isDown()) {
				logger.info(getPipelineName()+": "+stage.getName()+" is not down");
				return false;
			}
		}
		return true;
	}

	/**
	 * Run the thread.
	 */
	public void run() {
		//Only run if there are stages in the pipeline.
		if (stages.size() > 0) {
			//Start the stages
			for (PipelineStage stage: stages) stage.start();

			//Process objects, sleeping when there are none available.
			while (!stop && !interrupted()) {
				try {
					processObjects();
					if (!stop) sleep(1000);
				}
				catch (Exception ex) { stop = true; }
			}
			//Stop all the stages
			for (PipelineStage stage: stages) stage.shutdown();
		}
	}

	//Process objects until none are left.
	private void processObjects() {
		FileObject fileObject;
		File importedFile;
		ImportService importService;
		ImportedObject importedObject;

		while (!interrupted() && ((importedObject=getNextObject()) != null)) {
			//Get the object and where it came from.
			fileObject = importedObject.object;
			importService = importedObject.provider;

			//Make sure it has a standard extension
			fileObject.setStandardExtension();

			//Remember the original File so we can release it.
			importedFile = fileObject.getFile();

			//Sequence through the stages in the
			//pipeline (skipping any ImportServices).
			Iterator<PipelineStage> sit = stages.iterator();

			//Note: if a stage returns a null FileObject, it has
			//quarantined the object and we need to abort further
			//processing. That's why the condition in the while
			//statement includes a test for (fileObject != null).
			while ((fileObject != null) && sit.hasNext()) {
				PipelineStage stage = sit.next();
				if (stage instanceof Processor)
					fileObject = ((Processor)stage).process(fileObject);
				else if (stage instanceof StorageService)
					fileObject = ((StorageService)stage).store(fileObject);
				else if (stage instanceof ExportService)
					((ExportService)stage).export(fileObject);
				//Note that ImportServices are skipped;
				//they are only suppliers, not processors.
			}
			//Release the file and yield in case
			//someone else has anything on his mind.
			importService.release(importedFile);
			Thread.yield();
		}
		//Nothing left to do; return.
	}

	//Find the first ImportService which has an object available.
	private ImportedObject getNextObject() {
		if (!stop) {
			ImportService importService;
			FileObject fileObject;
			Iterator<ImportService> lit = importServices.iterator();
			while (lit.hasNext()) {
				importService = lit.next();
				fileObject = importService.getNextObject();
				if (fileObject != null) return new ImportedObject(fileObject, importService);
			}
		}
		return null;
	}

	//A class to encapsulate a FileObject and the ImportService which provided it.
	class ImportedObject {
		public FileObject object;
		public ImportService provider;
		public ImportedObject(FileObject object, ImportService provider) {
			this.object = object;
			this.provider = provider;
		}
	}

	/**
	 * Get HTML text describing the configuration of the pipeline
	 * by calling the getConfigHTML methods of each of the pipeline
	 * stages in turn. This method is called by the ConfigurationServlet.
	 * @param admin true if the configuration is allowed to display
	 * the values of username and password attributes.
	 * @return HTML text describing the configuration of the pipeline.
	 */
	public synchronized String getConfigHTML(boolean admin) {
		StringBuffer sb = new StringBuffer();
		sb.append("<h2>"+name+"</h2>");
		Iterator<PipelineStage> sit = stages.iterator();
		while (sit.hasNext()) sb.append(sit.next().getConfigHTML(admin));
		return sb.toString();
	}

	/**
	 * Get HTML text describing the status of the pipeline
	 * by calling the getStatusHTML methods of each of the pipeline
	 * stages in turn. This method is called by the StatusServlet.
	 * @return HTML text describing the status of the pipeline.
	 */
	public synchronized String getStatusHTML() {
		StringBuffer sb = new StringBuffer();
		sb.append("<h2>"+name+"</h2>");
		Iterator<PipelineStage> sit = stages.iterator();
		while (sit.hasNext()) sb.append(sit.next().getStatusHTML());
		return sb.toString();
	}

}