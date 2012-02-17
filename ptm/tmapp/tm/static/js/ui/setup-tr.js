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
  // console.log = function() {}

  // Set the focus on the translation input box
  $( 'textarea#form-tgt-txt' ).focus();

  // Clear the text area if the user hits back!
  $( 'textarea' ).val('');
  
  // Translog2 --- User action logging
  tlog2.init();
  $( '#form-tgt-submit' ).click(function(){
    tlog2.flushForm('form-action-log');
  });

  // Setup the timer
  // TODO(spenceg): Set the timeout based on the amount
  // of text to translate
  countdown.init(600);
  countdown.addCallback(function(){
    alert('hello');
  });
  countdown.show();
  
});