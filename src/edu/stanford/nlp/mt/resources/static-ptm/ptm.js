// Predictive Translation Memory 
//
// Setup using the module pattern:
// http://addyosmani.com/resources/essentialjsdesignpatterns/book/#designpatternsjavascript

(function(window){

  //Register ptm callbacks with the UI when it is ready
  $(document).ready(function(){

    //Configure the autocomplete box, even though it is initially hidden
    $( "#ptm-input_" ).autocomplete({
      minLength: 2,
      search: function(event,ui){
        var source = $("textarea#ptm-input_").val();
        var endsWithWhitespace=new RegExp("\\s$");
        console.log("Ends with whitespace:" + endsWithWhitespace.test(source));  
        return endsWithWhitespace.test(source);
      },
      
      source: function(request,response){
        ptm.autoCompleteReq(request,response);
      },
      
      select: function( event, ui ) {
        
        console.log( "Selected:" + ui.item);
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
  
  var serverURL = "http://jack.stanford.edu:8080";
  
  //Initialize any variables here
  var ui = {
    srcLang: function(){ return $("select#src-list_ option:selected").val(); },
    tgtLang: function(){ return $("select#tgt-list_").val(); },
    src: function(){     return $("textarea#src-input_").val(); },
    tgt: function(){     return $("textarea#ptm-input_").val(); },
  };
  
  //Callbacks from the server to render data to the interface
  var serveData = {
    //TODO: Want to remove the CSS/HTML from here...but how?
    oovResponse: function(data) {
      console.log("oovResponse");
      console.log(data);
  
      //Clear what's in there now.
      $("#oov-input_").html('');
      
      //Populate the OOV list dynamically
      for(var i in data.OOVs){
        var htmlStr = '<div class=\"oov-pair\">';
        htmlStr = htmlStr + '<form class="form-oov" name="form-oov-' + i + '">';
        htmlStr = htmlStr + '<span class="oov-src-text" id="form-oov-src-' + i +  '">' + data.OOVs[i] + '</span>';
        htmlStr = htmlStr + '<input type="text" class="oov-tgt" />';
        htmlStr = htmlStr + '<input type="submit" value="X" />'
        htmlStr = htmlStr + '</form></div>';
        
        console.log(htmlStr);
        $("#oov-input_").append(htmlStr);
      }
      
      $('.form-oov').submit(function(event){
        event.preventDefault();
        $(this).slideUp();
        
        //TODO: Send the OOV messages
        
        //TODO: Move this into the UI functionality
        console.log($('.form-oov:visible').length);
        if ($('.form-oov:visible').length === 1){
          console.log('In here');
          $('#ptm_').show();
        }
      });
    },
  };
  
  
  //Callbacks to be registered with the UI/client
  //
  //
  var fn = {
    //Initializes translation from the interface
    initTranslation: function(){

      var ptmMsg = {
        sourceLang: ui.srcLang(),
        targetLang: ui.tgtLang(),
        source: ui.src(),
      };
      
      console.log("POST: ptmInit");
      console.log(ptmMsg);
      console.log(serverURL);
      
      $.ajax({
            url: serverURL,
            dataType: "json",
            data: {ptmInit: JSON.stringify(ptmMsg), },
            success: serveData.oovResponse,
      });     
    },
    
    //User request for an autocomplete
    autoCompleteReq: function(request,response){
  
      var ptmMsg = {
        sourceLang: ui.srcLang(),
        targetLang: ui.tgtLang(),
        source: ui.src(),
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
              response( $.map( data, function( item ) {
                return { label: item.name, value: item.name }
              }));
            },
      });
    },
        
    //When the user selects a completion
    autoCompleteSelect: function(completion){

      //Set the autocomplete box on the interface
      //TODO: Move this into a separate interface component      
      $("textarea#ptm-input_").val(completion);
      
      var ptmMsg = {
        sourceLang: ui.srcLang(),
        targetLang: ui.tgtLang(),
        source: ui.src(),
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
        sourceLang: ui.srcLang(),
        targetLang: ui.tgtLang(),
        source: ui.src(),
        finishedTarget: ui.tgt(),
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
