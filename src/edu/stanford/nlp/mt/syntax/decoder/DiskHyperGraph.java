package edu.stanford.nlp.mt.syntax.decoder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.HashMap;

import edu.stanford.nlp.mt.syntax.decoder.HyperGraph;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

//this class implements functions of writting/reading hypergraph on disk
@SuppressWarnings("unchecked")
public class DiskHyperGraph {
//	shared by many hypergraphs
	HashMap tbl_associated_grammar = new HashMap();
	//static int cur_rule_id=1;
	BufferedWriter writer_out =null;//write items
	BufferedReader reader_in =null;//read items
	
//	shared by a single hypergraph
	HashMap tbl_item_2_id =new HashMap();//map item to id, used for saving hypergraph
	HashMap tbl_id_2_item =new HashMap();//map id to item, used for reading hypergraph
	int cur_item_id=1;
	
// static variables	
	static String SENTENCE_TAG ="#SENT: ";
	static String ITEM_TAG ="#I";
    static String DEDUCTION_TAG ="#D";	
    static String OPTIMAL_DEDUCTION_TAG ="#D*";
    
    static String ITEM_STATE_TAG= " ST ";
    static String NULL_ITEM_STATE= "nullstate";    
    static int NULL_RULE_ID = -1;//three kinds of rule: regular rule (id>0); oov rule (id=0), and null rule (id=-1)
    static int OOV_RULE_ID = 0;
    
    static String RULE_TBL_SEP =" -LZF- ";
    
//for writting hyper-graph: (1) saving each hyper-graph; (2) remember each regualar rule used; (3) dump the rule jointly (in case parallel decoding)    
    
    public void init_write(String f_items){
    }
    
    public HashMap get_used_grammar_tbl(){
    	return tbl_associated_grammar;
    }
    
    public void write_rules_non_parallel(String f_rule_tbl){
    }

    public void write_rules_parallel(BufferedWriter out_rules, HashMap tbl_done){
    }

    public void init_read(String f_hypergraphs, String f_rule_tbl){
    }
    
   
	public void save_hyper_graph(HyperGraph hg, int sent_id){	
	}
	
	public HyperGraph read_hyper_graph(){
		return null;
	}
}
