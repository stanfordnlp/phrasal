// Widget that displays translation options
// Absolute positioning is used to position the div.

// Dependencies: jquery

var PTMWidget = function(completions, divContainerId) {
  // CSS id for the widget
  this.domId = 'ptm-widget_';

  // CSS id of the div in which we will insert the widget
  this.containerId = divContainerId;
  
  // Ranked list of completions
  this.nbestList = completions;

  // Index in nbestList of the currently displayed nbest item
  this.nbestId = 0;

  // Callback for the select handler
  this.selectCB = 0;

  // Keyboard id for scroll event
  this.scrollKeys = {
    38:'prev',
    40:'next'
  };
  
  // Keyboard id for select event
  this.selectKeys = {
    13:'select-enter',
    39:'select-right-arrow',
  };
  
  // Handler proxy function
  this.handlerProxy = 0;

  //CSS theming
  this.defaultBackground = '#FFFFFF';
  this.tgtBorder = '#E80000';
  this.tgtBackground = '#FF0000';
  this.tgtSelectBackground = '#E80000';
  this.srcBorder = '#6699FF';
};

// selectCallback: function to call when an option is selected
// completions: (optional) ranked list of completions and divs to which the prefix maps. Supercedes the list of completions provided during instantiation
PTMWidget.prototype.Show = function(selectCallback, completions) {
  if (completions){
    this.nbestList = completions;
    this.nbestId = 0;
  }
  this.selectCB = selectCallback;

  // Calculate the position
  var option = this.nbestList[this.nbestId];
  var boundBox = this.GetBoundingBox(option);

  var top = boundBox.top;
  var left = boundBox.left;
  var styleString = "position:absolute;top:" + top + ";left:" + left + ";";
  styleString += "border-width:2px;border-style:solid;border-color:"+this.tgtBorder+";";
  styleString += "padding:0.2em;opacity:1.0;background-color:"+this.tgtBackground;
  
  // Setup the div string
  var divString = sprintf("<div style=\"%s\" id=\"%s\">",styleString,this.domId);
  divString += option.tgt + "</div>";

  // Insert the div into the dom.
  $( '#'+this.containerId ).append(divString);
  this.HighlightTokens(option.coverage, true);
  
  // Map the keyboard handler
  var fnProxy = $.proxy(this.KeyHandler, this);
  this.handlerProxy = fnProxy;
  $( document ).keydown(fnProxy);
};

PTMWidget.prototype.GetBoundingBox = function(option) {
  var boundBox = 0;
  for (var i in option.coverage){
    var newBox = $( option.coverage[i] ).offset();
    if (!boundBox){
      boundBox = newBox;
    } else {
      if (newBox.top < boundBox.top){
        boundBox.top = newBox.top;
      }
      if (newBox.left < boundBox.left){
        boundBox.left = newBox.left;
      }
      if (newBox.right > boundBox.right){
        boundBox.right = newBox.right;
      }
      if (newBox.bottom > boundBox.bottom){
        boundBox.bottom = newBox.bottom;
      }
    }
  }
  // TODO(spenceg): Make this more precise relative to the font size
  boundBox.top -= 30;
  return boundBox;
};

PTMWidget.prototype.Hide = function(doFade) {
  console.log("ptmWidget: Hide");
  $( document ).unbind('keydown', this.fnProxy);
  if (doFade) {
    console.log('doFade');
    var remProxy = $.proxy(this.Remove, this);
    $( '#'+this.domId ).fadeOut('slow', remProxy);
  } else {
    $( '#'+this.domId ).hide();
    this.Remove();
  }
};

PTMWidget.prototype.Remove = function() {
  $( '#'+this.domId ).remove();
};

PTMWidget.prototype.KeyHandler = function(event) {
  console.log("ptmWidget:Handler " + event.keyCode);
  if (this.scrollKeys[event.keyCode]){
    console.log("Scroll event");
    event.preventDefault();
    this.ScrollText(this.scrollKeys[event.keyCode]);

  } else {
    var completion = this.nbestList[this.nbestId];
    this.HighlightTokens(completion.coverage, false);
    if (this.selectKeys[event.keyCode]){
      console.log("Select event");
      event.preventDefault();
      this.selectCB(completion.tgt);
      this.Hide(true);
    } else {
      this.Hide(false);
    }
  }
};

PTMWidget.prototype.ScrollText = function(cmd) {
  console.log('scroll: ' + cmd);
  var lastOption = this.nbestList[this.nbestId];

  if (cmd == 'next') {
    this.nbestId++;
    if (this.nbestId == this.nbestList.length) {
      this.nbestId = 0;
    }
  } else {
    this.nbestId--;
    if (this.nbestId < 0) {
      this.nbestId = this.nbestList.length - 1;
    }
  }
  this.HighlightTokens(lastOption.coverage, false);

  var option = this.nbestList[this.nbestId];
  var boundBox = this.GetBoundingBox(option);
  $( '#'+this.domId ).css('top', boundBox.top);
  $( '#'+this.domId ).css('left', boundBox.left);
  $( '#'+this.domId ).text(option.tgt);
  this.HighlightTokens(option.coverage, true);
};

PTMWidget.prototype.HighlightTokens = function(divIdArray, doHighlight) {
//  console.log('Highlight: ' + doHighlight);
  var backgroundColor = 0;
  var textStyle = 0;
  if (doHighlight){
    backgroundColor = this.srcBorder;
    textStyle = 'bold';
  } else {
    backgroundColor = this.defaultBackground;
    textStyle = 'normal';
  }
  for (var i in divIdArray) {
    var divId = divIdArray[i];
    console.log(divId);
    
//    $( divId ).css( 'background-color', backgroundColor);
    $( divId ).css('border-color', backgroundColor);
//    $( divId ).css('font-weight', textStyle);
  }
};
