function setFocus() {
	var x = document.getElementById('value[1]');
	if (x) x.focus();
}

window.onload = setFocus;

function submitForm() {
	var form = document.getElementById("FormID");
	form.submit();
}
