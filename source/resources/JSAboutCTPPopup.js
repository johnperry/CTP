function showAboutPopup() {
	var id = "aboutPopupID";
	var pop = document.getElementById(id);
	if (pop) pop.parentNode.removeChild(pop);

	var w = 600;
	var h = 600;

	var div = document.createElement("DIV");
	div.className = "content";
	var h1 = document.createElement("H1");
	h1.appendChild(document.createTextNode("RSNA CTP"));
	h1.style.fontSize = "24pt";
	div.appendChild(h1);
	div.appendChild(document.createTextNode("\u00A0"));

	div.appendChild(document.createTextNode("\u00A0"));
	p = document.createElement("P");
	var iframe = document.createElement("IFRAME");
	iframe.style.width = w - 30;
	iframe.style.height = h - 120;
	iframe.src = "/credits.html";
	p.appendChild(iframe);
	div.appendChild(p);

	var closebox = "/icons/closebox.gif";
	showDialog(id, w, h, "About RSNA CTP", closebox, null, div, null, null);
}
