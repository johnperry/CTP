function setSizes() {
	var edis = document.getElementById("entrydisplay");
	var esel = document.getElementById("entryselect");

	var bodyPos = findObject(document.body);
	var eselPos = findObject(esel);
	var edisPos = findObject(edis);

	var space = edisPos.y - (eselPos.y + eselPos.h);
	var totalH = (bodyPos.h - eselPos.y) - space - 30;
	var eselH = totalH / 4;
	var edisH = totalH - eselH;
	if (eselH < 60) eselH = 60;
	if (edisH < 60) edisH = 60;

	esel.style.height = eselH;
	edis.style.height = edisH;
}
window.onload = setSizes;
window.onresize = setSizes;

function exportAuditLog() {
	window.open("?export", "_self");
}

function search() {
	var type = "";
	var text = "";
	var searchElements = document.getElementsByName("searchfield");
	for (var i=0; i< searchElements.length; i++) {
		var el = searchElements[i];
		if (el.checked) {
			type = el.value;
			var nextTD = el.parentNode.nextSibling;
			var input = nextTD.getElementsByTagName("INPUT")[0];
			text = input.value;
			break;
		}
	}
	if ((type != "") && ((text != "") || (type == "entry"))) {
		var req = new AJAX();
		var qs = "type="+type+"&text="+text+"&"+req.timeStamp();
		req.POST("/"+context, qs, displayQueryResults);
	}
}

function displayQueryResults(req) {
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml.firstChild;

		var seldiv = document.getElementById("entryselect");
		while (seldiv.firstChild) seldiv.removeChild(seldiv.firstChild);

		var entries = root.getElementsByTagName("entry");
		if (entries.length > 0) {
			for (var i=0; i<entries.length; i++) {
				var entry = entries[i];
				var id = entry.getAttribute("id");
				var time = entry.getAttribute("time");
				var span = document.createElement("SPAN");
				span.entry = id;
				span.appendChild( document.createTextNode( time + " ["+id+"]" ) );
				span.onclick = displayEntry;
				seldiv.appendChild( span );
				seldiv.appendChild( document.createElement( "BR" ) );
			}
		}
		else seldiv.appendChild( document.createTextNode( "No results" ) );
		var disdiv = document.getElementById("entrydisplay");
		while (disdiv.firstChild) disdiv.removeChild(disdiv.firstChild);
	}
}

function displayEntry(theEvent) {
	var event = getEvent(theEvent);
	var source = getSource(event);
	var id = source.entry;

	var req = new AJAX();
	var qs = "entry="+id+"&"+req.timeStamp();
	req.POST("/"+context, qs, displayEntryText);
}

function displayEntryText(req) {
	if (req.success()) {

		var xml = req.responseXML();
		var root = xml.firstChild;

		var disdiv = document.getElementById("entrydisplay");
		while (disdiv.firstChild) disdiv.removeChild(disdiv.firstChild);

		var entry = root.getElementsByTagName("entry")[0];
		if (entry) {
			var id = entry.getAttribute("id");
			var time = entry.getAttribute("time");
			var ctype = entry.getAttribute("contentType");
			var cdata = entry.firstChild;

			var text = cdata.nodeValue;
			text = time+"\nContent Type: "+ctype+"\nEntry ID: "+id+"\n\n"+text;

			var lines = text.split("\n");
			for (var i=0; i<lines.length; i++) {
				lines[i] = lines[i].replace(/\s/g, "\u00a0");
				disdiv.appendChild( document.createTextNode( lines[i] ) );
				disdiv.appendChild( document.createElement( "BR" ) );
			}
		}
		else disdiv.appendChild( document.createTextNode( "No result" ) );
	}
}
