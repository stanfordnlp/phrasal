package mt.syntax.decoder.ckyDecoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import mt.syntax.decoder.ckyDecoder.HyperGraph.Item;
import mt.syntax.decoder.ckyDecoder.HyperGraph.Deduction;
import mt.syntax.decoder.ckyDecoder.TMGrammar.Rule;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

//extract oracle tree from the hypergraph
//TODO assumption: we assume the LM order used for generating the hypergraph is >= order of the BLEU
public class OracleExtractor {
	//int[] ref_sentence;//reference string (not tree)
	private static int src_sent_len =0;
	private static int ref_sent_len =0;
	private static HashMap tbl_ref_ngrams = new HashMap();
	
	private static int g_lm_order=4;
	private static int g_bleu_order=4;

	//key: item; value: best_deduction, best_bleu, best_len, # of n-gram match where n is in [1,4]
	private static HashMap tbl_oracle_states = new HashMap();	
	private static int num_elements_in_state = 3+g_bleu_order;
	private static int STATE_BEST_DEDUCT=0;
	private static int STATE_BEST_BLEU=1;
	private static int STATE_BEST_LEN=2;
	private static int STATE_START_NGRAM_NUM=3;//the start position to store the number of ngram mathes
	

	public static void oracle_decode(int lm_order, int src_len, int[] ref_sent, Chart p_chart, Item goal_item){
		//goal_item identify the root of the hypergraph
		if(lm_order<g_bleu_order){System.out.println("error: lm order is smaller than bleu order, we cannot handle this case so far");System.exit(0);}
		g_lm_order=lm_order;		
		src_sent_len = src_len;
		ref_sent_len = ref_sent.length;
		//ref_sentence = ref_sent;
		tbl_ref_ngrams.clear();
		tbl_oracle_states.clear();
		
		get_ngrams(tbl_ref_ngrams,g_bleu_order,ref_sent);
		
		//do bottom-up decoding
		/*for(int width=1; width<=src_sent_len; width++){
			double avg_ref_len = (width==src_sent_len) ? ref_sent_len :  width*ref_sent_len*1.0/src_sent_len;//avg len
			for(int i=0; i<=src_sent_len-width; i++){
				int j= i + width;				
				ArrayList<Item> p_items = p_chart.l_bins[i][j].get_sorted_items();
				for(Item it : p_items){//"or" node
					process_one_item(it, avg_ref_len);
					System.out.println("-----item final state-------i: " +i +"; j=" +j);
					print_state((Object[])tbl_oracle_states.get(it));
				}
			}
		}*/
		process_one_item(goal_item);
		String str = extract_oracle_string(goal_item);
		System.out.println("Oracle string is: \n" + str);
		print_state((Object[])tbl_oracle_states.get(goal_item));
	}
	
	private static void print_state(Object[] state){
		System.out.println("State is");
		for(int i=0; i< state.length; i++)
			System.out.print(state[i] + " ---- ");
		System.out.println();
	}
	
	@SuppressWarnings("unchecked") 
	private static void process_one_item(Item it){
		double avg_ref_len = (it.j-it.i>=src_sent_len) ? ref_sent_len :  (it.j-it.i)*ref_sent_len*1.0/src_sent_len;//avg len?
		double best_blue=-1;
		Object[] best_state=null;		
		for(Deduction dt: it.l_deductions){//for each "and" node under the Item						
			Object[] state = compute_state(dt, avg_ref_len);
			print_state(state);
			if((Double)state[STATE_BEST_BLEU]>best_blue){//TODO: what if tie?
				best_blue = (Double)state[STATE_BEST_BLEU]; 
				best_state = state;							
			}
		}										
		tbl_oracle_states.put(it, best_state);
	}
	
	private static String extract_oracle_string(Item it){
		StringBuffer res = new StringBuffer();
		Object[] state = (Object[])tbl_oracle_states.get(it);
		Deduction p_edge = (Deduction)state[STATE_BEST_DEDUCT];
		Rule rl = p_edge.get_rule();
		
		if(rl==null){//deductions under "goal item" does not have rule
			if(p_edge.get_ant_items().size()!=1){//should only have one child
				System.out.println("error deduction under goal item have not equal one item"); System.exit(0);
			}
			return extract_oracle_string((Item)p_edge.get_ant_items().get(0));
		}	
		
		for(int c=0; c<rl.english.length; c++){
    		if(Symbol.is_nonterminal(rl.english[c])==true){
    			int id=Symbol.get_eng_non_terminal_id(rl.english[c]);
    			Item child = (Item)p_edge.get_ant_items().get(id);
    			res.append(extract_oracle_string(child));
    		}else{
    			res.append(Symbol.get_string(rl.english[c]));
    		}
    		if(c<rl.english.length-1) res.append(" ");
		}
		return res.toString();
	}
	
	@SuppressWarnings("unchecked") 
	private static Object[] compute_state(Deduction dt, double ref_len){
		Object[] res =new Object[num_elements_in_state];
		res[STATE_BEST_DEDUCT]=dt;

		//deductions under "goal item" does not have rule
		if(dt.get_rule()==null){
			if(dt.get_ant_items().size()!=1){//should only have one child
				System.out.println("error deduction under goal item have not equal one item"); System.exit(0);
			}
			Item child = (Item)dt.get_ant_items().get(0);
			if(tbl_oracle_states.containsKey(child)==false)
				process_one_item(child);//recursive call
			Object[] ant_state = (Object[]) tbl_oracle_states.get(child);
			for(int i=1; i< ant_state.length; i++)//copy the values, except the pointer to the deduction
				res[i] = ant_state[i];
			return res;
		}
		
		int[] en_words = dt.get_rule().english;
		HashMap new_ngram_counts = new HashMap();
		HashMap old_ngram_counts = new HashMap();//the ngram that has already been computed
		int total_hyp_len =0;
		int[] num_ngram_match = new int[g_bleu_order];
		//####calulate new and old ngram counts, and len
    	ArrayList words= new ArrayList();
    	for(int c=0; c<en_words.length; c++){
    		int c_id = en_words[c];
    		if(Symbol.is_nonterminal(c_id)==true){    			
    			int index=Symbol.get_eng_non_terminal_id(c_id);
    			Item it = dt.get_ant_items().get(index);//ant it 
    			if(tbl_oracle_states.containsKey(it)==false)
    				process_one_item(it);//recursive call
    			Object[] ant_state = (Object[]) tbl_oracle_states.get(it);    			
    			total_hyp_len += (Integer) ant_state[STATE_BEST_LEN];
    			for(int t=0; t<g_bleu_order; t++)
    				num_ngram_match[t] += (Integer)ant_state[STATE_START_NGRAM_NUM+t];
    	  			
    			int[] l_context =(int[])it.tbl_states.get(Chart.LM_L_STATE_SYM_ID);
    			int[] r_context =(int[])it.tbl_states.get(Chart.LM_R_STATE_SYM_ID);
    			
    			//System.out.println("rule is: " + Symbol.get_string(dt.get_rule().english));		
    			//System.out.println("left context is: " + Symbol.get_string(l_context));
    			//System.out.println("right context is: " + Symbol.get_string(r_context));	
    			
    			for(int t : l_context)//always have l_context
    				words.add(t);
    			get_ngrams(old_ngram_counts, g_bleu_order, l_context);    			
    			if(r_context.length>=g_lm_order-1){	    	
    				get_ngrams(new_ngram_counts, g_bleu_order, words);
    				get_ngrams(old_ngram_counts, g_bleu_order, r_context);
	    			words=new ArrayList();//start a new chunk    			
	    			for(int t : r_context)
	    				words.add(t);	    			
	    		}
    		}else{
    			words.add(c_id);
    			total_hyp_len += 1;
    		}
    	}
    	get_ngrams(new_ngram_counts, g_bleu_order, words);
    	res[STATE_BEST_LEN]= new Integer(total_hyp_len);
    	
    	//####now deduct ngram counts, and calculate bleu score
    	Iterator iter = new_ngram_counts.keySet().iterator();
    	while(iter.hasNext()){
    		String ngram = (String)iter.next();
    		int final_count = (Integer)new_ngram_counts.get(ngram);
    		if(old_ngram_counts.containsKey(ngram)){
    			final_count -= (Integer)old_ngram_counts.get(ngram);
    			if(final_count<0){
    				//System.out.println("rule is: " + Symbol.get_string(dt.get_rule().english));		
    				System.out.println("error: negative count for ngram: "+ Symbol.get_string(11844) + "; new: " + new_ngram_counts.get(ngram) +"; old: " +old_ngram_counts.get(ngram) ); System.exit(0);}
    		}
    		if(final_count>0 && tbl_ref_ngrams.containsKey(ngram)){//TODO: no ngram clip
    			num_ngram_match[ngram.split("\\s+").length-1] += final_count;    			
    		}
    	}
    	for(int t=0; t<g_bleu_order; t++)
			res[STATE_START_NGRAM_NUM+t] = num_ngram_match[t];
    	
    	//####now calculate the BLEU score
    	res[STATE_BEST_BLEU] = compute_bleu(total_hyp_len, ref_len, num_ngram_match);
    	//print_state(res);
    	return res;
	}
	
	//sentence-bleu
	private static double compute_bleu(int hyp_len, double ref_len, int[] ngram_match){
		if(hyp_len<=0 || ref_len<=0){System.out.println("error: ref or hyp is zero len"); System.exit(0);}
		double res=0;		
		double wt = 1.0/g_bleu_order;
		double prec = 0;
		double smooth_factor=1.0;
		for(int t=0; t<g_bleu_order && t<hyp_len; t++){
			if(ngram_match[t]>0)
				prec += wt*Math.log(ngram_match[t]*1.0/(hyp_len-t));
			else{
				smooth_factor *= 0.5;//TODO
				prec += wt*Math.log(smooth_factor/(hyp_len-t));
			}
		}
		double bp = (hyp_len>=ref_len) ? 1.0 : Math.exp(1-ref_len/hyp_len);	
		res = bp*Math.exp(prec);
		System.out.println("hyp_len: " + hyp_len + "; ref_len:" + ref_len + "prec: " + Math.exp(prec) + "; bp: " + bp + "; bleu: " + res);
		return res;
	}
	
	@SuppressWarnings("unchecked") 
	private static void get_ngrams(HashMap tbl, int order, int[] wrds){
		for(int i=0; i<wrds.length; i++)
			for(int j=0; j<order && j+i<wrds.length; j++){//ngram: [i,i+j]
				StringBuilder ngram = new StringBuilder();
				for(int k=i; k<=i+j; k++){
					ngram.append(wrds[k]);
					if(k<i+j) ngram.append(" ");
				}
				String ngram_str = ngram.toString();
				if(tbl.containsKey(ngram_str))
					tbl.put(ngram_str, (Integer)tbl.get(ngram_str)+1);
				else
					tbl.put(ngram_str, 1);
			}
	}

	@SuppressWarnings("unchecked") 
	private static void get_ngrams(HashMap tbl, int order, ArrayList wrds){
		for(int i=0; i<wrds.size(); i++)
			for(int j=0; j<order && j+i<wrds.size(); j++){//ngram: [i,i+j]
				StringBuilder ngram = new StringBuilder();
				for(int k=i; k<=i+j; k++){
					ngram.append(wrds.get(k));
					if(k<i+j) ngram.append(" ");
				}
				String ngram_str = ngram.toString();
				if(tbl.containsKey(ngram_str))
					tbl.put(ngram_str, (Integer)tbl.get(ngram_str)+1);
				else
					tbl.put(ngram_str, 1);
			}
	}
	
}
