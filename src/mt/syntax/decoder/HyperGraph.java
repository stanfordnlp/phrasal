package mt.syntax.decoder;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

import mt.syntax.util.FileUtility;
import mt.syntax.decoder.TMGrammar.Rule;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

/*this class implement HyperGraph data structure, and k-best extraction algorithm over a hyper-graph
 *to seed the kbest extraction, the deductions at the leaf item should be sorted (in our rule, means the rulebin should be sorted)*/
@SuppressWarnings("unchecked")
public class HyperGraph {
	public Item goal_item=null;

	public HyperGraph(Item g_item){
		goal_item = g_item;
	}

	//#################### item class ##########################
	/*in and/or graph, Item is a "or" node/vertex
	*it remembers all possible deduction that leads to the same symbol (e.g., NP)
	*state: lhs and edge ngram*/
	public static class Item implements Comparable
	{
		public int i, j;
		public ArrayList<Deduction> l_deductions=null;//each deduction is a "and" node
		public Deduction best_deduction=null;
		public int lhs; //this is the symbol like: NP, VP, and so on
		public HashMap tbl_states; //remember the state required by each model, for example, edge-ngrams for LM model

		//######### auxiluary variables, no need to store on disk
		private String signature=null;//signature of this item: lhs, states
		static String SIG_SEP = " -S- "; //seperator for state in signature

		//######## for pruning purpose
		public boolean is_dead=false;
		public double est_total_cost=0.0; //it includes the bonus cost

		//##########nbest-related stuff: one hyp is a derivation
		ArrayList l_nbest=new ArrayList();//ArrayList of DerivationState, in the paper is: D(^) [v]
		private PriorityQueue<DerivationState> heap_cands=null;//remember frontier states, in the paper, it is called cand[v]
		private HashMap  derivation_tbl = null;//rememeber which DerivationState has been explored
		private HashMap  nbest_str_tbl =null;//maintaining this tbl at each item (instead of at goal item only) is critical to fastly get unique nbest extraction

		public Item(int i_in, int j_in, int lhs_in, HashMap  states_in, Deduction init_deduction, double est_total_cost_in){
			i = i_in;
			j= j_in;
			lhs = lhs_in;
			tbl_states = states_in;
			est_total_cost=est_total_cost_in;
			add_deduction_in_item(init_deduction);
		}

		public Item(int i_in, int j_in, int lhs_in,  ArrayList<Deduction> l_deductions_in, Deduction best_deduction_in, HashMap  states_in){
			i = i_in;
			j= j_in;
			lhs = lhs_in;
			l_deductions = l_deductions_in;
			best_deduction = best_deduction_in;
			tbl_states = states_in;
		}

		public void add_deduction_in_item(Deduction dt){
			if(l_deductions==null)l_deductions = new ArrayList<Deduction>();
			l_deductions.add(dt);
			if(best_deduction==null || best_deduction.best_cost>dt.best_cost) best_deduction=dt;
		}

		public void add_deductions_in_item(ArrayList<Deduction> l_dt){
			for(Deduction dt : l_dt) add_deduction_in_item(dt);
		}

		//########### nbest ##############################
		private DerivationState lazy_k_best_extract(int next_n, boolean extract_unique_nbest, boolean extract_nbest_tree){
			DerivationState res=null;
			if(heap_cands==null){
				get_candidates(extract_unique_nbest, extract_nbest_tree);
			}
			int t_added =0; //sanity check
			while(l_nbest.size()<next_n){
				if(heap_cands.size()>0){
					res = heap_cands.poll();
					//derivation_tbl.remove(res.get_signature());//TODO: should remove? note that two state may be tied because the cost is the same
					if(extract_unique_nbest==true){
						String res_str = res.get_hyp(extract_nbest_tree,null,null);
						if(nbest_str_tbl.containsKey(res_str)==false){
							l_nbest.add(res);
							nbest_str_tbl.put(res_str,1);
						}
					} else{
						l_nbest.add(res);
					}
					lazy_next(res, extract_unique_nbest, extract_nbest_tree);//always extend the last, add all new hyp into heap_cands

					//debug: sanity check
					t_added++;
					if( extract_unique_nbest==false && t_added>1){//this is possible only when extracting unique nbest
						Support.write_log_line("In lazy_k_best_extract, add more than one time, nex_n is " + next_n, Support.ERROR);
						System.exit(0);
					}
				}else{
					break;
				}
			}
			return res;
		}

		//last: the last item that has been selected, we need to extend it
		//get the next hyp at the "last" deduction
		private void lazy_next(DerivationState last,boolean extract_unique_nbest, boolean extract_nbest_tree){
			if(last.p_edge.l_ant_items==null)
				return;
			for(int i=0; i < last.p_edge.l_ant_items.size();i++){//slide the ant item
				Item it = (Item) last.p_edge.l_ant_items.get(i);
				int[] new_ranks = new int[last.ranks.length];
				for(int c=0; c<new_ranks.length;c++)
					new_ranks[c]=last.ranks[c];

				new_ranks[i]=last.ranks[i]+1;
				String new_sig = DerivationState.get_signature(last.p_edge, new_ranks, last.deduction_pos);

				//why duplicate, e.g., 1 2 + 1 0 == 2 1 + 0 1
				if(derivation_tbl.containsKey(new_sig)==true){
					continue;
				}
				it.lazy_k_best_extract(new_ranks[i], extract_unique_nbest,extract_nbest_tree);
				if(new_ranks[i]<=it.l_nbest.size()//exist the new_ranks[i] derivation
				  /*&& "t" is not in heap_cands*/ ){//already checked before, check this condition
					double cost= last.cost - ((DerivationState)it.l_nbest.get(last.ranks[i]-1)).cost+((DerivationState)it.l_nbest.get(new_ranks[i]-1)).cost;
					DerivationState t = new DerivationState(last.p_edge, new_ranks, cost, last.deduction_pos);
					heap_cands.add(t);
					derivation_tbl.put(new_sig,1);
				}
			}
		}

		//get my best derivation, add 1best for all my children, used by get_candidates
		private DerivationState get_best_derivation(Deduction hyper_edge, int deduct_pos,  boolean extract_unique_nbest,boolean extract_nbest_tree){
			int[] ranks;
			double cost=0;
			if(hyper_edge.l_ant_items==null){//axiom
				ranks=null;
				cost=hyper_edge.best_cost;//this Deduction only have one single translation for the terminal symbol
			}else{//best combination
				ranks = new int[hyper_edge.l_ant_items.size()];
				for(int i=0; i < hyper_edge.l_ant_items.size();i++){
					ranks[i]=1;//rank start from one
					//add the 1best for my children
					Item child_it = (Item) hyper_edge.l_ant_items.get(i);
					child_it.lazy_k_best_extract(ranks[i], extract_unique_nbest,extract_nbest_tree);
				}
				cost=hyper_edge.best_cost;//TODO???????????????
			}
			DerivationState t = new DerivationState(hyper_edge, ranks, cost,deduct_pos );
			return t;
		}

		//this is the seeding function, for example, it will get down to the leaf, and sort the terminals
		//get a 1best from each deduction, and add them into the heap_cands
		private void get_candidates(boolean extract_unique_nbest,boolean extract_nbest_tree){
			heap_cands=new PriorityQueue<DerivationState>();
			derivation_tbl = new HashMap ();
			if(extract_unique_nbest==true)
				nbest_str_tbl=new HashMap ();
			//sanity check
			if(l_deductions==null){
				System.out.println("Error, l_deductions is null in get_candidates, must be wrong");
				System.exit(0);
			}
			int pos=0;
			for(Deduction hyper_edge : l_deductions){
				DerivationState t = get_best_derivation(hyper_edge,pos, extract_unique_nbest, extract_nbest_tree);
//				why duplicate, e.g., 1 2 + 1 0 == 2 1 + 0 1 , but here we should not get duplicate

				//sanity check
				if(derivation_tbl.containsKey(t.get_signature())==false){
					heap_cands.add(t);
					derivation_tbl.put(t.get_signature(),1);
				}else{
					System.out.println("Error: get duplicate derivation in get_candidates, this should not happen");
					System.out.println("signature is " + t.get_signature());
					System.out.println("l_deduction size is " + l_deductions.size());
					System.exit(0);
				}
				pos++;
			}

//			TODO: if tem.size is too large, this may cause unnecessary computation, we comment the segment to accormodate the unique nbest extraction
			/*if(tem.size()>global_n){
				heap_cands=new PriorityQueue<DerivationState>();
				for(int i=1; i<=global_n; i++)
					heap_cands.add(tem.poll());
			}else
				heap_cands=tem;
			*/
		}
		//##########end nbest###############################

		public void print_info(int level){
			Support.write_log_line(String.format("lhs: %s; cost: %.3f",lhs, best_deduction.best_cost), level);
		}


		//signature of this item: lhs, states (we do not need i, j)
		public String get_signature(){
			if(signature!=null)
				return signature;
			StringBuffer res = new StringBuffer();
			res.append(lhs);
			for(Integer st_name : Symbol.l_model_state_names){
				int[] st = (int[])tbl_states.get(st_name);//TODO assume the state is an int[] array
				if(st!=null){
					res.append(SIG_SEP);
					for(int i=0; i<st.length; i++){
						if(true/* st[i]!=Symbol.NULL_RIGHT_LM_STATE_SYM_ID &&
						   st[i]!=Symbol.NULL_LEFT_LM_STATE_SYM_ID &&
						   st[i]!=Symbol.LM_STATE_OVERLAP_SYM_ID*/){//TODO: equivalnce: number of <null> or <bo>?
							res.append(st[i]);//the symbol id
							if(i<st.length-1)res.append(" ");
						}
					}
				}else{System.out.println("state is null"); System.exit(0);}
			}
			signature=res.toString();
			//Support.write_log_line(String.format("Signature is %s", res), Support.INFO);
			return signature;
		}

		//the state_str does not lhs, it contain the original words (not symbol id)
		public static String get_string_from_state_tbl(HashMap tbl){
			StringBuffer res = new StringBuffer();
			for(int t=0; t<Symbol.l_model_state_names.size(); t++){
				Integer st_name = Symbol.l_model_state_names.get(t);
				int[] st = (int[])tbl.get(st_name);//TODO assume the state is an int[] array
				if(st!=null){
					res.append(Symbol.get_string(st));
					if(t<Symbol.l_model_state_names.size()-1) res.append(SIG_SEP);
				}else{System.out.println("state is null"); System.exit(0);}
			}
			return res.toString();
		}

		//the state_str does not lhs, it contain the original words (not symbol id)
		public static HashMap get_state_tbl_from_string(String state_str){
			HashMap res =new HashMap();
			String[] states = state_str.split(SIG_SEP);
			for(int i=0; i<Symbol.l_model_state_names.size(); i++){
				String[] state_wrds = states[i].split("\\s+");
				Integer st_name = Symbol.l_model_state_names.get(i);
				res.put(st_name, Symbol.get_terminal_ids(state_wrds));//TODO assume the state is an int[] array
			}
			return res;
		}

		//sort by est_total_cost: for prunning purpose
		public int compareTo(Object anotherItem) throws ClassCastException {
		    if (!(anotherItem instanceof Item))
		      throw new ClassCastException("An Item object expected.");
		    if(this.est_total_cost < ((Item)anotherItem).est_total_cost)
		    	return -1;
		    else if(this.est_total_cost == ((Item)anotherItem).est_total_cost)
		    	return 0;
		    else
		    	return 1;
		}

		public static Comparator NegtiveCostComparator = new Comparator() {
		    public int compare(Object item1, Object item2) {
		      double cost1=  ((Item) item1).est_total_cost;
		      double cost2=  ((Item) item2).est_total_cost;
		      if(cost1 > cost2)
			    	return -1;
			    else if(cost1==cost2)
			    	return 0;
			    else
			    	return 1;
		    }
		 };

	}


//	#################### Deduction class #########################################
	/*in and/or graph, this is a and node, or a hyper-arc (without including the head vertex/item)*/
	public static class Deduction
	{	//should remember two costs: best_total_cost, and transition cost
		//double non_stateless_transition_cost=0.0;//this remember the non-stateless cost assocated with the rule and the previous items (which have same states)
		public double best_cost=Symbol.IMPOSSIBLE_COST;//the 1-best cost of all possible derivation: best costs of ant items + non_stateless_transition_cost + r.statelesscost
		private Rule rule;
		//if(l_ant_items==null), then this shoud be the terminal rule
		private ArrayList<Item> l_ant_items=null; //ant items. In comparison, in a derivation, the parent should be the sub-derivation of the tail of the hyper-arc

		public Deduction(Rule rl, double total_cost, double non_stateless_cost, ArrayList<Item> ant_items){
			best_cost=total_cost;
			//non_stateless_transition_cost=non_stateless_cost;
			rule=rl;
			l_ant_items=ant_items;
		}

		public Rule get_rule(){return rule;}
		public ArrayList<Item> get_ant_items(){return l_ant_items;}
	}



//########################################## kbest extraction algorithm ##########################
	public void lazy_k_best_extract(ArrayList<Model> l_models, int global_n, boolean extract_unique_nbest, int sent_id, BufferedWriter out, boolean extract_nbest_tree, boolean add_combined_score){
		//long start = System.currentTimeMillis();
		if(goal_item==null)
			return;

		BufferedWriter out2=null;
		if(out==null)
			out2 = new BufferedWriter(new OutputStreamWriter(System.out));
		else
			out2 = out;

		int next_n=0;
		while(true){
			DerivationState cur = goal_item.lazy_k_best_extract(++next_n,extract_unique_nbest,extract_nbest_tree);//global_n is not used at all
			if( cur==null
				|| goal_item.l_nbest.size()<next_n //do not have more hypthesis
				|| goal_item.l_nbest.size()>global_n)
						break;

			//get individual model cost
			Item true_item = (Item)cur.p_edge.l_ant_items.get(0);//goal_item only has one ant item
			double[] model_cost = new double[l_models.size()];
			FileUtility.write_lzf(out2,sent_id + " ||| ");
			String str_hyp_numeric = ((DerivationState)true_item.l_nbest.get(cur.ranks[0]-1)).get_hyp(extract_nbest_tree, model_cost,l_models);
			String str_hyp_str = convert_hyp_2_string(cur, l_models, str_hyp_numeric, extract_nbest_tree, add_combined_score, model_cost, true_item);

			//print the str
			FileUtility.write_lzf(out2,str_hyp_str);
			FileUtility.write_lzf(out2,"\n");
			//Support.write_log_line(String.format("nbest: %d;", goal_item.l_nbest.size()) , Support.INFO);
		}
		//g_time_kbest_extract += System.currentTimeMillis()-start;
		//Support.write_log_line("time_kbest_extract: "+ Chart.g_time_kbest_extract, Support.INFO);
	}


	private static String convert_hyp_2_string(DerivationState cur, ArrayList<Model> l_models, String str_hyp_numeric, boolean extract_nbest_tree, boolean add_combined_score, double[] model_cost,  Item true_item){
		String[] tem = str_hyp_numeric.split("\\s+");
		StringBuffer str_hyp =new StringBuffer();
		//int t=0;//TODO: consider_start_sym
		int t=0;
		for(; t<tem.length; t++){
			tem[t] = tem[t].trim();
			if(extract_nbest_tree==true && ( tem[t].startsWith("(") || tem[t].endsWith(")"))){//tree tag
				if(tem[t].startsWith("(")==true){
					String tag = Symbol.get_string(Integer.valueOf(tem[t].substring(1)));
					if(tag.compareTo("PHRASE")==0)//TODO
						tag = "X";
					str_hyp.append("(");
					str_hyp.append(tag);
				}else{
					//System.out.println("tem is :" + tem[t] + "; the len is: "+ tem[t].length());
					//note: it may have more than two ")", e.g., "3499))"
					int first_bracket_pos = tem[t].indexOf(")");//TODO: assume the tag/terminal does not have ")"
					String tag = Symbol.get_string(Integer.valueOf(tem[t].substring(0, first_bracket_pos)));
					if(tag.compareTo("PHRASE")==0)//TODO
						tag = "X";

					str_hyp.append(tag);
					str_hyp.append(tem[t].substring(first_bracket_pos));
				}
			}else{//terminal symbol
				str_hyp.append(Symbol.get_string(Integer.valueOf(tem[t])));
			}
			if(t<tem.length-1)
				str_hyp.append(" ");
		}
		if(model_cost!=null){
			str_hyp.append(" |||");
			double tem_sum=0.0;
			for(int k=0; k<model_cost.length; k++){
				model_cost[k] += l_models.get(k).finaltransition(true_item.tbl_states);//add final tranition
				str_hyp.append(String.format(" -%.3f", model_cost[k]));
				tem_sum += model_cost[k]*l_models.get(k).weight;
			}
			//sanity check
			if(Math.abs(cur.cost-tem_sum)>1e-2){
				Support.write_log_line("In nbest extraction, Cost does not match", Support.ERROR);
				System.exit(0);
			}
		}

		if(add_combined_score==true)
			str_hyp.append(String.format(" ||| -%.3f",cur.cost));
		return str_hyp.toString();
	}

	/*each Item will maintain a list of this, each of which corresponds to a deduction and its children's ranks
	 * remember the ranks of a deduction node
	 * used for kbest extraction*/
	private static class DerivationState implements Comparable
	{
		Deduction p_edge;//in the paper, it is "e"

		//**lesson: once we define this as a static variable, which cause big trouble
		int deduction_pos; //this is my position in my parent's Item.l_deductions, used for signature calculation

		int[] ranks;//in the paper, it is "j", which is a ArrayList of size |e|
		double cost;

		public DerivationState(Deduction e, int[] r, double c ,int pos){
			p_edge =e ;
			ranks = r;
			cost=c;
			deduction_pos=pos;
		}

		private String get_signature(){
			return get_signature(p_edge, ranks,deduction_pos);
		}

		private static String get_signature(Deduction p_edge2, int[] ranks2, int pos){
			StringBuffer res = new StringBuffer();
			//res.apend(p_edge2.toString());//Wrong: this may not be unique to identify a Deduction (as it represent the class name and hashcode which my be equal for different objects)
			res.append(pos);
			if(ranks2!=null)
				for(int i=0; i<ranks2.length;i++){
					res.append(" ");
					res.append(ranks2[i]);
				}
			return res.toString();
		}

		//compute model_cost[]
		private void compute_cost(Rule rl, ArrayList<Item> ants_items, double[] model_cost, ArrayList l_models){
			if(model_cost==null)
				return;
			ArrayList ants_states = new ArrayList();
			if(ants_items!=null)
				for(Item ant: ants_items)
					ants_states.add(ant.tbl_states);
			for(int k=0; k< l_models.size(); k++){
				Model m = (Model) l_models.get(k);
				HashMap  tem_tbl = m.transition(rl, ants_states, -1, -1, -1);//TODO: i, j., j1
				model_cost[k] += ((Double)tem_tbl.get(Symbol.TRANSITION_COST_SYM_ID)).doubleValue();
			}
		}

		//if want to get model cost, then have to set model_cost and l_models
		private String get_hyp(boolean tree_format, double[] model_cost, ArrayList l_models){
			StringBuffer res = new StringBuffer();
			Rule rl = p_edge.rule;
			if(rl==null){//deductions under "goal item" does not have rule
				if(tree_format==true)
					res.append("(ROOT ");
				for(int id=0; id < p_edge.l_ant_items.size();id++){
					Item child = (Item)p_edge.l_ant_items.get(id);
					res.append(((DerivationState)child.l_nbest.get(ranks[id]-1)).get_hyp(tree_format, null,null));
	    			if(id<p_edge.l_ant_items.size()-1)
		    			res.append(" ");
    			}
				if(tree_format==true)
					res.append(")");

				return res.toString();
			}

			compute_cost(rl, p_edge.l_ant_items, model_cost, l_models);//TODO
			if(tree_format==true){
				res.append("(");
				res.append(rl.lhs);
				res.append(" ");
			}
			for(int c=0; c<rl.english.length; c++){
	    		if(Symbol.is_nonterminal(rl.english[c])==true){
	    			int id=Symbol.get_eng_non_terminal_id(rl.english[c]);
	    			Item child = (Item)p_edge.l_ant_items.get(id);
	    			res.append(((DerivationState)child.l_nbest.get(ranks[id]-1)).get_hyp(tree_format, model_cost, l_models));
	    		}else{
	    			res.append(rl.english[c]);
	    		}
	    		if(c<rl.english.length-1)
	    			res.append(" ");
			}
			if(tree_format==true)
				res.append(")");
			return res.toString();
		}

		//natual order by cost
		public int compareTo(Object another) throws ClassCastException {
		    if (!(another instanceof DerivationState))
		      throw new ClassCastException("An Derivation object expected.");
		    if(this.cost < ((DerivationState)another).cost)
		    	return -1;
		    else if(this.cost == ((DerivationState)another).cost)
		    	return 0;
		    else
		    	return 1;
		}
	}//end of Class DerivationState
}
