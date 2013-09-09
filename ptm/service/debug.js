$(document).ready(function(){
  // URL of the PTM service
  var _serverURL = 'http://127.0.0.1:8017/t';
  console.log('URL: ' + _serverURL);
  
  $('#form-tgt-input').keydown(function(e) {
//    e.preventDefault();
    if (e.which == 32) {
    
    // Fetch the form input
    var source = $('#src-input').val();
    // console.log(source);

    // Fetch the target
    var tgtPrefix = $('#tgt-input').val();
    // console.log(tgtPrefix);
    
    // Translation message
    var msg = {
      src : "AR",
      tgt : "EN",
      n : 3,
      text : source,
      tgtPrefix : tgtPrefix,
    };
    
    // Make ajax request
      $.ajax({
        url: _serverURL,
        dataType: "json",
        data: {tReq : JSON.stringify(msg), },
//      xhrFields: {
//        withCredentials: true
//      },
        success: function(data){
          //response is a callback
          console.log(data);
          $('#output').empty();
          for (var i = 0; i < data.tgtList.length; i++) {
            $('#output').append('<p>' + data.tgtList[i] + '</p>');
          }
        },
        error: function(jqXHR, textStatus, errorThrown){
          console.log("ptm failed: " + textStatus);
          console.log(errorThrown);
        },
    });
    }
    return true;
    // For form submission
    // return false; 
  });
});
