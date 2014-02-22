$(document).ready( function() {
	var BrowserLog = function(stats) {
		var log = JSON.stringify(stats);
		$("#BrowserLog").val(log);
	};
	var BrowserReport = function(stats) {
		$("#BrowserUserAgent").text(stats.userAgent);
		var support = [];
		var noSupport = [];
		for ( var key in stats.jQuerySupport )
			if ( stats.jQuerySupport[key] )
				support.push(key);
			else
				noSupport.push(key);
		var pass = [];
		var noPass = [];
		stats.modernizrTests.forEach(function(d) {
      // Ignore the "touch" test, which does not apply to
      // this experiment.
			if ( d.substr(0,3) === "no-" && d.substr(3) !== "touch") {
				noPass.push(d.substr(3));
			} else {
				pass.push(d);
      }
		})
		$("#BrowserSupport").text(support.join(", "));
		$("#BrowserNoSupport").text(noSupport.join(", "));
		$("#BrowserPass").text(pass.join(", "));
		$("#BrowserNoPass").text(noPass.join(", "));
	};
	var userAgent = navigator.userAgent;
	var jQuerySupport = $.support;
	var modernizrTests = $("html").attr("class").trim().split(/[ ]+/g);
	var stats = {
		"userAgent" : userAgent,
		"jQuerySupport" : jQuerySupport,
		"modernizrTests" : modernizrTests
	};
	BrowserLog(stats);
	BrowserReport(stats);
	console.log( "Browser Check", stats );
});
