/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.verifier;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;
import org.rsna.util.StringUtil;

/**
 * A class to encapsulate a study for the purpose of verification..
 */
public class StudyObject implements Serializable {

	static final long serialVersionUID = 1L;

	public String date;
	public String siUID;
	public String ptID;
	public String ptName;
	public Hashtable<String,Dates> instances = null;

	public StudyObject(String date, String siUID, String ptID, String ptName) {
		this.date = date;
		this.siUID = siUID;
		this.ptID = ptID;
		this.ptName = ptName;
		this.instances = new Hashtable<String,Dates>();
	}

	public Enumeration<String> getInstances() {
		return instances.keys();
	}

	public void putSubmitDate(String sopiUID) {
		Dates dates = instances.get(sopiUID);
		if (dates == null) dates = new Dates();
		dates.submitDate = StringUtil.getDateTime(-1, " ");
		instances.put(sopiUID, dates);
	}

	public void putEntryDate(String sopiUID, String entryDate) {
		Dates dates = instances.get(sopiUID);
		if (dates == null) dates = new Dates();
		dates.entryDate = entryDate;
		instances.put(sopiUID, dates);
	}

	public String getEntryDate(String sopiUID) {
		Dates dates = instances.get(sopiUID);
		if (dates == null) return "";
		return dates.entryDate;
	}

	public String getSubmitDate(String sopiUID) {
		Dates dates = instances.get(sopiUID);
		if (dates == null) return "";
		return dates.submitDate;
	}

	public int getVerifiedCount() {
		Enumeration<String> keys = instances.keys();
		int count = 0;
		while (keys.hasMoreElements()) {
			Dates dates = instances.get(keys.nextElement());
			if (!dates.entryDate.equals("")) count++;
		}
		return count;
	}

	public int getUnverifiedCount() {
		Enumeration<String> keys = instances.keys();
		int count = 0;
		while (keys.hasMoreElements()) {
			Dates dates = instances.get(keys.nextElement());
			if (dates.entryDate.equals("")) count++;
		}
		return count;
	}

	public int getInstanceCount() {
		return instances.size();
	}

	class Dates implements Serializable {
		static final long serialVersionUID = 1L;
		public String submitDate = "";
		public String entryDate = "";
		public Dates() { }
	}
}
