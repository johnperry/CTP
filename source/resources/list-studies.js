function hideRow(theEvent) {
	var tr = ((document.all) ? theEvent.srcElement : theEvent.target);
	while (tr.tagName != "TR") tr = tr.parentNode;
	tr.parentNode.removeChild(tr);
}

function deleteAll() {
	showTextDialog("checkSave", 400, 185, "Are you sure?", "/icons/closebox.gif", "Delete All Studies",
		"Are you sure you want to delete all studies?", callDelete, hidePopups);
}

function callDelete() {
	window.open("?deleteAll", "_self");
}