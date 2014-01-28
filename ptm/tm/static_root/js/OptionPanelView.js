var OptionPanelView = Backbone.View.extend({
	"el" : ".OptionPanelView"
});

OptionPanelView.prototype.initialize = function( options ) {
	this.views = {};
	this.views.container = d3.select( this.el )
		.style( "display", "inline-block" )
		.style( "visibility", "visible" )
		.style( "padding", 0 );
	this.views.bestTranslation = this.views.container.append( "div" ).attr( "class", "EnableBestTranslations" ).call( this.__bestTranslationsRenderOnce.bind(this) );
	this.views.suggestions = this.views.container.append( "div" ).attr( "class", "EnableSuggestions" ).call( this.__suggestionsRenderOnce.bind(this) );
	this.views.hover = this.views.container.append( "div" ).attr( "class", "EnableHover" ).call( this.__hoverRenderOnce.bind(this) );
	
	this.listenTo( this.model, "change:visible", this.changeVisibility );
	this.listenTo( this.model, "change:enableMT", this.renderBestTranslation );
	this.listenTo( this.model, "change:enableSuggestions", this.renderSuggestions );
	this.listenTo( this.model, "change:enableHover", this.renderHover );
};

OptionPanelView.prototype.changeVisibility = function() {
	var visible = this.model.get( "visible" );
	this.views.container
		.style( "visibility", visible ? "visible" : "hidden" );
};

OptionPanelView.prototype.renderBestTranslation = function() {
	this.views.bestTranslation.call( this.__bestTranslationsRenderAlways.bind(this) );
	this.views.suggestions.call( this.__suggestionsRenderAlways.bind(this) );
	this.views.hover.call( this.__hoverRenderAlways.bind(this) );
};

OptionPanelView.prototype.renderSuggestions = function() {
	this.views.suggestions.call( this.__suggestionsRenderAlways.bind(this) );
};

OptionPanelView.prototype.renderHover = function() {
	this.views.hover.call( this.__hoverRenderAlways.bind(this) );
};

OptionPanelView.prototype.__bestTranslationsRenderOnce = function( elem ) {
	var onChange = function() {
		var enabled = this.views.bestTranslation.select("input")[0][0].checked;
		this.model.set( "enableMT", enabled );
	}.bind(this);
	var onClick = function() {
		var enabled = this.model.get( "enableMT" );
		this.model.set( "enableMT", !enabled );
	}.bind(this);
	elem.classed( "OptionPanel", true )
	elem.append( "input" )
		.attr( "type", "checkbox" )
		.attr( "checked", "checked" )
		.style( "margin", "2px 5px" )
		.style( "pointer-events", "auto" )
		.style( "cursor", "pointer" )
		.on( "change", onChange )
	elem.append( "span" )
		.text( "Show a suggested translation for each sentence" )
		.style( "pointer-events", "auto" )
		.style( "cursor", "pointer" )
		.on( "click", onClick )
};

OptionPanelView.prototype.__bestTranslationsRenderAlways = function( elem ) {
	var enabled = this.model.get( "enableMT" );
	elem.select( "input" )
		.each( function() { d3.select(this)[0][0].checked = enabled } );
	elem.select( "span" )
		.style( "color", enabled ? "#444" : "#888" )
};

OptionPanelView.prototype.__suggestionsRenderOnce = function( elem ) {
	var onChange = function() {
		var enabled = this.views.suggestions.select("input")[0][0].checked;
		this.model.set( "enableSuggestions", enabled );
	}.bind(this);
	var onClick = function() {
		var enabled = this.model.get( "enableSuggestions" );
		this.model.set( "enableSuggestions", !enabled );
	}.bind(this);
	elem.classed( "OptionPanel", true )
	elem.append( "input" )
		.attr( "type", "checkbox" )
		.attr( "checked", "checked" )
		.style( "margin", "2px 5px 2px 15px" )
		.style( "pointer-events", "auto" )
		.style( "cursor", "pointer" )
		.on( "change", onChange )
	elem.append( "span" )
		.text( "Display autocomplete suggestions while typing" )
		.style( "pointer-events", "auto" )
		.style( "cursor", "pointer" )
		.on( "click", onClick )
};

OptionPanelView.prototype.__suggestionsRenderAlways = function( elem ) {
	var enabled = this.model.get( "enableSuggestions" ) && this.model.get( "enableMT" );
	elem.select( "input" )
		.each( function() { d3.select(this)[0][0].checked = enabled } );
	elem.select( "span" )
		.style( "color", enabled ? "#444" : "#888" )
};

OptionPanelView.prototype.__hoverRenderOnce = function( elem ) {
	var onChange = function() {
		var enabled = this.views.hover.select("input")[0][0].checked;
		this.model.set( "enableHover", enabled );
	}.bind(this);
	var onClick = function() {
		var enabled = this.model.get( "enableHover" );
		this.model.set( "enableHover", !enabled );
	}.bind(this);
	elem.classed( "OptionPanel", true )
	elem.append( "input" )
		.attr( "type", "checkbox" )
		.attr( "checked", "checked" )
		.style( "margin", "2px 5px 2px 15px" )
		.style( "pointer-events", "auto" )
		.style( "cursor", "pointer" )
		.on( "change", onChange )
	elem.append( "span" )
		.text( "Display word suggestions on mouse hover" )
		.style( "pointer-events", "auto" )
		.style( "cursor", "pointer" )
		.on( "click", onClick )
};

OptionPanelView.prototype.__hoverRenderAlways = function( elem ) {
	var enabled = this.model.get( "enableHover" ) && this.model.get( "enableMT" );
	elem.select( "input" )
		.each( function() { d3.select(this)[0][0].checked = enabled } );
	elem.select( "span" )
		.style( "color", enabled ? "#444" : "#888" )
};
