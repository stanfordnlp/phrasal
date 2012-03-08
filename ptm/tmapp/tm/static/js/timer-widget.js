// Implements an idle timer that expires after
// and interval has elapsed. It appears as an
// absolutely positioned div at the lower left
// of the screen.
// When it expires, a chain of callbacks are called.
//
// Requires:
//  jquery

(function(window){

  var timer = (function() {
    // Functions to call when the timer expires
    // No arguments are returned
    var _callbacks = new Array();

    // Value to idletimer from or to
    var _secs = 0;

    // Idle timeout value
    var _timeout = 1;
    
    // CSS id of the widget div
    var _cssId = 'idletimer-timer';

    // CSS id of the wrapper around the div text;
    var _cssTextWrapper = 'idletimer-timer-text';

    // Handle for the window timer
    var _timer = 0;

    var util = {
      go : function() {
        console.log('timer: go');
        var divString = '<div id="' + _cssId + '">';
        divString += '<div id="' + _cssTextWrapper + '">00:00</div></div>';

        $('body').append(divString);
        var elemSel = '#'+_cssId;
        $(elemSel).css({'position':'absolute',
                        'display':'block',
                        'bottom':'0px',
                        'left':'0px',
                        'border':'1px solid #000',
                        'color':'Red',
                        'font-size':'150%',
                        'background-color':'WhiteSmoke',
                       });
        $( '#'+_cssTextWrapper ).css('padding','0.2em');

        _timer = window.setTimeout("idletimer.timerHandler()", 1000);
        $(window).scroll(function() {
          var elemHeight = $('#idletimer-timer').outerHeight(true);
          var scrollBottom = $(window).scrollTop() + $(window).height();
	  console.log(scrollBottom);
          var margin = scrollBottom - elemHeight;
          var loc = {marginTop: margin + 'px'}
	  $('#idletimer-timer').stop().animate(loc, 10);
	});
      },

      done : function(){
        console.log('timer: done');
        for(var i=0; i < _callbacks.length; i++){
          var func = _callbacks[i];
          func();
        }
      },
    };
    
    var fn = {
      timerHandler : function(){
        // console.log('timer: time = ' + _secs.toString());
        _secs++;

        if (_secs == _timeout){
          window.clearTimeout(_timer);
          _timer = 0;
          util.done();
        } else {
          _timer = window.setTimeout("idletimer.timerHandler()", 1000);
        }

        var mins = parseInt(_secs / 60);
        var secs = _secs % 60;
        var timeStr = '';
        if (mins < 10){
          timeStr += '0';
        }
        timeStr += mins.toString() + ':';
        if (secs < 10) {
          timeStr += '0';
        }
        timeStr += secs.toString();
        $( '#'+_cssTextWrapper ).html(timeStr);
      },
      
      // Initialize the idletimer widget
      init : function(secs){
        console.log('timer: init ' + secs.toString() + 's');
        _timeout = secs;
      },

      // Display the idletimer widget
      show : function(){
        console.log('timer: show');
        util.go();
      },

      // Reset the idletimer
      reset : function(){
        _secs = 0;
      },

      // Add a callback that will be called when the timer
      // expires.
      addCallback: function(callback){
        console.log('timer: add new callback');
        _callbacks.push(callback);
      },
    };

    return fn;

  })();

  window.idletimer = timer;

})(window);
