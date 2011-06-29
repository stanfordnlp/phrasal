
//This is the default jQuery documentReady function
//that comes up with onLoad()
$(document).ready(function(){

    //Configure the autocomplete box, even though it is initially hidden
    //
    $( "#ptm-input_" ).autocomplete({
      minLength: 2,
      search: function(event,ui){
//        var source = $("textarea#ptm-input_").val();
//        var endsWithWhitespace=new RegExp("\\s$");
//        console.log("Ends with whitespace:" + endsWithWhitespace.test(source));  
//        return endsWithWhitespace.test(source);
          return true;
      },
      
      source: function(request,response){
        usrHandler.autoCompleteReq(request,response);
      },
      
      select: function( event, ui ) {
        
        console.log( "Selected:" + ui.item);
        if(ui.item){
          usrHandler.autoCompleteSelect(ui.item.label);
        }
      },
    });
    
    //Configure the handler for completing translation
    //
    $( '#form-ptm-input_' ).submit(function(event){
      event.preventDefault();
      usrHandler.doneWithTranslation();
      return false;
    });

    //Configure the form that will initiate translation
    //
    $('#lang-form_').submit(function(event){
      event.preventDefault();
      usrHandler.initTranslation();
      return false;
    });
    
});

var autoResp;

var usrHandler = {
  initTranslation: function(){

    var sourceLang = $("select#src-list_ option:selected").val();
    var targetLang = $("select#tgt-list_").val();
    var source = $("textarea#src-input_").val();
    
    var ptmMsg = new ptmInitC(sourceLang,targetLang,source);
    console.log("ptmInit");
    console.log(ptmMsg);
    var msg = {
      ptmInit: JSON.stringify(ptmMsg),
    };
    
    console.log(msg);
    
    $.ajax({
          url: "http://jack.stanford.edu:8080",
          dataType: "jsonp",
          data: msg,
    });
      
  },
  
  autoCompleteReq: function(request,response){
  
    var sourceLang = $("select#src-list_ option:selected").val();
    var targetLang = $("select#tgt-list_").val();
    var source = $("textarea#src-input_").val();
    
    var ptmMsg = new ptmPredictC(sourceLang,targetLang,source,request.term);
    console.log("ptmPredict");
    console.log(ptmMsg);
    var msg = {
      ptmPredict: JSON.stringify(ptmMsg),
    };
    console.log(msg);
    
    $.ajax({
          url: "http://jack.stanford.edu:8080",
          dataType: "jsonp",
          data: msg,
//          success: function(data){
//            console.log("Autocomplete returned from server");
//            console.log(data);
//            response( $.map( data, function( item ) {
//              return {
//                label: item.name,
//                value: item.name
//              }
//            }));
//          },
    });
    
    autoResp = response;
  },
  
  autoCompleteSelect: function(completion){
    console.log("autoCompleteSelect");
    console.log("Completion:" + completion);

    $("textarea#ptm-input_").val(completion);
    
    var sourceLang = $("select#src-list_ option:selected").val();
    var targetLang = $("select#tgt-list_").val();
    var source = $("textarea#src-input_").val();
    
    var ptmMsg = new ptmUserSelectionC(sourceLang,targetLang,source,completion);

    console.log("ptmUserSelection");
    console.log(ptmMsg);
    var msg = {
      ptmUserSelection: JSON.stringify(ptmMsg),
    };
    console.log(msg);
    
    $.ajax({
          url: "http://jack.stanford.edu:8080",
          dataType: "jsonp",
          data: msg,
    });
    
    //TODO: Remove
    fn(jump);
  },
  
  doneWithTranslation: function(){
    var sourceLang = $("select#src-list_ option:selected").val();
    var targetLang = $("select#tgt-list_").val();
    var source = $("textarea#src-input_").val();
    var tgt =  $("textarea#ptm-input_").val();
    
    var ptmMsg = new ptmDoneC(sourceLang,targetLang,source,tgt);
    console.log("ptmDone");
    console.log(ptmMsg);
    var msg = {
      ptmDone: JSON.stringify(ptmMsg),
    };
    console.log(msg);
    
    $.ajax({
          url: "http://jack.stanford.edu:8080",
          dataType: "jsonp",
          data: msg,
    });
  },
};

//
//
// UI Widgets



//////////////////////
// PTM jsonp x-domain calls. These handlers will be executed upon receipt from
// the jetty server.
//////////////////////


//
// Returns the 1-best translation
//
function ptmInitResponse(data){
  console.log(data);
  
  //TODO: Dynamically populate the list of OOVs
  $('#form-oov-1-src_').html(data.OOVs[0]);
  $('#form-oov-2-src_').html(data.OOVs[1]);  
  
  $('.form-oov').submit(function(event){
    event.preventDefault();
    console.log(this);
    return false;
  });

  //Process the list of OOVs
  //Create the list of forms for submission of OOV translations
};

//
// ACK response to ptmOOV
//
function ptmOOVResponse(data){
  console.log("OOV response:" + data);
};

//
//
//
function ptmPredictResponse(data){
  console.log("ptmPredictResponse");
  var sourceArr = [];
  for(var i in data.predictions){
    var item = {
      label: data.prefix + data.predictions[i],
      value: data.prefix,
    };
    sourceArr.push(item);
//    console.log(item);
  }
  
//  console.log(sourceArr);
//  console.log(autoResp);
  autoResp(sourceArr);
};

//
// 
//
function ptmUserSelectionResponse(data){
  //Send out which one was selected
};

//
//
//
function ptmDoneResponse(data){
  //Notify server that the translation is finished
};


////////////////////////
// PTM messages
////////////////////////

//
// Requests a 1-best translation from the server
//
function ptmInitC(sourceLang,targetLang,source){
  this.sourceLang = sourceLang;
  this.targetLang = targetLang;
  this.source = source;
};

//
// Sends a user-solicited phrase pair to the server
//
function ptmOOVC(sourceLang,targetLang,sourcePhrase,targetPhrase){
  this.sourceLang = sourceLang;
  this.targetLang = targetLang;
  this.sourcePhrase = sourcePhrase;
  this.targetPhrase = targetPhrase;
};

//
// Message that feeds the autocomplete with MT completions
//
function ptmPredictC(sourceLang,targetLang,source,prefix){
  this.sourceLang = sourceLang;
  this.targetLang = targetLang;
  this.source = source;
  this.prefix = prefix;
};

//
// Indicates an autocomplete selection by the user
//
function ptmUserSelectionC(sourceLang,targetLang,source,prefix){
  this.sourceLang = sourceLang;
  this.targetLang = targetLang;
  this.source = source;
  this.prefix = prefix;
//  this.completion = completion;
};

//
// User has completed translation. This is a new reference
//
function ptmDoneC(sourceLang,targetLang,source,finishedTarget){
  this.sourceLang = sourceLang;
  this.targetLang = targetLang;
  this.source = source;
  this.finishedTarget = finishedTarget;
};



///////////////////
// Old Deprecated stuff for reference
///////////////////

function someFunc(data){
  console.log(data);
};

var oneBest = {

  processSrc: function() {
    var requestJSON = {
//      sourceLang: $("select#src-list_ option:selected").val(),
//      targetLang: $("select#tgt-list_").val(),
      source: $("textarea#src-input_").val(),
      prefix: "1 2"
    };
    console.log(requestJSON);
    //TODO: The AJAX call here
    $.ajax({
          url: "http://jack.stanford.edu:8080",
          dataType: "jsonp",
          data: requestJSON,
//wsg: For x-domain requests, use jsonp, which will be executed upon
//     receipt. For dataType: json, register a success() handler.
//          success: function(data) {
//              alert('Handler');
//              console.log(data);
//              oneBest.processMtResult(data);
//          }
//         error: function(jqxhr, textstatus, errorThrown) {
//           console.log(textstatus);
//           console.log(errorThrown);
//         }
        });
  },
  
  processMtResult: function(data) {
  
  }
};
