// Predictive Translation Memory 
//
// Uses the module pattern:
// http://addyosmani.com/resources/essentialjsdesignpatterns/book/#designpatternsjavascript

//Key bindings (http://www.cambiaresearch.com/c4/702b8cd1-e5b0-42e6-83ac-25f0306e3e25/Javascript-Char-Codes-Key-Codes.aspx):
// 32 - spacebar
// 9 - tab
// 40 - down arrow
// 13 - enter
// 39 - right arrow

(function(window){

  //Register ptm callbacks with the UI when it is ready
  $(document).ready(function(){

    //Autocomplete box configuration
    $( "#ptm-input_" ).autocomplete({
      minLength: 1,
      delay: 50,
      
      //keyCode(32) == spacebar, which triggers autocomplete        
      search: function(event,ui){
        if(event.which === 32){
          ptm.clearCache();
        }
        return ptm.shouldFireAutocomplete();
      },
      
      source: function(request,response){
        ptm.autoCompleteReq(request,response);
      },
      
      focus: function( event, ui ) {
        ptm.addKeyStroke();      
      },
      
      select: function( event, ui ) {  
//        console.log( "Selected:" + ui.item);
        //Log user actions in the autocomplete box
        ptm.addKeyStroke();
        
        if(ui.item){
          ptm.autoCompleteSelect(ui.item.label);
        }
      },
    });
    
    //Setup keystroke counting (for typing)
    $( "#ptm-input_" ).keypress(function(event){
      ptm.addKeyStroke();    
    });    
    
    //Setup the clear button
    $( '#ptm-clear_' ).click(function(){
      ptm.reset();
    });
    
    //Configure the form that will initiate translation
    //Suppress the form POST
    $('#lang-form_').submit(function(event){
      event.preventDefault();
      ptm.initTranslation();
      return false;
    });   

    //Configure the handler for completing translation
    //Suppress the form POST
    $( '#form-ptm-input_' ).submit(function(event){
      event.preventDefault();
      ptm.doneWithTranslation();
      return false;
    });
    
    //Setup the source language list
    $( '#src-list_' ).append('<option value="NULL" selected="selected"></option>');
    var srcLangs = ptm.getSourceLanguages();
    for(var key in srcLangs){
      var selString = '<option value=\"' + key + '\">' + srcLangs[key] + '</option>';
      $( '#src-list_' ).append(selString);
    }
    $( '#src-list_' ).change(function(){
      ptm.selectSource($(this).val());
    });
    
    //Blank out all forms
    //This is needed when we redirect to the same page after a done event
    $(':input' )
      .not(':button, :submit, :reset')
      .val('')
      .removeAttr('checked')
      .removeAttr('selected');
  });


// The main ptm namespace
//
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
    src: function(){     return $("textarea#src-input_").val(); },
    tgt: function(){     return $("textarea#ptm-input_").val(); },
    srcOOV: function(context){ return $(context).find(".oov-src-text").html(); },
    tgtOOV: function(context){ return $(context).find(".oov-tgt").val(); },
    
    showPTM: function(){
      if ($('.form-oov:visible').length <= 1){
        $('#ptm_').show();
      }
    },
    
    cleanUp: function(myStr){
      var fmtString = new String(myStr);
      return $.trim(fmtString.toLowerCase());
    },
    
    toggleTgtSelect: function(isEnabled,langId){
      console.log("toggleTgtSelect: " + isEnabled + " " + langId);
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
        
        console.log("setupTgtBox: " + langId);
        console.log("tgtOrientation: " + orientation);
        
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
      console.log("New autocomplete position in textArea: ");
      console.log(newPos);
       
      $( "#ptm-input_" ).autocomplete( "option", "position", newPos );
    },
  };
  
  //Callbacks from the server to render data to the interface
  //
  //TODO: Remove the nested HTML here? Or at least specify the ids.
  var serveData = {
    
    //Acknowledgement received after sending a new OOV pair
    oovResponse: function(data) {
      console.log("oovResponse");
      console.log(data);
  
      //Clear what's in there now.
      $("#oov-input_").html('');
      
      if(data.OOVs.length === 0){
        console.log("No OOVs. Opening PTM window.");
        ptmUI.showPTM();
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
      console.log(htmlStr);
      $("#oov-input_").append(htmlStr);
      $('.form-oov').submit(fn.sendOOV);
    },
    
    //Predicted completions for the sent prefix
    predictResponse: function(data,response){
      console.log("Autocomplete response:");
      console.log(data);
      
      ptmUI.positionAutocomplete();
      
		  _predictionsCache = new SimpleTrie();
		  $.map(data.predictions, function(item,index) {
//			  console.log("Caching: " + item);
			  _predictionsCache.Add(item,index);		
		  });

      //Setup the autocomplete box source      
      response( $.map( data.predictions.slice(0,_numResultsToDisplay), function( item ) {
        return { label: item, value: ptmUI.tgt() + item }
      }));
    },
    
    //Fetch completions from the cache saved from the last server request
    predictResponseFromCache: function(response){
  	  console.log("predictResponseFromCache");
  	  var tgt_toks = ptmUI.tgt().split(" ");
  	  var completions = _predictionsCache.FindAll(tgt_toks[tgt_toks.length-1]);
  		
//  	  console.log(completions);		
  	
  	  if(completions != false){
  	 	  ptmUI.positionAutocomplete();
  		
  		 //Completions should be sorted by value in the iteration
  		 //i.e., we don't need to explicitly sort.		
        response( $.map( completions, function( item ) {
       	   return { label: item, value: ptmUI.tgt() + item }
     	  }));
      } else {
        completions = [];
        response( completions );      
      }
    },
  };
  
  
  //Callbacks to be registered with the UI/client
  //
  //
  var fn = {
    
    //Reset the UI
    reset: function(){
      console.log("reset:");
      window.location.replace(_uiURL);
      _predictionsCache = "";
      _numKeyStrokes = 0;
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
      console.log(langs);
      
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
              console.log("ptmInit failed: " + textStatus);
              console.log(errorThrown);
            },
      });     
    },
    
    //Send an OOV phrase pair to the server
    sendOOV: function(event){
      event.preventDefault();
      
      $(this).slideUp();
      
      var ptmMsg = {
        sourcePhrase: ptmUI.cleanUp(ptmUI.srcOOV(this)),
        targetPhrase: ptmUI.cleanUp(ptmUI.tgtOOV(this)),
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
      
      ptmUI.showPTM();
    },
    
    //User request for an autocomplete
    autoCompleteReq: function(request,response){
      console.log("autoCompleteRequest");
      
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
      
      var newTarget = new String(ptmUI.tgt() + " " + completion);
      
      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.cleanUp(ptmUI.src()),
        prefix: ptmUI.cleanUp(ptmUI.tgt()),
        completion: ptmUI.cleanUp(completion),
      };
      console.log("POST: ptmUserSelection");
      console.log(ptmMsg);
      
      //Set the autocomplete box on the interface
      //TODO(wsg2011) Bug in current version of jQueryUI whereby this
      //update is ignored. This is contrary to the documentation, which
      //states that an override of the select() callback will cancel an
      //automatic update to the textbox.
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
      
      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.cleanUp(ptmUI.src()),
        finishedTarget: ptmUI.cleanUp(ptmUI.tgt()),
        numKeyStrokes: _numKeyStrokes;
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
