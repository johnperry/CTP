function findObject(obj) {
	var objPos = new Object();
	objPos.obj = obj;
	objPos.h = obj.offsetHeight;
	objPos.w = obj.offsetWidth;
	var curleft = 0;
	var curtop = 0;
	if (obj.offsetParent) {
		curleft = obj.offsetLeft
		curtop = obj.offsetTop
		while (obj = obj.offsetParent) {
			curleft += obj.offsetLeft
			curtop += obj.offsetTop
		}
	}
	objPos.x = curleft;
	objPos.y = curtop;
	return objPos;
}

function getSource(theEvent) {
	return (document.all) ?
		theEvent.srcElement : theEvent.target;
}

//--------- The Image class ------------
function Image(
		theURL, theSeries, theAcquisition, theInstance, theRows, theColumns) {
	this.url = theURL;
	this.series = theSeries;
	this.acquisition = theAcquisition;
	this.instance = theInstance;
	this.rows = theRows;
	this.columns = theColumns;
}
//--------- End of the Image class ------------

//--------- The Stack class ------------
function Stack(current, first, last, theDiv) {
	this.currentIndex = current;
	this.firstIndex = first;
	this.lastIndex = last;
	this.div = theDiv;
	this.div.stack = this;
}

function StackPrototypeMove(increment) {
	this.currentIndex += increment;
	if (this.currentIndex < this.firstIndex) this.currentIndex = this.firstIndex;
	if (this.currentIndex > this.lastIndex) this.currentIndex = this.lastIndex;
}
Stack.prototype.move = StackPrototypeMove;

function StackPrototypeShowImage() {
	if (images.length > 0) {
		if (this.currentIndex < this.firstIndex) this.currentIndex = this.firstIndex;
		if (this.currentIndex > this.lastIndex) this.currentIndex = this.lastIndex;
		var image = images[this.currentIndex];
		var textDiv = this.div.firstChild;
		while (textDiv.firstChild) textDiv.removeChild(textDiv.firstChild);
		var text = document.createTextNode(image.series+"."+image.acquisition+"."+image.instance);
		textDiv.appendChild(text);
		var img = textDiv.nextSibling;
		img.setAttribute("src", image.url+"?format=jpeg");
	}
}
Stack.prototype.showImage = StackPrototypeShowImage;

function StackPrototypeSetWheelDriver() {
	if (this.div.addEventListener) {
		this.div.addEventListener('DOMMouseScroll', this.wheel, false); //Mozilla
		this.div.addEventListener("mousewheel", this.wheel, false);
	}
	else
		this.div.onmousewheel = this.wheel; //IE + Opera
}
Stack.prototype.setWheelDriver = StackPrototypeSetWheelDriver;

function StackPrototypeRemoveWheelDriver() {
	if (this.div.removeEventListener) {
		this.div.removeEventListener('DOMMouseScroll', this.wheel, false); //Mozilla
		this.div.removeEventListener("mousewheel", this.wheel, false);
	}
	else
		this.div.onmousewheel = null; //IE + Opera
}
Stack.prototype.removeWheelDriver = StackPrototypeRemoveWheelDriver;

function StackPrototypeWheel(event){
	var delta = 0;
	if (!event) event = window.event;
	var target = event.target;
	if (!target) target = event.srcElement;
	while (!target.stack) target = target.parentNode;
	if (event.wheelDelta) {
		// IE + Opera
	    delta = event.wheelDelta/120;
		if (window.opera) delta = -delta;
	}
	else if (event.detail) {
		// Mozilla
		delta = -event.detail/3;
	}
	if (delta < 0) delta = -1;
	if (delta > 0) delta = +1;
	target.stack.move(-delta);
	target.stack.showImage();
	if (event.preventDefault) event.preventDefault();
	event.returnValue = false;
}
Stack.prototype.wheel = StackPrototypeWheel;
//--------- End of the Stack class ------------

var mode;
var fullStack;		//a stack containing everything, in order
var seriesStacks;	//an array of stacks each containing one series, in order

function createStacks() {
	fullStack = new Stack(0, 0, images.length-1, getImageDiv(images[0]));
	seriesStacks = new Array();
	var i = 0;
	var k = 0;
	while (k < images.length) {
		var stack = getSeriesStack(k);
		seriesStacks[i++] = stack;
		k = stack.lastIndex + 1;
	}
}

function getSeriesStack(k) {
	var kk = k + 1;
	while ((kk < images.length) && (images[k].series == images[kk].series)) kk++;
	return new Stack(k, k, kk-1, getImageDiv(images[k]));
}

function tile() {
	if (mode != "tile") {
		mode = "tile";
		var main = document.getElementById("main");
		while (main.firstChild) main.removeChild(main.firstChild);
		for (var i=0; i<images.length; i++) {
			main.appendChild(getImageDiv(images[i]));
		}
	}
}

function stack() {
	if (mode != "stack") {
		mode = "stack";
		var main = document.getElementById("main");
		while (main.firstChild) main.removeChild(main.firstChild);
		main.appendChild(fullStack.div);
		fullStack.showImage();
		fullStack.setWheelDriver();
	}
}

function series() {
	if (mode != "series") {
		mode = "series";
		var main = document.getElementById("main");
		while (main.firstChild) main.removeChild(main.firstChild);
		for (var i=0; i<seriesStacks.length; i++) {
			var stack = seriesStacks[i];
			main.appendChild(stack.div);
			stack.showImage();
			stack.setWheelDriver();
		}
	}
}

function getImageDiv(image) {
	var div = document.createElement("DIV");
	if (document.all) div.style.display = "inline"; //IE
	else div.style.display = "inline-block"; //non-IE
	if (image) {
		div.style.width = image.columns + 2;
		div.style.height = image.rows + 2;

		var x = document.createElement("SPAN");
		x.style.position = "absolute";
		var text = document.createTextNode(image.series+"."+image.acquisition+"."+image.instance);
		x.appendChild(text);
		div.appendChild(x);

		var img = document.createElement("IMG");
		img.setAttribute("src", image.url+"?format=jpeg");
		div.appendChild(img);
	}
	return div;
}

//--------- Code to preload the images ------------
var loadReq;

function getImages() {
	loadReq = new AJAX();
	getImage(0);
}

var nextToLoad;
function getImage(i) {
	if (i < images.length) {
		var url = images[i].url;
		nextToLoad = i + 1;
		loadReq.GET(url, "format=jpeg", getNextImage);
	}
}

function getNextImage() {
	if (loadReq.success()) {
		window.setTimeout("getImage(nextToLoad);",10);
	}
}
//--------- End of code to preload images ----------

function windowLoaded() {
	createStacks();
	stack();
	getImages();
}

window.onload = windowLoaded;
