$(document).ready(function(){
  // URL of the PTM service
  var _serverURL = 'http://joan.stanford.edu:8017/t';
  console.log('URL: ' + _serverURL);

  $('#src-input').change(function(e) {
    var source = $('#src-input').val();
    var msg = {
      src : "AR",
      tgt : "EN",
      n : 3,
      text : source,
      tgtPrefix : "",
    };
    var ajaxTime = Date.now();
    $.ajax({
      url: _serverURL,
      dataType: "json",
      data: {tReq : JSON.stringify(msg), },
//      xhrFields: {
//        withCredentials: true
//      },
      success: function(data){
        //response is a callback
        var totalTime = (Date.now()-ajaxTime) / 1000;
        console.log(data);
        $('#output').empty();
        for (var i = 0; i < data.tgtList.length; i++) {
          $('#output').append('<p>' + data.tgtList[i] + '</p>');
        }
        $('#output').append('<span style="font-size:small;color:red">request: ' + totalTime.toFixed(2).toString() + 's</span>');
      },
      error: function(jqXHR, textStatus, errorThrown){
        console.log("Translation failed");
        console.log(errorThrown);
      },
    });
  });
  
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
      var ajaxTime = Date.now();
      $.ajax({
        url: _serverURL,
        dataType: "json",
        data: {tReq : JSON.stringify(msg), },
//      xhrFields: {
//        withCredentials: true
//      },
        success: function(data){
          //response is a callback
          var totalTime = (Date.now()-ajaxTime) / 1000;
          console.log(data);
          $('#output').empty();
          for (var i = 0; i < data.tgtList.length; i++) {
            $('#output').append('<p>' + data.tgtList[i] + '</p>');
          }
          $('#output').append('<span style="font-size:small;color:red">request: ' + totalTime.toFixed(2).toString() + 's</span>');
        },
        error: function(jqXHR, textStatus, errorThrown){
          console.log("Translation failed");
          console.log(errorThrown);
        },
    });
    }
    return true;
    // For form submission
    // return false; 
  });
});
