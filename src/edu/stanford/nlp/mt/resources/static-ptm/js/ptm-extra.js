
// Register in global namespace

// A simple (unoptimized) Trie for prefix-lookups
//TODO(spenceg) Right now it assumes that the values in the nodes
// are numerical, unique, and ascending in value. Make this a more general
// implementation.
var SimpleTrie = function(){
			//TODO(wsg) Make this value field more general
			this.defValue = Number.MAX_VALUE;
			this.root = new TrieNode("",0.0);			
};
SimpleTrie.prototype.Add = function(key,value){
				var node = this.Insert(this.root,key);
				if(node){
					// TODO(wsg) Make this more general later
					if(value < node.value){
						node.value = value;
					}
					return true;
				}
				return false;
			};
			
// Recursively insert the prefix into the Trie
// returns the last TrieNode which has the last character
SimpleTrie.prototype.Insert = function(node,prefix){
				if(prefix.length === 0){ 
					return "";
				}								
				var newKey = prefix[0];
				var newPref = prefix.slice(1,prefix.length);
				if(newPref.length > 0){
					if(node.kids.hasOwnProperty(newKey) === false){
						node.kids[newKey] = new TrieNode(this.defValue);					
					}
					return this.Insert(node.kids[newKey], newPref);					
					
				// Add a terminal				
				} else {
//					console.log("Inserting: " + newKey);
					if(node.kids.hasOwnProperty(newKey) === false){
						node.kids[newKey] = new TrieNode(this.defValue);
					}
					return node.kids[newKey];
				}
			};
			
// Return all strings beginning with this prefix
// sorted by their values
//TODO(spenceg) Currently this returns an unordered array. It should return
// a map with the values as the keys.
SimpleTrie.prototype.FindAll = function(prefix){
//				console.log("FindAll for: " + prefix);
				var fromNode = this.ContainsPrefix(prefix);
				if(fromNode){
//					console.log(fromNode.ToString());
					var strCache = {};
					this.FindAllHelper(fromNode,"",strCache);
					return strCache;
				}			
				return "";			
			};
		
// O(n) extraction of suffixes from the trie
// Post-order DFS through the child properties
SimpleTrie.prototype.FindAllHelper = function(fromNode,context,strCache){
				var noKids = true;
				for(var kid in fromNode.kids){
//					console.log("Kid: " + kid);
					noKids = false;
					this.FindAllHelper(fromNode.kids[kid],context + kid,strCache);
				}

				//Process the root				
				if(noKids || fromNode.value != this.defValue){
					var new_key = fromNode.value;
//					console.log("Appending: " + new_key + " || " + context);
					strCache[new_key] = context;			
				}
			};		
			
			// O(logn) search through the Trie
SimpleTrie.prototype.ContainsPrefix = function(prefix){			
				var fromNode = this.root;
				var key = prefix[0];
				var newPrefix = prefix.slice(1,prefix.length);				
				while(newPrefix.length > 0) {
					if(fromNode.kids.hasOwnProperty(key)){
						fromNode = fromNode.kids[key];
						key = newPrefix[0];
						newPrefix = newPrefix.slice(1,newPrefix.length);
					} else {
						return "";
					}					
				}
				
				if(fromNode.kids.hasOwnProperty(key)){
					return fromNode.kids[key];
				} else {
					return "";
				}
			};

//Do an O(logn) search through the tree
//and return the value if it exists
SimpleTrie.prototype.GetValue = function(prefix){
				var fromNode = this.ContainsPrefix(prefix);
				if(fromNode){
					return fromNode.value;
				} else {
					return "";
				}			
			};

//TrieNode objects are nodes in the SimpleTrie
var TrieNode = function(value){
			this.value = value;		
			this.kids = {};
	};
TrieNode.prototype.ToString = function(){
		return this.value.toString() + " " + this.kids.toString();
};

