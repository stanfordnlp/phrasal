var TargetBoxView = Backbone.View.extend({
	el : ".TargetBoxView"
});

TargetBoxView.prototype.initialize = function( options ) {
	var segmentId = options.segmentId;
	this.views = {};
	this.views.container = d3.select( this.el ).style( "pointer-events", "none" ).call( this.__containerRenderOnce.bind(this) );
	this.views.canvas = this.views.container.append( "div" ).attr( "class", "Canvas" ).style( "position", "absolute" );
	this.views.overlay = this.views.canvas.append( "div" ).attr( "class", "TargetOverlayView TargetOverlayView" + segmentId );
	this.views.textarea = this.views.container.append( "div" ).attr( "class", "TargetTextareaView TargetTextareaView" + segmentId );

	this.listenTo( this.model, "change:userText change:hasFocus", this.render.bind(this) );
};

TargetBoxView.prototype.render = function() {
	this.views.container.call( this.__containerRenderAlways.bind(this) );
};

TargetBoxView.prototype.__containerRenderOnce = function( elem ) {
	elem.style( "display", "inline-block" );
};

TargetBoxView.prototype.__containerRenderAlways = function() {};
