/*---------------------------------------------------------------
 *  Copyright 2015 by the Radiological Society of North America
 *
 *  This source software is released under the terms of the
 *  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
 *----------------------------------------------------------------*/

package org.rsna.ctp.stdstages.anonymizer.dicom;

public interface AnonymizerExtension {

	public String call(FnCall fnCall) throws Exception;

}
