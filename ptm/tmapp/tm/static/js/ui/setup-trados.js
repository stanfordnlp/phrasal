// ptm-setup.js
// Register ptm callbacks with the translation manager interface
//
// Requires:
//   ptm2.js
//   jquery-latest.js
//   translog2.js
//
$(document).ready(function(){
  // DEBUG: Uncomment this to disable logging
  // console.log = function() {}

  // PTM parameters
  var hostPort = '8017';
  var hostString = window.location.protocol + "//0.0.0.0" + ':' + hostPort;
  ptm.setHostString(hostString);

  // Set required CSS elements in the document template
  var cssArray = {
    tgtTxtArea: 'form-tgt-txt',
    srcTokClassPrefix: 'src-tok',
    statusBox: 'status-box',
    widgetContainerStyleId: 'wrapper1',
  };
  ptm.setCSSElements(cssArray);

  // Set the focus on the translation input box
  $( 'textarea#form-tgt-txt' ).focus();

  // Clear the textarea if the user hits back
  $( 'textarea' ).val('');
  
  // Set the source language
  var srcLang = $('div#src-lang').html();
  ptm.setSrcLang(srcLang);

  // Set the target language, and a callback for
  // further changes to the dropdown
  var tgtLang = $( 'select[name=form-tgt-lang]').val();
  ptm.setTgtLang(tgtLang);
  $( 'select[name=form-tgt-lang]' ).change(function() {
    ptm.setTgtLang(this.value);
  });

  // Set the source input
  var srcTxt = $('div#src-txt').html();
  ptm.setSrcTxt(srcTxt);

  // Attach the keystroke listener to the textarea
  $( 'textarea#form-tgt-txt' ).keypress(function(event){
    ptm.addKeyStroke(event);
  });

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

  // Attach to the form submit event, but don't prevent the default
  // action (POST)
  $( '#form-tgt-input' ).submit(function(event){
    ptm.doneWithTranslation();
  });

  // Here we go...
  ptm.initTranslation();

});