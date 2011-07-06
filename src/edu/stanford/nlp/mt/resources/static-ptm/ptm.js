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

  });


// The main ptm namespace
//
var ptm = (function() {
  
  //URL of PTM server (must be same-domain for json calls to work)
  var serverURL = "http://jack.stanford.edu:8080";
  
  //Top-k completion results that will appear in the autocomplete box
  var numResultsToDisplay = 10;
  
  //
  var ptmUI = {
    srcLang: function(){ return $("select#src-list_ option:selected").val(); },
    tgtLang: function(){ return $("select#tgt-list_").val(); },
    src: function(){     return $("textarea#src-input_").val(); },
    tgt: function(){     return $("textarea#ptm-input_").val(); },
    srcOOV: function(context){ return $(context).find(".oov-src-text").html(); },
    tgtOOV: function(context){ return $(context).find(".oov-tgt").val(); },
    
    showPTM: function(){
      if ($('.form-oov:visible').length === 1){
        $('#ptm_').show();
      }
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
      //console.log(data);
      
      var fontSize = $( "#ptm-input_" ).css("font-size");
      fontSize = parseInt(fontSize);
      console.log("fontSize: " + fontSize);
      
      var boxWidth = $( "#ptm-input_" ).css("width");
      boxWidth = parseInt(boxWidth);
      console.log("boxWidth: " + boxWidth);
      
      var charPos = $( "#ptm-input_" ).getSelection().end;
      charPos = parseInt(charPos);
      console.log("charPos: " + charPos);
      
      var vOffset = Math.floor((charPos * fontSize) / boxWidth);
      vOffset = (vOffset+1) * fontSize;
      
      var hOffset = (charPos * fontSize) % boxWidth;
      
      //my = Alignment position on the autocomplete box
      //at = Alignment position on the target element
      var newPos = {
        my: "left top",
        at: "left top",
        collision: "none",
        offset: hOffset.toString() + " " + vOffset.toString(),
      };
      console.log(newPos);
      
      $( "#ptm-input_" ).autocomplete( "option", "position", newPos );
      
      //TODO: Sanity check...should do this on the server side
      var predictions = data.predictions.slice(0,numResultsToDisplay);
      
      response( $.map( predictions, function( item ) {
      //  console.log(item);
        return { label: item, value: data.prefix + item }
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
        source: ptmUI.src(),
      };
      
      console.log("POST: ptmInit");
      console.log(ptmMsg);
      
      $.ajax({
            url: serverURL,
            dataType: "json",
            data: {ptmInit: JSON.stringify(ptmMsg), },
            success: serveData.oovResponse,
      });     
    },
    
    sendOOV: function(event){
      event.preventDefault();
      $(this).slideUp();
      
      var ptmMsg = {
        sourcePhrase: ptmUI.srcOOV(this),
        targetPhrase: ptmUI.tgtOOV(this),
      };
      
      console.log("POST: sendOOV");
      console.log(ptmMsg);
      
      $.ajax({
            url: serverURL,
            dataType: "json",
            data: {ptmOOVPhrasePair: JSON.stringify(ptmMsg), },
      });   
      
      ptmUI.showPTM();
    },
    
    //User request for an autocomplete
    autoCompleteReq: function(request,response){
  
      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.src(),
        prefix: ptmUI.tgt(),
      };
      console.log("POST: ptmPredict");
      console.log(ptmMsg);
    
      //Register the callback here since we'll send it directly
      //to the autocomplete box
      $.ajax({
            url: serverURL,
            dataType: "json",
            data: { ptmPredict: JSON.stringify(ptmMsg), },
            success: function(data){
              serveData.predictResponse(data,response);
            },
      });
    },
        
    //When the user selects a completion
    autoCompleteSelect: function(completion){
      console.log("autoCompleteSelect:");
      console.log(completion);
      console.log(ptmUI.tgt());
      
      var oldPrefix = ptmUI.tgt();
      
      //Set the autocomplete box on the interface
      //TODO: Move this into a separate interface component      
      $("textarea#ptm-input_").val(oldPrefix + completion);
      
      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.src(),
        prefix: oldPrefix,
        completion: completion,
      };
      console.log("POST: ptmUserSelection");
      console.log(ptmMsg);
      
      $.ajax({
            url: serverURL,
            dataType: "json",
            data: {ptmUserSelection: JSON.stringify(ptmMsg),},
      });
    },
    
    //User has finished the translation.
    //Submit to server
    doneWithTranslation: function(){
      
      var ptmMsg = {
        sourceLang: ptmUI.srcLang(),
        targetLang: ptmUI.tgtLang(),
        source: ptmUI.src(),
        finishedTarget: ptmUI.tgt(),
      };
      console.log("POST: ptmDone");
      console.log(ptmMsg);
      
      $.ajax({
            url: serverURL,
            dataType: "json",
            data: { ptmDone: JSON.stringify(ptmMsg), },
      });
    },
    
  };
  
  return fn;
})();

//Map ptm to the global namespace
window.ptm = ptm;

})(window);
