var SourceBoxState = Backbone.Model.extend();

SourceBoxState.prototype.reset = function() {
	this.set({
		/** @type {string} A string identify the source text segment. Value is set by PTM on initialization. */
		"segmentId" : null,
		
		/** @type {string[]} A list of strings representings tokens in the source text. Value is set by PTM on initialization. */
		"tokens" : [],
		
		/** @type {integer|null} Index of the token currently under mouse hover in the source box. **/
		"hoverTokenIndex" : null,
		
		/** @type {{integer:true}} Indexes of tokens that have a corresponding term in the typing UI. **/
		"matchedTokenIndexes" : {},
		
		"enableHover" : true,

		/** @type {boolean} **/
		"hasFocus" : false,
		
		"hoverXCoord" : 0,
		"hoverYCoord" : 0,
		"boxHeight" : 0,
		"boxWidth" : 0
	}, { silent : true } );
};

SourceBoxState.prototype.initialize = function( options ) {
	this.reset();
	this.view = new SourceBoxView({ "model" : this, "el" : options.el });
	this.on( "change:boxHeight change:boxWidth", this.updateBoxDims );
};

SourceBoxState.prototype.updateBoxDims = function() {
	var segmentId = this.get( "segmentId" );
	this.trigger( "updateBoxDims", segmentId );
};
