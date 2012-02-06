// Interface #2 WIDGET!!!

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
  this.tgtBackground = 'White';
  this.tgtBorder = 'WhiteSmoke';
  this.tgtSelectBackground = 'Gainsboro';
  this.tgtBackground = 'White';

  this.srcBackground = 'White';
  this.srcSelectBorder = 'WhiteSmoke';
  this.srcSelectBackground = 'WhiteSmoke';
  this.srcFontColor = 'Black';
  this.srcFontShaded = 'DarkGrey';

  // nbest size has a fixed dimension of 3
  this.nbestShadings = new Array(3);
  this.nbestShadings[0] = 'Blue';
  this.nbestShadings[1] = 'Red';
  this.nbestShadings[2] = 'Green';
  
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

  var divString = sprintf("<div id=\"%s\">", this.domId);
  for (var i=0; i < this.nbestShadings.length; i++) {
    var nbestOption = this.nbestList[this.nbestId + i]
    var tgtDiv = sprintf("<div class=\"%sitem\" id=\"%s%d\">%s</div>",this.domId,this.domId,i,nbestOption.tgt);
    divString = divString + tgtDiv;
  }
  divString = divString + "</div>";
  console.log(divString);

  // Insert the div into the dom and style the CSS.
  $( this.containerSel ).append(divString);
  // See: http://stackoverflow.com/questions/1909648/stacking-divs-in-top-of-each-other
  $( '#'+this.domId+'item').css('position','absolute');
  for (var i in this.nbestShadings) {
    var color = this.nbestShadings[i];
    $( '#'+this.domId+i ).css('color',color);
  }
  
  var width = $( this.widgetSel ).width();
  var height = $( this.widgetSel ).height();
  var boundBox = this.GetBoundingBox(option, width, height);

  $( this.widgetSel ).css('top', boundBox.top);
  $( this.widgetSel ).css('left', boundBox.left);
  $( this.widgetSel ).css('border-color', this.tgtBorder);
  $( this.widgetSel ).css('background-color', this.tgtBackground);

  this.Highlight(this.nbestId, true);
  
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

  boundBox.top += 30;
  
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
    this.Highlight(this.nbestId, false);
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
  var lastOption = this.nbestId;

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
  this.Highlight(lastOption, false);
  this.Highlight(this.nbestId, true);
};

PTMWidget.prototype.Highlight = function(optionId, doHighlight) {
  // console.log('Highlight: ' + optionId + ' ' + doHighlight);
  var option = this.nbestList[optionId];
  var prefDivIdArray = option.pref;
  var compDivIdArray = option.coverage;
  var menuSel = '#'+this.domId+optionId;
  var shading = this.nbestShadings[optionId];
  if (doHighlight) {
    // Fix the tgt menu
    $( menuSel ).css('background-color',this.tgtSelectBackground);

    // Fix the source tokens
    for (var i in compDivIdArray) {
      var divId = 'div#'+compDivIdArray[i];
      $( divId ).css('background-color',this.srcSelectBackground);
      $( divId ).css('color',shading);
      $( divId ).css('border-color',this.srcSelectBorder);
    }

    // Fix the prefix
    for (var i in prefDivIdArray) {
      var divId = 'div#'+prefDivIdArray[i];
      $( divId ).css('color',this.srcFontShaded);
    }
  } else {
    // Fix the tgt menu
    $( menuSel ).css('background-color',this.tgtBackground);

    // Fix the source tokens
    for (var i in compDivIdArray) {
      var divId = 'div#'+compDivIdArray[i];
      $( divId ).css('background-color',this.srcBackground);
      $( divId ).css('color',this.srcFontColor);
      $( divId ).css('border-color',this.srcBackground);
    }

    // Fix the prefix
    for (var i in prefDivIdArray) {
      var divId = 'div#'+prefDivIdArray[i];
      $( divId ).css('color',this.srcFontColor);
    }
  }
};


