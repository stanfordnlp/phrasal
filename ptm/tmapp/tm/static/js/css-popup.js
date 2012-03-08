// css-popup.js
//
// A popup that places a blanket over the page.
//
// Requires:
//   jquery-latest.js
//

var CssPopup = function(title,message) {

  // Create the divString
  var divString = "<div id=\"_popup\"><span id=\"_popupClose\">x</span><h1>"
  divString += title;
  divString += "</h1><p id=\"_popupContent\">";
  divString += message;
  divString += "</p></div><div id=\"_popupBackground\"></div>";

  $(document.body).append(divString);

  $( '#_popupClose' ).click(function(){
    $( 'div#_popup' ).hide();
    $( 'div#_popupBackground' ).hide();
  });
};

CssPopup.prototype.Show = function() {
  //request data for centering
  var windowWidth = $(document).width();
  var windowHeight = $(document).height();
  var popupHeight = $("#_popup").height();
  var popupWidth = $("#_popup").width();
  //centering
  $("#_popup").css({
    "position": "absolute",
    "top": windowHeight/2-popupHeight/2,
    "left": windowWidth/2-popupWidth/2
  });
  //only need force for IE6

  $("#_popupBackground").css({
    "height": windowHeight
  });
  
  // Need to center the window
  $( 'div#_popupBackground' ).show();
  $( 'div#_popup' ).show();
};

