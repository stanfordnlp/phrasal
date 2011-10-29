// Predictive Translation Memory 
//
//  Attaches a variable called 'ptm' to the global namespace
//  that exposes functions for controlling the PTM autocomplete
//  box.
//

(function(window){

// The main ptm namespace
var ptm = (function() {

  // Address of the translation UI (for redirect after translation completion)
  var _uiURL = window.location.protocol + "//" + window.location.host;

  // URL of PTM server (must be same-domain for json calls to work)
  var _serverURL = window.location.protocol + "//" + window.location.host;

  // Number of autocomplete results to fetch from the MT system
  var _numResultsToFetch = 100;  
  
  // Cache of last set of predictions
  var _predictionsCache = 0;

  // How many keystrokes the user has entered during the translation session
  var _numKeyStrokes = 0;

  // Timer for opening the ptm window
  var _ptmTimer = 0;

  // Timeout for displaying the ptm window (ms)
  var _ptmTimeout = 750;

  // CSS class for the source token CSS class
  var _srcTokStyleClass = "src-tok";

  // CSS id for div around the source text in which the PTM widget will
  // be dynamically positioned.
  var _widgetContainerStyleId = "container_";
  
  // Translation directions supported by the system
  // Languages are represented with ISO 639-1 (two-letter) language codes
  // The "source" property contains the display name of the source language
  // Available targets should be entered by their language codes
  var _translationDirections = {
    ar: {
      source: "Arabic",
      en: "English",
    },
    en: {
      source: "English",
      ar: "Arabic",
    },
  };
  
  //Rendering directions for the supported languages
  var _langOrientations = {
    en: "left",
    ar: "right",
  };
    
  // Convenience functions for controlling DOM elements
  var ptmUI = {
    srcLang: function(){ return $( 'select#src-list_ option:selected' ).val(); },
    tgtLang: function(){ return $( 'select#tgt-list_ option:selected' ).val(); },
    src: function(){ return $( 'textarea#src-input_' ).val(); },
    tgt: function(){ return $( 'textarea#ptm-input_' ).val(); },
    srcOOV: function(context){ return $(context).find( '.oov-src-text' ).html(); },
    tgtOOV: function(context){ return $(context).find( '.oov-tgt' ).val(); },
    
    showStatus: function(message){
      $( '#status-box_' ).html(message).show();   
    },
    
    hideStatus: function() {
      $( '#status-box_' ).slideUp();
    },
    
    // TODO(spenceg): Add an option on the interface
    enablePTM: function() {
      console.log('TODO(spenceg): Toggle ptm on.');
    },
    
    // TODO(spenceg): Add an option on the interface
    disablePTM: function(){
      console.log('TODO(spenceg): Toggle ptm off.');    
    },

    showTargetBox: function(){
      $( '#ptm_' ).show();
      $( '#ptm-input_' ).focus();
      return true;
    },

    // Disables the source textarea and enables a div that displays
    // the whitespace-tokenized source
    disableSourceBox: function() {
      console.log('ptmUI: disableSourceBox()');
      
      var srcToks = ptmUI.src();
      srcToks = $.trim(srcToks.replace(/\s+/g, ' ')).split(' ');
      var tokId = 0;
      var divStr = '';
      $( '#src-display_' ).html('');
      for (var tok in srcToks){
        var tokDiv = sprintf('<div class=\"%s\" id=\"%s-%d\">%s</div>', _srcTokStyleClass, _srcTokStyleClass, tokId++, srcToks[tok]);
        $( '#src-display_' ).append(tokDiv);
      }

      // Setup CSS orientation for the token divs
      var textAlign = $( '#src-input_' ).css('direction');
      var classSel = 'div.'+_srcTokStyleClass;
      if (textAlign == 'rtl') {
        $( classSel ).css('float', 'right');
      } else {
        $( classSel ).css('float', 'left');
      }
      $( classSel ).css('direction', $( '#src-input_' ).css('direction'));
      $( classSel ).css('text-align', $( '#src-input_' ).css('text-align'));

      $( '#src-form-container_' ).hide();
      $( '#src-display_' ).show();
 
      return true;
    },

    // Enable the source input text area.
    enableSourceBox: function() {
      $( '#src-display_' ).hide();
      $( '#src-form-container_').show();
      return true;
    },

    // Open the PTM div over the source tokens. The PTM widget assumes
    // control of certain key bindings
    openPTMWindow: function(filterIds) {
      console.log('ptmUI: openPTMWindow()');
      _ptmTimer = 0;
      if(filterIds){
        _ptmWidget.Show(ptm.autoCompleteSelect, filterIds);
      } else {
        _ptmWidget.Show(ptm.autoCompleteSelect);
      }
    },

    // Close the PTM div over the source tokens. Unbind the div
    // from all keys.
    closePTMWindow: function() {
      console.log('ptmUI: closePTMWindow()');
      if (_ptmWidget) {
        _ptmWidget.Hide(false);
      }
    },
    
    //Prepares a string for transmission to the server. Presently
    //just trim trailing whitespace and lowercase.
    cleanUp: function(myStr){
      var fmtString = new String(myStr);
      return $.trim(fmtString.toLowerCase());
    },

    setupSourceSelect: function() {
      console.log("setupSourceSelect: ");
      $( '#src-list_' ).append('<option value="NULL"></option>');
      for(var key in _translationDirections){
        var displayName = _translationDirections[key].source;
        var selString = sprintf('<option value=\"%s\">%s</options>', key, displayName);
//        console.log(selString);
        $( '#src-list_' ).append(selString);
      }
        
      // TODO(spenceg): Assume Arabic is default source for development
      $( '#src-list_' ).val('ar');
    },
    
    toggleTgtSelect: function(isEnabled,langId){
//      console.log("toggleTgtSelect: " + isEnabled + " " + langId);
      $( '#tgt-list_' ).html('<option value="NULL" selected="selected"></option>');
      var directions = _translationDirections[langId];
      for(var id in directions){
        if( id != "source" ){
          var selString = '<option value=\"' + id + '\">' + directions[id] + '</option>';
          $( '#tgt-list_' ).append(selString);
        }
      }
      
      $('#tgt-list_').change(function(){
        var langId = $(this).val();
        var orientation = _langOrientations[langId];
        
//        console.log("setupTgtBox: " + langId);
//        console.log("tgtOrientation: " + orientation);
        
        if(orientation == "right"){
          $( '#ptm-input_' ).css("direction","rtl");
          $( '#ptm-input_' ).css("text-align","right");
        } else {
          $( '#ptm-input_' ).css("direction","ltr");
          $( '#ptm-input_' ).css("text-align","left");
        }
      });
    },
    
    //Configure the orientation of the source box
    setupSrcBox: function(langId){
      console.log("setupSrcBox: " + langId);
      var orientation = _langOrientations[langId];
      console.log("srcOrientation: " + orientation);
      if(orientation == "right"){
        $( '#src-input_' ).css("direction","rtl");
        $( '#src-input_' ).css("text-align","right");
      } else {
        $( '#src-input_' ).css("direction","ltr");
        $( '#src-input_' ).css("text-align","left");
      }
    },

  }; // End of ptmUI functions
  
  //Callbacks from the server to render data to the interface
  var serveData = {

    // OOV list for a full source input string
    oovResponse: function(data) {
      console.log('handler: oovResponse');
//      console.log(data);

      // Discard OOV data on the current interface. Move on to
      // translation.
      ptmUI.disableSourceBox();
      ptmUI.showTargetBox();
      ptm.autoCompleteReq();
    },

    // Completions for a transmitted target-side prefix.
    predictResponse: function(data){
      console.log("handler: predictResponse:");
//      console.log(data);

      // Map the predictResponse message to a set of completions
      // data := Array[{first: completion, second: src-coverage},...]
      _predictionsCache = new SimpleTrie();
      _predictionsCache.prefix = ptmUI.tgt();
      var predictions = data.predictions;
      var completions = new Array(predictions.length);
      for (var idx in predictions) {
        var tgtText = predictions[idx].first;
        _predictionsCache.Add(tgtText, idx);
        var srcToks = predictions[idx].second.split("-");
        var srcCoverage = $.map(srcToks, function(val, i){
          return _srcTokStyleClass + "-" + val;
        });
        var option = {
          tgt: tgtText,
          coverage: srcCoverage,
        };
        completions[idx] = option;
      }

      // Create the PTM widget and start the timer
      _ptmWidget = new PTMWidget(completions, _widgetContainerStyleId);
      _ptmTimer = window.setTimeout("ptm.timerHandler()", _ptmTimeout);
    },
    
  }; // End of serveData callback handlers
  
  
  //Callbacks to be registered with the UI/client
  //This is the main PTM API
  var fn = {

    // Handler for the timer that opens the PTM widget
    timerHandler: function(){
      console.log('timerHandler:');

      // Two conditions on trying the cache:
      //  1) The length of the translation prefix is > 0
      //  2) The last character is not a space
      // Otherwise, we allow the widget to display all prefixes.
      var prefix = ptmUI.tgt();
      var tryCache = ($.trim(prefix).length > 0) &&
        prefix[prefix.length-1] != " ";

      if(_predictionsCache && tryCache){
        console.log('timerHandler: predictFromCache');
        // Create a set of filter ids
        var tgt_toks = prefix.split(" ");
  	    var word_prefix = tgt_toks[tgt_toks.length-1]; //Last partial word
  	    var completions = _predictionsCache.FindAll(word_prefix);
        var keyMap = {};
        for (var prop in completions){
//          console.log(prop + ':' + completions[prop]);
          keyMap[prop] = 1;
        }

        console.log(keyMap);
        ptmUI.openPTMWindow(keyMap);

      } else {
        // Don't filter the completions list
        ptmUI.openPTMWindow();
      }
    },

    //Reset the UI for another translation session
    reset: function(){
      console.log("reset:");
      ptmUI.enableSourceBox();
      ptmUI.closePTMWindow();
      _predictionsCache = 0;
      _numKeyStrokes = 0;
      window.location.replace(_uiURL);
    },
    
    // Perform action every time the user presses a key inside the
    // translation (target) window.
    addKeyStroke: function(event) {
      _numKeyStrokes++;
      console.log("addKeyStroke: " + _numKeyStrokes);

      // Spacebar triggers a server request. We would
      // obviously need to change this for Chinese ;)
      var doServerRequest = (event.keyCode == 32);
      ptm.togglePTMTimer(doServerRequest);
    },

    togglePTMTimer: function(doServerRequest) {
      // Disable the current ptm timer if it exists
      if (_ptmTimer) {
        window.clearTimeout(_ptmTimer);
        _ptmTimer = 0;
      }
      
      // Close the ptmWindow if it is open
      ptmUI.closePTMWindow();

      // Get the next completions from either the server or the cache
      if (doServerRequest){
        ptm.autoCompleteReq();
      } else if(_predictionsCache){
        // We have some stored completions
        _ptmTimer = window.setTimeout("ptm.timerHandler()", _ptmTimeout);
      } else {
        console.log('WARN: timer fired, but could not fetch completions.');
      }
    },

    // Set the duration (in ms) after which the PTM box will appear
    setPTMTimeout: function(value){

    },
    
    // Setup the selector with the available source languages
    setupSourceLanguages: function() {
      ptmUI.setupSourceSelect();
    },
    
    //Select the source language for translation
    selectSource: function(langId) {
      console.log("selectSource: " + langId);
      if(langId === "NULL"){
        ptmUI.toggleTgtSelect(false,langId);
      } else {
        ptmUI.setupSrcBox(langId);
        ptmUI.toggleTgtSelect(true,langId);
      }
    },
  
    //Initializes translation from the interface
    initTranslation: function(){
      ptmUI.hideStatus();

      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.cleanUp(ptmUI.src()),
      };
      
      console.log("POST: ptmInit");
      console.log(ptmMsg);
      
      $.ajax({
            url: _serverURL,
            dataType: "json",
            data: {ptmInit: JSON.stringify(ptmMsg), },
            success: serveData.oovResponse,
            error: function(jqXHR, textStatus, errorThrown){
              ptmUI.showStatus("Communication with server failed. Unable to translate source.");              
              console.log("ptmInit failed: " + textStatus);
              console.log(errorThrown);
            },
      });     
    },
    
    //Send an OOV phrase pair to the server
    sendOOV: function(event){
      event.preventDefault();
      
      // Clear this OOV regardless of whether the transmission
      // below succeeds
      $(this).slideUp();
      
      var srcOOVStr = ptmUI.srcOOV(this);
      var tgtOOVStr = ptmUI.tgtOOV(this);
      var ptmMsg = {
        sourcePhrase: ptmUI.cleanUp(srcOOVStr),
        targetPhrase: ptmUI.cleanUp(tgtOOVStr),
      };
      
      console.log("POST: sendOOV");
      console.log(ptmMsg);
      
      $.ajax({
            url: _serverURL,
            dataType: "json",
            data: {ptmOOVPhrasePair: JSON.stringify(ptmMsg), },
            error: function(jqXHR, textStatus, errorThrown){
              console.log("ptmOOVPhrasePair failed: " + textStatus);
              console.log(errorThrown);
            },
      });   
      
      ptmUI.showTargetBox();
    },
    
    //User request for an autocomplete
    autoCompleteReq: function(){
      console.log("autoCompleteRequest");
      
      ptmUI.hideStatus();
      
        var ptmMsg = {
          sourceLang: ptmUI.srcLang(),
          targetLang: ptmUI.tgtLang(),
          source: ptmUI.cleanUp(ptmUI.src()),
          prefix: ptmUI.cleanUp(ptmUI.tgt()),
          maxPredictions: _numResultsToFetch,
        };
        console.log("POST: ptmPredict");
        console.log(ptmMsg);
      
        //Register the callback here since we'll send it directly
        //to the autocomplete box
        $.ajax({
              url: _serverURL,
              dataType: "json",
              data: { ptmPredict: JSON.stringify(ptmMsg), },
              success: function(data){
                //response is a callback
                serveData.predictResponse(data);
              },
              error: function(jqXHR, textStatus, errorThrown){
                console.log("ptmPredict failed: " + textStatus);
                console.log(errorThrown);
              },
        });
    },
        
    //When the user selects a completion
    autoCompleteSelect: function(completion){
      console.log("autoCompleteSelect:");
      console.log("Completion: " + completion);
      
      ptmUI.hideStatus();
      
      var tgtPrefix = ptmUI.tgt();
      // If the cache is enabled, then we want to get the target
      // prefix associated with this cache. That way, we can discard
      // the partially completed word if the user selected a completion
      // mid-word.
      if(_predictionsCache){
        tgtPrefix = _predictionsCache.prefix;      
      }

      // Update the translation box.
      var newTarget = tgtPrefix + completion;
      newTarget = newTarget.replace(/\s+/g, ' ') + ' ';
      $("textarea#ptm-input_").val(newTarget);

      // Get completions for this new translation prefix
      ptm.togglePTMTimer(true);
      
      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.cleanUp(ptmUI.src()),
        prefix: ptmUI.cleanUp(tgtPrefix),
        completion: ptmUI.cleanUp(completion),
      };
      console.log("POST: ptmUserSelection");
      console.log(ptmMsg);
      
      $.ajax({
            url: _serverURL,
            dataType: "json",
            data: {ptmUserSelection: JSON.stringify(ptmMsg),},
            error: function(jqXHR, textStatus, errorThrown){
              console.log("ptmUserSelection failed: " + textStatus);
              console.log(errorThrown);
            },
      });
    },
    
    //User has finished the translation.
    //Submit to server
    doneWithTranslation: function(){
    
      ptmUI.hideStatus();
      
      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.cleanUp(ptmUI.src()),
        finishedTarget: ptmUI.cleanUp(ptmUI.tgt()),
        numKeyStrokes: _numKeyStrokes,
      };
      console.log("POST: ptmDone");
      console.log(ptmMsg);
      
      $.ajax({
            url: _serverURL,
            dataType: "json",
            data: { ptmDone: JSON.stringify(ptmMsg), },
            success: function(data, textStatus, jqXHR){
              console.log("ptmDone response: " + textStatus);
              window.location.replace(_uiURL);
            },
            error: function(jqXHR, textStatus, errorThrown){
              console.log("ptmDone failed: " + textStatus);
              console.log(errorThrown);
//              window.location.replace(_uiURL);
            },
      });
    },
    
  };
  
  return fn;
  
})();

//Map ptm to the global namespace
window.ptm = ptm;

})(window);
