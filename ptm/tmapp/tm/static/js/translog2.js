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
    //     mousemove keyup keydown
    var _jsEvents = 'blur focus focusin focusout load resize scroll unload click dblclick mousedown mouseup mouseover mouseout mouseenter mouseleave change select submit keypress error';

    // Initial capacity of the event log
    var _initCapacity = 5000;
    
    // The log of string events
    var _eventLog = 0;

    // End of the event log
    var _eventLogPtr = 0;

    // Delimiter between events in the output
    var _eventDelim = '|||';

    // Signals begin of user actions
    var _startEventName = 'start';

    // Signals end of user actions
    var _endEventName = 'end';
    
    var util = {
      add: function(event) {
        if (_eventLogPtr >= _eventLog.length) {
          _eventLogPtr = _eventLog.length;
          var newLog = new Array(_initCapacity);
          _eventLog = _eventLog.concat(newLog);
        }

        // TODO(spenceg) Add more to the event format
        var eventStr = event.type + ' ' + event.timeStamp;
        if (event.target.id) {
          eventStr += ' id:' + event.target.id;
        }
        if (event.pageX && event.pageY) {
          eventStr += ' x:' + event.pageX + ' y:' + event.pageY;
        }
        if (event.which){
          eventStr += ' k:' + event.which;
        }
        // WSGDEBUG
        console.log(eventStr);
        _eventLog[_eventLogPtr++] = eventStr;
      },

      addCustom: function(name,text){
        var d = new Date();
        var eventStr = name + ' ' + d.getTime();
        if (text){
          eventStr += ' ' + text;
        }
        _eventLog[_eventLogPtr++] = eventStr;
      },

      // Create the event log and add the START event
      start: function(){
        _eventLog = new Array(_initCapacity);
        util.addCustom(_startEventName);
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
        util.addCustom(_endEventName);
        
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

      // Fire a custom, named event with optional extra arguments
      customEvent: function(name,text){
        util.addCustom(name,text);
      },
      
      // Flush the log to input element
      // This should be bound to an event handler
      flushForm: function(input_id) {
        console.log('tlog2: Flush to input: ' + input_id);
        var eventStr = util.toStr();
        $( '#'+input_id ).val(eventStr);
        return true;
      },

      // Flush the input log to <tag_id>
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
