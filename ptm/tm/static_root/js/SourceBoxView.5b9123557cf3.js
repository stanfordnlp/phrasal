var SourceBoxView = Backbone.View.extend({
	el : ".SourceBoxView"
});

SourceBoxView.prototype.REGULAR_COLOR = "#333";
SourceBoxView.prototype.UNMATCHED_COLOR = "#333";
SourceBoxView.prototype.MATCHED_COLOR = "#4292C6";
SourceBoxView.prototype.HOVER_COLOR = "#ff7f0e";

SourceBoxView.prototype.ANIMATION_DURATION = 120;

SourceBoxView.prototype.initialize = function() {
	this.views = {};
	this.views.container = d3.select( this.el ).call( this.__containerRenderOnce.bind(this) );
	this.listenTo( this.model, "change", this.render );
};

SourceBoxView.prototype.render = function() {
	var tokens = this.model.get( "tokens" );
	var elems = this.views.container.selectAll( "span.Token" ).data( tokens );
	var subElems = elems.enter().append( "span" ).attr( "class", "Token" ).call( this.__tokenRenderOnce.bind(this) );
	subElems.append( "span" ).attr( "class", "TokenTerm" ).call( this.__tokenTermRenderOnce.bind(this) );
	subElems.append( "span" ).attr( "class", "TokenSep" ).call( this.__tokenSepRenderOnce.bind(this) );
	elems.exit().remove();

	this.views.container.call( this.__containerRenderAlways.bind(this) );
	this.views.container.selectAll( "span.Token" ).call( this.__tokenRenderAlways.bind(this) )
	this.views.container.selectAll( "span.Token" ).select( "span.TokenTerm" ).call( this.__tokenTermRenderAlways.bind(this) );
	this.views.container.selectAll( "span.Token" ).select( "span.TokenSep" ).call( this.__tokenSepRenderAlways.bind(this) );
};

SourceBoxView.prototype.__containerRenderOnce = function( elem ) {
	elem.style( "padding", "10px 60px 10px 15px" )  // "5px 60px 2.5px 15px"
		.classed( "SourceLang", true )
		.style( "pointer-events", "auto" )
		.style( "cursor", "default" )
		.on( "mouseover", this.__mouseOver.bind(this) )
		.on( "mouseout", this.__mouseOut.bind(this) )
		.on( "click", this.__mouseClick.bind(this) );
};
SourceBoxView.prototype.__containerRenderAlways = function( elem ) {
	var height = elem[0][0].offsetHeight;
	var width = elem[0][0].offsetWidth;
	this.model.set({
		"boxHeight" : height,
		"boxWidth" : width
	});
};

SourceBoxView.prototype.__tokenRenderOnce = function( elem ) {
	elem.style( "pointer-events", "auto" )
		.style( "cursor", "default" )
		.on( "click", this.__mouseClick.bind(this) );
};
SourceBoxView.prototype.__tokenRenderAlways = function() {};

SourceBoxView.prototype.__tokenTermRenderOnce = function( elem ) {
	elem.style( "display", "inline-block" )
		.style( "white-space", "pre-wrap" )
		.style( "vertical-align", "top" )
		.text( function(d) { return d } )
		.style( "pointer-events", "auto" )
		.style( "cursor", "default" )
		.on( "mouseover", this.__mouseOverToken.bind(this) )
		.on( "mouseout", this.__mouseOutToken.bind(this) )
		.on( "click", this.__mouseClickToken.bind(this) )
};
SourceBoxView.prototype.__tokenTermRenderAlways = function( elem ) {
	var hasFocus = this.model.get( "hasFocus" );
	var color = function( _, tokenIndex ) {
		var isHovered = ( tokenIndex === this.model.get( "hoverTokenIndex" ) );
		var isMatched = ( this.model.get( "matchedTokenIndexes" ).hasOwnProperty( tokenIndex ) );
		if ( hasFocus ) {
			if ( isHovered ) 
				return this.HOVER_COLOR;
			else if ( isMatched ) 
			 	return this.MATCHED_COLOR;
			else
				return this.UNMATCHED_COLOR;
		}
		else {
			return this.REGULAR_COLOR;
		}
	}.bind(this);
	elem.style( "color", color )
};

SourceBoxView.prototype.__tokenSepRenderOnce = function( elem ) {
	elem.style( "display", "inline-block" )
		.style( "white-space", "pre-wrap" )
		.style( "vertical-align", "top" )
		.text( " " )
};
SourceBoxView.prototype.__tokenSepRenderAlways = function() {};

SourceBoxView.prototype.__mouseOver = function() {
	if ( this.model.get( "enableHover" ) === true ) {
		var segmentId = this.model.get( "segmentId" );
		this.model.trigger( "mouseover", segmentId );
	}
};
SourceBoxView.prototype.__mouseOut = function() {
	var segmentId = this.model.get( "segmentId" );
	this.model.trigger( "mouseout", segmentId );
};
SourceBoxView.prototype.__mouseClick = function() {
	var segmentId = this.model.get( "segmentId" );
	this.model.trigger( "click", segmentId );
};
SourceBoxView.prototype.__mouseOverToken = function( _, tokenIndex ) {
	if ( this.model.get( "enableHover" ) === true ) {
		var containerLeft = this.views.container[0][0].offsetLeft;
		var containerTop = this.views.container[0][0].offsetTop;
		var elemLeft = d3.event.srcElement.offsetLeft;
		var elemTop = d3.event.srcElement.offsetTop;
		var xCoord = elemLeft - containerLeft;
		var yCoord = elemTop - containerTop;
		var segmentId = this.model.get( "segmentId" );
		this.model.set({
			"hoverXCoord" : xCoord,
			"hoverYCoord" : yCoord,
			"hoverTokenIndex" : tokenIndex
		});
		this.model.trigger( "mouseover:*", segmentId );
		this.model.trigger( "mouseover:token", segmentId, tokenIndex );
	}
};
SourceBoxView.prototype.__mouseOutToken = function( _, tokenIndex ) {
	var containerLeft = this.views.container[0][0].offsetLeft;
	var containerTop = this.views.container[0][0].offsetTop;
	var elemLeft = d3.event.srcElement.offsetLeft;
	var elemTop = d3.event.srcElement.offsetTop;
	var xCoord = elemLeft - containerLeft;
	var yCoord = elemTop - containerTop;
	var segmentId = this.model.get( "segmentId" );
	this.model.set({
		"hoverXCoord" : xCoord,
		"hoverYCoord" : yCoord,
		"hoverTokenIndex" : null
	});
	this.model.trigger( "mouseout:*", segmentId );
	this.model.trigger( "mouseout:token", segmentId, tokenIndex );
};
SourceBoxView.prototype.__mouseClickToken = function( _, tokenIndex ) {
	var segmentId = this.model.get( "segmentId" );
	this.model.trigger( "click:*", segmentId );
	this.model.trigger( "click:token", segmentId, tokenIndex );
};
