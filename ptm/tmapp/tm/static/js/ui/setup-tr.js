// setup-tr.js
// Register ptm callbacks with the translation manager interface
//
// Requires:
//   jquery-latest.js
//   translog2.js
//   timer-widget.js
//
$(document).ready(function(){
  // DEBUG: Uncomment this to disable logging
  console.log = function() {}

  // Set the focus on the translation input box
  $( 'textarea#id_txt' ).focus();

  // Clear the text area if the user hits back!
  $( 'textarea' ).val('');
  
  // Translog2 --- User action logging
  tlog2.init();
  $( 'input[name="form-tgt-submit"]' ).click(function(){
    tlog2.flushForm('action_log');
  });

  // Setup the idle timer
  var max_secs = 180;
  idletimer.init(max_secs);
  idletimer.addCallback(function(){
    alert('You exceeded the maximum idle time for this sentence. It will be submitted. Click OK to continue to the next sentence.');
    $( 'input[name="is_valid"]' ).val('False');
    $( 'input[name="form-tgt-submit"]' ).trigger('click');
  });
  $( 'textarea#id_txt' ).keydown(function(){
    idletimer.reset();
  });
  idletimer.show();
  
});