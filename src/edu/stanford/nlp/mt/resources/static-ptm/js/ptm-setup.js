//Register ptm callbacks with the UI when it is ready
$(document).ready(function(){

  // jQueryUI autocomplete box configuration
  $( "#ptm-input_" ).autocomplete({
    // Every keystroke potentially triggers an autocomplete event    
    minLength: 0,
    
    // ms delay after keystroke for lookup
    delay: 50,
    
    // Focus the 1-best when the autocomplete box appears
    autoFocus: true,
    
    //keyCode(32) == spacebar, which triggers autocomplete        
    search: function(event,ui){
      if(event.which === 32){
        ptm.clearCache();
      }
      return ptm.shouldFireAutocomplete();
    },
    
    source: function(request,response){
      ptm.autoCompleteReq(request,response);
    },
    
    focus: function( event, ui ) {
      ptm.addKeyStroke();
      return false;//Cancel the update of the box value
    },
    
    select: function( event, ui ) {  
      //Log user actions in the autocomplete box
      ptm.addKeyStroke();
      
      if(ui.item){
        ptm.autoCompleteSelect(ui.item.label);
      }
    },
  });
  
  //TODO(spenceg) Need to add hook to disable/enablePTM functions
  //
  //
  
  //Trigger an autocomplete on the onfocus event for the
  //ptm box.
  $( "#ptm-input_" ).focusin(function(event){
    $( "#ptm-input_" ).autocomplete("search");
  });
  
  //Setup keystroke counting (for typing)
  $( "#ptm-input_" ).keypress(function(event){
    ptm.addKeyStroke();    
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