var DatasetManager = Backbone.Model.extend({
	defaults : {
		"datasetURL" : null,
		"datasets" : []
	},
	url : "/static/data/index.json"
});

DatasetManager.prototype.initialize = function() {
	this.qs = new QueryString();
	this.qs.addValueParameter( "url", "url" );
	this.view = new DatasetManagerUI({ "model" : this });
	this.fetch({ "success" : this.loadQueryString.bind(this)});
};

/**
 * Save the value of "datasetURL" as "url" in the query string.
 **/
DatasetManager.prototype.saveQueryString = function() {
	var datasetURL = this.get( "datasetURL" );
	var qsState = { "url" : datasetURL }
	if ( datasetURL !== null ) {
		this.qs.write( qsState );
	}
};

/**
 * Read the value of "datasetURL" from the "url" field in the query string.
 * If not defined, use the value of "url" in the first record in index.json.
 **/
DatasetManager.prototype.loadQueryString = function() {
	var qsState = this.qs.read();
	var datasetURL = qsState[ "url" ];
	if ( datasetURL === undefined || datasetURL === null ) {
		datasetURL = this.get( "datasets" )[0].url;
	}
	this.on( "change:datasetURL", this.saveQueryString );
	this.set( "datasetURL", datasetURL );
	this.view.render();
};

var DatasetManagerUI = Backbone.View.extend({
	el : "select#DatasetManager"
});

DatasetManagerUI.prototype.initialize = function() {
	this.view = d3.select( this.el );
	this.view.on( "change", function() { this.model.set( "datasetURL", this.view[0][0].value ) }.bind(this) );
};

DatasetManagerUI.prototype.render = function() {
	var datasetURL = this.model.get( "datasetURL" );
	var datasets = this.model.get( "datasets" );
	var elems = this.view.selectAll( "option" ).data( datasets );
	elems.enter().append( "option" );
	elems.exit().remove();
	this.view.selectAll( "option" )
		.attr( "value", function(d) { return d.url } )
		.attr( "selected", function(d) { return ( d.url === datasetURL ) ? "selected" : null } )
		.text( function(d) { return d.label + " (" + d.url + ")" } );
};
