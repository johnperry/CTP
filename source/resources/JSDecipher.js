var keyText = "enter key";
function decipher() {
	var theEvent = event ? event : window.event;
	var source = (document.all) ? theEvent.srcElement : theEvent.target;

	var row = source;
	while (row.nodeName != 'TR') row = row.parentNode;

	var lastTD = row.lastChild;
	while (lastTD.nodeName != 'TD') lastTD = lastTD.previousSibling;
	while (lastTD.firstChild) lastTD.removeChild(lastTD.firstChild);

	lastTD.appendChild(document.createTextNode("Key: "));

	var key = document.createElement("INPUT");
	key.setAttribute("type","text");
	key.setAttribute("value", keyText);
	key.setAttribute("size", "25");
	lastTD.appendChild(key);
	key.focus();
	key.select();

	lastTD.appendChild(document.createTextNode(" "));

	var go = document.createElement("INPUT");
	go.setAttribute("type","button");
	go.setAttribute("value", "Decipher");
	go.onclick = callDecipher;
	lastTD.appendChild(go);
}

function callDecipher() {
	var theEvent = event ? event : window.event;
	var source = (document.all) ? theEvent.srcElement : theEvent.target;

	keyText = source.previousSibling.previousSibling.value;

	var row = source;
	while (row.nodeName != 'TR') row = row.parentNode;

	var td = row.firstChild;
	while (td.nodeName != "TD") td = td.nextSibling;
	var element = td;
	while (element.nodeType != 3) element = element.firstChild;

	var result = get(filepath, element.nodeValue, keyText);
	var font = document.createElement("FONT");
	font.setAttribute("color","red");
	font.appendChild(document.createTextNode(result));

	td = source.parentNode;
	while (td.firstChild) td.removeChild(td.firstChild);
	td.appendChild(font);
}

function get(file, element, key) {
	var req = new AJAX();
	var url = "/decipher";
	var qs = "file=" + escape(file)
			+ "&elem=" + escape(element)
			+ "&key=" + escape(key)
			+ "&timestamp=" + new Date().getTime();
	req.GET(url, qs, null);
	if (req.success()) return req.responseText();
	else return "Unable to decipher the element ["+req.status()+"]";
}
