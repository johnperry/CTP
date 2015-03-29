var saveAsProfileItems = new Array(
		new Item("New profile...", newProfileHandler) );

var fileItems = new Array(
		new Item("Save", save),
		new Item("", null),
		new Menu("Save as profile", saveAsProfileItems, "saveasprofilemenu"),
		new Item("", null),
		new Item("Close", checkSave) );

var gotoItems = new Array(
		new Item("0008", gotoHandler),
		new Item("0010", gotoHandler),
		new Item("0012", gotoHandler),
		new Item("0013", gotoHandler),
		new Item("0018", gotoHandler),
		new Item("0020", gotoHandler),
		new Item("0028", gotoHandler),
		new Item("0032", gotoHandler),
		new Item("0040", gotoHandler) );

var viewItems = new Array (
		new Item("Selected elements", selectedHandler),
		new Item("All elements", allHandler),
		new Item("",null),
		new Menu("Scroll to group", gotoItems) );

var editItems = new Array (
		new Item("New Parameter...",newParam),
		new Item("Remove Parameter...",removeParam),
		new Item("Remove All Parameters",removeAllParams),
		new Item("", null),
		new Item("New Element...",newElement),
		new Item("Remove Element...",removeElement),
		new Item("", null),
		new Item("New Keep Group...",newKeepGroup),
		new Item("Remove Keep Group...",removeKeepGroup),
		new Item("New Keep Safe Private Elements",createKeepSafePrivateElements),
		new Item("", null),
		new Item("Deselect all", deselectHandler) );

var helpItems = new Array (
		new Item("CTP Wiki", showWiki),
		new Item("CTP DICOM Anonymizer", showAnonymizer),
		new Item("CTP DICOM Anonymizer Configurator", showAnonymizerConfigurator) );

var fileMenu		= new Menu("File", fileItems, "filemenu");
var profilesMenu	= new Menu("Profiles", new Array(), "profilesmenu");
var viewMenu		= new Menu("View", viewItems);
var editMenu		= new Menu("Edit", editItems);
var helpMenu		= new Menu("Help", helpItems);

var menuBar = new MenuBar("menuBar", new Array (fileMenu, profilesMenu, viewMenu, editMenu, helpMenu));

window.onload = load;

function load() {
	var iconURL = closeboxURL;
	var iconHandler = checkSave;
	if (closeboxHome == "") {
		iconURL = "/icons/save.png";
		iconHandler = save;
	}

	setPageHeader("DICOM Anonymizer Configurator", "", iconURL, iconHandler);
	menuBar.setText(scriptFile);
	createProfilesMenus();
	menuBar.display();
	createTable();
	resize();
	savedXML = getXML(true);
}

window.onresize = resize;

var savedXML;
var tbody;
var req;

function createProfilesMenus() {
	var url = contextURL + profilesPath;
	var req = new AJAX();
	req.GET(url, req.timeStamp(), null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml.firstChild;

		//set the Profiles menu
		var profilesMenu = menuBar.index["profilesmenu"];
		profilesMenu.items = new Array();
		profilesMenu.items[profilesMenu.items.length] = new Item("Apply profiles", null);
		profilesMenu.items[profilesMenu.items.length] = new Item("", null);

		//do the DICOM profiles
		var dicom  = root.getElementsByTagName("dicom");
		var items = new Array();
		for (var i=0; i<dicom.length; i++) {
			items[i] = new Item(dicom[i].getAttribute("file"), applyProfileHandler);
		}
		if (items.length > 0) profilesMenu.items[profilesMenu.items.length] = new Menu("DICOM", items);
		else profilesMenu.items[profilesMenu.items.length] = new Item("DICOM profiles", null);

		//do the local profiles
		var local  = root.getElementsByTagName("saved");
		items = new Array();
		for (var i=0; i<local.length; i++) {
			items[i] = new Item(local[i].getAttribute("file"), applyProfileHandler);
		}
		if (items.length > 0) profilesMenu.items[profilesMenu.items.length] = new Menu("Saved profiles", items);
		else profilesMenu.items[profilesMenu.items.length] = new Item("Saved profiles", null);

		//now set up the Save as profile menu
		var sapMenu = menuBar.index["saveasprofilemenu"];
		items = new Array();
		items[items.length] = new Item("New profile...", newProfileHandler);
		if (local.length > 0) {
			items[items.length] = new Item("", null);
			for (var i=0; i<local.length; i++) {
				items[items.length] = new Item(local[i].getAttribute("file"), saveAsProfileHandler);
			}
		}
		sapMenu.items = items;

		//We have changed the menu system; reset the pointers.
		menuBar.setPointers();
	}
}

function createTable() {
	var main = document.getElementById("main");
	var div = document.createElement("DIV");
	div.className = "centered";
	var table = document.createElement("TABLE");
	table.setAttribute("border", "1");
	table.setAttribute("id", "row_table");
	tbody = document.createElement("TBODY");
	table.appendChild(tbody);
	div.appendChild(table);
	main.appendChild(div);

	//put in the header row
	tbody.appendChild(createHeaderRow());

	//now get the specified script from the server
	var url = contextURL + scriptPath;
	var qs = "p=" + pipe + "&s=" + stage;
	req = new AJAX();
	req.GET(url, qs+"&"+req.timeStamp(), null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml.firstChild;

		//do the params
		var x = getArray(root.getElementsByTagName("p"));
		x.sort(sortByT);
		for (var i=0; i<x.length; i++) {
			var t = x[i].getAttribute("t").toUpperCase();
			var value = x[i].firstChild;
			value = (value) ? value.nodeValue : "";
			tr = tbody.appendChild(createParamRow(t, value));
		}

		//do the elements
		var x = getArray(root.getElementsByTagName("e"));
		x.sort(sortByT);
		for (var i=0; i<x.length; i++) {
			var en = (x[i].getAttribute("en") == "T");
			var n = x[i].getAttribute("n");
			var t = fixTag(x[i].getAttribute("t"));
			var value = x[i].firstChild;
			value = (value) ? value.nodeValue : "";
			tr = tbody.appendChild(createElementRow(n, t, value));
			if (en) setCheckboxState(tr, en);
		}

		//do the global keep instructions
		var x = getArray(root.getElementsByTagName("k"));
		x.sort(sortByT);
		for (var i=0; i<x.length; i++) {
			var en = (x[i].getAttribute("en") == "T");
			var t = x[i].getAttribute("t");
			var n;
			if (t.indexOf("safe") == -1) {
				t = fixGroup(x[i].getAttribute("t"));
				n = "Keep group "+t;
			}
			else {
				n = "Keep safe private elements";
			}
			tr = tbody.appendChild(createGlobalRow("k", n, t));
			if (en) setCheckboxState(tr, en);
		}

		//do the global remove instructions
		var x = getArray(root.getElementsByTagName("r"));
		x.sort(sortByT);
		for (var i=0; i<x.length; i++) {
			var en = (x[i].getAttribute("en") == "T");
			var t = x[i].getAttribute("t");
			var n;

			if (t == "curves") n="Remove curves";
			else if (t == "overlays") n="Remove overlays";
			else if (t == "privategroups") n="Remove private groups";
			else if (t == "unspecifiedelements") n="Remove unchecked elements";

			tr = tbody.appendChild(createGlobalRow("r", n, t));
			if (en) setCheckboxState(tr, en);
		}
	}
	else alert("Unable to load the anonymizer script.");
}

//Method to get an array from a NodeList so we can sort it.
//If the list is null, return a zero-length array.
function getArray(list) {
	if (list != null) {
		var x = new Array();
		var length = list.length;
		for (var i=0; i<length; i++) {
			x[i] = list[i];
		}
		return x;
	}
	return new Array();
}

//Method to return the case-insensitive order
//of two nodes based on their "t" attributes.
function sortByT(a, b) {
	var aname = a.getAttribute("t").toLowerCase();
	var bname = b.getAttribute("t").toLowerCase();
	if (aname < bname) return -1;
	if (aname > bname) return +1;
	return 0;
}

//Row creation functions
function createHeaderRow() {
	var tr = document.createElement("TR");

	var th1 = document.createElement("TH");
	th1.className = "lt";
	th1.appendChild(document.createTextNode("Select"));

	var th2 = document.createElement("TH");
	th2.className = "ct";
	th2.appendChild(document.createTextNode("Element"));

	var th3 = document.createElement("TH");
	th3.className = "rt";
	th3.appendChild(document.createTextNode("Script"));

	tr.appendChild(th1);
	tr.appendChild(th2);
	tr.appendChild(th3);
	return tr;
}

function createParamRow(tag, value) {
	var tr = document.createElement("TR");
	tr.row_type = "p";
	tr.row_t = tag;

	tr.appendChild(document.createElement("TD"));

	var td = document.createElement("TD");
	tr.appendChild(td);
	td.className = "ct";
	td.appendChild(document.createTextNode(tag));

	td = document.createElement("TD");
	tr.appendChild(td);
	td.className = "rt";
	var input = document.createElement("INPUT");
	input.className = "tx";
	td.appendChild(input);
	setInputText(tr, value);

	return tr;
}

function createElementRow(name, tag, value) {
	var tr = document.createElement("TR");
	tag = fixTag(tag);
	tr.row_type = "e";
	tr.row_n = name;
	tr.row_t = tag;

	var td = document.createElement("TD");
	tr.appendChild(td);
	td.className = "lt";
	var cb = document.createElement("INPUT");
	cb.type = "checkbox";
	td.appendChild(cb);

	var td = document.createElement("TD");
	tr.appendChild(td);
	td.className = "ct";
	td.appendChild(document.createTextNode("["));
	var span = document.createElement("SPAN");
	span.className = "tag";
	td.appendChild(span);
	span.appendChild(document.createTextNode(tag.substring(0,4) + "," + tag.substring(4)));
	td.appendChild(document.createTextNode("] "));
	td.appendChild(document.createTextNode(name));

	td = document.createElement("TD");
	tr.appendChild(td);
	td.className = "rt";
	var input = document.createElement("INPUT");
	input.type = "text";
	td.appendChild(input);
	input.className = "tx";
	setInputText(tr, value);

	return tr;
}

function createGlobalRow(type, name, tag) {
	var tr = document.createElement("TR");
	tr.row_type = type;
	tr.row_n = name;
	tr.row_t = tag;

	var td = document.createElement("TD");
	tr.appendChild(td);
	td.className = "lt";
	var cb = document.createElement("INPUT");
	cb.type = "checkbox";
	td.appendChild(cb);

	td = document.createElement("TD");
	tr.appendChild(td);
	td.className = "ct";
	td.colSpan = 2;
	td.appendChild(document.createTextNode(name));

	return tr;
}

function getXML(acceptAll) {
	var xml = "<script>\n";
	var row = tbody.firstChild;
	while (row != null) {
		if ((row.nodeType == 1) && (row.tagName == "TR")) {
			var type = row.row_type;
			if (type == "p") xml += getParamXML(row);
			else if ((type == "e") && (acceptAll || getCheckboxState(row))) xml += getElementXML(row);
			else if ((type == "k") && (acceptAll || getCheckboxState(row))) xml += getKeepXML(row);
			else if ((type == "r") && (acceptAll || getCheckboxState(row))) xml += getRemoveXML(row);
		}
		row = row.nextSibling;
	}
	xml += "</script>";
	return xml;
}

function getParamXML(row) {
	return " <p t=\""+row.row_t+"\">" + getInputText(row) + "</p>\n";
}

function getElementXML(row) {
	return " <e en=\""+getCheckboxText(row)+"\" t=\""+row.row_t+"\" n=\""+row.row_n+"\">" + getInputText(row) + "</e>\n";
}

function getKeepXML(row) {
	return " <k en=\""+getCheckboxText(row)+"\" t=\""+row.row_t+"\">" + row.row_n + "</k>\n";
}

function getRemoveXML(row) {
	return " <r en=\""+getCheckboxText(row)+"\" t=\""+row.row_t+"\">" + row.row_n + "</r>\n";
}

//Important: In the following five routines,
//we are taking advantage of the fact that
//the HTML was generated programmatically,
//so we know that there are no intervening
//TextNodes.
function setCheckboxState(row, value) {
	row.firstChild.firstChild.checked = value;
}

function getCheckboxState(row) {
	return row.firstChild.firstChild.checked;
}

function getCheckboxText(row) {
	return getCheckboxState(row) ? "T" : "F";
}

function setInputText(row, value) {
	value = (value) ? value : "";
	var tb = row.firstChild.nextSibling.nextSibling.firstChild;
	tb.value = value;
}

function getInputText(row) {
	var tb = row.firstChild.nextSibling.nextSibling.firstChild;
	return tb.value;
}

//Handlers for closing the page
//
function checkSave(event, item) {
	var xml = getXML(true);
	if (xml != savedXML) {
		showTextDialog("checkSave", 350, 225, "Are you sure?", "/icons/closebox.gif", "Close",
			"The document has changed since it was last saved. "
			+ "Are you sure you want to exit without saving it?", close, hidePopups);
	}
	else close();
}

function close() {
	hidePopups();
	var xml = getXML(true);
	window.open(closeboxHome,"_self");
}

//Handlers for the File menu
//
function save(event, item) {
	savedXML = getXML(true);
	var qs = "p=" + pipe + "&s=" + stage;
	if (closeboxURL == "") qs += "&suppress";
	qs += "&xml="+encodeURIComponent(savedXML);
	var req = new AJAX();
	req.POST(contextURL + scriptPath, qs, displayResult);
}

function saveAsProfileHandler(event, item) {
	var profileName = item.title;
	saveProfile(profileName);
}

function newProfileHandler(event, item) {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("Profile name:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 210, "New Profile", "/icons/closebox.gif", "Create New Profile", div, createNewProfile, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}

function createNewProfile(event) {
	var name = document.getElementById("etext1").value;
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);
	if (name != "") saveProfile(name);
}

function saveProfile(name) {
	var xml = getXML(false);
	var namePath = "/" + encodeURIComponent(name);
	var qs = "";
	if (closeboxURL == "") qs += "suppress&";
	qs += "xml="+encodeURIComponent(xml);
	var req = new AJAX();
	req.POST(contextURL + profilePath + namePath, qs, displayResult);
}

function displayResult(req) {
	if (req.success()) {
		var result = req.responseText();
		if (result == "OK") {
			alert("The script was saved successfully.");
			createProfilesMenus();
		}
		else alert(result);
	}
}

//Handlers for the View menu
//
//Hide all the element rows that are not checked
function selectedHandler(event, item) {
	var row = tbody.firstChild;
	while (row != null) {
		if ((row.nodeType == 1) &&  (row.tagName == "TR")) {
			if (row.row_type == "e") {
				row.style.display = getCheckboxState(row) ? "" : "none"; //remove "block" for Firefox
			}
		}
		row = row.nextSibling;
	}
}

//Show all element rows
function allHandler(event, item) {
	var row = tbody.firstChild;
	while (row != null) {
		if ((row.nodeType == 1) &&  (row.tagName == "TR")) {
			row.style.display = ""; //remove "block" for Firefox
		}
		row = row.nextSibling;
	}
}

function gotoHandler(event, item) {
	var group = item.title;
	var row = tbody.firstChild;
	if (document.body.firstChild.scrollIntoView) {
		//IE compatible
		var lastrow = row;
		while (row != null) {
			if ((row.nodeType == 1) &&  (row.tagName == "TR") && (row.row_type == "e")) {
				var g = row.row_t.substr(0,4);
				if (g >= group) {
					row.scrollIntoView();
					document.body.firstChild.scrollIntoView();
					return;
				}
				else lastrow = row;
			}
			row = row.nextSibling;
		}
		lastrow.scrollIntoView();
		document.body.firstChild.scrollIntoView();
	}
	else {
		//non-IE-compatible
		while (row != null) {
			if ((row.nodeType == 1) &&  (row.tagName == "TR") && (row.row_type == "e")) {
				var g = row.row_t.substr(0,4);
				row.style.display = (g == group) ? "" : "none"; //remove "block" for Firefox
			}
			row = row.nextSibling;
		}
	}
}

//Handler for the Apply Profiles menu
//
var enableRows;
function applyProfileHandler(event, item) {
	var libraryPath = "/" + item.parentMenu.title.substr(0,5).toLowerCase();
	var namePath = "/" + encodeURIComponent(item.title);
	var url = contextURL + profilePath + libraryPath + namePath;
	req = new AJAX();
	req.GET(url, req.timeStamp(), null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml.firstChild;

		//get the rows in the table
		var rows = tbody.getElementsByTagName("TR");
		var currentRow = 1; //skip the header row

		//get an array of rows to enable
		enableRows = new Array();

		//do the params
		var x = getArray(root.getElementsByTagName("p"));
		x.sort(sortByT);
		for (var i=0; i<x.length; i++) {
			var t = x[i].getAttribute("t").toUpperCase();
			var value = x[i].firstChild;
			value = (value) ? value.nodeValue : "";

			//find the place in the table
			var row = null;
			for ( ; currentRow<rows.length; currentRow++) {
				var r = rows[currentRow];
				if (((r.row_type == "p") && (r.row_t == t))
						|| (r.row_type != "p")
							|| (r.row_t > t)) {
					row = r; break;
				}
			}
			if (row && (row.row_type == "p") && (row.row_t == t)) {
				if (value != "") setInputText(row, value);
			}
			else {
				var tr = tbody.appendChild(createParamRow(t, value));
				tbody.insertBefore(tr, row);
			}
		}

		//do the elements
		var x = getArray(root.getElementsByTagName("e"));
		x.sort(sortByT);
		for (var i=0; i<x.length; i++) {
			var en = (x[i].getAttribute("en") == "T");
			if (en) {
				var n = x[i].getAttribute("n");
				var t = fixTag(x[i].getAttribute("t"));
				var value = x[i].firstChild;
				value = (value) ? value.nodeValue : "";

				//find the place in the table
				var row = null;
				for ( ; currentRow<rows.length; currentRow++) {
					var r = rows[currentRow];
					if (r.row_type == "p") {
						//skip params
					}
					else if (((r.row_type == "e") && (r.row_t == t))
								|| (r.row_type != "e")
									|| (r.row_t > t)) {
						row = r; break;
					}
				}
				if (row && (row.row_type == "e") && (row.row_t == t)) {
					enableRows[enableRows.length] = row;
					setInputText(row, value);
				}
				else {
					var tr = tbody.appendChild(createElementRow(n, t, value));
					tbody.insertBefore(tr, row);
					enableRows[enableRows.length] = tr;
				}
			}
		}

		//do the keep commands
		var x = getArray(root.getElementsByTagName("k"));
		x.sort(sortByT);
		for (var i=0; i<x.length; i++) {
			var en = (x[i].getAttribute("en") == "T");
			if (en) {
				var n = x[i].firstChild.nodeValue;
				var t = fixGroup(x[i].getAttribute("t"));

				//find the place in the table
				var row = null;
				for ( ; currentRow<rows.length; currentRow++) {
					var r = rows[currentRow];
					if ((r.row_type == "p") || (r.row_type == "e")) {
						//skip params and elements
					}
					else if (((r.row_type == "k") && (r.row_t == t))
								|| (r.row_type != "k")
									|| (r.row_t > t)) {
						row = r; break;
					}
				}
				if (row && (row.row_type == "k") && (row.row_t == t)) {
					enableRows[enableRows.length] = row;
				}
				else {
					var tr = tbody.appendChild(createGlobalRow("k", n, t));
					tbody.insertBefore(tr, row);
					enableRows[enableRows.length] = tr;
				}
			}
		}

		//do the remove commands
		var x = getArray(root.getElementsByTagName("r"));
		x.sort(sortByT);
		for (var i=0; i<x.length; i++) {
			var en = (x[i].getAttribute("en") == "T");
			if (en) {
				var n = x[i].firstChild.nodeValue;
				var t = x[i].getAttribute("t");

				//Find the place in the table.
				//Note that remove commands are not inserted
				//if they are not already in the table, so this
				//algorithm is a little different from the ones
				//for the other element types.
				for ( ; currentRow<rows.length; currentRow++) {
					var row = rows[currentRow];
					var type = row.row_type;
					if ((type == "p") || (type == "e") || (type == "k")) {
						//skip params, elements, and keep commands
					}
					else if ((row.row_type == "r") && (row.row_t == t)) {
						enableRows[enableRows.length] = row;
						break;
					}
				}
			}
		}

		//Set the checkboxes after a suitable delay
		window.setTimeout(setRowEnables, 500);
	}
}

//Set the checkboxes in an array of rows
function setRowEnables() {
	for (var i=0; i<enableRows.length; i++) {
		setCheckboxState(enableRows[i], true);
	}
}

//Handlers for the Edit menu
//
function newParam(event, item) {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("Parameter name:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 240, "New Parameter", "/icons/closebox.gif", "Create New Parameter", div, createNewParam, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}

function createNewParam(event) {
	var text = document.getElementById("etext1");
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var tag = text.value.toUpperCase().replace(/^\s+|\s+$/g, '').replace(/\s+/g, '_');
	if (tag != "") {
		var rows = tbody.getElementsByTagName("TR");
		for (var i=1; i<rows.length; i++) {
			var row = rows[i];
			if ((row.row_type == "p") && (row.row_t == tag)) return;
			if ((row.row_type != "p") || (row.row_t > tag)) {
				var newrow = createParamRow(tag, "");
				row.parentNode.insertBefore(newrow, row);
				return;
			}
		}
	}
}

function removeParam(event, item) {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	div = document.createElement("DIV");
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("Parameter name:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 205, "Remove Parameter", "/icons/closebox.gif", "Remove Parameter", div, deleteParam, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}

function deleteParam(event) {
	var text = document.getElementById("etext1");
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var tag = text.value;
	if (tag != "") {
		tag = tag.toUpperCase().replace(/^\s+|\s+$/g, '').replace(/\s+/g, '_');
		var rows = tbody.getElementsByTagName("TR");
		for (var i=1; i<rows.length; i++) {
			var row = rows[i];
			if ((row.row_type == "p") && (row.row_t == tag)) {
				row.parentNode.removeChild(row);
				return;
			}
		}
	}
}

function removeAllParams(event, item) {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	showTextDialog("eDiv", 375, 235, "Are you sure?", "/icons/closebox.gif", "Remove All Parameters",
		"Are you sure you wish to remove all parameters? ", deleteAllParams, hidePopups);
}

function deleteAllParams(event) {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var rows = tbody.getElementsByTagName("TR");
	for (var i=rows.length-1; i>=0; i--) {
		var row = rows[i];
		if (row.row_type == "p") {
			row.parentNode.removeChild(row);
		}
	}
}

function newElement(event, item) {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	div = document.createElement("DIV");
	var p = document.createElement("P");
	p.className = "centeredblock";
	div.appendChild(p);

	var tbl = document.createElement("TABLE");
	p.appendChild(tbl);
	var tb = document.createElement("TBODY");
	tbl.appendChild(tb);

	var tr = document.createElement("TR");
	tb.appendChild(tr);
	var td = document.createElement("TD");
	tr.appendChild(td);
	td.appendChild(document.createTextNode("Group,Element:\u00A0"));
	var td = document.createElement("TD");
	tr.appendChild(td);
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	td.appendChild(text);

	tr = document.createElement("TR");
	tb.appendChild(tr);
	td = document.createElement("TD");
	tr.appendChild(td);
	td.appendChild(document.createTextNode("Element name:\u00A0"));
	td = document.createElement("TD");
	tr.appendChild(td);
	text = document.createElement("INPUT");
	text.id = "etext2";
	text.className = "textbox";
	td.appendChild(text);

	showDialog("ediv", 400, 275, "New Element", "/icons/closebox.gif", "Create New Element", div, createNewElement, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}

function createNewElement(event) {
	var tag = fixTag( document.getElementById("etext1").value );
	var name = document.getElementById("etext2").value.replace(/\s+/g, '');
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	if (tag == null) {
		alert("A tag must be in any of these forms:\n\n"
			+ "    ggggeeee\n"
			+ "    gggg,eeee\n\n"
			+ "and for private groups, also:\n\n"
			+ "    gggg[blockID]ee\n"
			+ "    gggg00[blockID]\n\n"
			+ "    gggg[blockID]ee\n"
			+ "    gggg,00[blockID]\n\n"
			+ "where g and e are hexadecimal.");
		return;
	}
	if (name.length == 0) {
		alert("You must specify a name for the element.");
		return;
	}

	var rows = tbody.getElementsByTagName("TR");
	for (var i=1; i<rows.length; i++) {
		var row = rows[i];
		if (row.row_type == "p") {
			//skip parameters
		}
		else if ((row.row_type == "e") && (row.row_t == tag)) {
			row.row_n = name;
			return;
		}
		else if ((row.row_type != "e") || (row.row_t > tag)) {
			var newrow = createElementRow(name, tag, "");
			row.parentNode.insertBefore(newrow, row);
			return;
		}
	}
}

function removeElement(event, item) {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	div = document.createElement("DIV");
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("Element tag:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 210, "Remove Element", "/icons/closebox.gif", "Remove Element", div, deleteElement, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}

function deleteElement(event) {
	var text = document.getElementById("etext1");
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var tag = fixTag(text.value);
	if (tag != null) {
		var rows = tbody.getElementsByTagName("TR");
		for (var i=1; i<rows.length; i++) {
			var row = rows[i];
			if ((row.row_type == "e") && (row.row_t == tag)) {
				row.parentNode.removeChild(row);
				return;
			}
		}
	}
}

function newKeepGroup(event, item) {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var div = document.createElement("DIV");
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("Group:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 240, "New Keep Group", "/icons/closebox.gif", "Create New Keep Group", div, createNewKeepGroup, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}

function createNewKeepGroup(event) {
	var text = document.getElementById("etext1");
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var group = fixGroup(text.value);
	if (group != null) {
		var name = "Keep group "+group;
		var rows = tbody.getElementsByTagName("TR");
		for (var i=1; i<rows.length; i++) {
			var row = rows[i];
			if ((row.row_type == "p") || (row.row_type == "e")) {
				//skip params and elements
			}
			else if ((row.row_type == "k") && (row.row_t == group)) return;
			else if ((row.row_type != "k") || (row.row_t == "safeprivateelements") || (row.row_t > group)) {
				var newrow = createGlobalRow("k", name, group) ;
				row.parentNode.insertBefore(newrow, row);
				return;
			}
		}
	}
}

function removeKeepGroup(event, item) {
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	div = document.createElement("DIV");
	var p = document.createElement("P");
	p.className = "centeredblock";
	p.appendChild(document.createTextNode("Group:\u00A0"));
	var text = document.createElement("INPUT");
	text.id = "etext1";
	text.className = "textbox";
	p.appendChild(text);
	div.appendChild(p);

	showDialog("ediv", 400, 250, "Remove Keep Group", "/icons/closebox.gif", "Remove Keep Group", div, deleteKeepGroup, hidePopups);
	window.setTimeout("document.getElementById('etext1').focus()", 500);
}

function deleteKeepGroup(event) {
	var text = document.getElementById("etext1");
	var div = document.getElementById("ediv");
	if (div) div.parentNode.removeChild(div);

	var group = fixGroup(text.value);
	if (group != null) {
		var rows = tbody.getElementsByTagName("TR");
		for (var i=1; i<rows.length; i++) {
			var row = rows[i];
			if ((row.row_type == "k") && (row.row_t == group)) {
				row.parentNode.removeChild(row);
				return;
			}
		}
	}
}

function createKeepSafePrivateElements(event, item) {
	var group = "safeprivateelements";
	var name = "Keep safe private elements";
	var rows = tbody.getElementsByTagName("TR");
	for (var i=1; i<rows.length; i++) {
		var row = rows[i];
		if ((row.row_type == "p") || (row.row_type == "e")) {
			//skip params and elements
		}
		else if ((row.row_type == "k") && (row.row_t == group)) return;
		else if ((row.row_type != "k") || (row.row_t > group)) {
			var newrow = createGlobalRow("k", name, group) ;
			row.parentNode.insertBefore(newrow, row);
			return;
		}
	}
}

hexPattern = /([0-9a-fA-F]{8})/;
hexCommaPattern = /([0-9a-fA-F]{4}),([0-9a-fA-F]{4})/;
pgPattern = /([0-9a-fA-F]{4})[,]{0,1}(\[.*\])([0-9a-fA-F]{2})/;  //private group data element
pcPattern = /([0-9a-fA-F]{4})[,]{0,1}00(\[.*\])/;  //private creator element

function fixTag(tag) {
	var x = tag.match(hexPattern);
	if (x) {
		return x[0].toLowerCase();
	}
	x = tag.match(hexCommaPattern);
	if (x) {
		return (x[1] + x[2]).toLowerCase();
	}
	x = tag.match(pgPattern);
	if (x) {
		var g = parseInt(x[1], 16);
		if ((g & 1) != 0) {
			return x[1].toLowerCase() + x[2].toUpperCase() + x[3].toLowerCase();
		}
	}
	x = tag.match(pcPattern);
	if (x) {
		var g = parseInt(x[1], 16);
		if ((g & 1) != 0) {
			return x[1].toLowerCase() + "00" + x[2].toUpperCase();
		}
	}
	return null;
}

function fixGroup(group) {
	if (group == null) return null;
	group = group.toLowerCase().replace(/[^0-9a-f]/g, '');
	if (group.length == 0) return null;
	var n = group.length;
	if (n == 8) return group;
	if (n < 8) return "0000".substr(0, 4-n) + group;
	return group.substr(0, 4);
}

function deselectHandler(event, item) {
	var row = tbody.firstChild;
	while (row != null) {
		if ((row.nodeType == 1) &&  (row.tagName == "TR")) {
			var type = row.row_type;
			if ((type == "e") || (type == "k") || (type == "r")) {
				setCheckboxState(row, false);
			}
		}
		row = row.nextSibling;
	}
}

//Handlers for the Help menu
//
function showWiki(event, item) {
	window.open("http://mircwiki.rsna.org/index.php?title=CTP-The_RSNA_Clinical_Trial_Processor","help");
}
function showAnonymizer(event, item) {
	window.open("http://mircwiki.rsna.org/index.php?title=The_CTP_DICOM_Anonymizer","help");
}
function showAnonymizerConfigurator(event, item) {
	window.open("http://mircwiki.rsna.org/index.php?title=The_CTP_DICOM_Anonymizer_Configurator","help");
}
