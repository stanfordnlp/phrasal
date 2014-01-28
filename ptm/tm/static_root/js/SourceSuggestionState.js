var SourceSuggestionState = Backbone.Model.extend();

SourceSuggestionState.prototype.reset = function() {
	this.set({
		"segmentId" : null,
		"tokenIndex" : null,
		"source" : "",
		"leftContext" : "",
		"targets" : [],
		"scores" : [],
		"xCoord" : 0,
		"yCoord" : 0,
		"optionIndex" : null,
		"hasFocus" : false
	}, { silent : true } );
};

SourceSuggestionState.prototype.initialize = function( options ) {
	this.reset();
	this.view = new SourceSuggestionView({ "model" : this, "el" : options.el });
};

SourceSuggestionState.prototype.nextOption = function() {
	var candidates = this.get( "candidates" );
	var optionIndex = this.get( "optionIndex" );
	if ( optionIndex === null )
		optionIndex = 0;
	else
		optionIndex ++;
	optionIndex = Math.max( optionIndex, candidates.length -1 );
	this.set( "optionIndex", optionIndex );
};

SourceSuggestionState.prototype.previousOption = function() {
	var optionIndex = this.get( "optionIndex" );
	if ( optionIndex === 0 )
		optionIndex = null;
	else
		optionIndex --;
	this.set( "optionIndex", optionIndex );
};
