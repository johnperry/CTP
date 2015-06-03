var user = null;
var split = null;
var ctpServer = null;

function loaded() {

	if (!IE) {
		var line2 = document.getElementById("line2");
		if (line2) line2.style.marginTop = "-2px";
	}

	user = new User();
	if (user.isLoggedIn) {
		replaceContent("usernameSpan", user.name);
		replaceContent("loginoutAnchor", "Logout");
	}
	else {
		replaceContent("usernameSpan", "Guest");
		replaceContent("loginoutAnchor", "Login");
	}

	//leftDiv, sliderDiv, rightDiv, fillHeight, sliderPosition, forceTopForIE, leftMin, rightMin, changeHandler
	split = new HorizontalSplit("left", "center", "right", true, 185, false, 1, 120);

	ctpServer = new CTPServer();
	replaceContent("serverip", ctpServer.ip);
	replaceContent("serverport", ctpServer.port);
	replaceContent("ctpbuild", ctpServer.build);
	replaceContent("ctpjava", " on Java "+ctpServer.java);

	var isAdmin = user.hasRole("admin");
	setVisibility("Admin", isAdmin);
	if (isAdmin) {
		setVisibility("UserManager", user.usersClassIsXMLFile);
		setVisibility("ObjectTracker", ctpServer.hasStage("org.rsna.ctp.stdstages.ObjectTracker"));
		setVisibility("IDMap", ctpServer.hasStage("org.rsna.ctp.stdstages.IDMap"));
		setVisibility("DatabaseVerifier", ctpServer.hasStage("org.rsna.ctp.stdstages.DatabaseVerifier"));
		setVisibility("DicomAnonymizer", ctpServer.hasStageType("org.rsna.ctp.stdstages.ScriptableDicom"));
		setVisibility("Scriptable", ctpServer.hasStageType("org.rsna.ctp.stdstages.Scriptable"));
	}

	loadConfigurationTree();

	window.onresize = resize;
	loadFrame('/summary?suppress');
	showSessionPopup();
}
window.onload = loaded;

function resize() {
	if (split) split.positionSlider();
}

//************************************************
//Utilities
//************************************************
function replaceContent(id, text) {
	var parent = document.getElementById(id);
	replaceNodeContent(parent, text);
}
function replaceNodeContent(parent, text) {
	if (parent) {
		while (parent.firstChild) parent.removeChild(parent.firstChild);
		parent.appendChild( document.createTextNode(text) );
	}
}
function setVisibility(id, showObject) {
	if (showObject) show(id, "block");
	else hide(id);
}
function hide(id) {
	var node = document.getElementById(id);
	hideNode(node);
}
function hideNode(node) {
	if (node) {
		node.style.visibility = "hidden";
		node.style.display = "none";
	}
}
function show(id, type) {
	var node = document.getElementById(id);
	showNode(node, type);
}
function showNode(node, type) {
	if (node) {
		node.style.visibility = "visible";
		if (type == null) type="block"
		node.style.display = type;
	}
}

//************************************************
//Login/Logout
//************************************************
function loginLogout() {
	if (user.isLoggedIn) {
		if (user.logoutURL == "") logout('/');
		else window.open(user.logoutURL, "_self");
	}
	else {
		if (user.loginURL == "") showLoginPopup('/');
		else window.open(user.loginURL, "_self");
	}
}

//************************************************
//Load a servlet in the right pane
//************************************************
function loadFrame(url) {
	var right = document.getElementById("right");
	while (right.firstChild) right.removeChild(right.firstChild);
	var iframe = document.createElement("IFRAME");
	iframe.src = url;
	right.appendChild(iframe);
}
function clearRight() {
	var right = document.getElementById("right");
	while (right.firstChild) right.removeChild(right.firstChild);
}

//************************************************
//Session popup
//************************************************
function showSessionPopup() {
	var cooks = getCookieObject();
	var ctpCookie = getCookie("CTP", cooks);
	if (ctpCookie == "") {
		setSessionCookie("CTP", "session");
		if (!user.isLoggedIn && (user.loginURL == "")) loginLogout();
	}
}

//************************************************
//Load the configuration in the left pane
//************************************************
function loadConfigurationTree() {
	loadPlugins();
	loadPipelines();
}

function loadPlugins() {
	var div = document.getElementById("Plugins");
	while (div.firstChild) div.removeChild(div.firstChild);
	var config = ctpServer.configXML;
	var pluginIndex = -1;
	var plugin = config.firstChild;
	var header = false;
	while (plugin) {
		if ((plugin.nodeType == 1) && (plugin.tagName == "Plugin")) {
			if (!header) {
				var hdr = document.createElement("DIV");
				hdr.className = "L1";
				hdr.appendChild( document.createTextNode("Plugins") );
				div.appendChild(hdr);
				header = true;
			}
			pluginIndex++;
			div.appendChild(createPlugin(plugin, pluginIndex));
		}
		plugin = plugin.nextSibling;
	}
}

function loadPipelines() {
	var div = document.getElementById("Pipelines");
	while (div.firstChild) div.removeChild(div.firstChild);
	var config = ctpServer.configXML;
	var pipeIndex = -1;
	var pipe = config.firstChild;
	var header = false;
	while (pipe) {
		if ((pipe.nodeType == 1) && (pipe.tagName == "Pipeline") && (pipe.getAttribute("enabled") != "no")) {
			if (!header) {
				var hdr = document.createElement("DIV");
				hdr.className = "L1";
				hdr.appendChild( document.createTextNode("Pipelines") );
				div.appendChild(hdr);
				header = true;
			}
			pipeIndex++;
			div.appendChild(createPipe(pipe, pipeIndex));
			var stageIndex = -1;
			var stage = pipe.firstChild;
			while (stage) {
				if (stage.nodeType == 1) {
					stageIndex++;
					div.appendChild(createStage(stage, pipeIndex, stageIndex));
				}
				stage = stage.nextSibling;
			}
		}
		pipe = pipe.nextSibling;
	}
}

function createPlugin(plugin, p) {
	var div = document.createElement("DIV");
	div.className = "L1y";
	var a = document.createElement("A");
	a.href = "javascript:getPluginSummary('"+p+"')";
	a.appendChild(document.createTextNode(plugin.getAttribute("name")));
	div.appendChild(a);
	return div;
}

function createPipe(pipe, p) {
	var div = document.createElement("DIV");
	div.className = "L1x";
	var a = document.createElement("A");
	a.href = "javascript:getPipeSummary('"+p+"')";
	a.appendChild(document.createTextNode(pipe.getAttribute("name")));
	div.appendChild(a);
	return div;
}

function createStage(stage, p, s) {
	var div = document.createElement("DIV");
	div.className = "L2";
	var a = document.createElement("A");
	a.href = "javascript:getStageSummary('"+p+"','"+s+"')";
	a.appendChild(document.createTextNode(stage.getAttribute("name")));
	div.appendChild(a);
	div.appendChild(document.createElement("BR"));
	return div;
}

//************************************************
//Load the summary pages
//************************************************
function getPipeSummary(p) {
	loadFrame("/summary?p="+p+"&suppress");
}
function getStageSummary(p, s) {
	loadFrame("/summary?p="+p+"&s="+s+"&suppress");
}
function getPluginSummary(x) {
	loadFrame("/summary?plugin="+x+"&suppress");
}
