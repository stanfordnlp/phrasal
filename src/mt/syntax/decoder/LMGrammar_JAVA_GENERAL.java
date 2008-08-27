package mt.syntax.decoder;

import java.io.BufferedReader;
import java.util.HashMap;

import mt.syntax.util.FileUtility;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

@SuppressWarnings("unchecked")
public class LMGrammar_JAVA_GENERAL extends LMGrammar {
	/*a backoff node is a hashtable, it may include:
	 * (1) probabilititis for next words
	 * (2) pointers to a next-layer backoff node (hashtable)
	 * (3) backoff weight for this node
	 * (4) suffix/prefix flag to indicate that there is ngrams start from this suffix
     */
	private LMHash  root=null;
	private int g_n_bow_nodes=0;
	private int g_n_suffix_nodes=0;
	static private float MIN_LOG_P= -9999.0f; //ngram prob must be smaller than this number
	static private double SUFFIX_ONLY= MIN_LOG_P*3; //ngram prob must be smaller than this number

	private double NON_EXIST_WEIGHT=0;//the history has not appeared at all
	private int num_rule_read=0;
	boolean g_is_add_prefix_infor=false;
	boolean g_is_add_suffix_infor=false;

	HashMap request_cache_prob = new HashMap();//cmd with result
	HashMap request_cache_backoff = new HashMap();//cmd with result
	HashMap request_cache_left_equiv = new HashMap();//cmd with result
	HashMap request_cache_right_equiv = new HashMap();//cmd with result
	int cache_size_limit= 250000;


	public LMGrammar_JAVA_GENERAL(int order, boolean is_add_suffix_infor, boolean is_add_prefix_infor){
		super(order);
		System.out.println("use java lm");
		g_is_add_prefix_infor = is_add_prefix_infor;
		g_is_add_suffix_infor = is_add_suffix_infor;
		Symbol.add_global_symbols(true);
		/*//debug
		LMHash[] t_arrays = new LMHash[10000000];
		System.out.println("##### mem used (kb): " + Support.getMemoryUse());
		System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
		for(int i=0; i<10000000;i++){
			LMHash t_h = new LMHash(5);
			double j=0.1f;
			t_h.put(i, j);

			//System.out.println("ele is " + t_h.get(i));
			t_arrays[i]=t_h;
			if(i%1000000==0){
				System.out.println(i +" ##### mem used (kb): " + Support.getMemoryUse());
				System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
			}
		}
		System.exit(0);
		//end*/


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


	//	signature of this item: i, j, lhs, states (in fact, we do not need i, j)
	private String get_signature(int[] words){
		StringBuffer res = new StringBuffer(words.length);
		for(int i=0; i<words.length; i++){
			res.append(" ");
			res.append(words[i]);
		}
		return res.toString();
	}



	/*note: the mismatch between srilm and our java implemtation is in: when unk words used as context, in java it will be replaced with "<unk>", but srilm will not, therefore the
	*lm cost by srilm may be smaller than by java, this happens only when the LM file have "<unk>" in backoff state*/
	//note: we never use check_bad_stuff here
	@Override
	protected double get_prob_specific(int[] ngram_wrds_in, int order, boolean check_bad_stuff){
		Double res;
		//cache
		//String sig = get_signature(ngram_wrds_in);
		//res = (Double)request_cache_prob.get(sig);
		//if(res!=null)return res;

		int[] ngram_wrds=replace_with_unk(ngram_wrds_in);//TODO
	    if(ngram_wrds[ngram_wrds.length-1]==Symbol.UNK_SYM_ID){//TODO: wrong implementation in hiero
			res= -Decoder.lm_ceiling_cost;
	    }else{
		    //TODO: untranslated words
			if(root==null){
				System.out.println("root is null");
				System.exit(0);
			}
			int last_word_id = ngram_wrds[ngram_wrds.length-1];
			LMHash  pos =root;
			//Double prob=(Double)pos.get(last_word_id);
			Double prob=get_valid_prob(pos,last_word_id);
			double bow_sum=0;
			for(int i=ngram_wrds.length-2; i>=0; i--){//reverse search, start from the second-last word
				LMHash  next_layer=(LMHash) pos.get(ngram_wrds[i]+Symbol.lm_end_sym_id);
				if(next_layer!=null){//have context/bow node
					pos=next_layer;
					//Double prob2=(Double)pos.get(last_word_id);
					Double prob2=get_valid_prob(pos,last_word_id);
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
			res = prob + bow_sum;
		}
	    //cache
	    //if(request_cache_prob.size()>cache_size_limit)
	    //	request_cache_prob.clear();
	    //request_cache_prob.put(sig, res);

		return res;
	}

	private Double get_valid_prob(LMHash pos, int wrd){
		Double res= (Double)pos.get(wrd);
		if(g_is_add_suffix_infor==false)
			return res;

		if(res!=null){
			if(res==SUFFIX_ONLY)
				return null;
			if(res>MIN_LOG_P)//logP without suffix flag
				return res;
			else//logP with suffix flag
				return res-MIN_LOG_P;
		}
		return null;
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
	//O(n^2)
	 @Override
	public int[] get_right_equi_state(int[] original_state_in, int order, boolean check_bad_stuff){
			if(Decoder.use_right_euqivalent_state==false || original_state_in.length!=g_order-1)
				return original_state_in;
			int[] res;
			//cache
			String sig = get_signature(original_state_in);
			res = (int[])request_cache_right_equiv.get(sig);
			if(res!=null) {
				//System.out.println("right cache hit");
				return res;
			}

			int[] original_state=replace_with_unk(original_state_in);//we do not put this statement at the beging to match the SRILM condition (who does not have replace_with_unk)
			res = new int[original_state.length];
			for(int i=1; i<=original_state.length; i++){//forward search
				int[] cur_wrds = Support.sub_int_array(original_state, i-1, original_state.length);
				if(have_prefix(cur_wrds)==false){
					res[i-1]=Symbol.NULL_RIGHT_LM_STATE_SYM_ID;
				}else{
					for(int j=i; j<=original_state.length; j++)
						res[j-1] = original_state[j-1];
					break;
				}
			}
			//cache
		    if(request_cache_right_equiv.size()>cache_size_limit)
		    	request_cache_right_equiv.clear();
		    request_cache_right_equiv.put(sig, res);

			//System.out.println("right org state: " + Symbol.get_string(original_state) +"; equiv state: " + Symbol.get_string(res));
			return res;
	  }

	 //O(n)
	 private boolean have_prefix(int[] words){
		 LMHash pos=root;
		 int i=words.length-1;
		 for( ; i>=0; i--){//reverse search
				LMHash  next_layer=(LMHash) pos.get(words[i]+Symbol.lm_end_sym_id);
				if(next_layer!=null){
					pos=next_layer;
				}else{
					break;
				}
		 }
		 if(i==-1 && pos.containsKey(Symbol.LM_HAVE_PREFIX_SYM_ID))
			 return true;
		 else
			 return false;
	 }

//		##################### end right equivalent state #############


	 //############################ begin left equivalent state ##############################


	/*several observation:
	 * In general:
	 * (1) In general, there might be more than one <bo> or <null>, and they can be in any position
	 * (2) in general, whenever there is a <bo> or <null> in a given ngram, then it will definitely backoff since it has same/more context
	*/
	//return: (1) the equivlant state vector; (2) the finalized cost; (3) the estimated cost
//	O(n^2)
	 @Override
	public int[] get_left_equi_state(int[] original_state_wrds_in, int order, double[] cost){
			if(Decoder.use_left_euqivalent_state==false){
				return original_state_wrds_in;
			}

			int[] original_state_wrds = replace_with_unk(original_state_wrds_in);//we do not put this statement at the beging to match the SRILM condition (who does not have replace_with_unk)

			//## deal with case overlap state
			if(original_state_wrds.length<g_order-1){
				for(int i=0; i<original_state_wrds.length; i++){
					int[] cur_wrds = Support.sub_int_array(original_state_wrds, 0, i+1);
					cost[1] += -get_prob(cur_wrds, cur_wrds.length, false);//est cost;
				}
				return original_state_wrds;
		    }

			//## non-overlaping state
			int[] res_equi_state = new int[original_state_wrds.length];
			double res_final_cost = 0.0;//finalized cost
			double res_est_cost=0.0;//estimated cost
			for(int i=original_state_wrds.length; i>0; i--){//BACKWORD search
				int[] cur_wrds = Support.sub_int_array(original_state_wrds, 0, i);
				if(have_suffix(cur_wrds)==false){
					int last_wrd = cur_wrds[i-1];
					if(last_wrd==Symbol.UNK_SYM_ID){
						 res_equi_state[i-1] = last_wrd;
						 res_est_cost += -get_prob(cur_wrds, cur_wrds.length, false);//est cost
					 }else{
						 if(last_wrd!=Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID )
							 res_final_cost += -get_prob(cur_wrds, cur_wrds.length, false);

						 res_equi_state[i-1]=Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID;
						 /*//TODO: for simplicity, we may just need BACKOFF_LEFT_LM_STATE_SYM_ID??
						 int[] backoff_history = Support.sub_int_array(cur_wrds, 0, cur_wrds.length-1);//ignore last wrd
						 double[] bow = new double[1];
						 boolean finalized_backoff = check_backoff_weight(backoff_history, bow, 0);//backoff weight is already added outside this function?
						 if(finalized_backoff==true){
							 res_equi_state[i-1]=Symbol.NULL_LEFT_LM_STATE_SYM_ID;//no state, no bow, no est_cost
						 }else{
							 res_equi_state[i-1]=Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID;
						 }*/
					 }
				}else{//have suffix
					for(int j=i; j>0; j--){
						res_equi_state[j-1] = original_state_wrds[j-1];
						cur_wrds = Support.sub_int_array(original_state_wrds, 0, j);
						res_est_cost += -get_prob(cur_wrds, cur_wrds.length, false);//est cost;
					}
					break;
				}
			}

			cost[0]=res_final_cost;
			cost[1]=res_est_cost;
			//System.out.println("left org state: "+ Symbol.get_string(original_state_wrds) + "; euqiv state: " + Symbol.get_string(res_equi_state) +  " final: " +res_final_cost + "; estcost: " +res_est_cost  );
			return res_equi_state;
	 }



	 private boolean have_suffix(int[] words){
		 LMHash pos=root;
		 for(int i=words.length-2; i>=0; i--){//reverse search, start from the second-last word
				LMHash  next_layer=(LMHash) pos.get(words[i]+Symbol.lm_end_sym_id);
				if(next_layer!=null){
					pos=next_layer;
				}else{
					return false;
				}
		 }
		 int last_word_id = words[words.length-1];
		 Double prob=(Double)pos.get(last_word_id);
		 if(prob!=null && prob<=MIN_LOG_P)
			 return true;
		 else
			 return false;
	 }


    @Override
		protected double get_prob_backoff_state_specific(int[] ngram_wrds, int order, int n_additional_bow){
		int[] backoff_wrds = Support.sub_int_array(ngram_wrds, 0, ngram_wrds.length-1);
		double[] sum_bow =new double[1];
		check_backoff_weight(backoff_wrds, sum_bow, n_additional_bow);
		return sum_bow[0];
	}



  //if exist backoff weight for backoff_words, then return the accumated backoff weight
//	if there is no backoff weight for backoff_words, then, we can return the finalized backoff weight
	private boolean check_backoff_weight(int[] backoff_words, double[] sum_bow, int num_backoff){
		if(backoff_words.length<=0)
			return false;

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
    	   in == Symbol.NULL_RIGHT_LM_STATE_SYM_ID ||
    	   in == Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID)
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
                          order = Integer.parseInt(line.substring(1, 2));
				if(order > g_order)
					break;
				System.out.println("begin to read ngrams with order " + order);
				continue; //skip this line
			}
			if(start==true)
				add_rule(line,order, g_is_add_suffix_infor, g_is_add_prefix_infor);
		}

		System.out.println("# of bow nodes: " + g_n_bow_nodes + " ; # of suffix nodes: " + g_n_suffix_nodes);
		System.out.println("add LMHash  " + g_n_bow_nodes);
		System.out.println("##### mem used (kb): " + Support.getMemoryUse());
		System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
	}


	private void add_rule(String line, int order, boolean is_add_suffix_infor, boolean is_add_prefix_infor){//format: prob \t ngram \t backoff-weight
		num_rule_read++;
		if(num_rule_read%1000000==0){
			System.out.println("read rules " + num_rule_read);
			//System.out.println("##### mem used (kb): " + Support.getMemoryUse());
			System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
		}
		String[] wrds = line.trim().split("\\s+");

		if(wrds.length<order+1 || wrds.length>order+2){//TODO: error
			//Support.write_log_line("wrong line: "+ line, Support.ERROR);
			return;
		}
		int last_word_id = Symbol.add_terminal_symbol(wrds[order]);

		//##### identify the BOW position, insert the backoff node if necessary, and add suffix information
		LMHash  pos =root;
		for(int i=order-1; i>0; i--){//reverse search, start from the second-last word
			if(is_add_suffix_infor==true){
				Double t_prob = (Double) pos.get(last_word_id);
				if(t_prob!=null){
					if(t_prob>MIN_LOG_P){//have prob, but not suffix flag
						double tem = t_prob+MIN_LOG_P;
						pos.put(last_word_id, tem);//overwrite
					}
				}else{
					pos.put(last_word_id, SUFFIX_ONLY);
				}
			}
			int cur_sym_id = Symbol.add_terminal_symbol(wrds[i]);
			LMHash  next_layer=(LMHash) pos.get(cur_sym_id+Symbol.lm_end_sym_id);
			if(next_layer!=null){
				pos=next_layer;
			}else{
				LMHash  new_tnode = new LMHash();//create new bow node
				pos.put(cur_sym_id+Symbol.lm_end_sym_id, new_tnode);
				pos = new_tnode;

				g_n_bow_nodes++;
				if(g_n_bow_nodes%1000000==0){
					System.out.println("add LMHash  " + g_n_bow_nodes);
					//System.out.println("##### mem used (kb): " + Support.getMemoryUse());
					System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
				}
			}
			if (pos.containsKey(Symbol.BACKOFF_WGHT_SYM_ID)==false)
				pos.put(Symbol.BACKOFF_WGHT_SYM_ID, NON_EXIST_WEIGHT);//indicate it is a backoof node, to distinguish from a pure prefix node
		}

		//##### add probability
		if(is_add_suffix_infor==true && pos.containsKey(last_word_id)){
			double tem= Double.parseDouble(wrds[0]) + MIN_LOG_P;
			pos.put(last_word_id, tem);//add probability and suffix flag
		}else
			pos.put(last_word_id, Double.parseDouble(wrds[0]));//add probability

		//##### add prefix infor, a prefix node is just like a BOW node
		if(is_add_prefix_infor==true){
			pos.put(Symbol.LM_HAVE_PREFIX_SYM_ID, 1);//for preifx [1,order-1]
			for(int i=1; i<order-1; i++){//ignore the last prefix
				pos=root;//reset pos
				for(int j=i; j>=1; j--){//reverse search: [1,i]
					int cur_sym_id = Symbol.add_terminal_symbol(wrds[j]);
					LMHash  next_layer=(LMHash) pos.get(cur_sym_id+Symbol.lm_end_sym_id);
					if(next_layer!=null){
						pos=next_layer;
					}else{
						LMHash  new_tnode = new LMHash();//create new prefix node
						pos.put(cur_sym_id+Symbol.lm_end_sym_id, new_tnode);
						pos = new_tnode;

						g_n_bow_nodes++;
						if(g_n_bow_nodes%1000000==0){
							System.out.println("add LMHash  " + g_n_bow_nodes);
							//System.out.println("##### mem used (kb): " + Support.getMemoryUse());
							System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
						}
					}
				}
				pos.put(Symbol.LM_HAVE_PREFIX_SYM_ID, 1);//only the last node should have this flag
			}
		}


		//##### add bow
		if( wrds.length==order+2){ //have bow weight to add
			pos=root;
			for(int i=order; i>=1; i--){//reverse search, start from the last word
				int cur_sym_id = Symbol.add_terminal_symbol(wrds[i]);
				LMHash  next_layer=(LMHash) pos.get(cur_sym_id+Symbol.lm_end_sym_id);
				if(next_layer!=null){
					pos=next_layer;
				}else{
					LMHash  new_tnode = new LMHash();//create new bow node
					pos.put(cur_sym_id+Symbol.lm_end_sym_id, new_tnode);
					pos = new_tnode;

					g_n_bow_nodes++;
					if(g_n_bow_nodes%1000000==0){
						System.out.println("add LMHash  " + g_n_bow_nodes);
						//System.out.println("##### mem used (kb): " + Support.getMemoryUse());
						System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
					}
				}

				//add bow weight here
				if(i==1){//force to override the backoff weight
					Double backoff_weight = new Double(wrds[order+1]);
					pos.put(Symbol.BACKOFF_WGHT_SYM_ID, backoff_weight.doubleValue());
				}else{
					if (pos.containsKey(Symbol.BACKOFF_WGHT_SYM_ID)==false)
						pos.put(Symbol.BACKOFF_WGHT_SYM_ID, NON_EXIST_WEIGHT);//indicate it is a backoof node, to distinguish from a pure prefix node
				}
			}
		}
	}


	/* ###################### not used
	 private boolean have_suffix_old(int[] words){
		 LMHash pos=root;
		 int i=words.length-1;
		 for(; i>=0; i--){//reverse search
				LMHash  next_layer=(LMHash) pos.get(words[i]+Symbol.lm_end_sym_id);
				if(next_layer!=null){
					pos=next_layer;
				}else{
					break;
				}
		 }
		 if(i==-1 && pos.containsKey(Symbol.LM_HAVE_SUFFIX_SYM_ID))
			 return true;
		 else
			 return false;
	 }
	 */


	//in theory: 64bytes (init size is 5)
	//in practice: 86 bytes (init size is 5)
	//in practice: 132 bytes (init size is 10)
	//in practice: 211 bytes (init size is 20)
	//important note: if we use tbl.put(key, new Integer(1)) instead of tbl.put(key, (new Integer(1)).intValue()), then for each element, we waste 16 bytes for the Integer object,
	//and the GC will not collect this Double object, because the hashtable ref it
	public static class LMHash //4bytes
	{
		//######note: key must be positive integer, and value must not be null
		/*if key can be both positive and negative, then lot of collision, or will take very long to call get()
		 * imagine, we put all numbers in [1,20000] in hashtable, but try to call get() by numbers [-20000,-1], it will take very long time
		 */

		//TODO: should round the array size to a prime number?
		static double load_factor = 0.6;
		static int default_init_size = 5;

		int size=0;//8 bytes?
		int[] key_array;//pointer itself is 4 bytes?, when it is newed, then add 10 more bytes, and the int itself
		Object[] val_array;//pointer itself is 4 bytes?, when it is newed, then add 10 more bytes, and the object itself

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