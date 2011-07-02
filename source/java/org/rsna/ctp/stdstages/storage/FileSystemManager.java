/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.storage;

import java.io.File;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.server.User;

/**
 * A class to keep track of FileSystems.
 */
public class FileSystemManager {

	static final Logger logger = Logger.getLogger(FileSystemManager.class);

	static Hashtable<File,FileSystemManager> fileSystemManagers
								= new Hashtable<File,FileSystemManager>();

	Hashtable<String,FileSystem> fileSystems = null;
	File root;
	String type;
	boolean requireAuthentication;
	boolean acceptDuplicateUIDs;
	boolean setReadable;
	boolean setWritable;
	List<ImageQualifiers> qualifiers;
	boolean loaded = false;

	/**
	 * Get the singleton instance of the FileSystemManager for a specific root directory.
	 * This method returns a new FileSystemManager of the specified type if a FileSystemManager
	 * cannot be found for the specified root directory.
	 * @param root the directory managed by the FileSystemManager. See the Javadoc for the
	 * FileSystem constructor.
	 * @param type the directory structure to be imposed on FileSystems in this root.
	 * @param requireAuthentication true if accesses to FileSystems associated with this
	 * root require authentication; false otherwise.
	 * @param setReadable true if files and directories in this root are to be world readable.
	 * @param setWritable true if files and directories in this root are to be world writable.
	 * @param qualifiers the list of qualifiers for the creation of JPEG images when a
	 * DICOM image is stored.
	 */
	public static FileSystemManager getInstance(
											File root,
											String type,
											boolean requireAuthentication,
											boolean acceptDuplicateUIDs,
											boolean setReadable,
											boolean setWritable,
											List<ImageQualifiers> qualifiers) {
		FileSystemManager fsm = fileSystemManagers.get(root);
		if (fsm == null) {
			fsm = new FileSystemManager(
						root,
						type,
						requireAuthentication,
						acceptDuplicateUIDs,
						setReadable,
						setWritable,
						qualifiers);
			fileSystemManagers.put(root,fsm);
		}
		return fsm;
	}

	/**
	 * Get the singleton instance of the FileSystemManager for a specific root directory.
	 * This method returns null if a FileSystemManager cannot be found for the
	 * specified root directory. This method should be used by servlets which are only
	 * interested in files which actually exist.
	 * @param root the directory managed by the FileSystemManager.
	 */
	public static FileSystemManager getInstance(File root) {
		return fileSystemManagers.get(root);
	}

	/**
	 * Create an empty table of FileSystems based on a specific root directory.
	 * @param root the directory within which all FileSystems are to be created.
	 * @param type the structure to be imposed on study directories in the
	 * FileSystems managed by this FileSystemManager. See the Javadoc for the
	 * FileSystem constructor.
	 * @param requireAuthentication true if accesses to FileSystems associated with this
	 * root require authentication; false otherwise.
	 * @param setReadable true if files and directories in this root are to be world readable.
	 * @param setWritable true if files and directories in this root are to be world writable.
	 * @param qualifiers the list of qualifiers for the creation of JPEG images when a
	 * DICOM image is stored.
	 */
	protected FileSystemManager(File root,
								String type,
								boolean requireAuthentication,
								boolean acceptDuplicateUIDs,
								boolean setReadable,
								boolean setWritable,
								List<ImageQualifiers> qualifiers) {
		this.root = root;
		this.type = type;
		this.requireAuthentication = requireAuthentication;
		this.acceptDuplicateUIDs = acceptDuplicateUIDs;
		this.setReadable = setReadable;
		this.setWritable = setWritable;
		this.qualifiers = qualifiers;
		fileSystems = new Hashtable<String,FileSystem>();
//		if (setReadable) root.setReadable(true,false); //Java 1.6
//		if (setWritable) root.setWritable(true,false); //Java 1.6
	}

	/**
	 * Get all the FileSystems for a specified User
	 * and return them all in a List.
	 * @param user the user requesting access.
	 * @return a list of all the FileSystems.
	 */
	public List<String> getFileSystemsFor(User user) {
		List<String> allFileSystems = getFileSystems();
		LinkedList<String> allowedFileSystems = new LinkedList<String>();
		Iterator<String> lit = allFileSystems.iterator();
		while (lit.hasNext()) {
			String fsName = lit.next();
			FileSystem fs = getFileSystem(fsName);
			if (fs.allowsAccessBy(user)) allowedFileSystems.add(fsName);
		}
		return allowedFileSystems;
	}

	/**
	 * Load all the FileSystems managed by this FileSystemManager
	 * and return them all in a List.
	 * @return a list of all the FileSystems.
	 */
	public List<String> getFileSystems() {
		if (!loaded) {
			File[] dirs = root.listFiles();
			for (int i=0; i<dirs.length; i++) {
				if (dirs[i].isDirectory()) getFileSystem(dirs[i].getName());
			}
			loaded = true;
		}
		return new LinkedList<String>(fileSystems.keySet());
	}

	/**
	 * Get the number of FileSystems managed by this FileSystemManager
	 * This method calls getFileSystems to ensure that all FileSystems have been loaded.
	 * @return the number of FileSystems.
	 */
	public int getSize() {
		getFileSystems();
		return fileSystems.size();
	}

	/**
	 * Get the named FileSystem, creating it if it does not exist.
	 * @param name the name of the FileSystem to be loaded. Note: the name is
	 * filtered by the static FileSystem.makeFSName() method, so the FileSystem.getName()
	 * method may return a different name from the one in this method call.
	 * @return the FileSystem with the specified name.
	 */
	public FileSystem getFileSystem(String name) {
		return getFileSystem(name, true);
	}

	/**
	 * Get the named FileSystem. If no FileSystem exists with the specified name,
	 * create it and add it to the table only if the create boolean is true.
	 * @param name the name of the FileSystem to be loaded. Note: the name is
	 * filtered by the static FileSystem.makeFSName() method, so the FileSystem.getName()
	 * method may return a different name from the one in this method call.
	 * @param create true if the FileSystem is to be created if it does not exist; false
	 * if null is to be returned if the FileSystem does not exist.
	 * @return the FileSystem with the specified name.
	 */
	public FileSystem getFileSystem(String name, boolean create) {
		name = FileSystem.makeFSName(name);
		FileSystem fs = fileSystems.get(name);
		if ((fs == null) && create) {
			fs = new FileSystem(
						root,
						name,
						type,
						requireAuthentication,
						acceptDuplicateUIDs,
						setReadable,
						setWritable,
						qualifiers);
			fileSystems.put(name,fs);
		}
		return fs;
	}

}
