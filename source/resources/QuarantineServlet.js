function getSeries(evt, studyUID) {
	var event = getEvent(evt);
	var source = getSource(event);
	var tr = getParentTR(source);
	getSeriesForTR(tr, studyUID);
}

function getSeriesForTR(tr, studyUID) {
	hidePopups();
	setTableStyle(tr, "normal");
	setRowStyle(tr, "bold");
	var url = "/" + context + "/series";
	var req = new AJAX();
	req.GET(url, "p="+p+"&s="+s+"&studyUID="+studyUID+"&"+req.timeStamp(), null);
	if (req.success()) {
		var html = req.responseText();
		var seriesDiv = document.getElementById("SeriesDiv");
		while (seriesDiv.firstChild) seriesDiv.removeChild(seriesDiv.firstChild);
		seriesDiv.innerHTML = html;
		var filesDiv = document.getElementById("FilesDiv");
		while (filesDiv.firstChild) filesDiv.removeChild(filesDiv.firstChild);
	}
}

function getFiles(evt, seriesUID) {
	var event = getEvent(evt);
	var source = getSource(event);
	var tr = getParentTR(source);
	getFilesForTR(tr, seriesUID);
}

function getFilesForTR(tr, seriesUID) {
	hidePopups();
	setTableStyle(tr, "normal");
	setRowStyle(tr, "bold");
	var url = "/" + context + "/files";
	var req = new AJAX();
	req.GET(url, "p="+p+"&s="+s+"&seriesUID="+seriesUID+"&"+req.timeStamp(), null);
	if (req.success()) {
		var html = req.responseText();
		var filesDiv = document.getElementById("FilesDiv");
		while (filesDiv.firstChild) filesDiv.removeChild(filesDiv.firstChild);
		filesDiv.innerHTML = html;
	}
}

function getParentTR(el) {
	var tr = el;
	while (tr && (tr.tagName != "TR")) tr = tr.parentNode;
	return tr;
}

function setRowStyle(tr, value) {
	var td = tr.firstChild;
	while (td) {
		if (td.tagName == "TD") td.style.fontWeight = value;
		td = td.nextSibling;
	}
}

function setTableStyle(tr, value) {
	var tbody = tr.parentNode;
	while (tbody && (tbody.tagName != "TBODY")) tbody = tbody.parentNode;
	if (tbody) {
		var row = tbody.firstChild;
		while (row) {
			if (row.tagName == "TR") setRowStyle(row, value);
			row = row.nextSibling;
		}
	}
}

function showImagePopup(filename) {
	var id = "imagePopupID";
	var pop = document.getElementById(id);
	if (pop) pop.parentNode.removeChild(pop);

	var w = 600;
	var h = 600;

	var div = document.createElement("DIV");
	div.className = "content";

	var para = document.createElement("P");
	var iframe = document.createElement("IFRAME");
	iframe.style.width = w - 45;
	iframe.style.height = h - 55;
	iframe.style.backgroundColor = "black";
	iframe.src = "/"+context+"/displayFile?p="+p+"&s="+s+"&filename="+filename;
	para.appendChild(iframe);
	div.appendChild(para);
	var closebox = "/icons/closebox.gif";

	//popupDivId, w, h, title, closeboxFile, heading, div, okHandler, cancelHandler, hide, closeboxHandler
	showDialog(id, w, h, filename, closebox, null, div, null, null, false, hidePopups);
}

