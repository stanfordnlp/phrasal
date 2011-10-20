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
  var _predictionsCache = "";
  
  // How many keystrokes the user has entered during the translation session
  var _numKeyStrokes = 0;

  // Timer for opening the ptm window
  var _ptmTimer = 0;

  // Timeout for displaying the ptm window (ms)
  var _ptmTimeout = 1000;
  
  //Translation directions supported by the system
  //Languages are represented with ISO 639-1 (two-letter) language codes
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
    
  //TODO(spence): All css names should be factored out into constants
  var ptmUI = {
    srcLang: function(){ return $( 'select#src-list_ option:selected' ).val(); },
    tgtLang: function(){ return $( 'select#tgt-list_' ).val(); },
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
    
    // Dependent on jQueryUI
    enablePTM: function() {
      console.log('TODO(spenceg): Toggle ptm on.');
    },
    
    // Dependent on jQueryUI
    disablePTM: function(){
      console.log('TODO(spenceg): Toggle ptm off.');    
    },

    showTargetBox: function(){
      $( '#ptm_' ).show();
      return true;
    },

    // Disables the textarea and enables a div containing the source
    disableSourceBox: function() {
      console.log('ptmUI: disableSourceBox()');
      
      var srcToks = ptmUI.src();
      srcToks = $.trim(srcToks.replace(/\s+/g, ' ')).split(' ');
      var tokId = 0;
      var divStr = '';
      var divClass = "src-token"
      $( '#src-display_' ).html('');
      var divStyle = 'border-width:1px;border-style:solid;border-color:#FFFFFF;';
      for (var tok in srcToks){
        var tokDiv = sprintf('<div style=\"%s\" class=\"%s\" id=\"srctok-%d\">%s</div>', divStyle, divClass, tokId++, srcToks[tok]);
        $( '#src-display_' ).append(tokDiv);
      }

      // Setup CSS orientation for the token divs
      var textAlign = $( '#src-input_' ).css('direction');
      if (textAlign == 'rtl') {
        $( divClass ).css('float', 'right');
      } else {
        $( divClass ).css('float', 'left');
      }
      $( divClass ).css('direction', $( '#src-input_' ).css('direction'));
      $( divClass ).css('text-align', $( '#src-input_' ).css('text-align'));

      $( '#src-form-container_' ).hide();
      $( '#src-display_' ).show();
 
      return true;
    },
    
    enableSourceBox: function() {
      $( '#src-display_' ).hide();
      $( '#src-form-container_').show();
      return true;
    },

    openPTMWindow: function() {
      console.log('ptmUI: openPTMWindow()');
      _ptmTimer = 0;

      // Open the PTM widget with the given set of completions
      // TODO(spenceg): Add the cache here as the second parameter
      _ptmWidget.Show(ptm.autoCompleteSelect);
    },

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
    
    oovResponse: function(data) {
      console.log('handler: oovResponse');
      console.log(data);
      // TODO(spenceg): Do something with the OOVs on the interface?

      ptmUI.disableSourceBox();
      ptmUI.showTargetBox();
      window.ptm.autoCompleteReq();
    },

    //Predicted completions for the sent prefix
    predictResponse: function(data){
      console.log("handler: predictResponse:");
      console.log(data);

      // Map the predictResponse message to a set of completions
      // data := Array[{first: completion, second: src-coverage},...]
      var predictions = data.predictions;
      var completions = new Array(predictions.length);
      for (var idx in predictions) {
        var srcToks = predictions[idx].second.split("-");
        var srcCoverage = $.map(srcToks, function(val, i){
          return "#srctok-" + val;
        });
        var option = {
          tgt: predictions[idx].first,
          coverage: srcCoverage,
        };
        completions[idx] = option;
      }

      // TODO(spenceg): Move the CSS id to a constant
      _ptmWidget = new PTMWidget(completions, "container_");
      _ptmTimer = window.setTimeout("ptm.timerHandler()", _ptmTimeout);
    },
    
  }; // End of serveData callback handlers
  
  
  //Callbacks to be registered with the UI/client
  //This is the main PTM API
  var fn = {

    timerHandler: function(){
      console.log("timerHandler:");
      ptmUI.openPTMWindow();
    },

    //Reset the UI
    reset: function(){
      console.log("reset:");
      ptmUI.enableSourceBox();
      ptmUI.closePTMWindow();
      _predictionsCache = "";
      _numKeyStrokes = 0;
      window.location.replace(_uiURL);
    },
    
    //TODO(spenceg): This is now the main entry point into PTM. 
    addKeyStroke: function(event) {
      _numKeyStrokes++;
      console.log("addKeyStroke: " + _numKeyStrokes);

      // Disable the ptm window timer if it is running
      if (_ptmTimer) {
        window.clearTimeout(_ptmTimer);
        _ptmTimer = 0;
      }
      // Close the ptmWindow if it is open
      ptmUI.closePTMWindow();

      // Get the next set of predictions when the user hits spacebar
      if (event.keyCode == 32){
        ptm.autoCompleteReq();
      }
    },    
    
    //Clear the predictions cache
    clearCache: function(){
      console.log("clearCache");
      _predictionsCache = "";
    },
        
    //Returns all source languages supported by the system
    getSourceLanguages: function() {
      var langs = {};
      for(var i in _translationDirections){
        langs[i] = _translationDirections[i].source;
      }      
      console.log("getSourceLanguages: " + langs);
      
      return langs;
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
        console.log(ptmUI.tgt());
      
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
      
      var tgt_prefix = ptmUI.tgt();
// TODO(spenceg): Re-enable the cache.
//      if(_predictionsCache != false){
//        tgt_prefix = _predictionsCache.prefix;      
//      }
      
      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.cleanUp(ptmUI.src()),
        prefix: ptmUI.cleanUp(tgt_prefix),
        completion: ptmUI.cleanUp(completion),
      };
      console.log("POST: ptmUserSelection");
      console.log(ptmMsg);
      
      var newTarget = new String(ptmUI.tgt() + " " + completion);
      $("textarea#ptm-input_").val(newTarget);
      
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
