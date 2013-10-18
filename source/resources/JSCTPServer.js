//Class to encapsulate a user, providing
//access to the user's roles on the server.
function CTPServer() {
	this.ip = "";
	this.port = "";
	this.build = "";
	this.java = "";
	this.configXML = null;

	var req = new AJAX();

	req.GET("/server", req.timeStamp(), null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml.documentElement;
		this.ip = root.getAttribute("ip");
		this.port = root.getAttribute("port");
		this.java = root.getAttribute("java");
		this.build = root.getAttribute("build");
		var child = root.firstChild;
		while (child) {
			if ((child.nodeType == 1) && (child.nodeName == "Configuration")) {
				this.configXML = child;
				break;
			}
			child = child.nextSibling;
		}
	}
}

CTPServer.prototype.hasStage = function(javaClass) {
	if (this.configXML) {
		var pipe = this.configXML.firstChild;
		while (pipe) {
			if ((pipe.nodeType == 1) && (pipe.nodeName == "Pipeline") && (pipe.getAttribute("enabled") != "no")) {
				var stage = pipe.firstChild;
				while (stage) {
					if ((stage.nodeType == 1) && (stage.getAttribute("class") == javaClass)) {
						return true;
					}
					stage = stage.nextSibling;
				}
			}
			pipe = pipe.nextSibling;
		}
	}
	return false;
}

CTPServer.prototype.hasStageType = function(type) {
	var req = new AJAX();
	req.GET("/server", "type="+type+"&"+req.timeStamp(), null);
	if (req.success()) {
		var xml = req.responseXML();
		var root = xml.documentElement;
		return (root.nodeName == "true");
	}
	return false
}
