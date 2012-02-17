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

  // Clear the textarea on back
  $( 'textarea' ).val('');
  
  // Translog2 --- User action logging
  tlog2.init();
  $( 'input[name="form-tgt-submit"]' ).click(function(){
    tlog2.flushForm('form-action-log');
  });

  // Setup the timer
  var n_tokens = $( '[id^="src-tok"]' ).length;
  var tok_per_sec = .0625;
  var max_secs = Math.round(n_tokens / tok_per_sec);
  countdown.init(max_secs);
  countdown.addCallback(function(){
    alert('You exceeded the maximum translation time for this sentence. It will be submitted. Click OK to continue to the next sentence.');
    $( 'input[name="form-complete"]' ).val('0');
    $( 'input[name="form-tgt-submit"]' ).trigger('click');
  });
  countdown.show();
  
});