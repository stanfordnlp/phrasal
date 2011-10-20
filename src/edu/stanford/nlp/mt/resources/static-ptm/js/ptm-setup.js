//Register ptm callbacks with the UI when it is ready
$(document).ready(function(){

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
  $( '#src-list_' ).append('<option value="NULL" selected="selected"></option>');
  var srcLangs = ptm.getSourceLanguages();
  for(var key in srcLangs){
    var selString = '<option value=\"' + key + '\">' + srcLangs[key] + '</option>';
    $( '#src-list_' ).append(selString);
  }
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