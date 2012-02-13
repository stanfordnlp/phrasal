// Implements a countdown timer that
// appears as an absolutely positioned div
// in the lower right of the screen
//
// Requires:
//  jquery

(function(window){

  var timer = (function() {
    // Functions to call when the timer expires
    // No arguments are returned
    var _callbacks = new Array();

    // Value to countdown from
    var _secs = 0;

    // CSS id of the widget div
    var _cssId = 'countdown-timer';

    // CSS id of the wrapper around the div text;
    var _cssTextWrapper = 'countdown-timer-text';

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
                        'bottom':'0',
                        'left':'0',
                        'border':'1px solid #000',
                        'color':'Red',
                        'font-size':'150%',
                        'background-color':'WhiteSmoke',
                       });
        $( '#'+_cssTextWrapper ).css('padding','0.2em');

        _timer = window.setTimeout("countdown.timerHandler()", 1000);
        window.scroll(function() {
          var scrollBottom = $(window).scrollTop() + $(window).height();
          var elemHeight = $(elemSel).outerHeight(true);
          var margin = scrollBottom - elemHeight;
          var loc = {"marginTop" : margin + 'px'}
				  $('#'+_cssId).stop().animate(loc, 10);
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
        _secs--;

        if (_secs == 0){
          window.clearTimeout(_timer);
          _timer = 0;
          util.done();
        } else {
          _timer = window.setTimeout("countdown.timerHandler()", 1000);
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
      
      // Initialize the countdown widget
      init : function(secs){
        console.log('timer: init ' + secs.toString() + 's');
        _secs = secs;
      },

      // Display the countdown widget
      show : function(){
        console.log('timer: show');
        util.go();
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

  window.countdown = timer;

})(window);
