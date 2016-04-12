/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

import java.awt.Rectangle;
import java.awt.Shape;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import org.apache.log4j.Logger;
import org.rsna.util.FileUtil;

/**
 * An encapsulation of pixel regions.
 */
public class Regions {

	static final Logger logger = Logger.getLogger(Regions.class);

	List<Rectangle> regions = null;

	/**
	 * Constructor; create an empty Regions object.
	 */
	public Regions() {
		regions = new LinkedList<Rectangle>();
	}

	/**
	 * Get the number of regions.
	 * @return the number of regions
	 */
	public int size() {
		return regions.size();
	}

	/**
	 * Get the regions as a String.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (Rectangle r : regions) {
			sb.append("(" + r.x + "," + r.y + "," + (r.x + r.width) + "," + (r.y + r.height) + ")");
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
		regions.add(new Rectangle(x, y, w, h));
	}

	/**
	 * Get a Vector containing all the region Rectangles.
	 * @return all the region Rectangles
	 */
	public Vector<Shape> getRegionsVector() {
		return new Vector(regions);
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
		LinkedList<Rectangle> rList = new LinkedList<Rectangle>();
		for (Rectangle r : regions) {
			if ((r.y <= row) && ((r.y + r.height) >= row)) rList.add(r);
		}
		int[] ranges = new int[2 * rList.size()];
		int k = 0;
		for (Rectangle r : rList) {
			ranges[k++] = r.x;
			ranges[k++] = r.x + r.width;
		}
		return ranges;
	}

}
