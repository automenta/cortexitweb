// Creates a Popup IFrame from the windows current selection.  
// Necessary because a normal GET URL can not hold an arbitrary amount of text (ex: greater than 2000 bytes).

//BOOKMARKLET: 
//javascript:(function(){ document.body.appendChild(document.createElement('script')).src="http://ajax.googleapis.com/ajax/libs/jquery/1.7.1/jquery.min.js";  document.body.appendChild(document.createElement('script')).src='http://localhost:8182/static/bookmarklet.js'; })();

var l = window.getSelection().toString();

var uniqueString = "cortexitPopup";

var w = screen.width * 0.85;
var h = screen.height * 0.65;
var le = (screen.width - w) / 2.0;
var t = 20; //(screen.height - h) / 2.0;

var closer = document.createElement("a");
closer.innerHTML = 'OK';
closer.setAttribute('id', uniqueString + 'X');
closer.setAttribute('href', "javascript:(function() {var i = document.getElementById(uniqueString + '_'); i.parentNode.removeChild(i); var i = document.getElementById(uniqueString + 'X'); i.parentNode.removeChild(i);})();");
closer.style.position = 'fixed';
closer.style.fontSize = '16px';
closer.style.top = (t + h) + 'px';
var cw = 100;
closer.style.width = cw + 'px';
closer.style.left = (le + w - cw + 1) + 'px';
closer.style.zIndex = '1001';
closer.style.backgroundColor = '#000';
closer.style.color = '#fff';
document.body.appendChild(closer);

// Add the iframe with a unique name
var iframe = document.createElement("iframe");
iframe.setAttribute('id', uniqueString + '_');
document.body.appendChild(iframe);



iframe.style.position = 'fixed';
iframe.style.width = w + 'px';
iframe.style.height = h + 'px';
iframe.style.left = le + 'px';
iframe.style.top = t + 'px';
iframe.style.zIndex = '1000';

iframe.contentWindow.name = uniqueString;

// construct a form with hidden inputs, targeting the iframe
var form = document.createElement("form");
form.target = uniqueString;
form.action = "http://localhost:8182";
form.method = "POST";

// repeat for each parameter
var input = document.createElement("input");
input.type = "hidden";
input.name = "text";
input.value = escape(l);
form.appendChild(input);

document.body.appendChild(form);
form.submit();
