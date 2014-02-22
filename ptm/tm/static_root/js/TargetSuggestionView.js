var TargetSuggestionView = Backbone.View.extend({
	el : "#TargetSuggestions"
});

TargetSuggestionView.prototype.X_OFFSET = 0;
TargetSuggestionView.prototype.Y_OFFSET = 12 + 7;
TargetSuggestionView.prototype.CATCHER_PADDING = 4;
TargetSuggestionView.prototype.MT_COLOR = "#4292C6";
TargetSuggestionView.prototype.ACTIVE_COLOR = "#ff7f0e";

TargetSuggestionView.prototype.initialize = function() {
	this.views = {};
	this.views.container = d3.select( this.el ).call( this.__containerRenderOnce.bind(this) );
	this.views.canvas = this.views.container.style( "position", "absolute" );
	this.views.catcher = this.views.canvas.append( "div" ).attr( "class", "Catcher" ).call( this.__catcherRenderOnce.bind(this) );
	this.views.overlay = this.views.canvas.append( "div" ).attr( "class", "Overlay" ).call( this.__overlayRenderOnce.bind(this) );
	this.listenTo( this.model, "change", this.render );
};

TargetSuggestionView.prototype.render = function() {
	var candidates = this.model.get( "candidates" );

  // Don't render if the prefix is empty
  
  
	var elems = this.views.overlay.selectAll( "div.token" ).data( candidates );
	elems.enter().append( "div" ).attr( "class", "token" ).call( this.__tokenRenderOnce.bind(this) );
	elems.exit().remove();

	this.views.container.call( this.__containerRenderAlways.bind(this) );
	this.views.overlay.call( this.__overlayRenderAlways.bind(this) )
	this.views.overlay.selectAll( "div.token" ).call( this.__tokenRenderAlways.bind(this) );
	this.views.catcher.call( this.__catcherRenderAlways.bind(this) );
};

TargetSuggestionView.prototype.__containerRenderOnce = function( elem ) {
	elem.style( "display", "inline-block" )
		.style( "pointer-events", "none" )
		.style( "visibility", "visible" );
};
TargetSuggestionView.prototype.__containerRenderAlways = function( elem ) {
	var hasFocus = this.model.get( "hasFocus" ) && this.model.get( "hasMasterFocus" );
	var candidates = this.model.get( "candidates" );
  var isInitial = this.model.get( "isInitial" );
	var isVisible = ( hasFocus && candidates.length > 0 && ! isInitial );
	elem.style( "visibility", isVisible ? "visible" : "hidden" )
		.style( "display", isVisible ? "inline-block" : "none" );
};

TargetSuggestionView.prototype.__catcherRenderOnce = function( elem ) {
	elem.style( "position", "absolute" )
		.style( "display", "inline-block" )
		.style( "background", "none" )
		.style( "pointer-events", "auto" )
		.style( "cursor", "default" )
		.on( "mouseover", this.__onMouseOver.bind(this) )
		.on( "mouseout", this.__onMouseOut.bind(this) );
};
TargetSuggestionView.prototype.__catcherRenderAlways = function( elem ) {
	var xCoord = this.model.get( "xCoord" );
	var yCoord = this.model.get( "yCoord" );
	var width = this.views.overlay[0][0].offsetWidth + (this.CATCHER_PADDING+1) * 2;
	var height = this.views.overlay[0][0].offsetHeight + (this.CATCHER_PADDING+1) * 2;
	elem.style( "left", (xCoord+this.X_OFFSET-this.CATCHER_PADDING-1) + "px" )
		.style( "top", (yCoord+this.Y_OFFSET-this.CATCHER_PADDING-1) + "px" )
		.style( "width", width + "px" )
		.style( "height", height + "px" )
};

TargetSuggestionView.prototype.__overlayRenderOnce = function( elem ) {
	elem.style( "position", "absolute" )
		.style( "display", "inline-block" )
		.style( "background", "#fff" )
		.style( "border", "1px solid " + this.MT_COLOR )
		.style( "box-shadow", "0 0 5px " + this.MT_COLOR )
		.style( "padding", "2px 0 0 0" )
};
TargetSuggestionView.prototype.__overlayRenderAlways = function( elem ) {
	var xCoord = this.model.get( "xCoord" );
	var yCoord = this.model.get( "yCoord" );
	elem.style( "left", (xCoord+this.X_OFFSET) + "px" )
		.style( "top", (yCoord+this.Y_OFFSET) + "px" );
};

TargetSuggestionView.prototype.__tokenRenderOnce = function( elem ) {
	var borderTop = function(_,i) {
		return i===0 ? null : "1px dotted " + this.MT_COLOR
	}.bind(this);
	elem.style( "position", "static" )
		.style( "display", "block" )
		.style( "padding", "0 2px 2px 2px" )
		.style( "border-top", borderTop )
		.style( "white-space", "nowrap" )
		.classed( "TargetLang", true )
		.style( "color", this.MT_COLOR )
		.style( "pointer-events", "auto" )
		.style( "cursor", "pointer" )
		.on( "mouseover", this.__onMouseOverOption.bind(this) )
		.on( "mouseout", this.__onMouseOutOption.bind(this) )
		.on( "mouseup", this.__onMouseClickOption.bind(this) );
};
TargetSuggestionView.prototype.__tokenRenderAlways = function( elem ) {
	var optionIndex = this.model.get( "optionIndex" );
	var color = function(d,i) {
		if ( optionIndex === i )
			return this.ACTIVE_COLOR;
		else
			return this.MT_COLOR;
	}.bind(this);
	elem.text( function(d) { return d } )
		.style( "color", color );
};

TargetSuggestionView.prototype.__onMouseOver = function() {
	var segmentId = this.model.get( "segmentId" );
	this.model.trigger( "mouseover", segmentId );
};
TargetSuggestionView.prototype.__onMouseOut = function() {
	var segmentId = this.model.get( "segmentId" );
	this.model.trigger( "mouseout", segmentId );
};
TargetSuggestionView.prototype.__onMouseClick = function() {
	var segmentId = this.model.get( "segmentId" );
	this.model.trigger( "click", segmentId );
};
TargetSuggestionView.prototype.__onMouseOverOption = function( _, optionIndex ) {
	var segmentId = this.model.get( "segmentId" );
	this.model.set( "optionIndex", optionIndex );
	this.model.trigger( "mouseover", segmentId );
	this.model.trigger( "mouseover:option", segmentId, optionIndex );
};
TargetSuggestionView.prototype.__onMouseOutOption = function( _, optionIndex ) {
	var segmentId = this.model.get( "segmentId" );
	this.model.set( "optionIndex", null );
	this.model.trigger( "mouseout", segmentId );
	this.model.trigger( "mouseout:option", segmentId, optionIndex );
};
TargetSuggestionView.prototype.__onMouseClickOption = function( _, optionIndex ) {
	var segmentId = this.model.get( "segmentId" )
	this.model.trigger( "click", segmentId );
	this.model.trigger( "click:option", segmentId, optionIndex );
};
