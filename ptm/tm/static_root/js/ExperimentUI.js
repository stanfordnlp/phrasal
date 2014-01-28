var ExperimentUI = Backbone.Model.extend({
	"defaults" : {
		"timer" : 180,
		"terminate" : false
	}
});

ExperimentUI.prototype.MAX_TIME = 180;

ExperimentUI.prototype.initialize = function() {
	this.set( "timer", this.MAX_TIME );
	this.vis = new ExperimentVis({ model: this });
	this.tick = _.debounce( this.__tick.bind(this), 1000, false );
	this.tick();
};

ExperimentUI.prototype.reset = function() {
	this.set( "timer", this.MAX_TIME );
	this.tick();
};

ExperimentUI.prototype.__tick = function() {
	this.set( "timer", this.get("timer") - 1 );
	if ( this.get("timer") <= 0 )
		this.set( "terminate", true );
	else
		this.tick();
};

var ExperimentVis = Backbone.View.extend({
	"el" : ".ExperimentUI"
});

ExperimentVis.prototype.MAX_OPACITY = 1.0;
ExperimentVis.prototype.MIN_OPACITY = 0.925;

ExperimentVis.prototype.initialize = function() {
	d3.select(this.el)
		.style( "vertical-align", "middle" )
		.style( "background", "#999" )
		.style( "border-top", "1px solid #333" )
		.style( "box-shadow", "0 -2px 5px #666" )
		.style( "opacity", this.MIN_OPACITY )
		.select( "span.ExperimentTimeRemaining" )
			.text( "3:00" );

	this.model.on( "change:timer", this.updateText, this );
	this.model.on( "change:terminate", this.terminateText, this );
	setInterval( this.updatePosition.bind(this), 25 );
};

ExperimentVis.prototype.updateText = function() {
	var timer = this.model.get( "timer" );
	var opacityMapping = d3.scale.linear().domain( [ 45, 30 ] ).range( [ this.MIN_OPACITY, this.MAX_OPACITY ] );
	var opacity = Math.min( this.MAX_OPACITY, Math.max( this.MIN_OPACITY, opacityMapping(timer) ) );
	var rMapping = d3.scale.linear().domain( [ 30, 20 ] ).range( [ 0, 0.8 ] );
	var gMapping = d3.scale.linear().domain( [ 30, 20 ] ).range( [ 0, 0.4 ] );
	var bMapping = d3.scale.linear().domain( [ 30, 20 ] ).range( [ 0, 0.4 ] );
	var r = Math.min( 0.8, Math.max( 0, rMapping(timer) ) ) * 255;
	var g = Math.min( 0.4, Math.max( 0, gMapping(timer) ) ) * 255;
	var b = Math.min( 0.4, Math.max( 0, bMapping(timer) ) ) * 255;

	timer = Math.max( 0, timer );
	var minutes = Math.floor( timer / 60 );
	timer -= minutes * 60;

	timer = Math.max( 0, timer );
	var seconds10 = Math.floor( timer / 10 );
	timer -= seconds10 * 10;

	timer = Math.max( 0, timer );
	var seconds1 = Math.round( timer );
	
	d3.select(this.el)
//		.style( "opacity", opacity )
//		.style( "color", "rgb("+r+","+g+","+b+")" )
		.select( "span.ExperimentTimeRemaining" )
			.text( minutes + ":" + seconds10 + seconds1 );
};

ExperimentVis.prototype.terminateText = function() {
	d3.select(this.el)
		.text( "Experiment ended due to inactivity." );
};

ExperimentVis.prototype.updatePosition = function() {
	var height = window.innerHeight;
	d3.select(this.el)
		.style( "top", (height-42) + "px" );
};
