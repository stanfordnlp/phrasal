package mt.syntax.decoder;

import java.io.BufferedReader;
import java.util.ArrayList;

import mt.syntax.util.FileUtility;

/*!!!!!!!!!!!!!!!!!!!!! replaced by LMGrammar_JAVA_General*/

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

//############################## not used class

@SuppressWarnings("unchecked")
public class LMGrammar_JAVA extends LMGrammar {
	/*a backoff node is a hashtable, it may include:
	 * (1) probability for next words
	 * (2) pointer to a next-layer backoff node (hashtable)
	 * (3) backoff weight for this node
	 * (4) suffix flat to indicate that there is ngrams start from this suffix
     */

	private LMHash  root=null;
	private int g_n_bow_nodes=0;
	private int g_n_suffix_nodes=0;
	//private double LOGZERO=-999.0;
	private double NON_EXIST_WEIGHT=0;//the history has not appeared at all
	private int num_rule_read=0;

	boolean g_is_add_prefix_infor=false;
	boolean g_is_add_suffix_infor=false;

	public LMGrammar_JAVA(int order, String grammar_file, boolean is_add_suffix_infor){
		super(order);
		System.out.println("use java lm");
		g_is_add_suffix_infor = is_add_suffix_infor;
		Symbol.add_global_symbols(true);
		/*//debug
		double[] bow = new double[1];
		int[] backoff_history = new int[1];
		backoff_history[0]=Symbol.UNTRANS_SYM_ID;
		boolean finalized_backoff = check_backoff_weight(backoff_history, bow, 0);//backoff weight is already added outside this function?

		//System.out.println("bow_weigth id: " + Symbol.BACKOFF_WGHT_SYM_ID);
		System.out.println("is final: " + finalized_backoff);
		System.out.println("bow: " + bow[0]);
		System.exit(0);*/
	}

	@Override
	public void write_vocab_map_srilm(String fname){
		System.out.println("Error: call write_vocab_map_srilm in java, must exit");
		System.exit(0);
	}


	//note: we never use check_bad_stuff here
	@Override
	protected double get_prob_specific(int[] ngram_wrds_in, int order, boolean check_bad_stuff){
		int[] ngram_wrds=replace_with_unk(ngram_wrds_in);//TODO
	    if(ngram_wrds[ngram_wrds.length-1]==Symbol.UNK_SYM_ID)//TODO: wrong implementation in hiero
			return -Decoder.lm_ceiling_cost;
	    //TODO: untranslated words
		if(root==null){
			System.out.println("root is null");
			System.exit(0);
		}
		int last_word_id = ngram_wrds[ngram_wrds.length-1];
		LMHash  pos =root;
		Double prob=(Double)root.get(last_word_id);
		double bow_sum=0;
		for(int i=ngram_wrds.length-2; i>=0; i--){//reverse search, start from the second-last word
			LMHash  next_layer=(LMHash) pos.get(ngram_wrds[i]+Symbol.lm_end_sym_id);
			if(next_layer!=null){//have context/bow node
				pos=next_layer;
				Double prob2=(Double)pos.get(last_word_id);
				if(prob2!=null){//reset, if backoff, will at least back off to here
					prob=prob2;
					bow_sum =0;
				}else{
					Double bow = (Double) pos.get( Symbol.BACKOFF_WGHT_SYM_ID );
					if(bow!=null)
						bow_sum += bow;
				}
			}else{//do not have context/bow node
				break;
			}
		}
		return prob + bow_sum;
	}

//	##################### begin right equivalent state #############
	//idea: from right to left, if a span does not have a backoff weight, which means all ngram having this span will backoff, and we can safely remove this state
    //the absence of backoff weight for low-order ngram implies the absence of higher-order ngram
    //the absence of backoff weight for low-order ngram implies the absence of backoff weight for high order ngram ????????????????
	/*e.g., if we do not have bow node for A, then we can say there is no bow nodes for
	 * (1)*A: implied by the trie structure
	 * (2)A*: if we have a BOW node for A* (with bow weight), due to the representantion of ARPA format, then we must have a probability for A*, which implies we have a BOW node for A
	 * (3)*A*
	 */

	  //the returned array lenght must be the same the len of original_state
    //the only change to the original_state is: replace with more non-null state words to null state
	 @Override
	public int[] get_right_equi_state(int[] original_state_in, int order, boolean check_bad_stuff){
			if(Decoder.use_right_euqivalent_state==false)
				return original_state_in;
			int[] original_state=replace_with_unk(original_state_in);

			int[] res = new int[original_state.length];
			LMHash  pos =root;
			/*if there is null words, it will be automatically ok*/
			for(int i=original_state.length-1; i>=0; i--){//reverse search
				int cur_wrd = original_state[i];

				LMHash  next_layer=(LMHash) pos.get(cur_wrd+Symbol.lm_end_sym_id);
				//can deal with: a lower-order ngram does not have a backoff weight (but have prob in lm), but its higher-order ngram has a bow
				if(next_layer!=null && (Double)next_layer.get(Symbol.BACKOFF_WGHT_SYM_ID)!=null){//TODO: we also need to make sure that next_layer.tbl_info contains prob (next_layer.tbl_info may not have a prob because the lm file have certain high-order ngram, but not have its sub-low-ngram)
					res[i] = cur_wrd;
					pos=next_layer;
				}else{//do not have a backoff weight
					for(int j=i; j>=0;j--)
						res[j] = Symbol.NULL_RIGHT_LM_STATE_SYM_ID;
					break;
				}
			}
			//System.out.println("right org state: " + Symbol.get_string(original_state) +"; equiv state: " + Symbol.get_string(res));
			return res;
	  }
//		##################### end right equivalent state #############


	 //############################ begin left equivalent state ##############################

	/*several observation:
	 * In general:
	 * (1) In general, there might be more than one <bo> or <null>, and they can be in any position
	 * (2) in general, whenever there is a <bo> or <null> in a given ngram, then it will definitely backoff since it has same/more context
	*/
	//return: (1) the equivlant state vector; (2) the finalized cost; (3) the estimated cost
	 @Override
	public int[] get_left_equi_state(int[] original_state_wrds_in, int order, double[] cost){
		    if(Decoder.use_left_euqivalent_state==false){
				return original_state_wrds_in;
			}
			//sanity check
			if(original_state_wrds_in.length> order-1){
				System.out.println("ngram state is greather than the max order-1");
				System.exit(0);
			}
			int[] original_state_wrds = replace_with_unk(original_state_wrds_in);//???

			int[] res_equi_state = new int[original_state_wrds.length];
			double res_final_cost = 0.0;//finalized cost
			double res_est_cost=0.0;//estimated cost
			boolean check_suffix=true;
			for(int i=0; i<original_state_wrds.length; i++){//from left to right
				int[] cur_suffix = Support.sub_int_array(original_state_wrds, 0, i+1);
				double[] t_cost = new double[2];
				int res_state = get_left_equi_state_ngram(cur_suffix,t_cost,check_suffix);
				if(res_state==Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID || res_state==Symbol.NULL_LEFT_LM_STATE_SYM_ID)
					check_suffix=false;
				res_equi_state[i]=res_state;
				res_final_cost += t_cost[0];
				res_est_cost += t_cost[1];
			}

			cost[0]=res_final_cost;
			cost[1]=res_est_cost;
			//System.out.println("left org state: "+ Symbol.get_string(original_state_wrds) + "; euqiv state: " + Symbol.get_string(res_equi_state) +  " final: " +res_final_cost + "; estcost: " +res_est_cost  );
			return res_equi_state;
	 }

	 //return: res_state, cost[0](additional final cost), cost[1](estimateion cost)
	 private int get_left_equi_state_ngram(int[] cur_suffix, double[] cost, boolean should_check_suffix){
		 int last_wrd = cur_suffix[cur_suffix.length-1];
		 int res_state;
		 if(last_wrd == Symbol.NULL_LEFT_LM_STATE_SYM_ID){
			 res_state=Symbol.NULL_LEFT_LM_STATE_SYM_ID;//no state, no bow, no est_cost
		 }else if(last_wrd == Symbol.UNK_SYM_ID){
//			note: the <unk> may be needed to calculate probablity of: <unk> *
			 /*res_state=Symbol.NULL_LEFT_LM_STATE_SYM_ID;//no state, no bow, no est_cost
			 cost[0] += -get_prob(cur_suffix, cur_suffix.length, false);*/
			 res_state = last_wrd;
			 cost[1] += -get_prob(cur_suffix, cur_suffix.length, false);//est cost
		 }else if(last_wrd == Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID){//new backoff weight is already added outside this function?
			 if(should_check_suffix==false){//means should always backoff, and backoff weights are finalized
				 res_state=Symbol.NULL_LEFT_LM_STATE_SYM_ID;//no state, no bow, no est_cost, //backoff weight is already added outside this function?
			 }else if(cur_suffix.length==1){//first word
				 res_state = Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID;
				 cost[1] += 0;//-get_prob(cur_suffix, cur_suffix.length, false);//TODO: est unk
			 }else{
				 int[] backoff_history = Support.sub_int_array(cur_suffix, 0, cur_suffix.length-1);//ignore last wrd
				 double[] bow = new double[1];
				 boolean finalized_backoff = check_backoff_weight(backoff_history, bow, 0);//backoff weight is already added outside this function?
				 if(finalized_backoff==true){
					 res_state=Symbol.NULL_LEFT_LM_STATE_SYM_ID;//no state, no bow, no est_cost
				 }else{
					 res_state=Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID;
					 cost[1] += 0;//-get_prob(cur_suffix, cur_suffix.length, false);//TODO: est unk
				 }
			 }
		 }else{//regular word
			 if(should_check_suffix==false){//means should always backoff, and backoff weights are zero
				 res_state=Symbol.NULL_LEFT_LM_STATE_SYM_ID;//no state, no bow, no est_cost
				 cost[0] += -get_prob(cur_suffix, cur_suffix.length, false);//just need the last two words
			 }else{
				 int[] backoff_ngram = backoff_ngram_with_suffix(cur_suffix);
				 if(backoff_ngram==null){//should not backoff
					 res_state = last_wrd;
					 cost[1] += -get_prob(cur_suffix, cur_suffix.length, false);//est cost
				 }else{//backoff
					 /*for a suffix, if there is no ngram (excluding the suffix itself) ending with this suffix, it does not mean the suffix ngram itself will be always backoff
					  * that is why we cannot split the prob into two parts*/
					 //double cur_prob = get_prob(backoff_ngram, backoff_ngram.length, false);//we cannot split the prob
					 double cur_prob = get_prob(cur_suffix, cur_suffix.length, false);//instead of backoff_ngram, use cur_suffix here
					 cost[0] += -cur_prob;
					 if(cur_suffix.length==1){//first word
						 res_state = Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID;
						 cost[1] += 0;//-cur_prob*(g_order-cur_suffix.length)/g_order;//TODO: in fact, we want to estimate remaining bow weights
					 }else{
						 /*est_cost: think about the case without using equiv state, in which case we will use cur_prob as est cost
						  * in current case, since the cur_prob is in final cost, we should use zero as est cost*/
						 int[] backoff_history = Support.sub_int_array(cur_suffix, 0, cur_suffix.length-1);//ignore last wrd
						 double[] bow = new double[1];
						 boolean finalized_backoff = check_backoff_weight(backoff_history, bow, cur_suffix.length-backoff_ngram.length);
						 if(finalized_backoff==true){//no backoff weight for backoff_history, //no estimation required
							 res_state=Symbol.NULL_LEFT_LM_STATE_SYM_ID;//do not need to have this wrd as state
							 //cost[0] += -bow[0];//addtional final cost, already counted in: cur_prob = get_prob(cur_suffix, cur_suffix.length, false);
						 }else{
							 res_state=Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID;
							 //cost[0] += -bow[0];//addtional final cost, but still need to calculate some high-order backoff weight in the future, already counted in: cur_prob = get_prob(cur_suffix, cur_suffix.length, false);
							 //cost[1] += -( get_prob(cur_suffix, cur_suffix.length,false)- cur_prob- bow[0]);//TODO: use get_prob_euqi_state?
							 cost[1] += 0;//-cur_prob*(g_order-cur_suffix.length)/g_order;//TODO: in fact, we want to estimate remaining bow weights
						}
					}
				 }
			 }
		 }
		 return res_state;
	 }



    private int[] backoff_ngram_with_suffix(int[] suffix){//must: suffix.size()<g_order
    	if(root==null){
			System.out.println("root is null");
			System.exit(0);
		}
		ArrayList res= new ArrayList();
		LMHash  pos = root;

		for(int i=suffix.length; i>=1; i--){//reverse search
			int cur_wrd =suffix[i-1];
			LMHash next_layer=(LMHash) pos.get(cur_wrd+Symbol.lm_end_sym_id);
			//we assume that the existence of suffix implies the existence of ngram prob for the suffix in the lm file
			if(next_layer!=null && next_layer.containsKey(Symbol.LM_HAVE_SUFFIX_SYM_ID)==true){
				//TODO: we also need to make sure that next_layer.tbl_info contains prob (next_layer.tbl_info may not have a prob because the lm file have certain high-order ngram, but not have its sub-low-ngram)
				pos=next_layer;
				res.add(0,cur_wrd);
			}else{
				break;
			}
		}
		if(res.size()==suffix.length)//have ngram end with this suffix
			return null;
		else{
			return  Support.sub_int_array(res, 0, res.size());
		}
	}

	@Override
	protected double get_prob_backoff_state_specific(int[] ngram_wrds, int order, int n_additional_bow){
		int[] backoff_wrds = Support.sub_int_array(ngram_wrds, 0, ngram_wrds.length-1);
		double[] sum_bow =new double[1];
		check_backoff_weight(backoff_wrds, sum_bow, n_additional_bow);
		return sum_bow[0];
	}

    //num_backoff: for a given history, h(1)...h(n-1), the longest will be first used, followed by shorter one, until the number of times is reached
	//however, the search is reverse: first get short one, then longer one
	protected double get_backoff_weight_sum(int[] backoff_wrds, int req_num_backoff){
		double[] sum_bow =new double[1];
		check_backoff_weight(backoff_wrds, sum_bow, req_num_backoff);
		return sum_bow[0];
	}

  //if exist backoff weight for backoff_words, then return the accumated backoff weight
//	if there is no backoff weight for backoff_words, then, we can return the finalized backoff weight
	private boolean check_backoff_weight(int[] backoff_words, double[] sum_bow, int num_backoff){
		double sum=0;
		LMHash  pos =root;
		int start_use_i = num_backoff-1;//the start index that backoff should be applied
		Double bow=null;
		int i=backoff_words.length-1;
		for(; i>=0; i--){
			LMHash next_layer=(LMHash) pos.get(backoff_words[i]+Symbol.lm_end_sym_id);
			if(next_layer!=null){
				bow = (Double)next_layer.get(Symbol.BACKOFF_WGHT_SYM_ID);
				if(bow!=null && i<=start_use_i)
					sum += bow;
				pos=next_layer;
			}else{
				break;
			}
		}
		sum_bow[0]=sum;
		if(i==-1 && bow!=null)//the higest order have backoff weight, so we cannot finalize
			return false;
		else
			return true;
	}
//	######################################## end left equiv state ###########################################


//	######################################## general helper function ###########################################
	@Override
	protected int replace_with_unk(int in){
       if(root.containsKey(in)==true ||
    	   in == Symbol.NULL_RIGHT_LM_STATE_SYM_ID || //root must have
    	   in == Symbol.NULL_LEFT_LM_STATE_SYM_ID ||
    	   in == Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID ||
    	   in == Symbol.LM_STATE_OVERLAP_SYM_ID)
		 return in;
	   else
		 return Symbol.UNK_SYM_ID;
	}

//	######################################## read LM grammar by the Java implementation ###########################################

	/*a backoff node is a hashtable, it may include:
	 * (1) probability for next words: key id is positive
	 * (2) pointer to a next-layer backoff node (hashtable): key id is negative!!!
	 * (3) backoff weight for this node
	 * (4) suffix flag to indicate that there is ngrams start from this suffix
     */

	//read grammar locally by the Java implementation
	@Override
	public void read_lm_grammar_from_file(String grammar_file){
		start_loading_time = System.currentTimeMillis();
		root = new LMHash();
		root.put(Symbol.BACKOFF_WGHT_SYM_ID, NON_EXIST_WEIGHT);

		BufferedReader t_reader_tree = FileUtility.getReadFileStream(grammar_file,"utf8");
		Support.write_log_line("Reading grammar from file " + grammar_file, Support.INFO);
		String line;
		boolean start=false;
		int order=0;

		while((line=FileUtility.read_line_lzf(t_reader_tree))!=null){
			line = line.trim();
			if(line.matches("^\\s*$")==true){
				continue;
			}
			if( line.matches("^\\\\\\d-grams:\\s*$")==true){//\1-grams:
				start=true;
				order = (Integer.valueOf(line.substring(1, 2))).intValue();
				if(order > g_order)
					break;
				System.out.println("begin to read ngrams with order " + order);
				continue; //skip this line
			}
			if(start==true)
				add_rule(line,order, g_is_add_suffix_infor);
		}

		System.out.println("# of bow nodes: " + g_n_bow_nodes + " ; # of suffix nodes: " + g_n_suffix_nodes);
		System.out.println("add LMHash  " + g_n_bow_nodes);
		System.out.println("##### mem used (kb): " + Support.getMemoryUse());
		System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
	}

	/*all prefix information is in the backoff nodes
	 *all suffix information is in the suffix nodes
	 * */
	private void add_rule(String line, int order, boolean is_add_suffix_infor){//format: prob \t ngram \t backoff-weight
		num_rule_read++;
		if(num_rule_read%1000000==0){
			System.out.println("read rules " + num_rule_read);
		}
		String[] wrds = line.trim().split("\\s+");
		if(wrds.length<order+1 || wrds.length>order+2){//TODO: error
			//Support.write_log_line("wrong line: "+ line, Support.ERROR);
			return;
		}

		//### identify the position, and insert the backoff node if necessary
		LMHash  pos =root;
		for(int i=order-1; i>0; i--){//reverse search, start from the second-last word
			int cur_sym_id = Symbol.add_terminal_symbol(wrds[i]);
			LMHash  next_layer=(LMHash) pos.get(cur_sym_id+Symbol.lm_end_sym_id);
			if(next_layer!=null){
				pos=next_layer;
			}else{
				LMHash  new_tnode = new LMHash();//create new bow node
				g_n_bow_nodes++;
				if(g_n_bow_nodes%1000000==0){
					System.out.println("add LMHash  " + g_n_bow_nodes);
					System.out.println("##### mem used (kb): " + Support.getMemoryUse());
					System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
				}
				pos.put(cur_sym_id+Symbol.lm_end_sym_id, new_tnode);
				pos = new_tnode;
			}
			if (pos.containsKey(Symbol.BACKOFF_WGHT_SYM_ID)==false)
				pos.put(Symbol.BACKOFF_WGHT_SYM_ID, NON_EXIST_WEIGHT);//indicate it is a backoof node, to distinguish from a pure suffix node
		}

		//### add probability
		int last_word_id = Symbol.add_terminal_symbol(wrds[order]);
		pos.put(last_word_id, new Double(wrds[0]));//add probability


		//### add bow, and suffix information if necessary
		if( wrds.length==order+2 || //have bow weight to add
			is_add_suffix_infor==true){
			pos=root;
			int stop_id= (wrds.length==order+2) ? 1 : 2;//if only for suffix infor, then ignore the first word
			for(int i=order; i>=stop_id; i--){//reverse search, start from the last word
				int cur_sym_id = Symbol.add_terminal_symbol(wrds[i]);
				LMHash  next_layer=(LMHash) pos.get(cur_sym_id+Symbol.lm_end_sym_id);
				if(next_layer!=null){
					pos=next_layer;
				}else{
					LMHash  new_tnode = new LMHash();//create new suffix node
					g_n_bow_nodes++;
					if(g_n_bow_nodes%1000000==0){
						System.out.println("add LMHash  " + g_n_bow_nodes);
						System.out.println("##### mem used (kb): " + Support.getMemoryUse());
						System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
					}
					pos.put(cur_sym_id+Symbol.lm_end_sym_id, new_tnode);
					pos = new_tnode;
				}
				if(is_add_suffix_infor==true && i>1)
					pos.put(Symbol.LM_HAVE_SUFFIX_SYM_ID, 1);

				//add bow weight here
				if(wrds.length==order+2 ){ //have bow weight to add
					if(i==1){//force to override the backoff weight
						Double backoff_weight = new Double(wrds[order+1]);
						pos.put(Symbol.BACKOFF_WGHT_SYM_ID, backoff_weight);
					}else{
						if (pos.containsKey(Symbol.BACKOFF_WGHT_SYM_ID)==false)
							pos.put(Symbol.BACKOFF_WGHT_SYM_ID, NON_EXIST_WEIGHT);//indicate it is a backoof node, to distinguish from a pure suffix node
					}
				}
			}

		}
	}

	//not used
	public static class LMHash //4bytes
	{
		//######note: key must be positive integer, and value must not be null
		/*if key can be both positive and negative, then lot of collision, or will take very long to call get()
		 * imagine, we put all numbers in [1,20000] in hashtable, but try to call get() by numbers [-20000,-1], it will take very long time
		 */

		//TODO: should round the array size to a prime number?
		static double load_factor = 0.5;
		static int default_init_size = 5;

		int size=0;//4 bytes
		int[] key_array;//8 bytes
		Object[] val_array;//8 bytes

		public LMHash(int init_size){
			key_array = new int[init_size];
			val_array = new Object[init_size];
		}

		public LMHash(){
			key_array = new int[default_init_size];
			val_array = new Object[default_init_size];
		}

		//return the positive position for the key
		private int hash_pos(int key, int length){
			//return Math.abs(key % length);
			return key % length;
		}

		public Object get(int key) {
			Object res =null;
			int pos = hash_pos(key, key_array.length);
		    while(key_array[pos] != 0){//search until empty cell,
		      if (key_array[pos] == key)
		        return val_array[pos]; // found
		      pos++; //linear search
		      pos = hash_pos(pos, key_array.length);
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
				System.out.println("LMHash, value is null");
				System.exit(0);
			}

			int pos = hash_pos(key, key_array.length);
			while (key_array[pos] != 0){//search until empty cell,
				if (key_array[pos] == key){
					val_array[pos] = value; // found, and overwrite
					return;
				}
				pos++; //linear search
				pos = hash_pos(pos, key_array.length);
			}

			//we get to here, means we do not have this key, need to insert it
			//data_array[pos] = new LMItem(key, value);
			key_array[pos] = key;
			val_array[pos] = value;

			size++;
			if(size>=key_array.length*load_factor)
				expand_tbl();
		}

		private void expand_tbl(){
			int new_size = key_array.length*2+1;//TODO
			int[] new_key_array = new int[new_size];
			Object[] new_val_array = new Object[new_size];

			for(int i=0; i<key_array.length; i++){
				if(key_array[i]!=0){//add the element
					int pos = hash_pos(key_array[i], new_key_array.length) ;
					while(new_key_array[pos] != 0){//find first empty postition, note that it is not possible that we need to overwrite
						pos++; //linear search
						pos = hash_pos(pos, new_key_array.length);
					}
					new_key_array[pos] = key_array[i];
					new_val_array[pos] = val_array[i];
				}
			}
			key_array = new_key_array;
			val_array = new_val_array;
		}
	}

}
