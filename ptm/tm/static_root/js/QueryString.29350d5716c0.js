// Jason Chuang [2013.07.19]
function QueryString()
{
	this.parameters = [];
}

QueryString.prototype.parameters = function()
{
	for ( var i = 0; i < arguments.length; i++ )
		this.addValueParameter( arguments[i] );
}
QueryString.prototype.addValueParameter = function( name, identifier, decoder, encoder )
{
	if ( identifier === undefined || identifier === null )
		identifier = name;
	if ( encoder === undefined || encoder === null )
		if ( decoder === undefined || decoder === null )
			encoder = "str";
		else
			encoder = decoder;
	if ( decoder === undefined || decoder === null )
		decoder = "str";

	var parameter = {
		'name' : name,
		'identifier' : identifier,
		'isArray' : false,
		'decoder' : this.valueDecoder( decoder ),
		'encoder' : this.valueEncoder( encoder )
	}
	this.parameters.push( parameter );
	return this;
}
QueryString.prototype.addArrayParameter = function( name, identifier, decoder, encoder )
{
	if ( identifier === undefined || identifier === null )
		identifier = name;
	if ( encoder === undefined || encoder === null )
		if ( decoder === undefined || decoder === null )
			encoder = "str";
		else
			encoder = decoder;
	if ( decoder === undefined || decoder === null )
		decoder = "str";
		
	var parameter = {
		'name' : name,
		'identifier' : identifier,
		'isArray' : true,
		'decoder' : this.arrayDecoder( decoder ),
		'encoder' : this.arrayEncoder( encoder )
	}
	this.parameters.push( parameter );
	return this;
}
QueryString.prototype.valueDecoder = function( decoder )
{
	if ( typeof decoder == "function" )
		return decoder;
	if ( typeof decoder == "string" )
	{
		if ( decoder == "int" )
			return function(d) { return parseInt(d,10) }
		if ( decoder == "float" )
			return function(d) { return parseFloat(d) }
		return function(d) { return d };
	}
	return null;
}
QueryString.prototype.valueEncoder = function( encoder )
{
	if ( typeof encoder == "function" )
		return encoder;
	if ( typeof encoder == "string" )
	{
		return function(d) { return String(d) };
	}
	return null;
}
QueryString.prototype.arrayDecoder = function( decoder )
{
	if ( typeof decoder == "function" )
		return decoder;
	var g = function(values)
	{
		var f = this.valueDecoder(decoder);
		var states = [];
		values.forEach( function(d) { states.push( f(d) ) } );
		return states;
	}
	return g.bind(this);
}
QueryString.prototype.arrayEncoder = function( encoder )
{
	if ( typeof encoder == "function" )
		return encoder;
	var g = function(states)
	{
		var f = this.valueEncoder(encoder);
		var values = [];
		states.forEach( function(d) { values.push( f(d) ) } );
		return values;
	}
	return g.bind(this);
}

QueryString.prototype.read = function( states )
{
	if ( states === undefined || states === null )
		states = {};
	for ( var i in this.parameters )
	{
		var p = this.parameters[i];
		if ( p.isArray )
		{
			var values = this.getValues( p.identifier );
			if ( values.length > 0 )
				states[p.name] = p.decoder( values );
		}
		else
		{
			var value = this.getValue( p.identifier );
			if ( value != null )
				states[p.name] = p.decoder( value );
		}
	}
	return states;
}
QueryString.prototype.write = function( states, replaceBrowserHistoryEntry, pageStates, pageTitle )
{
	if ( replaceBrowserHistoryEntry === undefined || typeof replaceBrowserHistoryEntry != "boolean" )
		replaceBrowserHistoryEntry = false;
	if ( pageStates === undefined )
		pageStates = null;
	if ( pageTitle === undefined )
		pageTitle = null;

	var s = [];
	for ( var i in this.parameters )
	{
		var p = this.parameters[i];
		if ( p.name in states )
		{
			if ( p.isArray )
			{
				var values = p.encoder( states[p.name] );
				for ( var j in values )
					if ( values[j].length > 0 )
						s.push( p.identifier + "=" + escape( values[j] ) );
			}
			else
			{
				var value = p.encoder( states[p.name] );
				if ( value.length > 0 )
					s.push( p.identifier + "=" + escape( value ) );
			}
		}
	}
	
	var protocol = window.location.protocol;
	var server = window.location.host;
	var path = window.location.pathname;
	var pageURL = protocol + '//' + server + path + ( s.length > 0 ? "?" + s.join( "&" ) : "" );
	
	if ( replaceBrowserHistoryEntry )
		history.replaceState( pageStates, pageTitle, pageURL );
	else
		history.pushState( pageStates, pageTitle, pageURL );
}

QueryString.prototype.getValue = function( key )
{
	var regex = this.getKeyRegex( key );
	var match = regex.exec( window.location.href );
	if ( match === null )
		return null;
	else
		return unescape( match[1] );
}
QueryString.prototype.getValues = function( key )
{
	var regex = this.getKeyRegex( key );
	var matches = window.location.href.match( regex );
	if ( matches === null )
		return [];
	else
	{
		for ( var i = 0; i < matches.length; i ++ )
		{
			var regex = this.getKeyRegex( key );
			var match = regex.exec( matches[i] );
			matches[i] = unescape( match[1] );
		}
		return matches;
	}
}
QueryString.prototype.getKeyRegex = function( key )
{
	return new RegExp( "[\\?&]" + key + "=([^&]*)", "g" );
}

