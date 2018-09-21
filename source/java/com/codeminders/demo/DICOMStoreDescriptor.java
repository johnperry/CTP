package com.codeminders.demo;

public class DICOMStoreDescriptor {

	private String projectId;
	private String locationId;
	private String dataSetName;
	private String dicomStoreName;

	public DICOMStoreDescriptor(String projectId, String locationId, String dataSetName, String dicomStoreName) {
		this.projectId = projectId;
		this.locationId = locationId;
		this.dataSetName = dataSetName;
		this.dicomStoreName = dicomStoreName;
	}
	
	public String getDataSetName() {
		return dataSetName;
	}
	public String getDicomStoreName() {
		return dicomStoreName;
	}
	public String getLocationId() {
		return locationId;
	}
	public String getProjectId() {
		return projectId;
	}
}
