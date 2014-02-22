var TargetOverlayView = Backbone.View.extend({
	el : ".TargetOverlayView"
});

TargetOverlayView.prototype.initialize = function() {
	this.views = {};
	this.views.container = d3.select( this.el ).style( "pointer-events", "none" )
		.style( "-moz-user-select", "none" )
		.style( "-webkit-user-select", "none" )
		.style( "-ms-user-select", "none" )
		.call( this.__containerRenderOnce.bind(this) );
	this.views.prefixContent = this.views.container.append( "span" ).attr( "class", "PrefixContent" ).call( this.__userContentStyles.bind(this) );
	this.views.editingContent = this.views.container.append( "span" ).attr( "class", "EditingContent" ).call( this.__userContentStyles.bind(this) );
	this.views.mtContent = this.views.container.append( "span" ).attr( "class", "MtContent" ).call( this.__mtContentStyles.bind(this) );

	this.render = _.debounce( this.__render, 10 );
	this.resize = _.debounce( this.__resize, 10 );
	this.listenTo( this.model, "change:userText change:prefixTokens change:hasFocus change:bestTranslation change:enableBestTranslation", this.render.bind(this) );
	this.listenTo( this.model, "change:boxOverlayWidth change:boxOverlayHeight", this.resize.bind(this) );
};

TargetOverlayView.prototype.__render = function() {
	var prefixContent = this.model.get( "overlayPrefix" ) + this.model.get( "overlaySep" );
	var editingContent = this.model.get( "overlayEditing" );
	var mtContent = this.model.get( "bestTranslation" ).join( " " );
	this.views.prefixContent.text( prefixContent );
	this.views.editingContent.text( editingContent );
	this.views.mtContent.text( mtContent );
	
	var caretXCoord = this.views.editingContent[0][0].offsetLeft + this.views.editingContent[0][0].offsetWidth;
	var caretYCoord = this.views.editingContent[0][0].offsetTop;
	var editXCoord = this.views.editingContent[0][0].offsetLeft;
	var editYCoord = this.views.editingContent[0][0].offsetTop;
	this.model.set({
		"caretXCoord" : caretXCoord,
		"caretYCoord" : caretYCoord,
		"editXCoord" : editXCoord,
		"editYCoord" : editYCoord
	});

	this.views.container.call( this.__containerRenderAlways.bind(this) );
};

TargetOverlayView.prototype.__resize = function() {
	var width = this.model.get( "boxOverlayWidth" );
	var height = this.model.get( "boxOverlayHeight" );
	this.views.container
		.style( "width", width + "px" )
		.style( "height", height + "px" );
};

TargetOverlayView.prototype.__containerRenderOnce = function( elem ) {
	elem.style( "display", "inline-block" )
		.style( "background", "none" )
		.style( "padding", "11px 60px 0px 15px" )
		.style( "opacity", 1 )
		.style( "line-height", "20px" )
};
TargetOverlayView.prototype.__containerRenderAlways = function( elem ) {
	var enableBestTranslation = this.model.get( "enableBestTranslation" );
	elem.transition()
		.style( "opacity", enableBestTranslation ? 1 : 0 )
};


TargetOverlayView.prototype.__userContentStyles = function( elem ) {
	elem.style( "visibility", "hidden" )
		.style( "vertical-align", "top" )
		.classed( "UserText", true )
		.style( "white-space", "pre-wrap" );
};

TargetOverlayView.prototype.__mtContentStyles = function( elem ) {
	elem.style( "visibility", "visible" )
		.style( "vertical-align", "top" )
		.classed( "TargetLang", true )
		.style( "white-space", "pre-wrap" );
};
