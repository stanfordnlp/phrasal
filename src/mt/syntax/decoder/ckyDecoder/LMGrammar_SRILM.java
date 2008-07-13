package mt.syntax.decoder.ckyDecoder;

import java.util.ArrayList;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

public class LMGrammar_SRILM  extends LMGrammar {
	SWIGTYPE_p_Ngram p_srilm=null;
	
	public LMGrammar_SRILM(int order){
		super(order);
		System.out.println("use local srilm");
		p_srilm = srilm.initLM(order, Symbol.lm_start_sym_id, Symbol.lm_end_sym_id );
		Symbol.add_global_symbols(false);
	}
	
//	read grammar locally by the Java implementation
	public void read_lm_grammar_from_file(String grammar_file){
		start_loading_time = System.currentTimeMillis();
		System.out.println("read lm by srilm tool");
        srilm.readLM(p_srilm, grammar_file);
        System.out.println("read lm finished");	
		//System.out.println("##### mem used (kb): " + Support.getMemoryUse());
		System.out.println("##### time used (seconds): " + (System.currentTimeMillis()-start_loading_time)/1000);
	}
	
	
	//big bug: we assume no equiv state is used if we use srilm
	//note: when using the srilm C interfact, the srilm itself will NOT do the replacement to unk, so it will return a zero-prob for unknown word
	//however, if using the srilm in the command line, the srilm will do the replacement to unk
	//since we have trouble to run the replace_with_unk (because we do not know the vocabulary), we will let srilm return a zero-prob, and then replace with the ceiling cost
	/*note: the mismatch between srilm and our java implemtation is in: when unk words used as context, in java it will be replaced with "<unk>", but srilm will not, therefore the 
	*lm cost by srilm may be smaller than by java, this happens only when the LM file have "<unk>" in backoff state*/
   protected double get_prob_specific(int[] ngram_wrds, int order, boolean check_bad_stuff){
	   /*int[] ngram_wrds=replace_with_unk(ngram_wrds_in);
	   if(ngram_wrds[ngram_wrds.length-1]==Symbol.UNK_SYM_ID)//TODO: wrong implementation in hiero
			return -Decoder.lm_ceiling_cost;
	   //TODO: untranslated words*/
	   
       int hist_size =  ngram_wrds.length-1;
       double res=0.0;
       SWIGTYPE_p_unsigned_int hist;
       //TODO in principle, there should not have bad left-side state symbols, though need to check
       if(check_bad_stuff ==true && Decoder.use_right_euqivalent_state==true){
	       //make sure the state input does not have null words, as otherwise the srilm will replace it with unk    
		   ArrayList clean_ngram = ignore_null_right_words(ngram_wrds);	
		   hist_size = clean_ngram.size()-1;
		   if(hist_size>=order || hist_size<0){
				  System.out.println("Error: hist size is " + hist_size);
			  	  return 0;//TODO: zero cost?
		   }
	       hist = srilm.new_unsigned_array(hist_size);
	       for(int i=0; i< hist_size; i++){
	           srilm.unsigned_array_setitem(hist, i, (Integer)clean_ngram.get(i));
	       }
	       res  = srilm.getProb_lzf(p_srilm, hist, hist_size, (Integer)clean_ngram.get(hist_size));
   		}else{
   		   hist = srilm.new_unsigned_array(hist_size);
 	       for(int i=0; i< hist_size; i++){
 	           srilm.unsigned_array_setitem(hist,i, ngram_wrds[i]);
 	       }
 	       res  = srilm.getProb_lzf(p_srilm, hist, hist_size, ngram_wrds[hist_size]);
   		}
       srilm.delete_unsigned_array(hist);
       return res;
   }
  
   //TODO: big bug, assume left-equiv state is not used by srilm
   //TODO: now,we assume this function is called by get_prob_backoff_state, which have only left_context as input, so only the last wrd is <bo>, all backoff_wrds are good
	private double get_backoff_weight_sum(int[] backoff_wrds, int req_num_backoff){
	    System.out.println("Error: call get_backoff_weight_sum in srilm, must exit");
		System.exit(0);
		return -1;
	   /*SWIGTYPE_p_unsigned_int hist = srilm.new_unsigned_array(backoff_wrds.length);
       for(int i=0; i< backoff_wrds.length; i++){
           srilm.unsigned_array_setitem(hist, i, backoff_wrds[i]);
       }
       int min_len = backoff_wrds.length - req_num_backoff + 1;
       return srilm.get_backoff_weight_sum(p_srilm, hist, backoff_wrds.length, min_len);*/		
	}
	
	public int[] get_left_equi_state(int[] original_state_wrds, int order, double[] cost){
		//int[] original_state_wrds = replace_with_unk(original_state_wrds_in);//???
		if(Decoder.use_left_euqivalent_state==false){
			return original_state_wrds;
		}
			
		//## non-overlaping state		
		System.out.println("Error: call get_left_euqi_state in srilm, must exit");
		System.exit(0);
		return null;
	}
	
	public void write_vocab_map_srilm(String fname){
		srilm.write_default_vocab_map(fname);
	}
	
	protected double get_prob_backoff_state_specific(int[] ngram_wrds, int order, int n_additional_bow){
		System.out.println("Error: call get_prob_backoff_state_specific in srilm, must exit");
		System.exit(0);
		return 0;
	}
	
    //	if exist backoff weight for backoff_words, then return the accumated backoff weight
	private boolean check_backoff_weight(int[] backoff_words, double[] sum_bow, int num_backoff){
		System.out.println("Error: call check_backoff_weight in srilm, must exit");
		System.exit(0);
		return true;
		/*
		if(num_backoff>0)
			sum_bow[0]= get_backoff_weight_sum(backoff_words, num_backoff);
		
		SWIGTYPE_p_unsigned_int srilm_hist = srilm.new_unsigned_array(backoff_words.length);
		for(int i=0; i<backoff_words.length; i++){
		        srilm.unsigned_array_setitem(srilm_hist, i, backoff_words[i]);
		}	
		long depth = srilm.getBOW_depth(p_srilm, srilm_hist, backoff_words.length);
		if(depth >=backoff_words.length){//canot be finalized
			System.out.println("cannot be finalized");
			return false;
		}else
			return true;*/		
	}
		  
	private long getBOW_depth(int[] hist, int order){
		  SWIGTYPE_p_unsigned_int srilm_hist = srilm.new_unsigned_array(hist.length);
		  for(int i=0; i<hist.length; i++){
		          srilm.unsigned_array_setitem(srilm_hist, i, hist[i]);
		  }	
		  return srilm.getBOW_depth(p_srilm, srilm_hist, hist.length);  
	  }
	
	 //the returned array lenght must be the same the len of original_state
	 //the only change to the original_state is: replace with more non-null state words to null state
	  public int[] get_right_equi_state(int[] original_state, int order, boolean check_bad_stuff){
		    //int[] original_state=replace_with_unk(original_state_in);
			if(Decoder.use_right_euqivalent_state==false || original_state.length!=g_order-1)
				return original_state;
			
			//## non-overlaping state		
			System.out.println("Error: call get_right_equi_state in srilm, must exit");
			System.exit(0);
			return null;
			/*int[] res = new int[original_state.length];
			long depth=0;
			if(check_bad_stuff==true){//must make sure the state input does not have null words
				ArrayList clean_state = ignore_null_right_words(original_state);				
				if(clean_state.size()>0){				
					SWIGTYPE_p_unsigned_int hist = srilm.new_unsigned_array(clean_state.size());
					for(int i=0; i< clean_state.size(); i++){
				           srilm.unsigned_array_setitem(hist,i, (Integer)clean_state.get(i));
				    }		
					depth = srilm.getBOW_depth(p_srilm, hist, clean_state.size());//return the depth that words has backoff state
				}
				if(depth==clean_state.size())//no change required
					return original_state;
			}else{					
				SWIGTYPE_p_unsigned_int hist = srilm.new_unsigned_array(original_state.length);
				for(int i=0; i< original_state.length; i++){
			           srilm.unsigned_array_setitem(hist,i, original_state[i]);
			    }		
				depth = srilm.getBOW_depth(p_srilm, hist, original_state.length);//return the depth that words has backoff state		
				if(depth==original_state.length)//no change required
					return original_state;
			}
			
			for(int i=res.length-1; i>=0; i--){//reverse search
				int cur_wrd = original_state[i];
				if(res.length-i<=depth){
					res[i] = cur_wrd;
				}else{//do not have a backoff weight
					for(int k=i; k>=0; k--)
						res[k] = Symbol.NULL_RIGHT_LM_STATE_SYM_ID;
					break;
				}
			}
			System.out.println("right origianl state: " + Symbol.get_string(original_state) +"; equiv state: " + Symbol.get_string(res));
			return res;*/
	  }
	  
	  //both left and right must be clean
		@SuppressWarnings("unchecked") 
	   private static ArrayList ignore_null_right_words(int[] ngram_wrds){
		   ArrayList t_ngram = new ArrayList();
		   for(int t=ngram_wrds.length-1; t>=0; t--){
			   if(ngram_wrds[t]==Symbol.NULL_RIGHT_LM_STATE_SYM_ID)//skip all the null words left
				   break;
			   t_ngram.add(0,ngram_wrds[t]);	   
		   }
		   return t_ngram;
	   }
			      
		
	   public int replace_with_unk(int in){ 	
		   System.out.println("Error: call replace_with_unk in srilm, must exit");
			System.exit(0);
			return 0;
	  }
		
		private  int[] backoff_ngram_with_suffix(int[] suffix){
			System.out.println("Error: call backoff_ngram_with_suffix in srilm, must exit");
			System.exit(0);
			return null;
		}
}
