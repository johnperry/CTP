**Prerequisites**
To run this demo you need _JRE 8, curl, gcloud and git_ to be installed

**Upload PHI DICOM dataset to Google Cloud**


Obtain an access token
```sh
export TOKEN=`gcloud auth application-default print-access-token`
```
Setup your environment
```sh
export PROJECT_ID=gcp-health
export LOCATION=us-central1
export DATASET=ctp-dataset
export DICOMSTORE=ctp-phi-dataset
```

Store your DICOM files 
```sh
stowrs -url https://healthcare.googleapis.com/v1alpha/projects/${PROJECT_ID}/locations/${LOCATION}/datasets/${DATASET}/dicomStores/${DICOMSTORE}/dicomWeb/studies?access_token=$TOKEN SOME_DICOM_FILE.dcm
```

**Run de-identification pipeline using MIRC CTP Launcher**

Clone source repository
```sh
git clone https://github.com/codeminders/CTP.git
```
Run MIRC CTP Launcher
```sh
cd CTP/products/CTP_extracted
java -jar Launcher.jar
```

- In **RSNA CTP Launcher** window switch to _Configuration_ tab and click "Authorization->Login with Google" menu
- Browser window with Google consent screen pops up. Login with your Google account and grant required permissions.
- Choose _GoogleCloudImportService_ and set _projectId_, _dicomStoreName_ properties
- Choose _GoogleCloudExportService_ and set _dicomStoreName_ property
- Click _File -> Save_
- Switch to _General_ tab and click _Start_ button
- Wait until status changes to _Running_, click _CTP Home Page_ and observe pipeline status page.

