function setFocus() {
	var x = document.getElementById('phi');
	if (x) x.focus();
}

window.onload = setFocus;

function submitURLEncodedForm() {
	var form = document.getElementById("URLEncodedFormID");
	form.submit();
}

function downloadCSV() {
	var p = document.getElementById("p").value;
	var s = document.getElementById("s").value;

	window.open("/lookup?p="+p+"&s="+s+"&format=csv", "_self");
}

function uploadCSV() {
	var id = "uploadCSVID";
	var pop = document.getElementById(id);
	if (pop) pop.parentNode.removeChild(pop);

	var w = 400;
	var h = 220;
	var closebox = "/icons/closebox.gif";

	var div = getUploadCSVDiv();

	//popupDivId, w, h, title, closeboxFile, heading, div, okHandler, cancelHandler, hide
	showDialog(id, w, h, "Upload CSV Lookup Table File", closebox, null, div, submitCSV, hidePopups);
}

function getUploadCSVDiv() {
	var div = document.createElement("DIV");
	div.className = "content";
	var h1 = document.createElement("H1");
	h1.appendChild(document.createTextNode("Select CSV File"));
	h1.style.fontSize = "18pt";
	div.appendChild(h1);
	div.appendChild(document.createElement("BR"));
	var center = document.createElement("CENTER");
	var p = document.createElement("P");

	var form = document.createElement("FORM");
	form.id = "CSVFormID";
	form.method = "post";
	form.target = "_self";
	form.encoding = "multipart/form-data";
	form.acceptCharset = "UTF-8";
	form.action = "/lookup";

	var input = document.createElement("INPUT");
	input.name = "filecontent";
	input.id = "selectedFile";
	input.type = "file";
	input.style.width = 320;

	form.appendChild(input);

	input = document.createElement("INPUT");
	input.name = "p";
	input.type = "hidden";
	input.value = document.getElementById("p").value;
	form.appendChild(input);

	input = document.createElement("INPUT");
	input.name = "s";
	input.type = "hidden";
	input.value = document.getElementById("s").value;
	form.appendChild(input);

	p.appendChild(form);

	center.appendChild(p)
	div.appendChild(center);
	return div
}

function submitCSV() {
	var form = document.getElementById("CSVFormID");
	form.submit();
}


