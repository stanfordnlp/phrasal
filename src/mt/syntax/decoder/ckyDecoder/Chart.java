package mt.syntax.decoder.ckyDecoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

import mt.syntax.decoder.ckyDecoder.DotChart.DotItem;
import mt.syntax.decoder.ckyDecoder.HyperGraph.Deduction;
import mt.syntax.decoder.ckyDecoder.HyperGraph.Item;
import mt.syntax.decoder.ckyDecoder.TMGrammar.Rule;
import mt.syntax.decoder.ckyDecoder.TMGrammar.RuleBin;
import mt.syntax.decoder.ckyDecoder.TMGrammar.TrieNode;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

/*Signatures of class:
 * Bin: i, j
 * SuperItem (used for CKY check): i,j, lhs
 * Item (or node): i,j, lhs, edge ngrams
 * Deduction (and node)*/

/* index of sentences: start from zero
 * index of cell: cell (i,j) represent span of words indexed [i,j-1] where i is in [0,n-1] and j is in [1,n]
 * */


//this class implement chart-parsing, evaluate model cost, pruning, and construct a hyper-graph (identified by the goal item)
public class Chart {
	public TMGrammar[] l_grammars;
	public DotChart[] l_dotcharts;//each grammar should have a dotchart associated with it
	public Bin[][] l_bins;//note that in some cell, it might be null
	public Bin goal_bin= new Bin(this);
	public int[] sentence;//a list of foreign words
	public int sent_len;//foreign sent len
	
	//static private int sent_id;
	//decoder-wide variables
	ArrayList<Model> l_models;
	
	//static variable
	static int IMPOSSIBLE=99999;
	static double EPSILON = 0.000001;	
	
	//state name definition
	static String TOTAL_COST_SYM = "total_cost";//total cost of the deduction(i.e., rule with ant-items)
	static String ADDITIVE_COST_SYM = "additive_cost";//cost(ant-items)+rl.statelesscost Hiero: cost
	static String TRANSITION_COST_SYM = "transition_cost";//sum of transition cost by non-stateless/non-contexual models, Hiero: dcost
	static String BONUS_SYM = "bonus";//outside estimation cost
	static String ITEM_STATES_SYM = "item_states";//to remember model states (e.g., LM_LEFT or LM_RIGHT)
	//model specific state names	
	static String LM_L_STATE_SYM="llS";
	static String LM_R_STATE_SYM="lrS";
	
	static int TOTAL_COST_SYM_ID = 0;
	static int ADDITIVE_COST_SYM_ID =0;
	static int TRANSITION_COST_SYM_ID = 0;
	static int BONUS_SYM_ID = 0;//the future cost estimation
	static int ITEM_STATES_SYM_ID = 0;	
	static int LM_L_STATE_SYM_ID = 0;
	static int LM_R_STATE_SYM_ID = 0;
	
	public static void add_static_symbols(){
		TOTAL_COST_SYM_ID = Symbol.add_terminal_symbol(TOTAL_COST_SYM);
		ADDITIVE_COST_SYM_ID = Symbol.add_terminal_symbol(ADDITIVE_COST_SYM);
		TRANSITION_COST_SYM_ID = Symbol.add_terminal_symbol(TRANSITION_COST_SYM);
		BONUS_SYM_ID = Symbol.add_terminal_symbol(BONUS_SYM);
		ITEM_STATES_SYM_ID = Symbol.add_terminal_symbol(ITEM_STATES_SYM);	
		LM_L_STATE_SYM_ID = Symbol.add_terminal_symbol(LM_L_STATE_SYM);
		LM_R_STATE_SYM_ID = Symbol.add_terminal_symbol(LM_R_STATE_SYM);
		set_state_names_list();
	}
	
	static ArrayList<Integer> l_model_state_names;
	private static void set_state_names_list(){//must call after add_static_symbols 
		l_model_state_names = new ArrayList<Integer>();
		l_model_state_names.add(LM_L_STATE_SYM_ID);
		l_model_state_names.add(LM_R_STATE_SYM_ID);
	}
	
	//statistics
	int gtem=0;
	int n_prepruned=0;//how many items have been pruned away because its cost is greater than the cutoff in calling chart.add_deduction_in_chart()
	int n_prepruned_fuzz1=0;
	int n_prepruned_fuzz2=0;
	int n_pruned=0;
	int n_merged=0;
	int n_added=0;
	int n_dotitem_added=0;//note: there is no pruning in dot-item
	int n_called_compute_item=0;
	public void print_info(int level){
		Support.write_log_line(String.format("ADD: %d; MERGED: %d; pruned: %d; pre-pruned: %d ,fuzz1: %d, fuzz2: %d; n_dotitem_added: %d", n_added, n_merged,n_pruned,n_prepruned, n_prepruned_fuzz1, n_prepruned_fuzz2, n_dotitem_added), level);
	}
	
	//time-profile variables, debug purpose
	long g_time_compute_item=0;
	long g_time_add_deduction=0;
	static long g_time_lm=0;
	static long g_time_score_sent=0;
	static long g_time_check_nonterminal=0;
	static long g_time_kbest_extract=0;
	
	public Chart(int[] sentence_in, ArrayList<Model> models, int sent_id1)
	{   
		sentence = sentence_in;
		sent_len = sentence.length;
		l_models = models;
		l_bins = new Bin[sent_len][sent_len+1];		
		//sent_id = sent_id1;
	}

	//add un-translated wrds into the chart as item (with large cost)
	//TODO: grammar specific?
	public void seed(TMGrammar[] grs, ArrayList<Integer> default_nonterminals, String[] sentence_str){
		l_grammars = grs;
		l_dotcharts = new DotChart[l_grammars.length];//each grammar will have a dot chart
		for(int i=0; i<l_grammars.length; i++){			
			l_dotcharts[i]= new DotChart(sentence, l_grammars[i], this);
			l_dotcharts[i].seed();
		}
		//add un-translated words
		//TODO
		/*the transition cost for phrase model, arity penalty, word penalty are all zero, except the LM cost*/
		 for(int i=0; i< sent_len; i++){
	         for( int sym : default_nonterminals){//create a rule, but do not add into the grammar trie
	        	 String sym_str = Symbol.get_string(sym);//do not have []
	        	 int lhs = Symbol.add_non_terminal_symbol(sym_str);
	        	 int[] french = new int[1];
	        	 french[0]= Symbol.add_terminal_symbol(sentence_str[i]);
	        	 int[] english = new int[1];
	        	 english[0]= Symbol.add_terminal_symbol(sentence_str[i]);
	        	 float[] feat_scores = new float[1];
	        	 feat_scores[0]=0;
	        	 int arity=0;
	        	 Rule r =  new TMGrammar_Memory.Rule_Memory(lhs, english, Symbol.UNTRANS_SYM_ID, feat_scores, arity);	//TODO: change onwer	        	 
	        	 add_axiom(i, i+1, r);
	         }
		 }
		 System.out.println("####finisehd seeding");
	}
	
	//construct the hypergraph with the help from DotChart
	public Item expand(){
		//long start = System.currentTimeMillis();
		long time_step1=0,time_step2=0,time_step3=0,time_step4=0;		
		for(int width=1; width<=sent_len; width++){
			for(int i=0; i<=sent_len-width; i++){
				int j= i + width;
				//Support.write_log_line(String.format("Process span (%d, %d)",i,j), Support.DEBUG);
				
				//(1) expand the cell in dotchart
				//long start_step1= Support.current_time();
				//Support.write_log_line("Step 1: expance cell", Support.DEBUG);
				for(int k=0; k< l_grammars.length; k++){
					l_dotcharts[k].expand_cell(i,j);
				}			
				//Support.write_log_line(String.format("n_dotitem= %d",n_dotitem_added), Support.INFO);
				//time_step1 += Support.current_time()-start_step1;
				
				//(2) populate COMPLETE rules into Chart: the regular CKY part
				//long start_step2= Support.current_time();
				//Support.write_log_line("Step 2: add complte items into Chart", Support.DEBUG);
				for(int k=0; k<l_grammars.length; k++){
					if(l_grammars[k].filter_span(i, j, sent_len) && l_dotcharts[k].l_dot_bins[i][j]!=null){					
						for(DotItem dt: l_dotcharts[k].l_dot_bins[i][j].l_dot_items){
							if(dt.tnode.get_rule_bin()!=null){//have rules under this trienode									
								if(dt.tnode.get_rule_bin().get_arity()==0){//rules without any non-terminal
									ArrayList<Rule> l_rules = dt.tnode.get_rule_bin().get_sorted_rules();
									for(Rule rl : l_rules)										
										add_axiom(i,j,rl);									
								}else{//rules with non-terminal									
									if(Decoder.use_cube_prune==true)
										complete_cell_cube_prune(i,j,dt,dt.tnode.get_rule_bin());
									else
										complete_cell(i,j,dt,dt.tnode.get_rule_bin());//populate chart.bin[i][j] with rules from dotchart[i][j]
								}
							}
						}
					}													
				}
				//time_step2 += Support.current_time()-start_step2;
				
				//(3) process unary rules (e.g., S->X, NP->NN), just add these items in chart, assume acyclic
				//long start_step3= Support.current_time();
				//Support.write_log_line("Step 3: add unary items into Chart", Support.DEBUG);
				for(int k=0; k< l_grammars.length; k++){
					if(l_grammars[k].filter_span(i, j, sent_len))
						add_unary_items(l_grammars[k],i,j);//single-branch path
				}
				//time_step3 += Support.current_time()-start_step3;
				
				//(4) in dot_cell(i,j), add dot-items that start from the /complete/ superIterms in chart_cell(i,j)
				//long start_step4= Support.current_time();
				//Support.write_log_line("Step 4: init new dot-items that starts from complete items in this cell", Support.DEBUG);
				for(int k=0; k< l_grammars.length; k++){
					if(l_grammars[k].filter_span(i, j, sent_len)){
						l_dotcharts[k].start_dotitems(i,j);
					}
				}
				//time_step4 += Support.current_time()-start_step4;
				
				//(5) sort the items in the cell: for pruning purpose
				//Support.write_log_line(String.format("After Process span (%d, %d), called:= %d",i,j,n_called_compute_item), Support.INFO);
				if(l_bins[i][j]!=null){
					//l_bins[i][j].print_info(Support.INFO);
					l_bins[i][j].get_sorted_items();//this is required
					
					//sanity check with this cell
					int sum_d=0;
					double sum_c =0.0;
					double sum_total=0.0;
					for(Item t_item : l_bins[i][j].l_sorted_items){
						if(t_item.l_deductions!=null)
							sum_d += t_item.l_deductions.size();
						sum_c += t_item.best_deduction.best_cost;
						sum_total += t_item.est_total_cost;
					}
					//System.out.println(String.format("n_items =%d; n_deductions: %d; s_cost: %.3f; c_total: %.3f", l_bins[i][j].tbl_items.size(),sum_d,sum_c,sum_total));	
				}
								
				//print_info(Support.INFO);
			}
		}
		print_info(Support.INFO);
				
		//transition_final: setup a goal item, which may have many deductions
		if(l_bins[0][sent_len]!=null){
			transit_to_goal(l_bins[0][sent_len]);//update goal_bin				
			if(goal_bin.l_sorted_items.size()!=1){
				Support.write_log_line("warning: the goal_bin does not have exactly one item", Support.ERROR);
				System.exit(0);
			}
		}else{
			Support.write_log_line("No complet item in the cell(0,n)", Support.ERROR);
			System.exit(0);
		}
		
		//debug purpose
		//long sec_consumed = (System.currentTimeMillis() -start)/1000;
		//Support.write_log_line("######Expand time consumption: "+ sec_consumed, Support.INFO);
		//Support.write_log_line(String.format("Step1: %d; step2: %d; step3: %d; step4: %d", time_step1, time_step2, time_step3, time_step4), Support.INFO);
		
		/*Support.write_log_line(String.format("t_compute_item: %d; t_add_deduction: %d;", g_time_compute_item/1000,g_time_add_deduction/1000), Support.INFO);
		for(Model m: l_models){
			Support.write_log_line("Model cost: " + m.time_consumed/1000, Support.INFO);
		}*/

		//Support.write_log_line(String.format("t_lm: %d; t_score_lm: %d; t_check_nonterminal: %d", g_time_lm, g_time_score_sent, g_time_check_nonterminal), Support.INFO);
		//LMModel tm_lm = (LMModel)l_models.get(0);
		//Support.write_log_line(String.format("LM lookupwords1, step1: %d; step2: %d; step3: %d",tm_lm.time_step1,tm_lm.time_step2,tm_lm.time_step3),Support.INFO);
		//debug end				
		return (Item)goal_bin.l_sorted_items.get(0);
	}
	
	
	//agenda based extension: this is necessary in case more than two unary rules can be applied in topological order s->x; ss->s
	//for unary rules like s->x, once x is complete, then s is also complete
	@SuppressWarnings("unchecked") 
	private int add_unary_items(TMGrammar gr, int i, int j){
		Bin chart_bin = l_bins[i][j];
		if(chart_bin==null)	return 0;
		int res=0;
		
		ArrayList t_queue = new ArrayList(chart_bin.get_sorted_items());//init queue		
		while(t_queue.size()>0){
			Item it = (Item)t_queue.remove(0);
			//Support.write_log_line("Item lhs: "+it.lhs, Support.DEBUG);
			TrieNode child_tnode = gr.get_root().match_symbol(it.lhs);//match rule and complete part
			if(child_tnode != null && child_tnode.get_rule_bin()!=null && child_tnode.get_rule_bin().get_arity()==1){//have rules under this trienode					
				ArrayList<Item> l_ants = new ArrayList<Item>();
				l_ants.add(it);
				ArrayList<Rule> l_rules = child_tnode.get_rule_bin().get_sorted_rules();
				for(Rule rl : l_rules){								
					HashMap  tbl_states = compute_item(rl, l_ants, i, j);				
					Item res_item = add_deduction_in_chart(chart_bin, tbl_states, rl, i, j, l_ants);
					if(res_item!=null){
						t_queue.add(res_item);	
						res++;						
						//Support.write_log_line(String.format("process %d complete item",res), Support.DEBUG);
					}
				}
			}
		}
		return res;
	}
	
	
	//axiom is the source item: flat phrase, or phrase with zero-arity
	private void add_axiom(int i, int j, Rule rl){
		if(l_bins[i][j]==null)
			l_bins[i][j]=new Bin(this);
		HashMap  tbl_states = compute_item(rl, null, i, j);
		add_deduction_in_chart(l_bins[i][j], tbl_states, rl, i, j, null);
	}

	private Item add_deduction_in_chart(Bin this_bin, HashMap  tbl_states, Rule rl, int i, int j,  ArrayList<Item> ants){
		long start = Support.current_time();
		Item res=null;
		HashMap  item_state_tbl = (HashMap )tbl_states.get(ITEM_STATES_SYM_ID);
		double total_cost=((Double)tbl_states.get(TOTAL_COST_SYM_ID)).doubleValue();//total_cost=ADDITIVE_COST+TRANSITION_COST+bonus
		double transition_cost=((Double)tbl_states.get(TRANSITION_COST_SYM_ID)).doubleValue();//non-stateless transition cost 
		double additive_cost=((Double)tbl_states.get(ADDITIVE_COST_SYM_ID)).doubleValue();
		//double bonus = ((Double)tbl_states.get(BONUS)).doubleValue();//not used
		double total_cost2=transition_cost+additive_cost; //total_cost2=total_cost-bonus;
		if(this_bin.should_prune(total_cost)==false){
			Deduction dt = new Deduction(rl,total_cost2,transition_cost,ants);
			Item item = new Item(i,j,rl.lhs,item_state_tbl,dt, total_cost);
			this_bin.add_deduction_in_bin(item);
			//Support.write_log_line(String.format("add an deduction with arity %d", rl.arity),Support.DEBUG);
			//rl.print_info(Support.DEBUG);
			res=item;
		}else{
			n_prepruned++;
			//Support.write_log_line(String.format("Prepruned an deduction with arity %d", rl.arity),Support.INFO);
			//rl.print_info(Support.INFO);
			res= null;
		}
		g_time_add_deduction += Support.current_time()-start;
		return res;
	}
	
	/*add complete Items in Chart
	 * pruning inside this function*/
	private void complete_cell(int i, int j, DotItem dt, RuleBin rb){
		if(l_bins[i][j]==null)
			l_bins[i][j]=new Bin(this);
		
		ArrayList<Rule> l_rules = rb.get_sorted_rules();
		//System.out.println(String.format("Complet_cell is called, n_rules: %d ", l_rules.size()));
		for(Rule rl : l_rules){		
			if(rb.get_arity()==1){				
				SuperItem super_ant1 = (SuperItem)dt.l_ant_super_items.get(0);
				//System.out.println(String.format("Complet_cell, size %d ", super_ant1.l_items.size()));
				//rl.print_info(Support.DEBUG);
				for(Item it_ant1: super_ant1.l_items){
					ArrayList<Item> l_ants = new ArrayList<Item>();
					l_ants.add(it_ant1);
					HashMap  tbl_states = compute_item(rl, l_ants, i, j);			
					add_deduction_in_chart(l_bins[i][j], tbl_states, rl, i, j, l_ants);
				}
			}else if(rb.get_arity()==2){
				SuperItem super_ant1 = (SuperItem)dt.l_ant_super_items.get(0);
				SuperItem super_ant2 = (SuperItem)dt.l_ant_super_items.get(1);
				//System.out.println(String.format("Complet_cell, size %d * %d ", super_ant1.l_items.size(),super_ant2.l_items.size()));
				//rl.print_info(Support.DEBUG);
				for(Item it_ant1: super_ant1.l_items){
					for(Item it_ant2: super_ant2.l_items){
						//System.out.println(String.format("Complet_cell, ant1(%d, %d), ant2(%d, %d) ",it_ant1.i,it_ant1.j,it_ant2.i,it_ant2.j ));
						ArrayList<Item> l_ants = new ArrayList<Item>();						
						l_ants.add(it_ant1);
						l_ants.add(it_ant2);
						HashMap  tbl_states = compute_item(rl, l_ants, i, j);				
						add_deduction_in_chart(l_bins[i][j], tbl_states, rl, i, j,l_ants);
					}					
				}
			}else{
				System.out.println("Sorry, we can only deal with rules with at most TWO non-terminals");
				System.exit(0);
			}
		}		
	}
		
	/*add complete Items in Chart
	 * pruning inside this function*/
	//TODO: our implementation do the prunining for each DotItem under each grammar, not aggregated as in the python version
	@SuppressWarnings("unchecked") 
	private void complete_cell_cube_prune(int i, int j, DotItem dt, RuleBin rb){
		if(l_bins[i][j]==null)
			l_bins[i][j]=new Bin(this);
		Bin bin_ij=l_bins[i][j];
		PriorityQueue<CubePruneState> heap_cands=new PriorityQueue<CubePruneState>();// in the paper, it is called cand[v]		
		HashMap  cube_state_tbl = new HashMap ();//rememeber which state has been explored
		
		ArrayList<Rule> l_rules = rb.get_sorted_rules();
		if(l_rules==null || l_rules.size()<=0)
			return;
			
		//seed the heap with best item
		Rule cur_rl = (Rule)l_rules.get(0);
		ArrayList<Item> l_cur_ants = new ArrayList<Item>();
		for(SuperItem si : dt.l_ant_super_items)
			l_cur_ants.add(si.l_items.get(0)); //TODO: si.l_items must be sorted
		HashMap  tbl_states = compute_item(cur_rl, l_cur_ants, i, j);
		
		int[] ranks = new int[1+dt.l_ant_super_items.size()];//rule, ant items
		for(int d=0; d<ranks.length; d++)
			ranks[d]=1;
		
		CubePruneState best_state = new CubePruneState(tbl_states, ranks, cur_rl, l_cur_ants);
		heap_cands.add(best_state);
		cube_state_tbl.put(best_state.get_signature(),1);
		//cube_state_tbl.put(best_state,1);
		
		//extend the heap
		Rule old_rl=null;
		Item old_item=null;
		int tem_c=0;
		while(heap_cands.size()>0){
			tem_c++;
			CubePruneState cur_state = heap_cands.poll();
			cur_rl = cur_state.rule;
			l_cur_ants = new ArrayList<Item>(cur_state.l_ants);//critical to create a new list			
			//cube_state_tbl.remove(cur_state.get_signature());//TODO, repeat
			add_deduction_in_chart(bin_ij, cur_state.tbl_states, cur_state.rule, i, j,cur_state.l_ants);//pre-pruning inside this function
			
			//if the best state is pruned, then all the remaining states should be pruned away
			if(((Double)cur_state.tbl_states.get(TOTAL_COST_SYM_ID)).doubleValue()>bin_ij.cut_off_cost+Decoder.fuzz1){
				//n_prepruned += heap_cands.size();
				n_prepruned_fuzz1 += heap_cands.size();
				/*if(heap_cands.size()>1){
					gtem++;
					System.out.println("gtem is " +gtem + "; size:" + heap_cands.size());
				}*/
				break;
			}
			//extend the cur_state
			for(int k=0; k<cur_state.ranks.length; k++){
				//GET new_ranks
				int[] new_ranks = new int[cur_state.ranks.length];
				for(int d=0; d< cur_state.ranks.length; d++)
					new_ranks[d]=cur_state.ranks[d];
				new_ranks[k]=cur_state.ranks[k]+1;
				
				String new_sig = CubePruneState.get_signature(new_ranks);
				//check condtion
				if( (cube_state_tbl.containsKey(new_sig)==true) 
				  || (k==0 && new_ranks[k] > l_rules.size())
				  || (k!=0 && new_ranks[k] > dt.l_ant_super_items.get(k-1).l_items.size())
				  ){					
					continue;
				}
				
				if(k==0){//slide rule
					old_rl = cur_rl;
					cur_rl = (Rule)l_rules.get(new_ranks[k]-1);
				}else{//slide ant
					old_item = l_cur_ants.get(k-1);//conside k==0 is rule
					l_cur_ants.set(k-1, dt.l_ant_super_items.get(k-1).l_items.get(new_ranks[k]-1));
				}
				
				HashMap  tbl_states2 = compute_item(cur_rl, l_cur_ants, i, j);
				CubePruneState t_state = new CubePruneState(tbl_states2, new_ranks, cur_rl, l_cur_ants);
				
				//add state into heap
				//cube_state_tbl.put(t_state,1);		
				cube_state_tbl.put(new_sig,1);
						
				if(((Double)tbl_states2.get(TOTAL_COST_SYM_ID)).doubleValue()<bin_ij.cut_off_cost+Decoder.fuzz2){
					heap_cands.add(t_state);		
				}else{
					//n_prepruned +=1;
					n_prepruned_fuzz2 +=1;
				}
				//recover
				if(k==0){//rule
					cur_rl = old_rl;
				}else{//ant
					l_cur_ants.set(k-1, old_item);
				}
			}		
		}	
	}
	private static class CubePruneState implements Comparable 
	{
		int[] ranks;
		HashMap  tbl_states;
		Rule rule;
		ArrayList<Item> l_ants;
		public CubePruneState(HashMap  st, int[] ranks_in, Rule rl, ArrayList<Item> ants){
			tbl_states = st;
			ranks = ranks_in;
			rule=rl;
			l_ants=new ArrayList<Item>(ants);//create a new vector is critical, because l_cur_ants will change later
		}
		public CubePruneState(int[] ranks_in){//fake: for equals			
			ranks = ranks_in;		
		}
		public static String get_signature(int[] ranks2){
			StringBuffer res = new StringBuffer();
			if(ranks2!=null)
				for(int i=0; i<ranks2.length; i++){
					res.append(" ");
					res.append(ranks2[i]);
				} 
			return res.toString();
		}
		
		public String get_signature(){
			return get_signature(ranks);
		}
		
		
		/*
		//compare by signature
		public boolean equals(Object other) throws ClassCastException {
			  if (!(other instanceof CubePruneState))
			      throw new ClassCastException("An CubePruneState object expected.");
			  CubePruneState another=(CubePruneState)other;
			  if(this.ranks==null || another.ranks==null)
				  return false;
			  if(this.ranks.length != another.ranks.length)
				  return false;
			  for(int i=0; i<this.ranks.length; i++)
				  if(this.ranks[i]!=another.ranks[i])
						return false;
				return true;
		}
		
		public int hashCode(){
			int hash = 7;
			for(int i=0; i<ranks.length; i++)
				hash = 31 * hash + ranks[i];
			return hash;
			//return ranks.hashCode();//this does not work
		}
		*/
		//natual order by cost
		public int compareTo(Object another) throws ClassCastException {
		    if (!(another instanceof CubePruneState))
		      throw new ClassCastException("An CubePruneState object expected.");
		    if((Double)this.tbl_states.get(TOTAL_COST_SYM_ID) < (Double)((CubePruneState)another).tbl_states.get(TOTAL_COST_SYM_ID))
		    	return -1;
		    else if((Double)this.tbl_states.get(TOTAL_COST_SYM_ID) == (Double)((CubePruneState)another).tbl_states.get(TOTAL_COST_SYM_ID))
		    	return 0;
		    else
		    	return 1; 
		}
	}
	
	//compute cost and the states of this item
	//returned ArrayList: total_cost, additive_cost, transition_cost, bonus, list of states
	@SuppressWarnings("unchecked") 
	private HashMap  compute_item(Rule rl, ArrayList<Item> ants_items, int i, int j){
		long start = Support.current_time();
		n_called_compute_item++;
		double additive_cost = rl.statelesscost;
	
		ArrayList ants_states = new ArrayList();
		if(ants_items!=null)
			for(Item ant: ants_items){
				additive_cost += ant.best_deduction.best_cost;
				ants_states.add(ant.tbl_states);
			}		
		HashMap  res_tbl_item_states = new HashMap ();
		double transition_cost = 0.0;//transition cost: the sum of costs of non-stateless/non-contextual models
		double bonus = 0.0;
		for( Model m: l_models){//not just the stateful ones because anything might have a prior
			long start2 = Support.current_time();
			if(m.stateless==false && m.contextual==false){				
				HashMap  tem_tbl = m.transition(rl, ants_states, i, j, 0);
				transition_cost += ((Double)tem_tbl.get(TRANSITION_COST_SYM_ID)).doubleValue()*m.weight;
				HashMap  tem_states_tbl = (HashMap )tem_tbl.get(ITEM_STATES_SYM_ID);
				bonus += ((Double)tem_tbl.get(BONUS_SYM_ID))*m.weight;//future cost estimation
				if(tem_states_tbl!=null){
					res_tbl_item_states.putAll(tem_states_tbl);
				}
			}else{
				bonus +=0; //TODO: future cost estimation is zero
			}
			m.time_consumed += Support.current_time()-start2;
		}
		double total_cost = additive_cost + transition_cost + bonus;

		HashMap  res = new HashMap ();
		res.put(TOTAL_COST_SYM_ID,total_cost); 
		res.put(ADDITIVE_COST_SYM_ID,additive_cost);
		res.put(TRANSITION_COST_SYM_ID, transition_cost);
		//res.put(BONUS,bonus); //NOT USED
		res.put(ITEM_STATES_SYM_ID,res_tbl_item_states);
		//System.out.println("t_cost: " + total_cost + "; a_cost: " + additive_cost +" ;t_cost: " + transition_cost +"; bonus: " + bonus );
		g_time_compute_item += Support.current_time()-start;
		return res;
	}
	
	//add all the items with GOAL_SYM state into the goal bin
	//so, the goal bin has only one Item, which itself has many deductions
	@SuppressWarnings("unchecked") 
	private void transit_to_goal(Bin bin1){//bin1: the bin[0][n]
		goal_bin.l_sorted_items = new ArrayList();
		Item goal_item = null;		
        for(Item item1 : bin1.get_sorted_items()){
            if( item1.lhs==Symbol.GOAL_SYM_ID){
                double cost = item1.best_deduction.best_cost;
                double final_transition_cost = 0.0;
                for(Model  m_i : l_models){
                    double mdcost = m_i.finaltransition(item1.tbl_states);
                    final_transition_cost += mdcost*m_i.weight;
                }
                ArrayList l_ants = new ArrayList();
                l_ants.add(item1);
                Deduction dt = new Deduction(null, cost+final_transition_cost, final_transition_cost, l_ants); 
                //Support.write_log_line(String.format("Goal item, total_cost: %.3f; ant_cost: %.3f; final_tran: %.3f; ",cost+final_transition_cost,cost,final_transition_cost),  Support.INFO);
                if(goal_item==null){
                	goal_item = new Item(0,sent_len+1,Symbol.GOAL_SYM_ID,null,dt, cost+final_transition_cost);
                	goal_bin.l_sorted_items.add(goal_item);
                }else{
                	goal_item.add_deduction_in_item(dt);
                	if(goal_item.best_deduction.best_cost>dt.best_cost)
                		goal_item.best_deduction=dt;
                }
            } 
        }
        Support.write_log_line(String.format("Goal item, best cost is %.3f",goal_item.best_deduction.best_cost),  Support.INFO);
        goal_bin.ensure_sorted();
    }
	
	
//	#################### Bin class
	/*Bin is a cell in parsing terminology*/
	//the Bin create Items, but not all Items will be used in the future in the hyper-graph	
	public class Bin
	{
		/*we need always maintain the priority queue (worst first), so that we can do prunning effieciently
		 *On the other hand, we need the l_sorted_items only when necessary*/
		
		//NOTE: MIN-HEAP, we put the worst-cost item at the top of the heap by manipulating the compare function
		//heap_items: the only purpose is to help deecide which items should be removed from tbl_items during pruning 
		@SuppressWarnings("unchecked") 
		private PriorityQueue<Item> heap_items =  new PriorityQueue<Item>(1, Item.NegtiveCostComparator);//TODO: initial capacity?
		private HashMap  tbl_items=new HashMap (); //to maintain uniqueness of items		
		private HashMap  tbl_super_items=new HashMap ();//signature by lhs
		private ArrayList<Item> l_sorted_items=null;//sort values in tbl_item_signature, we need this list whenever necessary
		
		//pruning parameters
		public double best_item_cost = IMPOSSIBLE;//remember the cost of the best item in the bin
		public double cut_off_cost = IMPOSSIBLE; //cutoff=best_item_cost+relative_threshold
		
		int dead_items=0;//num of corrupted items in heap_items, note that the item in tbl_items is always good
		Chart p_chart =null;
		
		public Bin(Chart chart){
			p_chart=chart;
		}
		
		public void print_info(int level){
			Support.write_log_line(String.format("#### Stat of Bin, n_items=%d, n_super_items=%d",tbl_items.size(),tbl_super_items.size()), level);			
			ensure_sorted();
			for(Item it : l_sorted_items)
	        	it.print_info(level);
		}
		
		public boolean should_prune(double total_cost){
			//Support.write_log_line("cut_off_cost: "+cut_off_cost +" real: "+ total_cost, Support.INFO);
			return(total_cost>=cut_off_cost);			
		}
		
		private double find_min(double a, double b){
			return (a<=b)?a :b;
		}

		private void run_pruning(){
			//Support.write_log_line(String.format("Pruning: heap size: %d; n_dead_items: %d", heap_items.size(),dead_items ), Support.DEBUG);
			if(heap_items.size()==dead_items){//TODO:clear the heap, and reset dead_items??	
				heap_items.clear();
				dead_items=0;
				return;
			}
			while(heap_items.size()-dead_items>Decoder.max_n_items //bin limit pruning 
				  ||  heap_items.peek().est_total_cost>=cut_off_cost){//relative threshold pruning
				Item worst_item = (Item)heap_items.poll();
				if(worst_item.is_dead==true)//clear the corrupted item
					dead_items--;
				else{
					tbl_items.remove(worst_item.get_signature());//always make tbl_items current					
					p_chart.n_pruned++;					
					//Support.write_log_line(String.format("Run_pruning: %d; cutoff=%.3f, realcost: %.3f",p_chart.n_pruned,cut_off_cost,worst_item.est_total_cost), Support.INFO);
				}					
			}
			if(heap_items.size()-dead_items==Decoder.max_n_items){//TODO:??	
				cut_off_cost = find_min(cut_off_cost, heap_items.peek().est_total_cost+EPSILON);
			}			
		}
		
		 //lzf, get a sorted list of Items in the bin, and also make sure the list of items in any SuperItem is sorted, this will be called only necessary, which means that the list is not always sorted
		//mainly needed for goal_bin and cube-pruning
		@SuppressWarnings("unchecked") 
		private void ensure_sorted(){
	        if(l_sorted_items==null){
	        	//get a sorted items ArrayList
	        	Object[] t_col = tbl_items.values().toArray();
	        	Arrays.sort(t_col);
	        	l_sorted_items = new ArrayList<Item>();
	        	for(int c=0; c<t_col.length;c++)
	        		l_sorted_items.add((Item)t_col[c]);
	        	//TODO: we cannot create new SuperItem here because the DotItem link to them
	        	
	        	//update tbl_super_items
	        	ArrayList<SuperItem> tem_list = new ArrayList<SuperItem>(tbl_super_items.values());
	        	for(SuperItem t_si : tem_list)
	        		t_si.l_items.clear();
	        	
	            for(Item it :  l_sorted_items){
	            	SuperItem si = ((SuperItem)tbl_super_items.get(it.lhs));
	            	if(si==null){//sanity check
	            		Support.write_log_line("Does not have super Item, have to exist", Support.ERROR);
	            		System.exit(0);	            	
	            	}
	            	si.l_items.add(it);
	            }
	            
	            ArrayList<Integer> to_remove = new ArrayList<Integer> ();
	          //note: some SuperItem may not contain any items any more due to pruning
                for (Iterator e = tbl_super_items.keySet().iterator(); e.hasNext();) {
                    //Integer k = (Integer)e.nextElement();
                	Integer k = (Integer)e.next();                	
                    if(((SuperItem)tbl_super_items.get(k)).l_items.size()<=0){
                    	to_remove.add(k);//note that: we cannot directly do the remove, because it will throw ConcurrentModificationException
                            //System.out.println("have zero items in superitem " + k);
                        //tbl_super_items.remove(k);
                    }
                }
                for(Integer t : to_remove)
                	tbl_super_items.remove(t);
	        }
		}
		
		public Item getitem(int pos){//not used 
	        ensure_sorted();
	        return l_sorted_items.get(pos);
	    }

		public ArrayList<Item> get_sorted_items(){
	        ensure_sorted();
	        return l_sorted_items;
		}
		
		public HashMap  get_sorted_super_items(){
			ensure_sorted();
	        return tbl_super_items;
		}    
		
		//this function is called only there is no such item in the tbl
		@SuppressWarnings("unchecked") 
		private void add_item(Item item){
			tbl_items.put(item.get_signature(), item);//add/replace the item	
			l_sorted_items=null; //reset the list
			heap_items.add(item);
			
			//since l_sorted_items==null, this is not necessary because we will always call ensure_sorted to reconstruct the tbl_super_items
			//add a super-items if necessary
			SuperItem si = (SuperItem)tbl_super_items.get(item.lhs);
			if(si==null){
				si = new SuperItem(item.lhs);			
				tbl_super_items.put(item.lhs, si);
			}
			si.l_items.add(item);					
			
			if(item.est_total_cost<best_item_cost){
				best_item_cost = item.est_total_cost;
			}
		}
			
		private Item get_old_item(Item new_item){
			return (Item)tbl_items.get(new_item.get_signature());
		} 
		
		/* each item has a list of deductions
		 * need to check whether the item is already exist, if yes, just add the deductions*/
		public boolean add_deduction_in_bin( Item new_item){
			boolean res=false;
			Item old_item = get_old_item(new_item);
			
			if(old_item!=null){//have an item with same states, combine items
				p_chart.n_merged++;
				if(new_item.est_total_cost<old_item.est_total_cost){
					//the position of old_item in the heap_items may change, basically, we should remove the old_item, and re-insert it (linear time, this is too expense)
					old_item.is_dead=true;//heap_items.remove(old_item);
					dead_items++;
					new_item.add_deductions_in_item(old_item.l_deductions);
					add_item(new_item);	//this will update the HashMap , so that the old_item is destroyed				
					res=true;
				}else{
					old_item.add_deductions_in_item(new_item.l_deductions);
				}
			}else{//first time item
				p_chart.n_added++;
				add_item(new_item);
				res=true;
			}			
			cut_off_cost = find_min(best_item_cost+Decoder.relative_threshold, IMPOSSIBLE);			
			run_pruning();
			return res;
		}
	}

	/*list of items that have the same lhs but may have different LM states
	 * In Hiero: this is called PseudoBin*/
	public class SuperItem{
		int lhs;
		ArrayList<Item> l_items=new ArrayList<Item>();
		public SuperItem(int lhs_in){
			lhs = lhs_in;			
		}
	}
	
}


