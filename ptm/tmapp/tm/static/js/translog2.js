// Logs and formats all javascript events
//
// Requires:
//  jquery
//
(function(window){

  var tlog2 = (function(){

    // Default js event list
    // TODO(spenceg): Removed the following events, which seem to be
    // redundant for our purposes
    //     mousemove keyup
    var _jsEvents = 'blur focus focusin focusout load resize scroll unload click dblclick keydown mousedown mouseup mouseover mouseout mouseenter mouseleave change select submit keypress error';

    // Initial capacity of the event log
    var _initCapacity = 5000;

    // Start time (in ms) of the log
    var _startTime = 0;
    
    // The log of string events
    var _eventLog = 0;

    // End of the event log
    var _eventLogPtr = 0;

    // Delimiter between events in the output
    var _eventDelim = '|';

    // Signals begin of user actions
    var _startEventName = 'start';

    // Signals end of user actions
    var _endEventName = 'end';
    
    var util = {
      add: function(event) {
	// event.timeStamp is not reliable across browsers
	var d = new Date();
	var _now = d.getTime();

	// Extend the event log size if necessary
        if (_eventLogPtr >= _eventLog.length) {
          _eventLogPtr = _eventLog.length;
          var newLog = new Array(_initCapacity);
          _eventLog = _eventLog.concat(newLog);
        }


        // TODO(spenceg) Add more to the event format
        var eventStr = event.type + ' ' + (_now - _startTime);
        if (event.target.id) {
          eventStr += ' id:' + event.target.id;
        }
        if (event.pageX && event.pageY) {
          eventStr += ' x:' + event.pageX + ' y:' + event.pageY;
        }
        if (event.which){
          eventStr += ' k:' + event.which;
        }
        console.log(eventStr);
        _eventLog[_eventLogPtr++] = eventStr;
      },

      // Create the event log and add the START event
      start: function(){
        var d = new Date();
        _eventLog = new Array(_initCapacity);

        // Add the START event
        _startTime = d.getTime();
        var eventStr = _startEventName + ' 0 ' + _startTime.toString();
        _eventLog[_eventLogPtr++] = eventStr;
      },

      // Converts the event log to a string representation. Also adds the
      // END event to the log
      // Supposedly, string appends are the fastest way to build a string
      // in javascript.
      toStr: function() {
        // Copy _eventLogPtr so that additional events aren't collected
        // while the string is constructed.
        var lastEventIdx = _eventLogPtr;
        var logStr = '';
        for (var i = 0; i < _eventLogPtr; ++i){
          logStr += _eventLog[i];
          logStr += _eventDelim;
        }

        // Add the END event
        var d = new Date();
        var eventStr = _endEventName + ' ' + (d.getTime() - _startTime);
        logStr += _eventDelim + eventStr;
       
        return logStr;
      },
    };
    
    var fn = {

      // Initialize the logger
      init: function(){
        console.log('tlog2: initializing');
        util.start();

        // Bind the listener to the document
        $(document).bind(_jsEvents, function(event) {
          util.add(event);
        });
        
        return true;
      },

      // Flush the log to a named input element
      // That is, the element is of type input and has
      // its name field set.
      flushForm: function(input_name) {
        console.log('tlog2: Flush to input: ' + input_name);
        var eventStr = util.toStr();
        $( 'input[name='+input_name+']' ).val(eventStr);
        return true;
      },

      // Append the input log to the innerHTML or
      // the element named with this CSS id.
      // This should be bound to an event handler
      flushTag: function(tag_id){
        console.log('tlog2: Flush to tag: ' + tag_id);
        var eventStr = util.toStr();
        $( '#'+tag_id ).html(eventStr);
        return true;
      },
    };

    return fn;

  })();

  window.tlog2 = tlog2;

})(window);
