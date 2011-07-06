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
        return (event.which === 32);
      },
      
      source: function(request,response){
        ptm.autoCompleteReq(request,response);
      },
      
      select: function( event, ui ) {  
//        console.log( "Selected:" + ui.item);
        if(ui.item){
          ptm.autoCompleteSelect(ui.item.label);
        }
      },
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
  var _uiURL = window.location.protocal + "//" + window.location.host;
  
  //URL of PTM server (must be same-domain for json calls to work)
  var _serverURL = window.location.protocal + "//" + window.location.host;
  
  //Top-k completion results that will appear in the autocomplete box
  var _numResultsToDisplay = 10;
  
  //
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
  };
  
  //Callbacks from the server to render data to the interface
  //
  //TODO: Remove the nested HTML here? Or at least specify the ids.
  var serveData = {
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
    
    predictResponse: function(data,response){
      console.log("Autocomplete response:");
      console.log(data);
      
      var charPos = $( "#ptm-input_" ).getSelection().end;
      var prefix = ptmUI.tgt();
      if(charPos < prefix.length){
        console.log("Prefix length: " + prefix.length);
        console.log("Caret pos: " + charPos);
        return;
      }

      $( '#ptm-renderbox_' ).html(prefix);
      console.log($( '#ptm-renderbox_' ));
      var textHeight = $( '#ptm-renderbox_' ).height();
      console.log("textHeight: " + textHeight);
      var textWidth = $( '#ptm-renderbox_' ).width();
      console.log("textWidth: " + textWidth);
            
      var boxWidth = $( "#ptm-input_" ).width();
      console.log("boxWidth: " + boxWidth);
            
      var vOffset = Math.floor(textWidth / boxWidth);
      vOffset = (vOffset+1) * textHeight;
      
      var hOffset = textWidth % boxWidth;
      
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
      
      if(data.predictions.length > _numResultsToDisplay){
        console.log("# returned results exceeds max results: " + data.predictions.length);
      }

      //Setup the autocomplete box source      
      response( $.map( data.predictions, function( item ) {
        return { label: item, value: ptmUI.tgt() + item }
      }));
    },
  };
  
  
  //Callbacks to be registered with the UI/client
  //
  //
  var fn = {
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
