var PTM = Backbone.Model.extend({
	"url" : function() {
		return this.get( "url" );
	},
	"defaults" : {
		"isLogging" : true,
		"postEditMode" : false,
		"maxIdleTime" : 180,
		"readOnlyMode" : false,
		"docURL" : "",
		"sourceLang" : "de",
		"targetLang" : "en",
		"activities" : []
	}
});

PTM.prototype.reset = function() {
	d3.select( "#PTM" ).selectAll( "*" ).remove();
	
	this.set({
		"url" : null,
		"docId" : null,
		"segmentIds" : [],
		"segments" : {},
		"focusSegment" : null,
		"hasMasterFocus" : true,
	});

	// Define or create a stub for all models and views.
	/** @param {TranslateServer} **/
	var sourceLang = this.get( "sourceLang" );
	var targetLang = this.get( "targetLang" );
	this.server = new TranslateServer( sourceLang, targetLang );
	
	/** @param {DocumentView} **/
	this.optionPanel = null;
	this.experimentUI = null;
	this.documentView = null;
	this.sourceBoxes = {};
	this.sourceSuggestions = {};
	this.targetBoxes = {};
	this.targetSuggestions = {};
	
	// Define cache for a cache for storing rqReq and tReq results
	this.cache = {};
	/** @param {{string:Object}} Cache for storing rqReq results, indexed by word. **/
	this.cache.wordQueries = { ":" : { "rules" : [] } };
	/** @param {{segmentId:{string:Object}}} Cache for storing tReq results, indexed by segmentId and then by prefix. **/
	this.cache.translations = {};

	// Define debounced methods
	this.updateSourceSuggestions = _.debounce( this.__updateSourceSuggestions, 10 );
	this.updateTargetSuggestions = this.__updateTargetSuggestions; //_.debounce( this.__updateTargetSuggestions, 10 );
	this.textareaFocusOrBlur = _.debounce( this.__textareaFocusOrBlur, 25 );
};

PTM.prototype.initialize = function() {
	this.listenTo( this, "change", this.makeActivityLogger( "ptm", "", this ) );
	var referenceTime = new Date().getTime();
	this.set( "referenceTime", referenceTime );

  // Uncomment this to enable debug output
  //console.log = function() {}
};

PTM.prototype.load = function( url ) {
	if ( url === undefined ) {
		url = this.get( "docURL" );
	}
	this.reset();
	this.set( "url", url );
	this.fetch({
		success : this.loaded.bind(this),
		error: this.loadError.bind(this)
	});
};

PTM.prototype.loadError = function( model, response, options ) {
	console.log( "[PTM.load] fail", this, model, response, options );
};

/**
 * Post-process segments received from the MT server.
 * @private
 **/
PTM.prototype.loaded = function( model, response, options ) {
	console.log( "[PTM.load] success", this, model, response, options );
	var segments = this.get( "segments" );
	var segmentIds = _.keys( segments ).sort( function(a,b) { return parseInt(a) - parseInt(b) } );
	this.set( "segmentIds", segmentIds );
	this.setup();
};

PTM.prototype.__timestamp = function() {
	return ( new Date().getTime() - this.get("referenceTime") ) / 1000;
};

/**
 * Return user-entered translations.
 * @return {string[]} A list of user-entered translations.
 **/
PTM.prototype.getUserResponses = function() {
	var responses = {};
	for ( var key in this.targetBoxes ) {
		var targetBox = this.targetBoxes[key];
		responses[key] = targetBox.get("userText");
	}
	return responses;
};

/**
 * Return user interaction log.
 * @return {Object[]} A list of user interaction events.
 **/
PTM.prototype.getInteractionLog = function() {
	var responses = this.get( "activities" );
	return responses;
};

/**
 * Replay a translation session.
 * @param {Object[]} [activities] A list of UI events.
 * @param {number} [delay] Delay in seconds before playback begins.
 **/
PTM.prototype.playback = function( activities, delay ) {
	var startReplay = function() {
		this.set( "isLogging", false );
	        this.experimentUI.off( "change:terminate", this.terminateExperiment, this );
	        this.experimentUI.set({"timer":0});
		for ( var key in this.sourceBoxes )
			this.sourceBoxes[key].reset();
		for ( var key in this.sourceBoxSuggestions )
			this.sourceBoxSuggestions[key].reset();
		for ( var key in this.targetBoxes )
		    this.targetBoxes[key].set({"userText":""});
		    //this.targetBoxes[key].reset();
		for ( var key in this.targetSuggestions )
			this.targetSuggestions[key].reset();
	}.bind(this);
	var endReplay = function() {
		this.set( "isLogging", true );
	}.bind(this);
	var replay = function( element, subElement, keyValues ) {
		return function() {
			if ( element === "ptm" )
				this.set( keyValues );
			else if ( subElement === "" )
				this[ element ].set( keyValues );
			else
				this[ element ][ subElement ].set( keyValues );
		}.bind(this);
	}.bind(this);
	
	if ( activities === undefined )
		activities = this.get( "activities" );
	if ( delay === undefined )
		delay = 1;
	
	setTimeout( startReplay, delay * 1000 );
	var time = Math.max.apply( Math, 
		activities.map( function( activity ) {
			var time = activity.time;
			var element = activity.element;
			var subElement = activity.subElement;
			var keyValues = activity.keyValues;
			setTimeout( replay( element, subElement, keyValues ), (time+delay) * 1000 );
			return time;
		})
	);
	setTimeout( endReplay, (time+delay) * 1000 );
};

PTM.prototype.makeActivityLogger = function( elemId, subElemId, elem ) {
	return function() {
		if ( this.get("isLogging") === true ) {
			this.logActivities( elemId, subElemId, elem );
		}
	}.bind(this);
};
PTM.prototype.logActivities = function( elemId, subElemId, elem ) {
	var activity = {
		"time" : this.__timestamp(),
		"element" : elemId,
		"subElement" : subElemId,
		"keyValues" : {}
	};
	for ( var attribute in elem.changed ) {
		activity.keyValues[ attribute ] = elem.get( attribute );
	}
	this.get("activities").push(activity);
};

PTM.prototype.textareaOnFocus = function( segmentId ) {
	this.textareaFocusOrBlur( segmentId );
};
PTM.prototype.textareaOnBlur = function( segmentId ) {
	this.textareaFocusOrBlur( null );
}
PTM.prototype.__textareaFocusOrBlur = function( segmentId ) {
	hasMasterFocus = ( segmentId !== null );
	this.set( "hasMasterFocus", hasMasterFocus );
	
	var segmentIds = this.get( "segmentIds" );
//	segmentIds.forEach( function(segmentId) {
//		this.sourceSuggestions[segmentId].set( "hasMasterFocus", hasMasterFocus );
//		this.targetSuggestions[segmentId].set( "hasMasterFocus", hasMasterFocus );
//	}.bind(this) );
};

/**
 * Controller for the Predictive Translate Memory
 **/
PTM.prototype.setup = function() {
	var postEditMode = this.get( "postEditMode" );
	var segments = this.get( "segments" );
	var segmentIds = this.get( "segmentIds" );
	
	// Create a visualization for the entire document
	this.documentView = new DocumentView({ "model" : this });
	
	// Create an experimentUI (count-down clock, etc)
	this.experimentUI = new ExperimentUI({ "maxIdleTime" : this.get("maxIdleTime") });
	this.experimentUI.on( "change:tick", this.makeActivityLogger( "experimentUI", "", this.experimentUI ), this );
	this.experimentUI.on( "change:terminate", this.terminateExperiment, this );
	
	// Create source boxes and typing UIs
	segmentIds.forEach( function(segmentId) {
		
		// Generate HTML DOM elements
		this.documentView.addSegment( segmentId );

		// Create a SourceBox (matching pair of state and view) for each segment
		var sourceBox = new SourceBoxState({
			"el" : ".SourceBoxView" + segmentId
		});
		if ( postEditMode ) {
			sourceBox.set({
				"enableHover" : false
			});
		}
		sourceBox.on( "change", this.makeActivityLogger( "sourceBoxes", segmentId, sourceBox ), this );
		sourceBox.set({
			"segmentId" : segmentId,
			"tokens" : segments[ segmentId ].tokens,
			"layoutSpec" : segments[ segmentId ].layoutSpec
		});
		this.sourceBoxes[segmentId] = sourceBox;
		
		var sourceSuggestion = new SourceSuggestionState({ "el" : ".SourceSuggestionView" + segmentId });
		sourceSuggestion.on( "change", this.makeActivityLogger( "sourceSuggestions", segmentId, sourceSuggestion ), this );
		sourceSuggestion.set({
			"segmentId" : segmentId
		});
		this.sourceSuggestions[segmentId] = sourceSuggestion;
		
		// Create state and view objects for the typing UI
		var targetBox = new TargetBoxState({ "segmentId" : segmentId });
		if ( postEditMode ) {
			targetBox.set({
				"enableSuggestions" : false,
				"enableBestTranslation" : false,
				"postEditMode" : true
			});
		}
		targetBox.on( "change", this.makeActivityLogger( "targetBoxes", segmentId, targetBox ), this );
		targetBox.set({
			"segmentId" : segmentId,
			"chunkVector" : segments[segmentId].chunkVector,
			"userText" : ""
		});
		this.targetBoxes[segmentId] = targetBox;
		
		var targetSuggestion = new TargetSuggestionState({ "el" : ".TargetSuggestionView" + segmentId });
		targetSuggestion.on( "change", this.makeActivityLogger( "targetSuggestions", segmentId, targetSuggestion ), this );
		targetSuggestion.set({
			"segmentId" : segmentId
		});
		this.targetSuggestions[segmentId] = targetSuggestion;
		
		this.listenTo( sourceBox, "mouseover", function(){} );
		this.listenTo( sourceBox, "mouseout", function(){} );
		this.listenTo( sourceBox, "click", this.focusOnSegment );
		this.listenTo( sourceBox, "mouseover:token", this.showSourceSuggestionsFromText );
		this.listenTo( sourceBox, "mouseout:token", this.hideSourceSuggestions );
		this.listenTo( sourceBox, "click:token", function(){} );
		this.listenTo( sourceBox, "updateBoxDims", this.updateTargetSuggestions );
		this.listenTo( sourceBox, "updateBoxDims", this.resizeDocument );
		this.listenTo( sourceBox, "click", this.experimentUI.reset.bind(this.experimentUI) );
		
		this.listenTo( sourceSuggestion, "mouseover", this.showSourceSuggestionsFromFloatingBox );
		this.listenTo( sourceSuggestion, "mouseout", this.hideSourceSuggestions );
		this.listenTo( sourceSuggestion, "click", function(){} );
		this.listenTo( sourceSuggestion, "mouseover:option", function(){} );
		this.listenTo( sourceSuggestion, "mouseout:option", function(){} );
		this.listenTo( sourceSuggestion, "click:option", this.clickToInsertSourceSuggestion );
		this.listenTo( sourceSuggestion, "click", this.experimentUI.reset.bind(this.experimentUI) );
		
		this.listenTo( targetBox, "keypress:enter", this.insertSelectedTargetSuggestion );
		this.listenTo( targetBox, "keypress:enter+meta", this.focusOnNextSegment );
		this.listenTo( targetBox, "keypress:enter+shift", this.focusOnPreviousSegment );
		this.listenTo( targetBox, "keypress:tab", this.insertSelectedTargetSuggestion_OR_insertFirstSuggestion );
//		this.listenTo( targetBox, "keypress:tab", this.insertFirstSuggestion );
		this.listenTo( targetBox, "keypress:up", this.previousTargetSuggestion );
		this.listenTo( targetBox, "keypress:down", this.nextTargetSuggestion );
		this.listenTo( targetBox, "keypress:esc", this.noTargetSuggestion_OR_cycleAssists );
		this.listenTo( targetBox, "updateMatchingTokens", this.updateMatchingTokens );
		this.listenTo( targetBox, "updateSuggestions", this.updateTargetSuggestions );
		this.listenTo( targetBox, "updateEditCoords", this.updateTargetSuggestions );
		this.listenTo( targetBox, "updateFocus", this.focusOnSegment );
		this.listenTo( targetBox, "updateTranslations", this.loadTranslations );
		this.listenTo( targetBox, "updateBoxDims", this.resizeDocument );
		this.listenTo( targetBox, "keypress", this.experimentUI.reset.bind(this.experimentUI) );
		this.listenTo( targetBox, "textareaOnFocus", this.textareaOnFocus );
		this.listenTo( targetBox, "textareaOnBlur", this.textareaOnBlur );
		
		this.listenTo( targetSuggestion, "mouseover", function(){} );
		this.listenTo( targetSuggestion, "mouseout", function(){} );
		this.listenTo( targetSuggestion, "click", function(){} );
		this.listenTo( targetSuggestion, "mouseover:option", function(){} );
		this.listenTo( targetSuggestion, "mouseout:option", function(){} );
		this.listenTo( targetSuggestion, "click:option", this.clickToInsertTargetSuggestion );
		this.listenTo( targetSuggestion, "click", this.experimentUI.reset.bind(this.experimentUI) );
		
		this.cache.translations[ segmentId ] = {};
		this.loadTranslations( segmentId, "" );
	}.bind(this) );
	this.documentView.addSegment( null );

	// Create an options panel
	this.optionPanel = new OptionPanelState();
  // TODO(spenceg): Jeff said to disable this for the experiment
  // Nice feature for users, but a significant confound.
//	if ( postEditMode ) {
		this.optionPanel.set({
			"enableHover" : false,
			"enableSuggestions" : false,
			"enableMT" : false,
			"visible" : false
		});
//	}
	this.optionPanel.on( "change", this.makeActivityLogger( "optionPanel", "", this.optionPanel ), this );
	this.listenTo( this.optionPanel, "change", this.setAssists );
	
	// Focus on the first segment
	this.focusOnSegment( segmentIds[0] );
};

PTM.prototype.terminateExperiment = function() {
	d3.selectAll("*")
		.style( "pointer-events", "none" )
		.style( "user-select", "none" )
		.style( "-moz-user-select", "none" )
		.style( "-webkit-user-select", "none" )
		.style( "-ms-user-select", "none" );
	alert( "You exceeded the maximum idle time! Your partial translation has been submitted. Click OK to continue to the next sentence." );
	$( "input[name=valid]" ).val( "False" );
	$( "input[name=form-tgt-submit]" ).trigger( "click" );
};

PTM.prototype.resizeDocument = function( segmentId ) {
	this.documentView.resize();
};

PTM.prototype.setAssists = function() {
	var enableAll = this.optionPanel.get("visible");
	var enableMT = this.optionPanel.get("enableMT");
	var enableBestTranslation = enableAll && enableMT;
	var enableSuggestions = enableAll && enableMT && this.optionPanel.get("enableSuggestions");
	var enableHover = enableAll && enableMT && this.optionPanel.get("enableHover");
	var segmentIds = this.get( "segmentIds" );
	for ( var i = 0; i < segmentIds.length; i++ ) {
		var id = segmentIds[i];
		this.targetBoxes[id].set({
			"enableSuggestions" : enableSuggestions,
			"enableBestTranslation" : enableBestTranslation
		});
		this.sourceBoxes[id].set({
			"enableHover" : enableHover
		});
	}
};

PTM.prototype.cycleAssists = function( segmentId ) {
	var enableAll = this.optionPanel.get("visible");
	var enableSuggestions = this.targetBoxes[segmentId].get("enableSuggestions");
	if ( enableAll ) {
		if ( enableSuggestions ) {
			enableSuggestions = false;
			enableBestTranslation = true;
		}
		else {
			enableSuggestions = true;
			enableBestTranslation = true;
		}
	}
	else {
		enableSuggestions = false;
		enableBestTranslation = false;
	}
	var segmentIds = this.get( "segmentIds" );
	for ( var i = 0; i < segmentIds.length; i++ ) {
		var id = segmentIds[i];
		this.targetBoxes[id].set({
			"enableSuggestions" : enableSuggestions,
			"enableBestTranslation" : enableBestTranslation
		});
		this.sourceBoxes[id].set({
			"enableHover" : enableSuggestions
		});
	}
	this.optionPanel.set({
		"enableMT" : enableBestTranslation,
		"enableHover" : enableSuggestions,
		"enableSuggestions" : enableSuggestions
	});
};

PTM.prototype.noTargetSuggestion_OR_cycleAssists = function( segmentId ) {
	var optionIndex = this.targetSuggestions[segmentId].get("optionIndex");
	if ( optionIndex === null )
		this.cycleAssists( segmentId )
	else
		this.noTargetSuggestion( segmentId );
};

PTM.prototype.showSourceSuggestionsFromText = function( segmentId ) {
	var tokenIndex = this.sourceBoxes[segmentId].get("hoverTokenIndex");
	this.updateSourceSuggestions( segmentId, tokenIndex );
};
PTM.prototype.showSourceSuggestionsFromFloatingBox = function( segmentId ) {
	var tokenIndex = this.sourceSuggestions[segmentId].get("tokenIndex");
	this.updateSourceSuggestions( segmentId, tokenIndex );
};
PTM.prototype.hideSourceSuggestions = function( segmentId ) {
	this.updateSourceSuggestions( segmentId, null );
};

PTM.prototype.__updateSourceSuggestions = function( segmentId, tokenIndex ) {
	if ( tokenIndex !== this.sourceSuggestions[segmentId].get("tokenIndex") ) {
		var xCoord = this.sourceBoxes[segmentId].get("hoverXCoord");
		var yCoord = this.sourceBoxes[segmentId].get("hoverYCoord");
		var segments = this.get( "segments" );
		var segment = segments[segmentId];
		var source = ( tokenIndex === null ) ? "" : segment.tokens[ tokenIndex ];
		var leftContext = ( source === "" || tokenIndex === 0 ) ? "" : segment.tokens[ tokenIndex-1 ];
		this.sourceBoxes[segmentId].set({
			"hoverTokenIndex" : tokenIndex,
			"isEmptySuggestion" : false,
		});
		this.sourceSuggestions[segmentId].set({
			"source" : source,
			"tokenIndex" : tokenIndex,
			"leftContext" : leftContext,
			"targets" : [],	  // To be filled in asynchronously by loadWordQueries.
			"scores" : [],	  // To be filled in asynchronously by loadWordQueries.
			"optionIndex" : null,
			"xCoord" : xCoord,
			"yCoord" : yCoord
		});
		if ( tokenIndex !== null ) {
			this.loadWordQueries( segmentId, source , leftContext );
		}
	}
};

PTM.prototype.__updateTargetSuggestions = function( segmentId ) {
	var candidates = this.targetBoxes[segmentId].get("suggestions");
	var yOffset = this.sourceBoxes[segmentId].get("boxHeight");
	var xCoord = this.targetBoxes[segmentId].get("editXCoord");
	var yCoord = this.targetBoxes[segmentId].get("editYCoord");
  var isInitial = this.targetBoxes[segmentId].get("userText").length === 0;
	this.targetSuggestions[segmentId].set({
		"candidates" : candidates,
		"optionIndex" : null,
    "isInitial" : isInitial,
		"xCoord" : xCoord,
		"yCoord" : yCoord + yOffset
	});
};

PTM.prototype.updateMatchingTokens = function( segmentId, matchingTokens ) {
	this.sourceBoxes[segmentId].set({
		"matchedTokenIndexes" : matchingTokens,
	});
};

PTM.prototype.clickToInsertSourceSuggestion = function( segmentId, optionIndex ) {
	var options = this.sourceSuggestions[segmentId].get( "targets" );
	if ( options.length > 0 ) {
		var text = options[ optionIndex ];
		this.targetBoxes[segmentId].replaceEditingToken( text );
		this.targetBoxes[segmentId].focus();
	}
};
PTM.prototype.clickToInsertTargetSuggestion = function( segmentId, optionIndex ) {
	var options = this.targetSuggestions[segmentId].get( "candidates" );
	if ( options.length > 0 ) {
		var text = options[ optionIndex ];
		this.targetBoxes[segmentId].replaceEditingToken( text );
		this.targetBoxes[segmentId].focus();
	}
};

PTM.prototype.insertFirstSuggestion = function( segmentId ) {
	var firstSuggestion = this.targetBoxes[segmentId].get( "firstSuggestion" );
	if ( firstSuggestion.length > 0 ) {
		this.targetBoxes[segmentId].replaceEditingToken( firstSuggestion );
		this.targetBoxes[segmentId].focus();
	}
};
PTM.prototype.insertSelectedTargetSuggestion = function( segmentId ) {
	var optionIndex = this.targetSuggestions[segmentId].get( "optionIndex" );
	if ( optionIndex !== null ) {
		var suggestions = this.targetBoxes[segmentId].get( "suggestions" );
		var text = ( optionIndex >= suggestions.length ) ? "" : suggestions[ optionIndex ];
		this.targetBoxes[segmentId].replaceEditingToken( text );
		this.targetBoxes[segmentId].focus();
	}
};

PTM.prototype.previousTargetSuggestion = function( segmentId ) {
	this.targetSuggestions[segmentId].previousOption();
};
PTM.prototype.nextTargetSuggestion = function( segmentId ) {
	this.targetSuggestions[segmentId].nextOption();
};
PTM.prototype.noTargetSuggestion = function( segmentId ) {
	this.targetSuggestions[segmentId].noOption();
};

PTM.prototype.focusOnSegment = function( focusSegment ) {
	this.set( "focusSegment", focusSegment );
	var segmentIds = this.get( "segmentIds" );
	segmentIds.forEach( function(segmentId) {
		if ( focusSegment === segmentId ) {
			this.sourceBoxes[segmentId].set( "hasFocus", true );
			this.sourceSuggestions[segmentId].set( "hasFocus", true );
			this.targetBoxes[segmentId].focus();  // Needed to avoid an event loop (focusing on its textarea triggers another focus event)
      var userText = this.targetBoxes[segmentId].get("userText");
      this.targetSuggestions[segmentId].set( "isInitial", userText.length === 0 );
			this.targetSuggestions[segmentId].set( "hasFocus", true );
		}
		else {
			this.sourceBoxes[segmentId].set( "hasFocus", false );
			this.sourceSuggestions[segmentId].set( "hasFocus", false );
			this.targetBoxes[segmentId].set( "hasFocus", false );
			this.targetSuggestions[segmentId].set( "hasFocus", false );
		}
	}.bind(this) );
};

PTM.prototype.insertSelectedTargetSuggestion_OR_insertFirstSuggestion = function( segmentId ) {
	var i = this.targetBoxes[segmentId].get("caretIndex");
	var userText = this.targetBoxes[segmentId].get("userText");
	if (i < userText.length) {
		this.targetBoxes[segmentId].focus();
	} 
	else {
		var optionIndex = this.targetSuggestions[segmentId].get("optionIndex");
		 if ( optionIndex === null )
			this.insertFirstSuggestion( segmentId );
		 else
			this.insertSelectedTargetSuggestion( segmentId );
	}
};

PTM.prototype.insertSelectedTargetSuggestion_OR_focusOnNextSegment = function( segmentId ) {
	var optionIndex = this.targetSuggestions[segmentId].get("optionIndex");
	if ( optionIndex === null )
		this.focusOnNextSegment( segmentId );
	else
		this.insertSelectedTargetSuggestion( segmentId );
};

PTM.prototype.focusOnNextSegment = function( focusSegment ) {
	var segments = this.get( "segments" );
	var segmentIds = this.get( "segmentIds" );
	var index = ( segmentIds.indexOf( focusSegment ) + 1 ) % segmentIds.length;
	var typingNewFocus = ( index >= segmentIds.length ) ? null : segmentIds[ index ];
	this.focusOnSegment( typingNewFocus );
};

PTM.prototype.focusOnPreviousSegment = function( focusSegment ) {
	var segments = this.get( "segments" );
	var segmentIds = this.get( "segmentIds" );
	var index = ( segmentIds.indexOf( focusSegment ) + segmentIds.length - 1 ) % segmentIds.length;
	var typingNewFocus = ( index < 0 ) ? null : segmentIds[ index ];
	this.focusOnSegment( typingNewFocus );
};

PTM.prototype.loadWordQueries = function( segmentId, source, leftContext ) {
	var reContainsAlphabets = /\w/g;
	var filterEmptyResults = function( response ) {
		if ( source.match( reContainsAlphabets ) === null ) {
			response.result = [ { 'tgt' : [ source ] } ];
		}
		if ( response.hasOwnProperty( "result" ) ) {
			response.result = _.filter( response.result, function(d) { return ( d.tgt.length > 0 ) && ( d.tgt[0].match( reContainsAlphabets ) !== null ) } );
		}
		return response;
	}.bind(this);
	var getTargetTerms = function( response ) {
		if ( response.hasOwnProperty( "result" ) )
			return response.result.map( function(d) { return d.tgt.join(" "); } );
		else
			return [];
	}.bind(this);
	var getTargetScores = function( response ) {
		if ( response.hasOwnProperty( "result" ) )
			return response.result.map( function(d) { return d.score } );
		else
			return [];
	}.bind(this);
	var update = function( response ) {
		var expectedSource = this.sourceSuggestions[segmentId].get("source");
		var expectedLeftContext = this.sourceSuggestions[segmentId].get("leftContext");
		if ( source === expectedSource && leftContext === expectedLeftContext ) {
			var targets = getTargetTerms( response );
			var scores = getTargetScores( response );
			var isEmptySuggestion = ( targets.length === 0 );
			this.sourceBoxes[segmentId].set({
				"isEmptySuggestion" : isEmptySuggestion
			})
			this.sourceSuggestions[segmentId].set({
				"source" : source,
				"leftContext" : leftContext,
				"targets" : targets,
				"scores" : scores
			});
		}
	}.bind(this);
	var cacheKey = leftContext + ":" + source;
	var cacheAndUpdate = function( response, request, isSuccessful ) {
		if ( isSuccessful ) {
			response = filterEmptyResults( response );
			this.cache.wordQueries[ cacheKey ] = response;
			update( response );
		}
	}.bind(this);
	if ( this.cache.wordQueries.hasOwnProperty( cacheKey ) ) {
		update( this.cache.wordQueries[ cacheKey ] );
	} else {
    var segments = this.get( "segments" );
    var inputProperties = segments[ segmentId ].inputProperties;
		this.server.wordQuery( source, leftContext, inputProperties, cacheAndUpdate );
	}
};

PTM.prototype.recycleTranslations = function( segmentId ) {
	var targetBox = this.targetBoxes[segmentId];
	var prefix = targetBox.get( "prefix" );
	if ( prefix !== null ) {
		var editingPrefix = targetBox.get( "editingPrefix" );
		var translationList = targetBox.get( "translationList" );
		var s2tAlignments = targetBox.get( "s2tAlignments" );
		var t2sAlignments = targetBox.get( "t2sAlignments" );
	
		var recycledTranslationList = [];
		var recycleds2tAlignments = [];
		var recycledt2sAlignments = [];

		// Recycle any translation that is still valid (i.e., matches the current editingPrefix)
		var editingPrefixHash = editingPrefix.replace( /[ ]+/g, "" );
		for ( var i = 0; i < translationList.length; i++ ) {
			var translation = translationList[i];
			var translationHash = translation.join( "" );
			var isValid = ( translationHash.substr( 0, editingPrefixHash.length ) === editingPrefixHash );
			if ( isValid ) {
				recycledTranslationList.push( translationList[i] );
				recycleds2tAlignments.push( s2tAlignments[i] );
				recycledt2sAlignments.push( t2sAlignments[i] );
			}
		}
		// Retrain at least one translation, even if none is valid
		if ( recycledTranslationList.length === 0 && translationList.length > 0 ) {
			recycledTranslationList.push( translationList[0] );
			recycleds2tAlignments.push( s2tAlignments[0] );
			recycledt2sAlignments.push( t2sAlignments[0] );
		}
		targetBox.set({
			"prefix" : editingPrefix,
			"translationList" : recycledTranslationList,
			"s2tAlignments" : recycleds2tAlignments,
	  		"t2sAlignments" : recycledt2sAlignments
		});
	}
};

PTM.prototype.loadTranslations = function( segmentId, prefix ) {
	/**
	 * Convert machine translation from a string to a list of tokens.
	 * @private
	 **/
	var amendTranslationTokens = function( response ) {
		if ( response.hasOwnProperty( "result" ) ) {
			var translationList = [];
			for ( var n = 0; n < response.result.length; n++ ) {
				translationList.push( response.result[n].tgt );
			}
			response.translationList = translationList;
		}
		return response;
	}.bind(this);
	/**
	 * Build alignment grids from the raw alignments.
	 * @private
	 **/
	var amendAlignIndexes = function( response ) {
		var s2tList = [];
		var t2sList = [];
		if ( response.hasOwnProperty( "result" ) ) {
			for ( var n = 0; n < response.result.length; n++ ) {
				var alignList = response.result[n].align;
					var s2t = {};
					var t2s = {};
					for (var i = 0; i < alignList.length; ++i) {
						var st = alignList[i].split("-");
						var srcIndex = parseInt(st[0]);
						var tgtIndex = parseInt(st[1]);
						if ( s2t.hasOwnProperty( srcIndex ))
							s2t[srcIndex].push( tgtIndex );
						else
							s2t[srcIndex] = [ tgtIndex ];
						if ( t2s.hasOwnProperty( tgtIndex ))
							t2s[tgtIndex].push(srcIndex);
						else
							t2s[tgtIndex] = [srcIndex];
					}
					s2tList.push(s2t);
					t2sList.push(t2s);
			}
		}
		response.s2t = s2tList;
		response.t2s = t2sList;
		return response;
	}.bind(this);
	var update = function( response ) {
		var postEditMode = this.get( "postEditMode" );
		if ( postEditMode ) {
			this.targetBoxes[ segmentId ].set({
				"userText" : response.translationList[0].join(" ")
			});
		}
		else {
			if ( this.targetBoxes[segmentId].get("editingPrefix") === prefix ) {
				var translationList = response.translationList;
				var s2t = response.s2t;
				var t2s = response.t2s;
				this.targetBoxes[ segmentId ].set({
					"prefix" : prefix,
					"translationList" : translationList,
					"s2tAlignments" : s2t,
					"t2sAlignments" : t2s
				});
			}
		}
	}.bind(this);
	var cacheAndUpdate = function( response, request, isSuccessful ) {
		if ( isSuccessful ) {
			response = amendTranslationTokens( response );
			response = amendAlignIndexes( response );
			this.cache.translations[ segmentId ][ prefix ] = response;
			update( response );
		}
	}.bind(this);

  // Check the cache for translations
  if ( this.cache.translations[ segmentId ].hasOwnProperty( prefix ) ) {
		if ( this.cache.translations[ segmentId ][ prefix ] !== null ) {
			update( this.cache.translations[ segmentId ][ prefix ] );  // Otherwise request has already been sent
		}
	} else {
	// Otherwise, request translations from the service
		var segments = this.get( "segments" );
		var source = segments[ segmentId ].tokens.join( " " );
    var inputProperties = segments[ segmentId ].inputProperties;
		this.cache.translations[ segmentId ][ prefix ] = null;
		this.server.translate( source, prefix, inputProperties, cacheAndUpdate );  // Asynchronous request
		
		// Try to recover partial set of translations during the asynchronous request
		this.recycleTranslations( segmentId );
	}
};
