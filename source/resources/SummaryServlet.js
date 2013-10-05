window.onload = loaded;
window.onresize = resize;

function loaded() {
	resize();
}

function resize() {
	var bodyPos = findObject(document.body);
	var status = document.getElementById('status');
	if (status) {
		var statusPos = findObject(status);
		var statusHeight = bodyPos.h - statusPos.y;
		if (statusHeight < 100) statusHeight = 100;
		status.style.height = statusHeight;
	}
}
