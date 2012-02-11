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

  // Timer for opening the ptm window
  var _ptmTimer = 0;

  // The autosuggest div
  var _ptmWidget = 0;

  // Timeout for displaying the ptm window (ms)
  var _ptmTimeout = 750;

  // ISO 639-1 language code for source text
  var _srcLang = 'en';

  // ISO 639-1 language code for target text
  var _tgtLang = 'ar';

  // Source text (unprocessed)
  var _srcRaw = '';
  
  // CSS classes
  var _tgtTxtSel = '';
  var _srcTokStyleClass = "src-tok";
  var _statusBoxSel = 'div#status-box';
  var _widgetContainerStyleId = "container_";
  
  // ###############################
  // Functions for manipulating the DOM / UI
  var ptmUI = {
    tgt: function(){ return $( _tgtTxtSel ).val(); },

    // Parse the custom array of CSS Ids and Classes (comes in via the setup
    // script
    processCSSArray: function(cssArray) {
      if (cssArray.tgtTxtArea) {
        _tgtTxtSel = 'textarea#' + cssArray.tgtTxtArea;
      } else {
        console.log('ptmUI: tgtTxtArea undefined!');
      }

      if (cssArray.srcTokClassPrefix) {
        _srcTokStyleClass = cssArray.srcTokClassPrefix;
      } else {
        console.log('ptmUI: srcTokClassPrefix undefined!');
      }

      if (cssArray.statusBox) {
        _statusBoxSel = 'div#'+cssArray.statusBox;
      } else {
        console.log('ptmUI: statusBox id undefined!');
      }
      
      if (cssArray.widgetContainerStyleId) {
        _widgetContainerStyleId = cssArray.widgetContainerStyleId;
      } else {
        console.log('ptmUI: widgetContainerStyleId undefined!');
      }
    },

    addTgtCompletion: function(prefix, completion) {
      var newTarget = $.trim(prefix + completion);
      $( _tgtTxtSel ).val(newTarget + ' ');
    },
    
    showStatus: function(message){
      $( _statusBoxSel ).html(message).show();   
    },
    
    hideStatus: function() {
      $( _statusBoxSel ).slideUp();
    },
    
    // TODO(spenceg): Add an option on the interface
    enablePTM: function() {
      console.log('TODO(spenceg): Toggle ptm on.');
    },
    
    // TODO(spenceg): Add an option on the interface
    disablePTM: function(){
      console.log('TODO(spenceg): Toggle ptm off.');    
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
    
  }; // End of ptmUI functions

  var ptmUtil = {
    //Prepares a string for transmission to the server. Presently
    //just trim trailing whitespace and lowercase.
    cleanUp: function(myStr){
      var fmtString = new String(myStr);
      return $.trim(fmtString.toLowerCase());
    },
    
  }; // End of ptmUtil functions
  
  // ########################
  // Callbacks from the server to render data to the interface
  var serveData = {

    // OOV list for a full source input string
    oovResponse: function(data) {
      console.log('handler: oovResponse');
      console.log(data);

      // TODO(spenceg): Highlight the OOVs

      ptm.autoCompleteReq();
    },

    // Completions for a transmitted target-side prefix.
    predictResponse: function(data){
      console.log("handler: predictResponse:");
      console.log(data);

      // Map the predictResponse message to a set of completions
      // data := Array[{first: completion, second: src-coverage},...]
      _predictionsCache = new SimpleTrie();
      _predictionsCache.prefix = ptmUI.tgt();
      var predictions = data.predictions;
      var completions = new Array(predictions.length);
      for (var idx in predictions) {
        // The target completion text
        var tgtText = predictions[idx].tgtPhrase;
        _predictionsCache.Add(tgtText, idx);

        // Source coverage of the completion
        var srcToks = predictions[idx].srcCoverage.split("-");
        var srcCoverage = $.map(srcToks, function(val, i){
          return _srcTokStyleClass + "-" + val;
        });

        // Source coverage of the prefix
        var srcPrefCoverage = 0;
        if (predictions[idx].srcPrefCoverage){ 
          var prefToks = predictions[idx].srcPrefCoverage.split("-");
          srcPrefCoverage = $.map(prefToks, function(val, i){
            return _srcTokStyleClass + "-" + val;
          });
        }

        var option = {
          tgt: tgtText,
          pref: srcPrefCoverage,
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

        // console.log(keyMap);
        ptmUI.openPTMWindow(keyMap);

      } else {
        // Don't filter the completions list
        ptmUI.openPTMWindow();
      }
    },

    // Perform action every time the user presses a key inside the
    // translation (target) window.
    addKeyStroke: function(event) {
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
      _ptmTimeout = value;
    },

    // Full URL of the translation host
    setHostString: function(value) {
      _uiURL = value;
      _serverURL = value;
    },

    // Set the CSS elements in the interface
    setCSSElements: function(cssArray) {
      ptmUI.processCSSArray(cssArray);
    },

    // Set the source language
    setSrcLang: function(langCode) {
      _srcLang = langCode;
    },

    // Set the target language
    setTgtLang: function(langCode) {
      _tgtLang = langCode;
    },

    setSrcTxt: function(srcTxt) {
      _srcRaw = ptmUtil.cleanUp(srcTxt);
    },
  
    //Initializes translation from the interface
    initTranslation: function(){
      ptmUI.hideStatus();
      console.log(_serverURL);

      var ptmMsg = {
        sourceLang: _srcLang,
        targetLang: _tgtLang,
        source: _srcRaw,
      };
      
      console.log("POST: ptmInit");
      console.log(ptmMsg);
      
      $.ajax({
            url: _serverURL,
            dataType: "json",
            data: {ptmInit: JSON.stringify(ptmMsg), },
            success: serveData.oovResponse,
            xhrFields: {
              withCredentials: true
            },
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
        sourcePhrase: ptmUtil.cleanUp(srcOOVStr),
        targetPhrase: ptmUtil.cleanUp(tgtOOVStr),
      };
      
      console.log("POST: sendOOV");
      // console.log(ptmMsg);
      
      $.ajax({
            url: _serverURL,
            dataType: "json",
            data: {ptmOOVPhrasePair: JSON.stringify(ptmMsg), },
            xhrFields: {
              withCredentials: true
            },
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
          sourceLang: _srcLang,
          targetLang: _tgtLang,
          source: _srcRaw,
          prefix: ptmUtil.cleanUp(ptmUI.tgt()),
          maxPredictions: _numResultsToFetch,
        };
        console.log("POST: ptmPredict");
        // console.log(ptmMsg);
      
        //Register the callback here since we'll send it directly
        //to the autocomplete box
        $.ajax({
              url: _serverURL,
              dataType: "json",
              data: { ptmPredict: JSON.stringify(ptmMsg), },
              xhrFields: {
                withCredentials: true
              },
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
      ptmUI.addTgtCompletion(tgtPrefix, completion);

      // Get completions for this new translation prefix
      ptm.togglePTMTimer(true);
      
      var ptmMsg = {
        sourceLang: _srcLang,
        targetLang: _tgtLang,
        source: _srcRaw,
        prefix: ptmUtil.cleanUp(tgtPrefix),
        completion: ptmUtil.cleanUp(completion),
      };
      console.log("POST: ptmUserSelection");
      // console.log(ptmMsg);

// TODO(spenceg): This should be an ajax call to tmapp with the
// the new rule.
//      $.ajax({
//            url: _serverURL,
//            dataType: "json",
//            data: {ptmUserSelection: JSON.stringify(ptmMsg),},
//            xhrFields: {
//              withCredentials: true
//            },
//            error: function(jqXHR, textStatus, errorThrown){
//              console.log("ptmUserSelection failed: " + textStatus);
//              console.log(errorThrown);
//            },
//      });
    },
    
    //User has finished the translation.
    //Submit to server
    doneWithTranslation: function(){
    
      ptmUI.hideStatus();
      
      var ptmMsg = {
        sourceLang: _srcLang,
        targetLang: _tgtLang,
        source: _srcRaw,
        finishedTarget: ptmUtil.cleanUp(ptmUI.tgt()),
      };
      console.log("POST: ptmDone");
      // console.log(ptmMsg);
      
      $.ajax({
            url: _serverURL,
            dataType: "json",
            data: { ptmDone: JSON.stringify(ptmMsg), },
            xhrFields: {
              withCredentials: true
            },
            error: function(jqXHR, textStatus, errorThrown){
              console.log("ptmDone failed: " + textStatus);
              console.log(errorThrown);
            },
      });

      return true;
    },    
  };
  
  return fn;
  
})();

//Map ptm to the global namespace
window.ptm = ptm;

})(window);
