/*---------------------------------------------------------------
*  Copyright 2016 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.logger;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;

public class QueueEntry implements Serializable {
	
	public final String cohortName;
	public final File file;
	public final LinkedList<LoggedElement> list;
	
	public QueueEntry(String cohortName, File file, LinkedList<LoggedElement> list) {
		this.cohortName = cohortName;
		this.file = file;
		this.list = list;
	}
}
