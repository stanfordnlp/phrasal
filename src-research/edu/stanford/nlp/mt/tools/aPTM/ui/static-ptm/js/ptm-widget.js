// Widget that displays translation options
// Absolute positioning is used to position the div.

// Dependencies: jquery

var PTMWidget = function(completions, divContainerId) {
  // CSS id for the widget (see ptm.css)
  this.domId = 'ptm-widget_';

  // CSS selector for the widget
  this.widgetSel = 'div#'+this.domId;

  // CSS id of the div in which we will insert the widget
  this.containerSel = 'div#'+divContainerId;
  
  // Full list that should never be filtered
  this.fullNbestList = completions;

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
  this.defaultBackground = 'White';
  this.tgtBorder = '#E80000';
  this.tgtBackground = 'Red';
  this.tgtSelectBackground = '#E80000';
  this.srcBorder = '#6699FF';
  this.srcFontColor = 'Black';
  this.srcFontShaded = 'DarkGrey';
};

// selectCallback: function to call when an option is selected
// filterIds: (optional) An associative array of (nbestId,true/1/whatever) showing which items to display in the list. 
PTMWidget.prototype.Show = function(selectCallback, filterIds) {
  if (filterIds){
    this.nbestList = $.grep(this.fullNbestList, function(item, index){
      return filterIds[index];
    });
    if (this.nbestList.length == 0){
      console.log('WARN: Filtered all completions in the nbest list!');
      return;
    }
  }
  this.nbestId = 0;
  this.selectCB = selectCallback;

  // Calculate the position
  var option = this.nbestList[this.nbestId];

  var divString = sprintf("<div id=\"%s\">%s</div>", this.domId, option.tgt);

  // Insert the div into the dom and style the CSS.
  $( this.containerSel ).append(divString);
  var width = $( this.widgetSel ).width();
  var height = $( this.widgetSel ).height();
  var boundBox = this.GetBoundingBox(option, width, height);
  
  $( this.widgetSel ).css('top', boundBox.top);
  $( this.widgetSel ).css('left', boundBox.left);
  $( this.widgetSel ).css('border-color', this.tgtBorder);
  $( this.widgetSel ).css('background-color', this.tgtBackground);

  this.HighlightPrefix(option.pref, true);
  this.HighlightTokens(option.coverage, true);
  
  // Map the keyboard handler
  var fnProxy = $.proxy(this.KeyHandler, this);
  this.handlerProxy = fnProxy;
  $( document ).keydown(fnProxy);
};

PTMWidget.prototype.GetBoundingBox = function(option, width, height) {
  var boundBox = 0;
  var boxWidth = 0;
  for (var i in option.coverage){
    var sel = 'div#'+option.coverage[i];
    var newBox = $( sel ).offset();
    if (!boundBox){
      boundBox = newBox;
      boxWidth += $( sel ).width();
    } else {
      if (newBox.top < boundBox.top){
        boundBox.top = newBox.top;
      }
      if (newBox.left < boundBox.left){
        boundBox.left = newBox.left;
        boxWidth += $( sel ).width();
      }
    }
  }

  // Now boundbox marks the boundaries of the covered source tokens
  // Center the div according to its width/height dimentions
  var leftOffset = (boxWidth - width) / 2;
  boundBox.left += leftOffset;
  if (boundBox.left < 0){
    boundBox.left = 0;
  }

  if (height < 30){
    boundBox.top -= 30;
  } else {
    boundBox.top -= height;
  }

  return boundBox;
};

PTMWidget.prototype.Hide = function(doFade) {
//  console.log("ptmWidget: Hide");
  $( document ).unbind('keydown', this.fnProxy);
  if (doFade) {
//    console.log('doFade');
    var remProxy = $.proxy(this.Remove, this);
    $( this.widgetSel ).fadeOut(remProxy);
  } else {
    $( this.widgetSel ).hide();
    this.Remove();
  }
};

PTMWidget.prototype.Remove = function() {
  $( this.widgetSel ).remove();
};

PTMWidget.prototype.KeyHandler = function(event) {
//  console.log("ptmWidget:Handler " + event.keyCode);
  if (this.scrollKeys[event.keyCode]){
//    console.log("Scroll event");
    event.preventDefault();
    this.ScrollText(this.scrollKeys[event.keyCode]);

  } else {
    var completion = this.nbestList[this.nbestId];
    this.HighlightPrefix(completion.pref, false);
    this.HighlightTokens(completion.coverage, false);
    if (this.selectKeys[event.keyCode]){
//      console.log("Select event");
      event.preventDefault();
      this.selectCB(completion.tgt);
      this.Hide(true);
    } else {
      this.Hide(false);
    }
  }
};

PTMWidget.prototype.ScrollText = function(cmd) {
//  console.log('scroll: ' + cmd);
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
  this.HighlightPrefix(lastOption.pref, false);
  this.HighlightTokens(lastOption.coverage, false);

  var option = this.nbestList[this.nbestId];
  $( this.widgetSel ).text(option.tgt);
  var width = $( this.widgetSel ).width();
  var height = $( this.widgetSel ).height();
  var boundBox = this.GetBoundingBox(option, width, height);
  var curPosition = $( this.widgetSel ).offset();

  if (boundBox.top != curPosition.top ||
      boundBox.left != curPosition.left){
  //  console.log(boundBox);
  //  console.log(curPosition);
    var topParam = {
      true:'+='+Math.abs(curPosition.top-boundBox.top),
      false:'-='+Math.abs(curPosition.top-boundBox.top)
      };
    var topPos = topParam[curPosition.top-boundBox.top < 0];
//  console.log('top: '+topPos);
  
    var leftParam = {
      true:'+='+Math.abs(curPosition.left-boundBox.left),
      false:'-='+Math.abs(curPosition.left-boundBox.left)
      };
    var leftPos = leftParam[curPosition.left-boundBox.left < 0];
//  console.log('left: ' + leftPos);

    // See here for different easing functions:
    // http://jqueryui.com/demos/effect/easing.html
    $( this.widgetSel ).animate(
      {
        top:topPos,
        left:leftPos
      },
      {
        duration:'fast',
        easing:'easeInOutCubic'
    });
  }
  this.HighlightPrefix(option.pref, true);  
  this.HighlightTokens(option.coverage, true);  
};

PTMWidget.prototype.HighlightTokens = function(divIdArray, doHighlight) {
  //console.log('Highlight: ' + doHighlight);
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
    var divId = 'div#'+divIdArray[i];
    
//    $( divId ).css( 'background-color', backgroundColor);
    $( divId ).css('border-color', backgroundColor);
//    $( divId ).css('font-weight', textStyle);
  }
};

PTMWidget.prototype.HighlightPrefix = function(divIdArray, doShading) {
  //console.log('HighlightPref:' + divIdArray);
  if (divIdArray) {
    var textColor = 0;
    if (doShading){
      textColor = this.srcFontShaded;
    } else {
      textColor = this.srcFontColor;
    }
    for (var i in divIdArray) {
      var divId = 'div#'+divIdArray[i];
      $( divId ).css('color', textColor);
    }
  }
};
