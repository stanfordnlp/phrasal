// Predictive Translation Memory 
//
//  Attaches a variable called 'ptm' to the global namespace
//  that exposes functions for controlling the PTM autocomplete
//  box.
//

(function(window){

// The main ptm namespace
var ptm = (function() {
  
  //Address of the translation UI (for redirect after translation completion)
  var _uiURL = window.location.protocol + "//" + window.location.host;
  
  //URL of PTM server (must be same-domain for json calls to work)
  var _serverURL = window.location.protocol + "//" + window.location.host;
  
  //Number of autocomplete results to fetch from the MT system
  var _numResultsToFetch = 100;  
  
  //Top-k completion results that will appear in the autocomplete box
  var _numResultsToDisplay = 10;
  
  //Cache of last set of predictions
  var _predictionsCache = "";
  
  //How many keystrokes the user has entered during the translation session
  var _numKeyStrokes = 0;
  
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
    srcLang: function(){ return $("select#src-list_ option:selected").val(); },
    tgtLang: function(){ return $("select#tgt-list_").val(); },
    src: function(){ return $("textarea#src-input_").val(); },
    tgt: function(){ return $("textarea#ptm-input_").val(); },
    srcOOV: function(context){ return $(context).find(".oov-src-text").html(); },
    tgtOOV: function(context){ return $(context).find(".oov-tgt").val(); },
    
    showStatus: function(message){
      $( "#status-box_" ).html(message).show();   
    },
    
    hideStatus: function() {
      $( "#status-box_" ).slideUp();
    },
    
    // Dependent on jQueryUI
    enablePTM: function() {
      $( "#ptm-input_" ).autocomplete("enable");    
    },
    
    // Dependent on jQueryUI
    disablePTM: function(){
      $( "#ptm-input_" ).autocomplete("disable");    
    },
    
    showTargetBox: function(){
      if ($('.form-oov:visible').length <= 1){
        ptmUI.showStatus("Done with OOV input.");
        $('#ptm_').show();
        return true;
      }
      return false;
    },
    
    disableSourceBox: function() {
      $("textarea#src-input_").attr("disabled",true);
    },
    
    enableSourceBox: function() {
      $("textarea#src-input_").attr("disabled",false);          
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
    
    //Position the autocomplete div
    positionAutocomplete: function() {
      var prefix = ptmUI.tgt();
      $( '#ptm-renderbox_' ).html(prefix);
//      console.log("prefix: " + prefix);
      var textHeight = $( '#ptm-renderbox_' ).height();
//      console.log("textHeight: " + textHeight);
      var textWidth = $( '#ptm-renderbox_' ).width();
//      console.log("textWidth: " + textWidth);
            
      var boxWidth = $( "#ptm-input_" ).width();
//      console.log("boxWidth: " + boxWidth);
            
      var vOffset = Math.floor(textWidth / boxWidth);
//      console.log("vOffset1: " + vOffset);
      vOffset = (vOffset+1) * textHeight;
//      console.log("vOffset2: " + vOffset);
      
      var hOffset = textWidth % boxWidth;
//      console.log("hOffset: " + hOffset);
      
      //my = Alignment position on the autocomplete box
      //at = Alignment position on the target element
      //offset = horizontal, vertical (pixel) offset values
      var newPos = {
        my: "left top",
        at: "left top",
        collision: "none",
        offset: hOffset.toString() + " " + vOffset.toString(),
      };
//      console.log("New autocomplete position in textArea: ");
//      console.log(newPos);
       
      $( "#ptm-input_" ).autocomplete( "option", "position", newPos );
    },
  };
  
  //Callbacks from the server to render data to the interface
  //
  var serveData = {
    
    //Acknowledgement received after sending a new OOV pair
    //TODO(spenceg) Remove the embedded HTML here? Or at least specify the ids.
    oovResponse: function(data) {
      console.log("oovResponse");
      console.log(data);
  
      //Translation succeeded; disable source input
      ptmUI.disableSourceBox();  
  
      //Clear what's in there now.
      $("#oov-input_").html('');
      
      if(data.OOVs.length === 0){
        console.log("No OOVs! Opening PTM window.");
        ptmUI.showTargetBox();
        return;
      }
      
      //Populate the OOV list dynamically
      var htmlStr = "";
      for(var i in data.OOVs){
        htmlStr = htmlStr + '<div class=\"oov-pair\">';
        htmlStr = htmlStr + '<form class="form-oov" name="form-oov-' + i + '">';
        htmlStr = htmlStr + '<span class="oov-src-text" id="form-oov-src-' + i +  '">' + data.OOVs[i] + '</span>';
        htmlStr = htmlStr + '<input type="text" class="oov-tgt" />';
        htmlStr = htmlStr + '<input type="submit" value="X" />'
        htmlStr = htmlStr + '</form></div>';        
      }
//      console.log(htmlStr);
      $("#oov-input_").append(htmlStr);
      $('.form-oov').submit(fn.sendOOV);
    },
    
    //Predicted completions for the sent prefix
    predictResponse: function(data,response){
      console.log("Autocomplete response:");
      console.log(data);
      
      ptmUI.positionAutocomplete();
      
      var tgt_prefix = ptmUI.tgt();   
		  _predictionsCache = new SimpleTrie();
		  _predictionsCache.prefix = tgt_prefix;
		  
      //Cache all of the predictions
		  $.map(data.predictions, function(item,index) {
			  _predictionsCache.Add(item,index);		
		  });

      //Setup the autocomplete box source
      //with the top-k predictions
      var top_predictions = data.predictions.slice(0,_numResultsToDisplay);    
      response( $.map( top_predictions, function( item ) {
        return { label: item, 
                 value: tgt_prefix + item }
      }));
    },
    
    //Fetch completions from the cache saved from the last server request
    predictResponseFromCache: function(response){
  	  console.log("predictResponseFromCache");
  	  var tgt_toks = ptmUI.tgt().split(" ");
  	  var word_prefix = tgt_toks[tgt_toks.length-1]; //Last partial word
  	  var completions = _predictionsCache.FindAll(word_prefix);
  		
  	  if(completions != false){
  	 	  ptmUI.positionAutocomplete();
  		
  		  //Completions should be sorted by value in the iteration
  		  //i.e., we don't need to explicitly sort.		
        var tgt_prefix = ptmUI.tgt();
        response( $.map( completions, function( item ) {
       	   return { label: word_prefix + item, 
       	            value: tgt_prefix + item }
     	  }));
      
      } else {
        completions = [];
        response( completions );      
      }
    },
  };
  
  
  //Callbacks to be registered with the UI/client
  //This is the main PTM API
  var fn = {
    
    //Reset the UI
    reset: function(){
      console.log("reset:");
      ptmUI.enableSourceBox();
      _predictionsCache = "";
      _numKeyStrokes = 0;
      window.location.replace(_uiURL);
    },
    
    //Keep track of number 
    addKeyStroke: function() {
      _numKeyStrokes++;
      console.log("addKeyStroke: " + _numKeyStrokes);
    },    
    
    //Clear the predictions cache
    clearCache: function(){
      console.log("clearCache");
      _predictionsCache = "";
    },
    
    // If we should fire the autocomplete function
    shouldFireAutocomplete: function(){
      console.log("shouldFireAutocomplete");
      var charPos = $( "#ptm-input_" ).getSelection().end;
      var prefix = ptmUI.tgt();
      return (charPos >= prefix.length);
    },
    
    //Returns all source languages supported by the system
    getSourceLanguages: function() {
      var langs = {};
      for(var i in _translationDirections){
        langs[i] = _translationDirections[i].source;
      }
      
      console.log("getSourceLanguages:");
//      console.log(langs);
      
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
    autoCompleteReq: function(request,response){
      console.log("autoCompleteRequest");
      
      ptmUI.hideStatus();
      
      if(_predictionsCache == false){
	      var ptmMsg = {
	        sourceLang: ptmUI.srcLang(),
	        targetLang: ptmUI.tgtLang(),
	        source: ptmUI.cleanUp(ptmUI.src()),
	        prefix: ptmUI.cleanUp(ptmUI.tgt()),
	        maxPredictions: _numResultsToDisplay,
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
	              serveData.predictResponse(data,response);
	            },
	            error: function(jqXHR, textStatus, errorThrown){
	              console.log("ptmPredict failed: " + textStatus);
	              console.log(errorThrown);
	            },
	      });
      
      } else {
			  serveData.predictResponseFromCache(response);      
      }
    },
        
    //When the user selects a completion
    autoCompleteSelect: function(completion){
      console.log("autoCompleteSelect:");
      console.log("Completion: " + completion);
      
      ptmUI.hideStatus();
      
      var tgt_prefix = ptmUI.tgt();
      if(_predictionsCache != false){
        tgt_prefix = _predictionsCache.prefix;      
      }
      
      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.cleanUp(ptmUI.src()),
        prefix: ptmUI.cleanUp(tgt_prefix),
        completion: ptmUI.cleanUp(completion),
      };
      console.log("POST: ptmUserSelection");
      console.log(ptmMsg);
      
      //Set the autocomplete box on the interface
      //TODO(spenceg) Bug in current version of jQueryUI whereby this
      //update is ignored. This is contrary to the documentation, which
      //states that an override of the select() callback will cancel an
      //automatic update to the textbox.
//      var newTarget = new String(ptmUI.tgt() + " " + completion);
//      $("textarea#ptm-input_").val(newTarget);
      
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
