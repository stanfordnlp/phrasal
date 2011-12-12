//Register ptm callbacks with the UI when it is ready

$(document).ready(function(){

  // DEBUG: Uncomment this to disable logging
  //console.log = function() {}
  
  //Log a keystroke in the translation box. Start the timers
  //for the PTM box.
  $( "#ptm-input_" ).keypress(function(event){
    ptm.addKeyStroke(event);    
  });    
  
  //Setup the clear button
  $( '#ptm-clear_' ).click(function(){
    ptm.reset();
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
  
  //Setup the source language list
  ptm.setupSourceLanguages();
  // Register a select event handler.
  $( '#src-list_' ).change(function(){
    ptm.selectSource($(this).val());
  });
  
  //Blank out all forms
  //This is needed when we redirect to the same page after a done event
  $(':input' )
    .not(':button, :submit, :reset')
    .val('')
    .removeAttr('checked')
    .removeAttr('selected');
});