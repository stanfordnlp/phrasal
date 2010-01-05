package mt.syntax.decoder;

import java.util.ArrayList;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

@SuppressWarnings("unchecked")
public abstract class LMGrammar {
	int g_order=3;
	long start_loading_time;
	
	//init 
	public LMGrammar(int order){
		g_order=order; 
	}
	public void end_lm_grammar(){
		//do nothing
	}
	
	public abstract void read_lm_grammar_from_file(String grammar_file);
	
	public abstract void write_vocab_map_srilm(String fname);
	
	public final double score_a_sent(ArrayList words_in, int order, int start_index){//1-indexed
		return score_a_sent( Support.sub_int_array(words_in, 0, words_in.size() ), order, start_index);
	}
	private final double score_a_sent(int[] words, int order, int start_index){//1-indexed
		//long start = Support.current_time();
		double res=0.0;
		if(words==null||words.length<=0)
			return res;
		
		int[] ngram_wrds;		
		//extra partial-ngrams at the begining						
		for(int j=start_index; j<order && j<=words.length; j++){//TODO: start_index dependents on the order, e.g., g_order-1 (in srilm, for 3-gram lm, start_index=2. othercase, need to check)
			ngram_wrds = Support.sub_int_array(words, 0, j);
			res+=get_prob(ngram_wrds, order, true);
		}		
		//regular order-ngram
		for(int i=0; i<=words.length-order; i++){
			ngram_wrds = Support.sub_int_array(words, i, i+order);
			res+=get_prob(ngram_wrds, order, true);
		}
		//Chart.g_time_score_sent += Support.current_time()-start;
		return res;
	}
	
	//Note: it seems the List or ArrayList is much slower than the int array, e.g., from 11 to 9 seconds
	//so try to avoid call this function
   public final double get_prob(ArrayList ngram_wrds, int order , boolean check_bad_stuff){	   
	   return get_prob(Support.sub_int_array(ngram_wrds, 0, ngram_wrds.size()), order, check_bad_stuff);
   }
   
   public final double get_prob(int[] ngram_wrds, int order , boolean check_bad_stuff){
	   if(ngram_wrds.length > order){
			System.out.println("ngram length is greather than the max order");
			System.exit(0);
	   }	   
      int hist_size =  ngram_wrds.length-1;
      if(hist_size>=order || hist_size<0){
		  System.out.println("Error: hist size is " + hist_size);
	  	  return 0;//TODO: zero cost?
      }      
	  double res= get_prob_specific(ngram_wrds, order, check_bad_stuff);
	  if(res<-Decoder.lm_ceiling_cost)
	      res=-Decoder.lm_ceiling_cost;

	  //System.out.println("Prob: "+ Symbol.get_string(ngram_wrds) + "; " + res);

	  return res;
   }   
   protected abstract double get_prob_specific(int[] ngram_wrds, int order, boolean check_bad_stuff);
   
   
// called by LMModel to calculate additional bow for backoff Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID  
	//must be: ngram_wrds.length <= order
   //	 called by LMModel to calculate additional bow for backoff Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID
	//must be: ngram_wrds.length <= order
   
   public final double get_prob_backoff_state(ArrayList ngram_wrds, int order, int n_additional_bow){
	   	return get_prob_backoff_state( Support.sub_int_array(ngram_wrds, 0, ngram_wrds.size()), order, n_additional_bow);
   }
   
   public final double get_prob_backoff_state(int[] ngram_wrds, int order, int n_additional_bow){
		if(ngram_wrds.length > order){
			System.out.println("ngram length is greather than the max order");
			System.exit(0);
		}
		if(ngram_wrds[ngram_wrds.length-1]!=Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID){
			System.out.println("last wrd is not <bow>");
			System.exit(0);
		}
		if(n_additional_bow>0){
			return get_prob_backoff_state_specific(ngram_wrds, order, n_additional_bow); 		
		}else
			return 0.0;
	}
   	protected abstract double get_prob_backoff_state_specific(int[] ngram_wrds, int order, int n_additional_bow);
    
   
    public abstract int[] get_left_equi_state(int[] original_state_wrds, int order, double[] cost);
    
    //idea: from right to left, if a span does not have a backoff weight, which means all ngram having this span will backoff, and we can safely remove this state
	//the absence of backoff weight for low-order ngram implies the absence of higher-order ngram
	//the absence of backoff weight for low-order ngram implies the absence of backoff weight for high order ngram
	public abstract int[] get_right_equi_state(int[] original_state, int order, boolean check_bad_stuff);
		
	
	//######################################## common function ###########################################
	public final int[] replace_with_unk(int[] in){
		int[] res = new int[in.length];
		for(int i=0; i < in.length; i++){
			res[i]=replace_with_unk(in[i]);
		}
		return res;
	}	
	protected abstract int replace_with_unk(int in);
   
	
	
//###################### not used		
	
	 /*
	  //only java based method is available
	   //note: there will be never right-side state words going into original_state_wrds
	 public int[] get_left_euqi_state_vold(int[] original_state_wrds, int order, double[] cost){
		if(Decoder.use_left_euqivalent_state==false){
			return original_state_wrds;
		}		
		//sanity check
		if(original_state_wrds.length> order-1){
			System.out.println("ngram state is greather than the max order-1");
			System.exit(0);
		}
		int[] res_equi_state = new int[original_state_wrds.length];
		int pos_res_state=0;
		double res_final_cost =0.0;//finalized cost
		double res_est_cost=0.0;//estimated cost
		
		for(int i=0; i<original_state_wrds.length; i++){//from left to right
			int[] cur_suffix = Support.sub_int_array(original_state_wrds, 0, i+1);
			int last_wrd = cur_suffix[cur_suffix.length-1];
			int[] backoff_ngram  = backoff_ngram_with_suffix(cur_suffix);			
			if(backoff_ngram==null && 
			   last_wrd != Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID &&
			   last_wrd != Symbol.UNK_SYM_ID &&
			   last_wrd != Symbol.NULL_LEFT_LM_STATE_SYM_ID
			 ){//in this case, we cannot decide if it will backoff, so just remain the original state				
				res_equi_state[pos_res_state++]= original_state_wrds[i];//no finalized cost
				res_est_cost += -get_prob(cur_suffix, order, false);//TODO: use get_prob_euqi_state?
			}else{//for any ngram end with cur_suffix, it will definitely backoff at least to cur_suffix
				//##################get cur pob
				double cur_prob =0.0;
				
				if(last_wrd == Symbol.UNK_SYM_ID){//should directly go to NULL state
					cur_prob = get_prob(cur_suffix, order, false);//TODO: use get_prob_euqi_state?
				}else if(last_wrd == Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID || last_wrd == Symbol.NULL_LEFT_LM_STATE_SYM_ID){
					cur_prob =0 ;
				}else{
					cur_prob = get_prob(backoff_ngram, order, false);//TODO: use get_prob_euqi_state?
				}				
				//System.out.println("cur_prob: " + cur_prob + "; string: " + Symbol.get_string(original_state_wrds));
				//System.out.println("cur_suffix: " + Symbol.get_string(cur_suffix));				
				res_final_cost += -cur_prob;
				
				//##################now consider the backoff weight
				if(last_wrd == Symbol.UNK_SYM_ID || last_wrd == Symbol.NULL_LEFT_LM_STATE_SYM_ID){////should directly go to NULL state, no backoff weight is required					
					res_equi_state[pos_res_state++]=Symbol.NULL_LEFT_LM_STATE_SYM_ID;//no state, no bow, no est_cost
				}else if(i==0){//first word
					//System.out.println("####first wrd backoff, cur_prob is: " + cur_prob);
					res_equi_state[pos_res_state++]=Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID;
					res_est_cost += -( get_prob(cur_suffix, order, false) - cur_prob );//TODO: use get_prob_euqi_state?
				}else{
					int[] backoff_history = Support.sub_int_array(original_state_wrds, 0, i);//ignore last wrd
					double[] bow = new double[1];
					boolean finalized_backoff;
					double true_bow=0.0;
					if(last_wrd == Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID){
						finalized_backoff = check_backoff_weight(backoff_history, bow, 0);//backoff weight is obtained outside this function?						
					}else{
						finalized_backoff = check_backoff_weight(backoff_history, bow, cur_suffix.length-backoff_ngram.length);
						true_bow= bow[0];
					}						
					
					if(finalized_backoff==true){//no backoff weight for backoff_history, //no estimation required
						//System.out.println("####ignore the state word" );
						res_equi_state[pos_res_state++]=Symbol.NULL_LEFT_LM_STATE_SYM_ID;//do not need to have this wrd as state
						res_final_cost += -true_bow;//addtional final cost						
					}else{
						//System.out.println("####backoff" );
						res_equi_state[pos_res_state++]=Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID;
						res_final_cost += -bow[0];//addtional final cost, but still need to calculate some high-order backoff weight in the future
						res_est_cost += -( get_prob(cur_suffix, order,false)-cur_prob-bow[0] );//TODO: use get_prob_euqi_state?
					}
					//System.out.println("finalized_bow: " + bow[0]);
				}
				
				//###### consider all the future words
				//in both case, all the high order words can be safely removed from the state
				//since the ngram will definitely backoff, and the backoff weights are finalized
				//add all the finalized cost
				for(int j=i+1; j<original_state_wrds.length; j++){					
					int[] cur_ngram =  Support.sub_int_array(original_state_wrds, 0, j+1);
					int last_wrd2 =cur_ngram[cur_ngram.length-1];
					
					if(last_wrd2 == Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID || last_wrd2 == Symbol.NULL_LEFT_LM_STATE_SYM_ID){//last wrd is <bo>
						//do nothing
						//System.out.println("######tem prob is " + tem_prob + " nd: " +n_additional_bow + " string is: " + Symbol.get_string(tem));	
					}else if(last_wrd2 == Symbol.UNK_SYM_ID){//no backoff weight is required
						res_final_cost += -get_prob(cur_ngram,order,false);						
					}else{//regular wrd
						//TODO: in fact, we just need the last two wrd (including the current wrd)			
						double tem =get_prob(cur_ngram,order,false);
						res_final_cost += -tem;//all the backoff weights will be final
						//System.out.println("ignore state, finalprob: " + tem);
					}
					res_equi_state[pos_res_state++]=Symbol.NULL_LEFT_LM_STATE_SYM_ID;
				}
				break;
			}			
		}
		
		cost[0]=res_final_cost;
		cost[1]=res_est_cost;
		//System.out.println("######org state: "+ Symbol.get_string(original_state_wrds) + "; euqiv state: " + Symbol.get_string(res_equi_state) +  " final: " +res_final_cost + "; estcost: " +res_est_cost  );
		return res_equi_state;
	}*/
	
	
	
	
	
	
	
	
	/*public static class HashMap
	{
		//######note: key must be positive integer, and value must not be null
		
		//TODO: should round the array size to a prime number?
		
		static double load_factor = 0.75;
		static int default_init_size = 5;
		
		int size=0;		
		LMItem[] data_array;
		
		public HashMap(int init_size){
			data_array = new LMItem[init_size];
		}
		
		public HashMap(){
			data_array = new LMItem[default_init_size];
		}
		
		public Object get(int key) {
			Object res =null;
			
			int pos = key % data_array.length;			
		    while(data_array[pos] != null){//search until empty cell,
		      if (data_array[pos].key == key)
		        return data_array[pos].val; // found
		      pos++; //linear search
		      pos = pos % data_array.length;
		    }	 
			return res;
		}
		
		public boolean containsKey(int key) {
			if(get(key)!=null)
				return true;
			else
				return false;
		}
		
		public int size() {
			return size;
		}
		
		public void put(int key, Object value) {
			if(value==null){
				System.out.println("HashMap, value is null");
				System.exit(0);
			}
			
			int pos = key % data_array.length;			
			while (data_array[pos] != null){//search until empty cell,
				if (data_array[pos].key == key){
					data_array[pos].val = value; // found, and overwrite
					return;
				}
				pos++; //linear search
				pos = pos % data_array.length;
			}
			
			//we get to here, means we do not have this key, need to insert it
			data_array[pos] = new LMItem(key, value);				
			
			size++;
			if(size>=data_array.length*load_factor)
				expand_tbl();
		}
		
		private void expand_tbl(){
			int new_size = data_array.length*2+1;//TODO				
			LMItem[] new_data_array = new LMItem[new_size];
			for(int i=0; i<data_array.length; i++){
				if(data_array[i]!=null){//add the element
					int pos = data_array[i].key % new_data_array.length;					
					while(new_data_array[pos] != null){//find first empty postition, note that it is not possible that we need to overwrite					
						pos++; //linear search
						pos = pos % new_data_array.length;
					}					
					new_data_array[pos] =  new LMItem(data_array[i].key, data_array[i].val);	
				}
			}
			data_array = new_data_array;
		}
		
		public class LMItem{
			int key;
			Object val;
			public LMItem(int k, Object v){
				key = k;
				val = v;
			}		
		}
	}*/
	
	
	
	
	//######################################## retired functions
/*
	//state_start_pos: in the ngram_wrds, the start pos of lm state words
	public double get_prob_left_euqi_state(int[] ngram_wrds, int order, int n_additional_bow){
		ArrayList tem = new ArrayList(ngram_wrds.length);
		for(int i=0; i<ngram_wrds.length; i++)
			tem.add(ngram_wrds[i]);
		return get_prob_left_euqi_state(tem,order,n_additional_bow);
	}
	
	public double get_prob_left_euqi_state(ArrayList ngram_wrds, int order, int n_additional_bow){
		if(ngram_wrds.size() > order){
			System.out.println("ngram length is greather than the max order");
			System.exit(0);
		}
		if((Integer)ngram_wrds.get(ngram_wrds.size()-1)==Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID){//only need to add required backoff weight
			if(n_additional_bow>0){				
				int[] backoff_wrds = new int[ngram_wrds.size()-1];
				for(int i=0; i<backoff_wrds.length; i++)
					backoff_wrds[i]=(Integer)ngram_wrds.get(i);
				double tem = get_backoff_weight_sum(backoff_wrds, n_additional_bow);
				//System.out.println("############ n_add: " + n_additional_bow + " double: " + tem);
				return tem;//state_start_pos is essentially saying how many additional backoff weight is required
			}else
				return 0.0;
		}		
		return get_prob(ngram_wrds,order);	
	}
*/	
	
	
	/*several observation: 
	 * In general:
	 * (1) In general, there might be more than one <bo>, and they can be in any position
	 * (2) in general, whenever there is a <bo> in a given ngram, then it will definitely backoff since it has same/more context
	 * But, if we make the BIG assumption, then
	 * (1) there is maximum one <bo>, and only the right most wrd can be <bo> 
	 * 
	 * Without the Big assumption, this observations breaks when a non-terminal does not have a right state. If there is a rule: X i am 
	 * then there will have new state: <bo> I am . 
	*/
	/*
//	return: (1) the equivlant state vector; (2) the finalized cost; (3) the estimated cost
	public int[] get_left_euqi_stateOldV(List original_state_wrds, int order, double[] cost, int n_additional_bow){
		if(original_state_wrds.size()<=0)
			return new int[0];
		if(Decoder.use_euqivalent_state==false){
			int[] res = new int[original_state_wrds.size()];
			for(int t=0; t< res.length; t++)
				res[t]= (Integer) original_state_wrds.get(t);
			return res;
		}
	
		//sanity check
		if(original_state_wrds.size() > order-1){
			System.out.println("ngram state is greather than the max order-1");
			System.exit(0);
		}
		ArrayList res_equi_state = new ArrayList();
		double res_final_cost =0.0;//finalized cost
		double res_est_cost=0.0;//estimated cost
			
		for(int i=0; i<original_state_wrds.size(); i++){
			List cur_suffix = original_state_wrds.subList(0, i+1);		
			ArrayList backoff_ngram  = backoff_ngram_with_suffix(cur_suffix);			
			if(backoff_ngram==null && 
			 (Integer)cur_suffix.get(cur_suffix.size()-1) != Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID &&
			 (Integer)cur_suffix.get(cur_suffix.size()-1) != Symbol.UNK_SYM_ID 
			 ){//in this case, we cannot decide if it will backoff, so just remain the original state
				res_equi_state.add((Integer)original_state_wrds.get(i));
				//no finalized cost
				res_est_cost += -get_prob(cur_suffix, order);//TODO: use get_prob_euqi_state?
			}else{//for any ngram end with cur_suffix, it will definitely backoff at least to cur_suffix
				double cur_prob =0.0;
				if((Integer)cur_suffix.get(cur_suffix.size()-1) != Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID)
					cur_prob = get_prob(backoff_ngram, order);//TODO: use get_prob_euqi_state?
				else{
					//System.out.println("##############end with <bo>");
					if( i!= original_state_wrds.size()-1){
						System.out.println("Error : <bo> appear in position not the rightmost");
						System.out.println("original is " + original_state_wrds.toString());
						System.exit(0);
					}
				}
				//System.out.println("cur_prob: " + cur_prob + "; string: " + Symbol.get_string(original_state_wrds));
				//System.out.println("cur_suffix: " + Symbol.get_string(cur_suffix));
				res_final_cost += -cur_prob;			
				//now consider the backoff weight
				if((Integer)cur_suffix.get(cur_suffix.size()-1) == Symbol.UNK_SYM_ID){//no backoff weight is required
					//no state, no bow, no est_cost
				}else if(i==0){//first word
					//System.out.println("####first wrd backoff, cur_prob is: " + cur_prob);
					res_equi_state.add(Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID);					
					res_est_cost += -(get_prob(cur_suffix, order)-cur_prob);//TODO: use get_prob_euqi_state?
				}else {
					List backoff_history = original_state_wrds.subList(0, i);
					double[] bow = new double[1];
					boolean finalized_backoff;
					if((Integer)cur_suffix.get(cur_suffix.size()-1) != Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID)
						finalized_backoff = check_backoff_weight(backoff_history, bow, cur_suffix.size()-backoff_ngram.size());
					else
						finalized_backoff = check_backoff_weight(backoff_history, bow, n_additional_bow);
					if(finalized_backoff==true){//no backoff weight for backoff_history
						//System.out.println("####ignore the state word" );
						//do not need to have this wrd as state
						res_final_cost += -bow[0];//addtional final cost
						//no estimation required
					}else{
						//System.out.println("####backoff" );
						res_equi_state.add(Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID);
						res_final_cost += -bow[0];//addtional final cost, but still need to calculate some high-order backoff weight in the future
						//res_est_cost += -(get_prob(cur_suffix, cur_suffix.size())-bow[0]);//TODO: use get_prob_euqi_state?
						res_est_cost += -(get_prob(cur_suffix, order)-cur_prob-bow[0]);//TODO: use get_prob_euqi_state?
					}
					//System.out.println("finalized_bow: " + bow[0]);
				}
				//in both case, all the high order words can be safely removed from the state
				//since the ngram will definitely backoff, and the backoff weights are finalized
				//add all the finalized cost
				for(int j=i+1; j<original_state_wrds.size(); j++){					
					List cur_ngram = original_state_wrds.subList(0, j+1);
					if((Integer)cur_ngram.get(cur_ngram.size()-1) == Symbol.UNK_SYM_ID){//no backoff weight is required
						res_final_cost += -get_prob(cur_ngram,order);
						//no est_cost, no state, no bow
						continue;
					}
					//TODO: in fact, we just need the last two wrd (including the current wrd)					
					if((Integer)cur_ngram.get(cur_ngram.size()-1) != Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID ){//last wrd is not <bo>
						double tem =get_prob(cur_ngram,order);
						res_final_cost += -tem;//all the backoff weights will be final
						//System.out.println("ignore state, finalprob: " + tem);
					}else{//if <bo>, then, no additional final backoff weight
//						sanity check
						if( j!= original_state_wrds.size()-1){
							System.out.println("Error : <bo> appear in position not the rightmost");
							System.out.println("original is " + original_state_wrds.toString());
							System.exit(0);
						}
						int[] tem = new int[cur_ngram.size()];
						for(int t=0; t<tem.length; t++)
							tem[t] =(Integer) cur_ngram.get(t);
						double tem_prob = get_prob_left_euqi_state(tem, order, n_additional_bow);
						res_final_cost += -tem_prob;
						//System.out.println("######tem prob is " + tem_prob + " nd: " +n_additional_bow + " string is: " + Symbol.get_string(tem));					
					}
				}
				break;
			}			
		}
		cost[0]=res_final_cost;
		cost[1]=res_est_cost;
		int[] res_int = new int[res_equi_state.size()];
		for(int t=0; t< res_int.length; t++)
			res_int[t]=(Integer)res_equi_state.get(t);
		//System.out.println("######org state: "+ Symbol.get_string(original_state_wrds) + "; euqiv state: " + Symbol.get_string(res_int));
		//System.out.println("res is: "+ Symbol.get_string(res_int));
		//System.out.println("2905 is: for"+ Symbol.get_string(2905));
		return res_int;
	}*/
	
	/*public String[] replace_with_unk(String[] in){
	String[] res = new String[in.length];
	for(int i=0; i < in.length; i++)
		if(root.tbl_info.containsKey(in[i])==false)
			res[i]=UNK;
		else
			res[i]=in[i];
	return res;
	}*/
	
	/*
	private TrieNode lookup_trie(String[] ngram_wrds, int[] order_used, double[] prob_cur){
		TrieNode pos = root;		
		Double bw_wgt=null;
		if(prefix_wrds==null)
			return pos;
		
		if(prefix_wrds.length==1)
			bw_wgt=(Double)root.tbl_backoff_weight.get(prefix_wrds[prefix_wrds.length-1]);
		
		for(int i=0; i < prefix_wrds.length; i++){
			String cur_sym=prefix_wrds[i];
			TrieNode next_layer=pos.match_symbol(cur_sym);
			if(next_layer!=null){
				pos=next_layer;
				depth[0]++;
				if(i==prefix_wrds.length-2){
					bw_wgt=(Double)next_layer.tbl_backoff_weight.get(prefix_wrds[prefix_wrds.length-1]);
				}
			}else{
				break;//TODO
			}
		}
		if(depth[0]==prefix_wrds.length && bw_wgt!=null)
			backoff_weight[0]=bw_wgt.doubleValue();
		else
			backoff_weight[0]=NON_EXIST_WEIGHT;
		return pos;
	}*/
	
	
	/*public double score_a_sent(String[] words_in, int order, int start_index){//1-indexed
		long start = Support.current_time();
		double res=0.0;
		if(words_in==null||words_in.length<=0)
			return res;
		String[] words=replace_with_unk(words_in);
		//Support.write_log_line("replaced sent is: " + Support.arrayToString(words, " "),Support.DEBUG );
		
		String[] prefix;
		String cur;	
		
		//extra partial-ngrams at the begining						
		for(int j=start_index; j<order && j<=words.length; j++){//TODO: start index dependents on the order, e.g., g_order-1 (in srilm, for 3-gram lm, start_index=2. othercase, need to check)
			if(j-1>0){
				prefix = new String[j-1];
				for(int k=0; k<j-1; k++){
					prefix[k]=words[k];
				}
			}
			else
				prefix=null;			
			cur=words[j-1];
			//Support.write_log_line("scoring: " + Support.arrayToString(prefix, " ") +" "+cur, Support.DEBUG);
			res+=get_prob(prefix,cur);
		}
		
		//regular order-ngram
		for(int i=0; i<=words.length-order; i++){
			if(order-1>0)
				prefix = new String[order-1];
			else
				prefix = null;
			for(int k=i; k<i+order-1; k++){
				prefix[k-i]=words[k];
			}
			cur=words[i+order-1];
			//Support.write_log_line("scoring: " + Support.arrayToString(prefix, " ") +" "+cur, Support.DEBUG);
			res+=get_prob(prefix,cur);
		}
		Chart.g_time_score_sent += Support.current_time()-start;
		return res;
	}*/
	
	/*
	public class TrieNode 
	{	
		HashMap  tbl_info=null;//a tbl of TrieNode, or backoff weight
		//double prob;
		//double backoff_weight=NON_EXIST_WEIGHT;
		//HashMap  prob_next_word=new THashHashMap ();
		//HashMap  tbl_backoff_weight=new THashHashMap ();
		
		//last order of ngrams, do not want to have a tbl_chilren which contain dump TrieNode
		public TrieNode match_symbol(int sym_id){//looking for the next layer trinode corresponding to this symbol
			//if(sym_id==null)
			//	Support.write_log_line("Match_symbol: sym is null", Support.ERROR);
			if(tbl_info==null)
				return null;
			return (TrieNode) tbl_info.get(sym_id);
		}
	}*/
}
