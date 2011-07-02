/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import org.apache.log4j.Logger;
import org.rsna.util.FileUtil;

/**
 * An encapsulation of pixel regions.
 */
public class Regions {

	static final Logger logger = Logger.getLogger(Regions.class);

	List<Region> regions = null;

	/**
	 * Constructor; create an empty Regions object.
	 */
	public Regions() {
		regions = new LinkedList<Region>();
	}

	/**
	 * Get the number of regions.
	 */
	public int size() {
		return regions.size();
	}

	/**
	 * Get the regions as a String.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (Region r : regions) {
			sb.append("(" + r.left + "," + r.top + "," + r.right + "," + r.bottom + ")");
		}
		return sb.toString();
	}

   /**
	* Add a new region.
	* @param x the left coordinate of the region
	* @param y the top coordinate of the region
	* @param w the width of the region
	* @param h the height of the region
	*/
	public void addRegion(int x, int y, int w, int h) {
		regions.add(new Region(x, y, w, h));
	}

   /**
	* Get an array of index ranges corresponding to the
	* intersection of the regions and a row, where
	* each range has two ints, the first defining the start
	* of a region and the second defining the end of the region.
	* @param row the y coordinate of the row
	* @return the ranges corresponding to the regions in the row.
	*/
	public int[] getRangesFor(int row) {
		LinkedList<Region> rList = new LinkedList<Region>();
		for (Region r : regions) {
			if ((r.top <= row) && (r.bottom >= row)) rList.add(r);
		}
		int[] ranges = new int[2 * rList.size()];
		int k = 0;
		for (Region r : rList) {
			ranges[k++] = r.left;
			ranges[k++] = r.right;
		}
		return ranges;
	}

	//A class to contain the information for a single region.
	class Region {
		public int x;
		public int y;
		public int w;
		public int h;
		public int left;
		public int top;
		public int right;
		public int bottom;

		public Region(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.left = x;
			this.top = y;
			this.right = x + w;
			this.bottom = y + h;
		}

	}
}
