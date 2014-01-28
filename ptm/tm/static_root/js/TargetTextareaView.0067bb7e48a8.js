var TargetTextareaView = Backbone.View.extend({
	el : ".TargetTextareaView"
});

TargetTextareaView.prototype.BACKGROUND = "#ffffff"

TargetTextareaView.prototype.KEY = {
	TAB : 9,
	ENTER : 13,
	ESC : 27,
	UP_ARROW : 38,
	DOWN_ARROW : 40
};

TargetTextareaView.prototype.initialize = function() {
	this.container = d3.select( this.el ).style( "pointer-events", "none" );
	this.textarea = this.container.append( "textarea" ).call( this.__textareaRenderOnce.bind(this) );
	
	this.resize = _.debounce( this.__resize, 10 );
	this.listenTo( this.model, "change:userText change:hasFocus", this.render.bind(this) );
	this.listenTo( this.model, "change:boxTextareaWidth change:boxTextareaHeight", this.resize.bind(this) );
};

TargetTextareaView.prototype.render = function() {
	var userText = this.model.get( "userText" );
	if ( userText !== this.textarea[0][0].value ) {
		this.textarea[0][0].value = userText;
	}
	this.textarea.call( this.__textareaRenderAlways.bind(this) );
};

TargetTextareaView.prototype.__resize = function() {
	var width = this.model.get( "boxTextareaWidth" );
	var height = this.model.get( "boxTextareaHeight" );
	this.textarea
		.style( "width", width + "px" )
		.style( "height", height + "px" );
};

TargetTextareaView.prototype.__textareaRenderOnce = function( elem ) {
	var onFocus = function() { this.model.updateFocus() }.bind(this);
	var onBlur = function() {}.bind(this);
	var onKeyDown = function() {
		var postEditMode = this.model.get("postEditMode");
		var keyCode = d3.event.keyCode;
		if ( keyCode === this.KEY.ENTER || keyCode === this.KEY.TAB || keyCode === this.KEY.ESC ) {
			d3.event.preventDefault();
			d3.event.cancelBubble = true;
		}
		else if ( keyCode === this.KEY.UP_ARROW || keyCode === this.KEY.DOWN_ARROW ) {
			if ( !postEditMode ) {
				d3.event.preventDefault();
				d3.event.cancelBubble = true;
			}
		}
		if ( this.__continuousKeyPress ) {
			var userText = this.textarea[0][0].value;
			var caretIndex = this.textarea[0][0].selectionEnd;
			this.model.set({
				"userText" : userText,
				"caretIndex" : caretIndex
			});
		}
		this.__continuousKeyPress = true;
	}.bind(this);
	var onKeyPress = function() {
	}.bind(this);
	var onKeyUp = function() {
		var postEditMode = this.model.get("postEditMode");
		var keyCode = d3.event.keyCode;
		if ( keyCode === this.KEY.ENTER ) {
			var segmentId = this.model.get( "segmentId" );
			if ( d3.event.shiftKey ) {
				this.model.trigger( "keypress keypress:enter+shift", segmentId )
			}
			else if ( d3.event.metaKey || d3.event.ctrlKey || d3.event.altKey || d3.event.altGraphKey ) {
				this.model.trigger( "keypress keypress:enter+meta", segmentId )
			}
			else {
				this.model.trigger( "keypress keypress:enter", segmentId );
			}
		}
		else if ( keyCode === this.KEY.TAB ) {
			var segmentId = this.model.get( "segmentId" );
			this.model.trigger( "keypress keypress:tab", segmentId );
		}
		else if ( keyCode === this.KEY.UP_ARROW ) {
			if ( !postEditMode ) {
				var segmentId = this.model.get( "segmentId" );
				this.model.trigger( "keypress keypress:up", segmentId );
			}
		}
		else if ( keyCode === this.KEY.DOWN_ARROW ) {
			if ( !postEditMode ) {
				var segmentId = this.model.get( "segmentId" );
				this.model.trigger( "keypress keypress:down", segmentId );
			}
		}
		else if ( keyCode === this.KEY.ESC ) {
			var segmentId = this.model.get( "segmentId" );
			this.model.trigger( "keypress keypress:esc", segmentId );
		}
		else {
			var userText = this.textarea[0][0].value;
			var caretIndex = this.textarea[0][0].selectionEnd;
			this.model.set({
				"userText" : userText,
				"caretIndex" : caretIndex
			});
			this.model.trigger( "keypress", segmentId );
		}
		this.__continuousKeyPress = false;
	}.bind(this);
	var onCopy = function() {
		var userText = this.textarea[0][0].value;
		var caretIndex = this.textarea[0][0].selectionEnd;
		this.model.set({
			"userText" : userText,
			"caretIndex" : caretIndex
		});
	}.bind(this);
	var onPaste = function() {
		var userText = this.textarea[0][0].value;
		var caretIndex = this.textarea[0][0].selectionEnd;
		this.model.set({
			"userText" : userText,
			"caretIndex" : caretIndex
		});
	}.bind(this);
	var onMouseDown = function() {
		var caretIndex = this.textarea[0][0].selectionEnd;
		this.model.set({
			"caretIndex" : caretIndex
		});
	}.bind(this);
	elem.style( "width", (this.model.WIDTH-75) + "px" )
		.style( "min-height", this.model.MIN_HEIGHT + "px" )
		.style( "height", this.model.MIN_HEIGHT + "px" )
		.style( "padding", "11px 60px 14px 15px" )
		.style( "border", "none" )
		.style( "outline", "none" )
		.style( "background", this.BACKGROUND )
		.style( "resize", "none" )
		.classed( "UserText", true )
		.style( "line-height", "20px" )
		.style( "pointer-events", "auto" )
		.style( "cursor", "default" )
		.on( "focus", onFocus )
		.on( "blur", onBlur )
		.on( "keydown", onKeyDown )
		.on( "keyPress", onKeyPress )
		.on( "keyup", onKeyUp )
		.on( "copy", onCopy )
		.on( "paste", onPaste )
		.on( "click", onMouseDown );
/*
	var height = elem[0][0].offsetHeight;
	var width = elem[0][0].offsetWidth;
	this.model.set({
		"boxOverlayHeight" : height - 24,
		"boxOverlayWidth" : width - 75,
		"boxHeight" : height,
		"boxWidth" : width
	});
*/
};

TargetTextareaView.prototype.__textareaRenderAlways = function( elem ) {
	var hasFocus = this.model.get( "hasFocus" );
	var enableBestTranslation = this.model.get( "enableBestTranslation" );
	
	if ( hasFocus )
		elem.transition().duration( this.model.ANIMATION_DURATION + this.model.ANIMATION_DELAY )
			.style( "background", "#fff" );
	else
		elem.transition().duration( this.model.ANIMATION_DURATION )
			.style( "background", this.BACKGROUND );
	
	if ( enableBestTranslation )
		elem.style( "cursor", "default" );
	else
		elem.style( "cursor", "text" );
/*
	var height = elem[0][0].offsetHeight;
	var width = elem[0][0].offsetWidth;
	this.model.set({
		"boxOverlayHeight" : height - 24,
		"boxOverlayWidth" : width - 75,
		"boxHeight" : height,
		"boxWidth" : width
	});
*/
};
