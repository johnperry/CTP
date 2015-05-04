function showImagePopup(url, filename) {
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
	iframe.src = url;
	para.appendChild(iframe);
	div.appendChild(para);
	var closebox = "/icons/closebox.gif";

	//popupDivId, w, h, title, closeboxFile, heading, div, okHandler, cancelHandler, hide, closeboxHandler
	showDialog(id, w, h, filename, closebox, null, div, null, null, false, hidePopups);
}