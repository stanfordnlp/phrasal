$(document).ready(function(){
  
  var _serverURL = 'http://127.0.0.1:8017/t';

  console.log('URL: ' + _serverURL);
  
  
  $('#form-src-input').submit(function(event) {
    event.preventDefault();
    console.log('Inside click handler2');
    // Fetch the form input
    var source = $('#src-input').val();
    console.log(source);
    
    // Translation message
    var msg = {
      src : "EN",
      tgt : "AR",
      n : 1,
      text : source,
    };
    
    // Make ajax request
    $.ajax({
      url: _serverURL,
      dataType: "json",
      data: {translationRequest : JSON.stringify(msg), },
//      xhrFields: {
//        withCredentials: true
//      },
      success: function(data){
        //response is a callback
        console.log("Received message: " + data);
        $('#output').append('<p>' + data.tgtList[0] + '</p>');
      },
      error: function(jqXHR, textStatus, errorThrown){
        console.log("ptm failed: " + textStatus);
        console.log(errorThrown);
      },
    });
    return false;
  });

  
});