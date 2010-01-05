package edu.stanford.nlp.mt.syntax.decoder;

import java.util.ArrayList;
import java.util.Formatter;

/*Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

/*public interfaces
 * TMGrammar: init and load the grammar
 * TrieNode: match symbol for next layer
 * RuleBin: get sorted rules
 * Rule: rule information
 * */
@SuppressWarnings("unchecked")
public abstract class TMGrammar {
	/*TMGrammar is composed by Trie nodes
	Each trie node has:
	(1) RuleBin: a list of rules matching the french sides so far
	(2) a HashMap  of next-layer trie nodes, the next french word used as the key in HashMap
	*/
	static protected ArrayList<Model> p_l_models=null;
	protected int span_limit=10;
	protected static int default_owner=0;//will change autotmatically
	protected static String non_terminal_regexp="^[A-Z]+[0-9]*$";
	protected static String non_terminal_replace_regexp="[0-9]+";

	static public int OOV_RULE_ID =0;

	public TMGrammar(ArrayList<Model> l_models, String default_ow, int span_limit_in, String non_terminal_regexp_in, String non_terminal_replace_regexp_in){
		p_l_models = l_models;
		default_owner=Symbol.add_terminal_symbol(default_ow);
		non_terminal_regexp=non_terminal_regexp_in;
		non_terminal_replace_regexp=non_terminal_replace_regexp_in;
		span_limit = span_limit_in;
	}

	public abstract TrieNode get_root();

	public abstract void read_tm_grammar_from_file(String grammar_file);

	public abstract void read_tm_grammar_glue_rules();


	//if the span covered by the chart bin is greather than the limit, then return false
	public boolean filter_span(int start, int end, int len){
		if(span_limit==-1){//mono-glue grammar
			return (start==0);
		}else
			return (end-start<=span_limit);
	}

	public static int get_eng_non_terminal_id(String symbol){
		//long start = Support.current_time();
		/*String new_str = symbol.replaceAll("[\\[\\]\\,]+", "");//TODO
		int res = new Integer(new_str.substring(new_str.length()-1, new_str.length())).intValue()-1;//TODO: assume only one integer, start from one*/
		int res = Integer.parseInt(symbol.substring(symbol.length() - 2, symbol.length() - 1)) - 1;//TODO: assume only one integer, start from one
		//Chart.g_time_check_nonterminal += Support.current_time()-start;
		return res;
		//return (new Integer(new_str.substring(new_str.length()-1, new_str.length()))).intValue();//TODO: assume only one integer, start from zero
	}

//	TODO: we assume all the Chinese training text is lowercased, and all the non-terminal symbols are in [A-Z]+
	public static boolean is_non_terminal(String symbol){
		//long start = Support.current_time();
		//boolean res = symbol.matches(non_terminal_regexp);
		boolean res;
		if(symbol.length()<=4)//[x,?]
			res = false;
		else{
			String non_terminal = symbol.substring(1,symbol.length()-3); //e.g., [PHRASE,2]
			if(non_terminal.compareTo(Decoder.default_non_terminal)==0//TODO
			   || non_terminal.compareTo(Symbol.GOAL_SYM)==0)//TODO
				res=true;
			else
				res=false;
		}
		//Chart.g_time_check_nonterminal += Support.current_time()-start;
		return res;
	}

	//DotNode
	public abstract class TrieNode{
		public abstract TrieNode match_symbol(int sym_id);//find next layer
		public abstract RuleBin get_rule_bin();
		public abstract boolean is_no_child_trienodes();
	}

	//contain all rules with the same french side (and thus same arity)
	public abstract class RuleBin {
		protected int arity=0;//number of non-terminals
		protected int[] french;

		//TODO: now, we assume this function will be called only after all the rules have been read
		//this method need to be synchronized as we will call this function only after the decoding begins
		//to avoid the synchronized method, we should call this once the grammar is finished
		//public synchronized ArrayList<Rule> get_sorted_rules(){
		public abstract ArrayList<Rule> get_sorted_rules();

		public  abstract int[] get_french();

		public abstract int get_arity();
	}

	public abstract static class Rule{
		int rule_id = OOV_RULE_ID;
		//Rule formate: [Phrase] ||| french ||| english ||| feature scores
		public int lhs;//tag of this rule, state to upper layer
		public int[] french;//only need to maintain at rulebine
		public int[] english;
		public int owner;
		public float[] feat_scores;//the feature scores for this rule
		public int arity=0;//TODO: disk-grammar does not have this information, so, arity-penalty feature is not supported in disk-grammar

		//public String str;
		/*this remember all the stateless cost: sum of cost of all stateless models (e..g, phrase model, word-penalty, phrase-penalty). The LM model cost is not included here.
		this will be set by Grmmar.estimate_rule*/
		public  float statelesscost=0; //this is set in estimate_rule()

		public Rule(String line){
			//TODO
		}

		public Rule(){
			//TODO
		}

		public String convert_to_string(){
			StringBuffer res = new StringBuffer();
			res.append("["); res.append(Symbol.get_string(lhs)); res.append("] ||| ");
			res.append(Symbol.get_string(french)); res.append(" ||| ");
			res.append(Symbol.get_string(english)); res.append(" |||");
			for(int i=0; i<feat_scores.length; i++)
				res.append(new Formatter().format(" %.4f", feat_scores[i]));
			return res.toString();
		}
	}

}
