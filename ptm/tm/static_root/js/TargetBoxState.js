var TargetBoxState = Backbone.Model.extend();

TargetBoxState.prototype.reset = function() {
	this.set({
		// Initialized once by PTM
		"segmentId" : null,
		"chunkVector" : [],
		"postEditMode" : false,
		
		// States based on machine translations
		"prefix" : "",        // @value {string} Prefix used to generate the translations.
		"translationList" : [], // @value {string[][]} A list of translations. For each translation: a list of tokens represented as a string.
		"s2tAlignments" : [],
    	"t2sAlignments" : [],

		// States based on user inputs
		"caretIndex" : 0,
		"hasFocus" : false,

		// State based on user inputs both within and outside of TargetBox
		"userText" : "",
		
		"enableSuggestions" : true,
		"enableBestTranslation" : true,
		
		// Derived states
		"userTokens" : [ "" ],
		"editingPrefix" : "",
		"overlayPrefix" : "",
		"overlaySep" : "",
		"overlayEditing" : "",
		"prefixTokens" : [],
		"suggestionList" : [],
		"suggestionExpList" : [],
		"bestTranslation" : [],       // @value {string[]} A list of tokens in the best translation.
		"suggestions" : [],           // @value {string[]} A list of suggestions matching the current user's input
		"firstSuggestion" : "",
		"matchingTokens" : {},
		
		// States based on graphics rendering results
		"caretXCoord" : null,
		"caretYCoord" : null,
		"editXCoord" : null,
		"editYCoord" : null,
		"canvasXCoord" : null,
		"canvasYCoord" : null,
		"boxTextareaHeight" : 0,
		"boxTextareaWidth" : 0,
		"boxOverlayHeight" : 0,
		"boxOverlayWidth" : 0,
		"boxHeight" : 0,
		"boxWidth" : 0
	}, { silent : true } );
};

TargetBoxState.prototype.WIDTH = 775;
TargetBoxState.prototype.MIN_HEIGHT = 18;
TargetBoxState.prototype.ANIMATION_DELAY = 180;
TargetBoxState.prototype.ANIMATION_DURATION = 120;
TargetBoxState.prototype.IMMEDIATELY = 5;  // milliseconds

TargetBoxState.prototype.WHITESPACE = /[ ]+/g;
TargetBoxState.prototype.WHITESPACE_SEPS = /([ ]+)/g;
TargetBoxState.prototype.MAX_PRECOMPUTED_SUGGESTIONS = 100;
TargetBoxState.prototype.MAX_VISIBLE_SUGGESTIONS = 4;

TargetBoxState.prototype.initialize = function( options ) {
        this.reset();
	var segmentId = options.segmentId;
	this.view = new TargetBoxView({ "model" : this, "el" : ".TargetBoxView" + segmentId, "segmentId" : segmentId });
	this.viewTextarea = new TargetTextareaView({ "model" : this, "el" : ".TargetTextareaView" + segmentId });
	this.viewOverlay = new TargetOverlayView({ "model" : this, "el" : ".TargetOverlayView" + segmentId });
	this.__textarea = this.view.views.textarea.select( "textarea" );
	this.__overlay = this.view.views.overlay;
	setInterval( this.workarounds.bind(this), 25 );
	
	this.updateSuggestionList = this.__updateSuggestionList; //_.debounce( this.__updateSuggestionList, this.IMMEDIATELY );
	this.updateBestTranslation = this.__updateBestTranslation; //_.debounce( this.__updateBestTranslation, this.IMMEDIATELY );
	this.updateSuggestions = this.__updateSuggestions; //_.debounce( this.__updateSuggestions, this.IMMEDIATELY );
	this.updateMatchingTokens = _.debounce( this.__updateMatchingTokens, this.IMMEDIATELY );
	this.on( "change:prefix change:translationList", this.updatePrefixTokensAndSuggestionList );
	this.on( "change:userText change:prefixTokens", this.updateUserTokens );
	this.on( "change:editingPrefix", this.updateTranslations );
	this.on( "change:userTokens change:translationList change:enableBestTranslation", this.updateBestTranslation );
	this.on( "change:userTokens change:suggestionList change:bestTranslation change:caretIndex change:enableSuggestions", this.updateSuggestions );
	this.on( "change:userTokens change:alignIndexList change:enableBestTranslation", this.updateMatchingTokens );
//	this.on( "change:caretIndex", this.triggerUpdateCaretIndex );
	this.on( "change:editXCoord change:editYCoord", this.updateEditCoords );
	this.on( "change:boxWidth change:boxHeight", this.updateBoxDims );
};

TargetBoxState.prototype.workarounds = function() {
	var postEditMode = this.get("postEditMode");
	if ( postEditMode ) {
		var textareaHeight = this.__textarea[0][0].scrollHeight - 25;
		var overlayHeight = this.__overlay[0][0].scrollHeight - 11;
		var height = Math.max( textareaHeight, overlayHeight );
		var width = this.__textarea[0][0].offsetWidth - 75;
		this.set({
			"boxTextareaHeight" : height,
			"boxTextareaWidth" : width,
			"boxOverlayHeight" : height,
			"boxOverlayWidth" : width,
			"boxHeight" : height + 30,
			"boxWidth" : width + 75
		});
	}
	else {
		var textareaHeight = this.__textarea[0][0].scrollHeight - 25;
		var overlayHeight = this.__overlay[0][0].scrollHeight - 11;
		var height = Math.max( textareaHeight, overlayHeight );
		var width = this.__textarea[0][0].offsetWidth - 75;
		var text = this.__textarea.node().value;
		this.set({
			"boxTextareaHeight" : height,
			"boxTextareaWidth" : width,
			"boxOverlayHeight" : height,
			"boxOverlayWidth" : width,
			"boxHeight" : height + 30,
			"boxWidth" : width + 75,
			"userText" : text
		});
	}
};

TargetBoxState.prototype.__identifyContinugousSuggestion = function( translation, s2t, t2s, baseTargetTokenIndex ) {

	// chunkVector is never changed after initialization.
	var chunkVector = this.get( "chunkVector" );

	// Identify all source tokens that correspond to the target token containing the caret
	var sourceTokenIndexes = t2s[ baseTargetTokenIndex ];
	if ( sourceTokenIndexes && sourceTokenIndexes.length > 0 ) {

		// Identify chunk index belonging to the left-most source token
    var leftMostSourceTokenIndex = translation.length + 1;
    var sourceChunks = {};
    for (var i = 0; i < sourceTokenIndexes.length; ++i ) {
      var sourceIndex = sourceTokenIndexes[ i ];
      var sourceChunkIndex = chunkVector[ sourceIndex ];
      sourceChunks[ sourceChunkIndex ] = true;
      if ( sourceIndex < leftMostSourceTokenIndex ) {
        leftMostSourceTokenIndex = sourceIndex;
      }
    }
		// All chunks left of the above index are considered "matched" and not touched.
		// Reverse look up: Identify corresponding chunk indexes
		// Reverse look up: Identify all corresponding target tokens
    var targetTokenIndexes = [];
    for ( var i = leftMostSourceTokenIndex; i < chunkVector.length; i++ ) {
     	if ( !(chunkVector[i] in sourceChunks) ) {
       	break;
     	}
     	if ( s2t.hasOwnProperty(i) ) {
       	Array.prototype.push.apply( targetTokenIndexes, s2t[i] );
     	}
   	}

  		// Chunk in the target language
		if ( targetTokenIndexes.length > 0 ) {
      targetTokenIndexes.sort(function (a, b) { return a - b; });
			targetTokenIndexes = _.uniq( targetTokenIndexes, true );
			var rightMostTargetTokenIndex = -1;
			// Construction a continuguos suggestion text in the target language
			var suggestionTokens = [];
			for ( var i = 0; i < targetTokenIndexes.length; i++ ) {
				var targetTokenIndex = targetTokenIndexes[i];
				if ( targetTokenIndex < baseTargetTokenIndex ) {
          // Skip alignments into the prefix
					continue;
				}
				if ( rightMostTargetTokenIndex >= 0 && targetTokenIndex - rightMostTargetTokenIndex !== 1 ) {
          // Stop when a discontinuity is encountered. The source was reordered.
					break;
				}
				rightMostTargetTokenIndex = targetTokenIndex;
				suggestionTokens.push( translation[targetTokenIndex] );
			}
			if (suggestionTokens.length > 0) {
				var suggestionText = suggestionTokens.join(" ");
				return suggestionText;
			}
		}
	}
	return null;
};

TargetBoxState.prototype.updatePrefixTokensAndSuggestionList = function() {
	var prefix = this.get( "prefix" );
	var prefixTokens = prefix.split( this.WHITESPACE );
	var prefixLength = ( prefix === "" ) ? 0 : prefixTokens.length;

	var baseTargetTokenIndex = prefixLength;
	var translationList = this.get( "translationList" ); // prefix is always updated whenever translationList or alignIndexList
	var s2tAlignments = this.get( "s2tAlignments" );
	var t2sAlignments = this.get( "t2sAlignments" );
	var suggestionList = {};
	var suggestionRank = 0;
	var suggestionExpList = {};
	var suggestionExpRank = 0;

	// Determine suggestions starting from the current prefix position...
	var maxBaseTargetTokenIndex = baseTargetTokenIndex;
	for ( var translationIndex = 0; translationIndex < translationList.length; translationIndex++ ) {
		
		// Terminate if we reach the maximum number of suggestions
		if ( suggestionRank === this.MAX_PRECOMPUTED_SUGGESTIONS ) {
	 		break;
		}
		
		// Get the next translation, and its source-to-target and target-to-source alignments
		var translation = translationList[ translationIndex ];
		var s2t = s2tAlignments[ translationIndex ];
    var t2s = t2sAlignments[ translationIndex ];
		var suggestionText = this.__identifyContinugousSuggestion( translation, s2t, t2s, baseTargetTokenIndex );
		if ( suggestionText !== null ) {
			if ( ! suggestionList.hasOwnProperty(suggestionText) ) {
				suggestionList[suggestionText] = suggestionRank++;
			}
		}
		maxBaseTargetTokenIndex = Math.max( translation.length, maxBaseTargetTokenIndex );
		
		// EDIT 2014/01/17
		// Always insert next token of the best translation, so autocomplete (based on word suggestion) is consistent with what's show on the screen (sentence suggestion).
		if ( translationIndex === 0 && suggestionRank === 0 ) {
	  		suggestionList[ translationList[0][baseTargetTokenIndex] ] = suggestionRank++;
		}
	}

	// The following block is redundant after "EDIT 2014/01/17" above.
	// Insert the next token of the best translation, if no suggestions are found at this point
	if ( suggestionRank === 0 && translationList.length > 0 ) {
  		suggestionList[ translationList[0][baseTargetTokenIndex] ] = suggestionRank++;
	}

  // ********
	// Determine suggestions starting from further down in the sentence
  // ********
  for ( var futureTargetTokenIndex = baseTargetTokenIndex; futureTargetTokenIndex < maxBaseTargetTokenIndex; futureTargetTokenIndex++ ) {

		// Restrict search to only the best MT
		// Alternative is to search all MTs with "translationIndex < translationList.length"
		for ( var translationIndex = 0; translationIndex < 1; translationIndex++ ) {

			// Terminate if we reach the maximum number of suggestions
			if ( suggestionExpRank === this.MAX_PRECOMPUTED_SUGGESTIONS ) {
		 		break;
			}

			// Get the next translation, and its source-to-target and target-to-source alignments
			var translation = translationList[ translationIndex ];
			var s2t = s2tAlignments[ translationIndex ];
	    	var t2s = t2sAlignments[ translationIndex ];
			var suggestionText = this.__identifyContinugousSuggestion( translation, s2t, t2s, futureTargetTokenIndex );
			if ( suggestionText !== null ) {
				if ( ! suggestionList.hasOwnProperty(suggestionText) && ! suggestionExpList.hasOwnProperty(suggestionText) ) {
					suggestionExpList[suggestionText] = suggestionExpRank++;
				}
			}
		}
	}

	var suggestionListFlattened = new Array( suggestionRank );
	var suggestionExpListFlattened = new Array( suggestionExpRank );
	for ( var suggestion in suggestionList ) {
 		var rank = suggestionList[ suggestion ];
 		suggestionListFlattened[ rank ] = suggestion;
	}
	for ( var suggestion in suggestionExpList ) {
		var rank = suggestionExpList[ suggestion ];
		suggestionExpListFlattened[ rank ] = suggestion;
	}
	this.set({
		"prefixTokens" : prefixTokens,
		"prefixLength" : prefixLength,
		"suggestionList" : suggestionListFlattened,
		"suggestionExpList" : suggestionExpListFlattened
	});
};

TargetBoxState.prototype.updateUserTokens = function() {
	var userText = this.get( "userText" );
	var userTokens = userText.split( this.WHITESPACE );
	var userTokensAndSeps = userText.split( this.WHITESPACE_SEPS );
	var userLength = ( userText === "" ) ? 0 : userTokens.length;
	var prefixLength = this.get( "prefixLength" );
	
	var editingPrefix = "";
	var overlayPrefix = userText;
	var overlaySep = "";
	var overlayEditing = "";
	if ( userLength === 0 ) {
		overlayPrefix = "";
		overlaySep = "";
		overlayEditing = ""
	}
	else if ( prefixLength === 0 ) {
		overlayPrefix = "";
		overlaySep = "";
		overlayEditing = userText;
	}
	else if ( userLength > prefixLength ) {
		overlayPrefix = userTokensAndSeps.slice( 0, prefixLength*2-1 ).join("");
		overlaySep = userTokensAndSeps[ prefixLength*2-1 ];
		overlayEditing = userTokensAndSeps.slice( prefixLength*2 ).join("");
	}
	if ( userLength > 1 ) {
		editingPrefix = userTokensAndSeps.slice( 0, userTokensAndSeps.length-2 ).join("");
	}
	this.set({
		"userTokens" : userTokens,
		"userLength" : userLength,
		"editingPrefix" : editingPrefix,
		"overlayPrefix" : overlayPrefix,
		"overlaySep" : overlaySep,
		"overlayEditing" : overlayEditing
	});
};

TargetBoxState.prototype.updateTranslations = function() {
	var postEditMode = this.get("postEditMode");
	if ( !postEditMode ) {
		var segmentId = this.get( "segmentId" );
		var editingPrefix = this.get( "editingPrefix" );
		this.trigger( "updateTranslations", segmentId, editingPrefix );
	}
};

TargetBoxState.prototype.__updateBestTranslation = function() {
	var bestTranslation = [];
	var userTokens = this.get( "userTokens" );
	var userToken = userTokens[ userTokens.length - 1 ];
	var translationList = this.get( "translationList" );
	var translationIndex = 0;

	if ( translationList.length > translationIndex ) {
		var mtTokens = translationList[translationIndex];
		if ( mtTokens.length >= userTokens.length ) {
			var mtToken = mtTokens[ userTokens.length - 1 ];
			if ( mtToken.substr( 0, userToken.length ) === userToken ) {
				bestTranslation.push( mtToken.substr( userToken.length ) );
			}
			else {
				bestTranslation.push( "" )
			}
			for ( var n = userTokens.length; n < mtTokens.length; n++ ) {
				var mtToken = mtTokens[n];
				bestTranslation.push( mtToken );
			}
		}
	}
	this.set( "bestTranslation", bestTranslation );
};

TargetBoxState.prototype.__updateSuggestions = function() {
	var suggestions = [];
	var firstSuggestion = "";
	var prefix = this.get( "prefix" );
	var caretIndex = this.get( "caretIndex" );
	var bestTranslation = this.get( "bestTranslation" );
  
	// Only show suggestions if caret is in the first word following the prefix
	// Lowerbound: Must be longer than prefix
	if ( caretIndex > prefix.length || prefix.length === 0 ) {

		// Only show suggestions if we've not yet reached the end of the best translation
		if ( bestTranslation.length > 0 ) {

			// Upperbound: Matching all characters following the prefix
			var userText = this.get( "userText" );
			var editingText = userText.substr( prefix.length ).trimLeft();
			var suggestionList = this.get( "suggestionList" );
			for ( var i = 0; i < suggestionList.length; i++ ) {
				var suggestion = suggestionList[i];
				if ( suggestion.substr( 0, editingText.length ) === editingText && suggestion.length > editingText.length ) {
					suggestions.push( suggestion );
				}
				if ( suggestions.length >= this.MAX_VISIBLE_SUGGESTIONS ) {
					break;
				}
			}
			
			if ( editingText.length > 0 ) {
				var suggestionExpList = this.get( "suggestionExpList" );
				for ( var i = 0; i < suggestionExpList.length; i++ ) {
					var suggestion = suggestionExpList[i];
					if ( suggestion.substr( 0, editingText.length ) === editingText && suggestion.length > editingText.length ) {
						suggestions.push( suggestion );
					}
					if ( suggestions.length >= this.MAX_VISIBLE_SUGGESTIONS ) {
						break;
					}
				}
			}
		}
	}
	if ( suggestions.length > 0 ) {
		firstSuggestion = suggestions[ 0 ];
	}
	if ( this.get( "enableSuggestions" ) !== true ) {
		suggestions = [];
	}
	this.set({
		"suggestions" : suggestions,
		"firstSuggestion" : firstSuggestion
	});
	this.trigger( "updateSuggestions", this.get( "segmentId" ), suggestions );
};

TargetBoxState.prototype.__updateMatchingTokens = function() {
	var matchingTokens = {};
	if ( this.get( "enableBestTranslation" ) === true ) {
		var userTokens = this.get( "userTokens" );
		var s2tAlignments = this.get( "s2tAlignments" );
		var t2sAlignments = this.get( "t2sAlignments" );
		if ( s2tAlignments.length > 0 && t2sAlignments.length > 0 ) {
		    var s2t = s2tAlignments[0];
			var t2s = t2sAlignments[0];
			if ( userTokens.length > 0 ) {
        var size = userTokens.length;
        // Account for the pad at the end of the user prefix
 				var maxIndex = userTokens[size-1].length === 0 ? userTokens.length-1 : userTokens.length;
				var rightMostSrcIndex = -1;
		        for ( var t = 0; t < maxIndex; t++ ) {
					if ( t2s.hasOwnProperty(t) ) {
						var srcIndexList = t2s[ t ];
						for ( var s = 0; s < srcIndexList.length; s++ ) {
							var srcIndex = srcIndexList[ s ];
							matchingTokens[ srcIndex ] = true;
							rightMostSrcIndex = Math.max( rightMostSrcIndex, srcIndex );
						}
					}
		        }
				// Blank out unaligned source tokens
				for ( var s = 0; s < rightMostSrcIndex; s++ ) {
					if ( ! s2t.hasOwnProperty(s) ) {
						matchingTokens[ s ] = true;
					}
				}
			}
			this.set( "matchingTokens", matchingTokens );
			this.trigger( "updateMatchingTokens", this.get( "segmentId" ), matchingTokens );
		}
	}
	// Trigger an update only if the client received a valid server response.
//	this.set( "matchingTokens", matchingTokens );
//	this.trigger( "updateMatchingTokens", this.get( "segmentId" ), matchingTokens );
};

TargetBoxState.prototype.replaceEditingToken = function( text ) {
	var editingPrefix = this.get( "editingPrefix" );
	var userText = ( editingPrefix === "" ? "" : editingPrefix + " " ) + ( text === "" ? "" : text + " " );
	this.set( "userText", userText );
};

TargetBoxState.prototype.updateFocus = function() {
	var segmentId = this.get( "segmentId" );
	this.trigger( "updateFocus", segmentId );
};

TargetBoxState.prototype.updateEditCoords = function() {
	var segmentId = this.get( "segmentId" );
	this.trigger( "updateEditCoords", segmentId );
};

TargetBoxState.prototype.updateBoxDims = function() {
	var segmentId = this.get( "segmentId" );
	this.trigger( "updateBoxDims", segmentId );
};

TargetBoxState.prototype.focus = function() {
	var postEditMode = this.get("postEditMode");
	if ( postEditMode ) {
		this.viewTextarea.textarea[0][0].selectionStart = 0;
		this.viewTextarea.textarea[0][0].selectionEnd = 0;
		this.viewTextarea.textarea[0][0].focus();
	}
	else {
		var caretIndex = this.viewTextarea.textarea[0][0].value.length;
		this.viewTextarea.textarea[0][0].selectionStart = caretIndex;
		this.viewTextarea.textarea[0][0].selectionEnd = caretIndex;
		this.viewTextarea.textarea[0][0].focus();
	}
	this.set({
		"hasFocus" : true,
		"caretIndex" : caretIndex
	});
};
