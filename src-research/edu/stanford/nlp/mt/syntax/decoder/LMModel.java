package edu.stanford.nlp.mt.syntax.decoder;

import java.util.ArrayList;
import java.util.HashMap;

import edu.stanford.nlp.mt.syntax.decoder.TMGrammar.Rule;

/* Zhifei Li, <zhifei.work@gmail.com>
 * Johns Hopkins University
 */

//Stateless=false, contextual=false
@SuppressWarnings("unchecked")
public class LMModel extends Model {
  /*
   * we assume the LM is in ARPA format for equivalent state: (1)we assume it is
   * a backoff lm, and high-order ngram implies low-order ngram; absense of
   * low-order ngram implies high-order ngram (2) for a ngram, existence of
   * backoffweight => existence a probability Two ways of dealing with low
   * counts: SRILM: don't multiply zeros in for unknown words Pharaoh: cap at a
   * minimum score exp(-10), including unknown words
   */
  static long time_step1 = 0, time_step2 = 0, time_step3 = 0;
  int g_order = 3;
  // boolean add_boundary=false; //this is needed unless the text already has
  // <s> and </s>

  LMGrammar p_lm = null;

  public LMModel(int order_in, LMGrammar lm, double wght) {
    super(wght);
    g_order = order_in;
    p_lm = lm;
  }

  /*
   * when calculate transition prob: when saw a <bo>, then need to add backoff
   * weights, start from non-state words
   */
  // return states and cost
  private HashMap lookup_words1_equv_state(int[] en_words, ArrayList antstates) {
    // long start_step1 = Support.current_time();
    // for left state
    ArrayList left_state_org_wrds = new ArrayList();
    // boolean keep_left_state = true;//stop if: (1) end of rule; (2)
    // left_state_org_wrds.size()==g_order-1; (3) seperating point;
    ArrayList cur_ngram = new ArrayList(); // before l_context finish, left
                                           // state words are in cur_ngram,
                                           // after that, all words will be
                                           // replaced with right state words
    double trans_cost = 0.0;

    for (int c = 0; c < en_words.length; c++) {
      int c_id = en_words[c];
      if (Symbol.is_nonterminal(c_id) == true) {// non terminal symbol
        if (antstates != null) {

          int index = Symbol.get_eng_non_terminal_id(c_id);
          HashMap s_tbl = (HashMap) antstates.get(index);
          int[] l_context = (int[]) s_tbl.get(Symbol.LM_L_STATE_SYM_ID);
          int[] r_context = (int[]) s_tbl.get(Symbol.LM_R_STATE_SYM_ID);
          if (l_context.length != r_context.length) {
            System.out
                .println("Error, left and right contexts not having same length");
            System.exit(0);
          }
          // ##################left context
          // System.out.println("left context: " +
          // Symbol.get_string(l_context));
          for (int i = 0; i < l_context.length; i++) {
            int t = l_context[i];
            cur_ngram.add(t);
            if (t == Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID) {// always calculate
                                                           // cost for <bo>:
                                                           // additional backoff
                                                           // weight
              int n_a_bow = cur_ngram.size() - (i + 1);
              trans_cost += -p_lm.get_prob_backoff_state(cur_ngram,
                  cur_ngram.size(), n_a_bow);// compute additional backoff
                                             // weight
              if (cur_ngram.size() == g_order)
                cur_ngram.remove(0);// change the cur_ngram
            } else if (cur_ngram.size() == g_order) {
              trans_cost += -p_lm.get_prob(cur_ngram, g_order, false);// compute
                                                                      // the
                                                                      // current
                                                                      // word
                                                                      // probablity
              cur_ngram.remove(0);// change the cur_ngram
            }
            if (left_state_org_wrds.size() < g_order - 1)
              left_state_org_wrds.add(t);
          }

          // ####################right context
          // note: left_state_org_wrds will never take words from right context
          // because it is either duplicate or out of range
          // also, we will never score the right context probablity because they
          // are either duplicate or partional ngram
          // System.out.println("right context: " +
          // Symbol.get_string(r_context));
          int t_size = cur_ngram.size();
          for (int i = 0; i < r_context.length; i++)
            cur_ngram.set(t_size - r_context.length + i, r_context[i]);// replace
                                                                       // context
        } else {// ant state is null
          System.out.println("Error, in lookup_wrods1, null antstates");
          System.exit(0);
        }
      } else {// terminal words
        // System.out.println("terminal: " + Symbol.get_string(c_id));
        cur_ngram.add(c_id);
        if (cur_ngram.size() == g_order) {
          trans_cost += -p_lm.get_prob(cur_ngram, g_order, true);// compute the
                                                                 // current word
                                                                 // probablity
          cur_ngram.remove(0);// change the cur_ngram
        }
        if (left_state_org_wrds.size() < g_order - 1)
          left_state_org_wrds.add(c_id);
      }
    }
    // ### create tabl
    HashMap res_tbl = new HashMap();
    HashMap model_states = new HashMap();
    res_tbl.put(Symbol.ITEM_STATES_SYM_ID, model_states);

    // ##### get left euquiv state
    double[] lm_l_cost = new double[2];
    int[] equiv_l_state = p_lm.get_left_equi_state(
        Support.sub_int_array(left_state_org_wrds, 0,
            left_state_org_wrds.size()), g_order, lm_l_cost);
    model_states.put(Symbol.LM_L_STATE_SYM_ID, equiv_l_state);
    // System.out.println("left state: " + Symbol.get_string(equiv_l_state));

    // ##### trabsition and estimate cost
    trans_cost += lm_l_cost[0];// add finalized cost for the left state words
    res_tbl.put(Symbol.TRANSITION_COST_SYM_ID, trans_cost);
    // System.out.println("##tran cost: " + trans_cost +" lm_l_cost[0]: " +
    // lm_l_cost[0]);
    double lm_future_est_cost;
    if (Decoder.use_left_euqivalent_state)
      lm_future_est_cost = lm_l_cost[1];
    else
      lm_future_est_cost = estimate_state_prob(model_states, false, false);// bonus
                                                                           // function
    res_tbl.put(Symbol.BONUS_SYM_ID, lm_future_est_cost);
    // ##### get right equiv state
    // if(cur_ngram.size()>g_order-1 || equiv_l_state.length>g_order-1)
    // System.exit(0);
    int[] equiv_r_state = p_lm.get_right_equi_state(
        Support.sub_int_array(cur_ngram, 0, cur_ngram.size()), g_order, true);
    model_states.put(Symbol.LM_R_STATE_SYM_ID, equiv_r_state);
    // System.out.println("right state: " + Symbol.get_string(right_state));

    // time_step2 += Support.current_time()-start_step2;
    return res_tbl;
  }

  private double score_chunk(ArrayList l_words,
      boolean consider_incomplete_ngram, boolean skip_start) {
    if (l_words.size() <= 0)
      return 0.0;
    if (consider_incomplete_ngram == true) {
      if (skip_start == true)
        return -p_lm.score_a_sent(l_words, g_order, 2);// skip the START symbol
      else
        return -p_lm.score_a_sent(l_words, g_order, 1);
    } else {
      return -p_lm.score_a_sent(l_words, g_order, g_order);
    }
  }

  // return cost, including partial ngrams
  /*
   * in general: consider all the complete ngrams, and all the incomplete-ngrams
   * that WILL have sth fit into its left side, soif the left side of
   * incomplete-ngrams is a ECLIPS, then ignore the incomplete-ngramsif the left
   * side of incomplete-ngrams is a Non-Terminal, then consider the
   * incomplete-ngramsif the left side of incomplete-ngrams is boundary of a
   * rule, then consider the incomplete-ngrams
   */
  private double estimate_rule_prob(int[] en_words) {
    double res = 0.0;
    boolean consider_incomplete_ngram = true;
    ArrayList l_words = new ArrayList();
    boolean skip_start = true;
    if (en_words[0] != Symbol.START_SYM_ID)
      skip_start = false;

    for (int c = 0; c < en_words.length; c++) {
      int c_wrd = en_words[c];
      /*
       * if(c_wrd==Symbol.ECLIPS_SYM_ID){ res +=
       * score_chunk(l_words,consider_incomplete_ngram,skip_start);
       * consider_incomplete_ngram=false;//for the LM bonus function: this
       * simply means the right state will not be considered at all because all
       * the ngrams in right-context will be incomplete l_words.clear();
       * skip_start=false; }else
       */if (Symbol.is_nonterminal(c_wrd) == true) {
        res += score_chunk(l_words, consider_incomplete_ngram, skip_start);
        consider_incomplete_ngram = true;
        l_words.clear();
        skip_start = false;
      } else {
        l_words.add(c_wrd);
      }
    }
    res += score_chunk(l_words, consider_incomplete_ngram, skip_start);
    return res;
  }

  // this function is called when left_equiv state is NOT used
  // in state, all the ngrams are incomplete
  // only get the estimation for the left-state
  // get the true prob for right-state, if add_end==true
  private double estimate_state_prob(HashMap state, boolean add_start,
      boolean add_end) {
    double res = 0.0;
    int[] l_context = (int[]) state.get(Symbol.LM_L_STATE_SYM_ID);

    if (l_context != null) {
      ArrayList list;
      if (add_start == true) {
        list = new ArrayList(l_context.length + 1);
        list.add(Symbol.START_SYM_ID);
      } else {
        list = new ArrayList(l_context.length);
      }
      for (int k = 0; k < l_context.length; k++) {
        // if(l_context[k]!=Symbol.LM_STATE_OVERLAP_SYM_ID)
        list.add(l_context[k]);
      }
      boolean consider_incomplete_ngram = true;
      boolean skip_start = true;
      if ((Integer) list.get(0) != Symbol.START_SYM_ID)
        skip_start = false;
      res += score_chunk(list, consider_incomplete_ngram, skip_start);
    }
    /*
     * if(add_start==true) System.out.println("left context: "
     * +Symbol.get_string(l_context) + ";prob "+res);
     */
    if (add_end == true) {// only when add_end is true, we get a complete ngram,
                          // otherwise, all ngrams in r_state are incomplete and
                          // we should do nothing
      int[] r_context = (int[]) state.get(Symbol.LM_R_STATE_SYM_ID);
      ArrayList list = new ArrayList(r_context.length + 1);
      for (int k = 0; k < r_context.length; k++)
        list.add(r_context[k]);
      list.add(Symbol.STOP_SYM_ID);
      double tem = score_chunk(list, false, false);// consider_incomplete_ngram=false;
                                                   // skip_start=false
      res += tem;
      // System.out.println("right context:"+ Symbol.get_string(r_context) +
      // "; score: " + tem);
    }
    return res;
  }

  private double compute_euqiv_state_final_transition(HashMap state) {
    double res = 0.0;
    ArrayList cur_ngram = new ArrayList();
    int[] l_context = (int[]) state.get(Symbol.LM_L_STATE_SYM_ID);
    int[] r_context = (int[]) state.get(Symbol.LM_R_STATE_SYM_ID);
    if (l_context.length != r_context.length) {
      System.out
          .println("Error, left and right contexts not having same length");
      System.exit(0);
    }

    // ##################left context
    cur_ngram.add(Symbol.START_SYM_ID);
    for (int i = 0; i < l_context.length; i++) {
      int t = l_context[i];
      cur_ngram.add(t);
      if (t == Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID) {// calculate cost for <bo>:
                                                     // additional backoff
                                                     // weight
        int n_a_bow = cur_ngram.size() - (i + 1);
        res += -p_lm.get_prob_backoff_state(cur_ngram, cur_ngram.size(),
            n_a_bow);// compute additional backoff weight
      } else {// partial ngram
        res += -p_lm.get_prob(cur_ngram, cur_ngram.size(), false);// compute the
                                                                  // current
                                                                  // word
                                                                  // probablity
      }
      if (cur_ngram.size() == g_order)
        cur_ngram.remove(0);// change the cur_ngram
    }

    // ####################right context
    // switch context, we will never score the right context probablity because
    // they are either duplicate or partional ngram
    int t_size = cur_ngram.size();
    for (int i = 0; i < r_context.length; i++)
      cur_ngram.set(t_size - r_context.length + i, r_context[i]);// replace
                                                                 // context

    cur_ngram.add(Symbol.STOP_SYM_ID);
    res += -p_lm.get_prob(cur_ngram, cur_ngram.size(), false);// compute the
                                                              // current word
                                                              // probablity
    return res;
  }

  /*
   * the transition cost for LM: sum of the costs of the new ngrams created
   * depends on the antstates and current rule
   */
  // antstates: ArrayList of states of this model in ant items
  @Override
  public HashMap transition(Rule r, ArrayList antstates, int i, int j, int j1) {// j1
                                                                                // is
                                                                                // not
                                                                                // used
                                                                                // at
                                                                                // all
    // long start = Support.current_time();
    HashMap res = lookup_words1_equv_state(r.english, antstates);
    // Chart.g_time_lm += Support.current_time()-start;
    return res;
  }

  /* depends on the rule only */
  /*
   * will consider all the complete ngrams, and all the incomplete-ngrams that
   * will have sth fit into its left side
   */
  @Override
  public double estimate(Rule r) {
    return estimate_rule_prob(r.english);
  }

  // only called after a complete hyp for the whole input sentence is obtaned
  @Override
  public double finaltransition(HashMap state) {
    if (state != null) {
      return compute_euqiv_state_final_transition(state);
    } else {
      return 0.0;
    }
  }

  // ######################################################################################################
  // not used

  /*
   * // TODO: assume that the left and right state is seprated, i.e., the span
   * is large enough private double
   * compute_euqiv_state_final_transition_vold_latest(HashMap state){ double
   * res=0.0;
   * 
   * int[] l_context =(int[])state.get(Chart.LM_L_STATE_SYM_ID);
   * //System.out.println("left context: " +Symbol.get_string(l_context));
   * ArrayList list = new ArrayList(); list.add(Symbol.START_SYM_ID); for(int
   * k=0; k<l_context.length; k++){ list.add(l_context[k]); }
   * 
   * for(int i=1; i<list.size(); i++){//consider_incomplete_ngram=true;
   * skip_start=true int[] cur_wrds = Support.sub_int_array(list, 0, i+1);
   * if(cur_wrds[cur_wrds.length-1]==Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID) res +=
   * -p_lm.get_prob_backoff_state(cur_wrds, cur_wrds.length, 1);//addional_bow
   * is one as we only add one symbol <s> else res += -p_lm.get_prob(cur_wrds,
   * cur_wrds.length, false); }
   * 
   * //System.out.println("left context: " +Symbol.get_string(l_context) +
   * ";prob "+res); //TODO right context int[] r_context
   * =(int[])state.get(Chart.LM_R_STATE_SYM_ID); list = new
   * ArrayList(r_context.length+1); for(int k=0; k<r_context.length;k++)
   * list.add(r_context[k]); list.add(Symbol.STOP_SYM_ID); double
   * tem2=score_chunk(list,false,false);//consider_incomplete_ngram=false;
   * skip_start=false res += tem2; //System.out.println("right context:"+
   * Symbol.get_string(r_context) + "; score: " + tem2);
   * 
   * return res; } //when calculate transition prob: when saw a <bo>, then need
   * to add backoff weights, start from non-state words // return states and
   * cost private HashMap lookup_words1_equv_state_old_again(int[] en_words,
   * ArrayList antstates){ //long start_step1 = Support.current_time(); HashMap
   * res_tbl=new HashMap(); HashMap model_states = new HashMap ();
   * res_tbl.put(Chart.ITEM_STATES_SYM_ID,model_states);
   * 
   * //for left state ArrayList left_state_org_wrds = new ArrayList(); boolean
   * keep_left_state = true;//stop if: (1) end of rule; (2)
   * left_state_org_wrds.size()==g_order-1; (3) seperating point; ArrayList
   * cur_ngram = new ArrayList(); double trans_cost=0.0; int span_len=0;
   * 
   * for(int c=0; c<en_words.length; c++){ int c_id = en_words[c];
   * if(Symbol.is_nonterminal(c_id)==true){//non terminal symbol
   * if(antstates!=null){ int index = Symbol.get_eng_non_terminal_id(c_id);
   * HashMap s_tbl =(HashMap)antstates.get(index); int[] l_context
   * =(int[])s_tbl.get(Chart.LM_L_STATE_SYM_ID); int[] r_context
   * =(int[])s_tbl.get(Chart.LM_R_STATE_SYM_ID); boolean is_overlap=false;
   * 
   * //##################left context int lm_l_state_size = 0;
   * if(l_context!=null){ //System.out.println("left context: " +
   * Symbol.get_string(l_context)); for(int t : l_context){
   * if(t==Symbol.LM_STATE_OVERLAP_SYM_ID){//complete overlap between left and
   * right state is_overlap=true; }else if(t==Symbol.NULL_LEFT_LM_STATE_SYM_ID){
   * if(keep_left_state==true){ left_state_org_wrds.add(t);
   * if(left_state_org_wrds.size()>=g_order-1)//do not need the left state
   * anymore keep_left_state =false; } span_len++; }else{ //t =
   * p_lm.replace_with_unk(t); lm_l_state_size++;//remember how many words in
   * cur_ngram is from the left state cur_ngram.add(t);
   * if(t==Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID){//always calculate cost for
   * <bo>: additional backoff weight int n_a_bow =
   * cur_ngram.size()-lm_l_state_size; trans_cost +=
   * -p_lm.get_prob_backoff_state(cur_ngram, cur_ngram.size(),n_a_bow);//compute
   * additional backoff weight if(cur_ngram.size()==g_order)
   * cur_ngram.remove(0);//change the cur_ngram }else
   * if(cur_ngram.size()==g_order){ trans_cost += -p_lm.get_prob(cur_ngram,
   * g_order,false);//compute the current word probablity
   * cur_ngram.remove(0);//change the cur_ngram }
   * 
   * if(keep_left_state==true){ left_state_org_wrds.add(t);
   * if(left_state_org_wrds.size()>=g_order-1)//do not need the left state
   * anymore keep_left_state =false; } span_len++; } }
   * 
   * if(is_overlap==true){//continue, but need to change the context for(int
   * k=1; k<=lm_l_state_size; k++)//remove the context from cur_ngram
   * cur_ngram.remove(cur_ngram.size()-1);//remove last span_len -=
   * lm_l_state_size; }else{//seprating point cur_ngram.clear();//clear the
   * history keep_left_state = false; span_len = 10000000; } }else{//seprating
   * point: no l_contex, no Symbol.LM_STATE_OVERLAP_SYM_ID flag
   * System.out.println("Error, null left context"); System.exit(0); }
   * //####################right context if(r_context!=null){
   * //System.out.println("right context: " + Symbol.get_string(r_context));
   * for(int t : r_context){ //t = p_lm.replace_with_unk(t); cur_ngram.add(t);
   * span_len++; //note: left_state_org_wrds will never take words from right
   * context because it is either duplicate or out of range //also, we will
   * never score the right context probablity because they are either duplicate
   * or partional ngram if(cur_ngram.size()==g_order){
   * cur_ngram.remove(0);//change the cur_ngram } } } }else{//ant state is null
   * System.out.println("Error, in lookup_wrods1, null antstates");
   * System.exit(0); } }else{//terminal words //c_id =
   * p_lm.replace_with_unk(c_id); //System.out.println("terminal: " +
   * Symbol.get_string(c_id)); cur_ngram.add(c_id); span_len++;
   * 
   * if(cur_ngram.size()==g_order){ trans_cost += -p_lm.get_prob(cur_ngram,
   * g_order, true);//compute the current word probablity
   * cur_ngram.remove(0);//change the cur_ngram } if(keep_left_state==true){
   * left_state_org_wrds.add(c_id);
   * if(left_state_org_wrds.size()>=g_order-1)//do not need the left state
   * anymore keep_left_state =false; } } }
   * 
   * //now get euquiv left state double[] lm_l_cost = new double[2]; int[]
   * equiv_l_state; equiv_l_state =
   * p_lm.get_left_equi_state(Support.sub_int_array(left_state_org_wrds, 0,
   * left_state_org_wrds.size()),g_order,lm_l_cost);
   * 
   * if(span_len<=g_order-1){//means the left state and right state are complete
   * overlap int[] equiv_l_state2 = new int[equiv_l_state.length+1]; for(int
   * k=0; k<equiv_l_state.length; k++) equiv_l_state2[k]=equiv_l_state[k];
   * equiv_l_state2[equiv_l_state2.length-1]=Symbol.LM_STATE_OVERLAP_SYM_ID;
   * model_states.put(Chart.LM_L_STATE_SYM_ID, equiv_l_state2);
   * //System.out.println("left state(overlap): " +
   * Symbol.get_string(equiv_l_state2)); }else if(equiv_l_state.length>0){
   * model_states.put(Chart.LM_L_STATE_SYM_ID, equiv_l_state);
   * //System.out.println("left state: " + Symbol.get_string(equiv_l_state)); }
   * trans_cost += lm_l_cost[0];//finalized cost for the left state words
   * if(Decoder.use_left_euqivalent_state) cached_lm_l_bonus = lm_l_cost[1];
   * else cached_lm_l_bonus =
   * estimate_state_prob(model_states,false,false);//bonus function
   * 
   * //now get the equiv right state int r_size = (cur_ngram.size()<g_order-1) ?
   * cur_ngram.size():g_order-1; if(cur_ngram.size()>g_order-1){
   * System.out.println("Error, surving ngram is too large"); System.exit(0); }
   * int[] right_state = new int[r_size]; for(int f=cur_ngram.size()-r_size;
   * f<cur_ngram.size(); f++) right_state[f-(cur_ngram.size()-r_size)] =
   * (Integer)cur_ngram.get(f);//right_state[f-(replaced_words.length-r_size)] =
   * (Integer)t_words.get(f); int[] equiv_r_state =
   * p_lm.get_right_equi_state(right_state,g_order, true);
   * model_states.put(Chart.LM_R_STATE_SYM_ID, equiv_r_state);
   * //System.out.println("right state: " + Symbol.get_string(right_state));
   * 
   * res_tbl.put(Chart.TRANSITION_COST_SYM_ID,trans_cost);
   * //System.out.println("##tran cost: " + trans_cost +" lm_l_cost[0]: " +
   * lm_l_cost[0]);
   * 
   * //time_step2 += Support.current_time()-start_step2; return res_tbl; }
   * 
   * 
   * // TODO: assume that the left and right state is seprated, i.e., the span
   * is large enough private double
   * compute_euqiv_state_final_transition_old_again(HashMap state){ double
   * res=0.0;
   * 
   * int[] l_context =(int[])state.get(Chart.LM_L_STATE_SYM_ID);
   * //System.out.println("left context: " +Symbol.get_string(l_context));
   * ArrayList list = new ArrayList(); list.add(Symbol.START_SYM_ID);
   * if(l_context!=null){//TODO int k=0; boolean is_last_backoff=false; for(;
   * k<l_context.length; k++){
   * if(l_context[k]==Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID){
   * is_last_backoff=true; break; }
   * if(l_context[k]==Symbol.NULL_LEFT_LM_STATE_SYM_ID){ break; }
   * list.add(l_context[k]); } double tem2 = score_chunk(list,true,true); res +=
   * tem2; if(is_last_backoff==true){ list.add(l_context[k]); int
   * n_additional_bow = 1;//since we only add one <s> tem2 =
   * -p_lm.get_prob_backoff_state(list, list.size(), n_additional_bow);//last
   * ngram res += tem2; //System.out.println("equvi prob final: " + tem2 +
   * " ; string: " + Symbol.get_string(tem)); }
   * //System.out.println("left context:" + Symbol.get_string(l_context) +
   * "; score: " + tem2); } //System.out.println("left context: "
   * +Symbol.get_string(l_context) + ";prob "+res); //TODO right context int[]
   * r_context =(int[])state.get(Chart.LM_R_STATE_SYM_ID); list = new
   * ArrayList(r_context.length+1); for(int k=0; k<r_context.length;k++)
   * list.add(r_context[k]); list.add(Symbol.STOP_SYM_ID); double
   * tem2=score_chunk(list,false,false);//consider_incomplete_ngram=false;
   * skip_start=false res += tem2; //System.out.println("right context:"+
   * Symbol.get_string(r_context) + "; score: " + tem2);
   * 
   * return res; }
   */

  // ############################ retired functions
  /*
   * // return states and cost private HashMap lookup_words1(int[] en_words,
   * ArrayList antstates){ //long start_step1 = Support.current_time(); HashMap
   * res_tbl=new HashMap ();
   * 
   * //step1: get the chunks ArrayList chunks = new ArrayList(); //ArrayList of
   * ArrayList: list of "words" ArrayList words= new ArrayList();
   * chunks.add(words); for(int c=0; c<en_words.length; c++){ int c_id =
   * en_words[c]; if(Symbol.is_nonterminal(c_id)==true){ if(antstates!=null){
   * int idex=Symbol.get_eng_non_terminal_id(c_id); HashMap s_tbl =(HashMap
   * )antstates.get(idex); int[] l_context
   * =(int[])s_tbl.get(Chart.LM_L_STATE_SYM_ID); int[] r_context
   * =(int[])s_tbl.get(Chart.LM_R_STATE_SYM_ID); for(int t : l_context)//always
   * have l_context words.add(t); if(r_context!=null){ words=new
   * ArrayList();//start a new chunk chunks.add(words); for(int t : r_context)
   * words.add(t); } }else{//get stateless cost
   * System.out.println("Error, in lookup_wrods1, null antstates");
   * System.exit(0); } }else{ words.add(c_id); } } //time_step1 +=
   * Support.current_time()-start_step1;
   * 
   * //step2: get the transition score //long start_step2 =
   * Support.current_time(); double trans_cost=0.0; for(int c=0;
   * c<chunks.size(); c++){ ArrayList t_words = (ArrayList)chunks.get(c);
   * if(t_words.size()<=0) continue; trans_cost += -p_lm.score_a_sent(t_words,
   * g_order, g_order);//send g_order: ignore incomplete ngrams }
   * res_tbl.put(Chart.TRANSITION_COST_SYM_ID,trans_cost); //time_step2 +=
   * Support.current_time()-start_step2;
   * 
   * //step3: get the context state //long start_step3 = Support.current_time();
   * HashMap model_states = new HashMap ();
   * res_tbl.put(Chart.ITEM_STATES_SYM_ID,model_states);
   * 
   * // get the left state ArrayList left_words = (ArrayList)chunks.get(0); int
   * l_size = (left_words.size()<g_order-1) ? left_words.size() : g_order-1;
   * int[] left_state = new int[l_size]; for(int c=0; c<l_size; c++)
   * left_state[c]= (Integer)left_words.get(c);
   * model_states.put(Chart.LM_L_STATE_SYM_ID, left_state);
   * 
   * // get the right state ArrayList right_words =
   * (ArrayList)chunks.get(chunks.size()-1);
   * if(chunks.size()>1||right_words.size()>=g_order){ int r_size =
   * (right_words.size()<g_order-1) ? right_words.size():g_order-1; int[]
   * right_state = new int[r_size]; for(int c=right_words.size()-r_size;
   * c<right_words.size(); c++) right_state[c-(right_words.size()-r_size)] =
   * (Integer)right_words.get(c); model_states.put(Chart.LM_R_STATE_SYM_ID,
   * right_state); } //time_step3 += Support.current_time()-start_step3; return
   * res_tbl; }
   */
  /*
   * private int[] convert_state(HashMap state){ if(state==null) return null;
   * 
   * int[] l_context =(int[])state.get(Chart.LM_L_STATE_SYM_ID); int[] r_context
   * =(int[])state.get(Chart.LM_R_STATE_SYM_ID); int s_left=0; int s_right=0;
   * if(l_context!=null) s_left = l_context.length; if(r_context!=null) s_right
   * = r_context.length; int[] t_words; if(s_right>0) t_words = new
   * int[s_left+s_right+1];//one for eclips else t_words = new int[s_left];
   * 
   * for(int i=0; i<s_left; i++) t_words[i]=l_context[i]; for(int i=0;
   * i<s_right; i++){ if(i==0)t_words[s_left]=Symbol.ECLIPS_SYM_ID;
   * t_words[s_left+i+1]=r_context[i]; } return t_words; }
   */

  /*
   * // return cost, including partial ngrams private double lookup_words2(int[]
   * en_words){ double res=0.0; boolean consider_incomplete_ngram=true;
   * ArrayList l_words = new ArrayList(); boolean skip_start=true;
   * if(en_words[0]!=Symbol.START_SYM_ID) skip_start=false;
   * 
   * for(int c=0; c<en_words.length; c++){ int c_wrd =en_words[c];
   * if(c_wrd==Symbol.ECLIPS_SYM_ID){ res +=
   * score_chunk(l_words,consider_incomplete_ngram,skip_start);
   * consider_incomplete_ngram=false;//for the LM bonus function: this simply
   * means the right state will not be considered at all l_words.clear();
   * skip_start=false; }else if(Symbol.is_nonterminal(c_wrd)==true){ res +=
   * score_chunk(l_words,consider_incomplete_ngram,skip_start);
   * consider_incomplete_ngram=true; l_words.clear(); skip_start=false; }else{
   * l_words.add(c_wrd); } } res +=
   * score_chunk(l_words,consider_incomplete_ngram,skip_start); return res; }
   */

  /*
   * when calculate transition prob: when saw a <bo>, then need to add backoff
   * weights, start from non-state words
   */
  /*
   * // return states and cost private HashMap
   * lookup_words1_equv_stateVold(int[] en_words, ArrayList antstates){ //long
   * start_step1 = Support.current_time(); HashMap res_tbl=new HashMap ();
   * 
   * //step1: get the chunks ArrayList chunks = new ArrayList(); //ArrayList of
   * ArrayList: list of "words" ArrayList words= new ArrayList(); ArrayList
   * chunks_lm_l_state_size = new ArrayList(); chunks.add(words);
   * chunks_lm_l_state_size.add(g_order); for(int c=0; c<en_words.length; c++){
   * int c_id = en_words[c]; if(Symbol.is_nonterminal(c_id)==true){
   * if(antstates!=null){ int idex=Symbol.get_eng_non_terminal_id(c_id); HashMap
   * s_tbl =(HashMap )antstates.get(idex); int[] l_context
   * =(int[])s_tbl.get(Chart.LM_L_STATE_SYM_ID); int[] r_context
   * =(int[])s_tbl.get(Chart.LM_R_STATE_SYM_ID); if(l_context!=null){//TODO
   * chunks_lm_l_state_size.set(chunks_lm_l_state_size.size()-1,
   * l_context.length);//TODO: add left context start pos for(int t :
   * l_context)//always have l_context words.add(t); } if(r_context!=null){//key
   * observation: number of chunks = number of r_context state in the rule + 1
   * words=new ArrayList();//start a new chunk chunks.add(words);
   * chunks_lm_l_state_size.add(g_order); for(int t : r_context) words.add(t); }
   * }else{//get stateless cost
   * System.out.println("Error, in lookup_wrods1, null antstates");
   * System.exit(0); } }else{ words.add(c_id); } } //time_step1 +=
   * Support.current_time()-start_step1;
   * 
   * //step2: get the transition score and state //long start_step2 =
   * Support.current_time(); HashMap model_states = new HashMap ();
   * res_tbl.put(Chart.ITEM_STATES_SYM_ID,model_states); double trans_cost=0.0;
   * boolean will_compute_right_state=false; if(chunks.size()>1 ||
   * ((ArrayList)chunks.get(0)).size()>=g_order) will_compute_right_state=true;
   * 
   * for(int c=0; c<chunks.size(); c++){ ArrayList t_words =
   * (ArrayList)chunks.get(c); List
   * replaced_words=p_lm.replace_with_unk(t_words);
   * 
   * //if(replaced_words.length<=0) continue;//need for empty state int
   * lm_l_state_size = (Integer) chunks_lm_l_state_size.get(c);
   * 
   * //compute right context state if(c==(chunks.size()-1) &&
   * will_compute_right_state==true){//key observation: number of chunks =
   * number of r_context state in the rule + 1 int r_size =
   * (replaced_words.size()<g_order-1) ? replaced_words.size():g_order-1; int[]
   * right_state = new int[r_size]; for(int f=replaced_words.size()-r_size;
   * f<replaced_words.size(); f++) right_state[f-(replaced_words.size()-r_size)]
   * =
   * (Integer)replaced_words.get(f);//right_state[f-(replaced_words.length-r_size
   * )] = (Integer)t_words.get(f); model_states.put(Chart.LM_R_STATE_SYM_ID,
   * right_state); //System.out.println("hello, called"); } int
   * n_additional_bow1 =0; if(c==0){//compute left state
   * if(replaced_words.size()<=0){ cached_lm_l_bonus=0; continue; } int l_size =
   * (replaced_words.size()<g_order-1) ? replaced_words.size() : g_order-1;
   * if(will_compute_right_state==true){ //System.out.println("hello, called");
   * List left_state_org_wrds = replaced_words.subList(0, l_size); double[]
   * lm_l_cost = new double[2];
   * 
   * if((Integer)left_state_org_wrds.get(l_size-1)==Symbol.
   * BACKOFF_LEFT_LM_STATE_SYM_ID) n_additional_bow1 = l_size -
   * lm_l_state_size;//TODO wrong: assume the last wrd is <bo>, how many
   * additional ngram is required? int[] equiv_l_state=
   * p_lm.get_left_euqi_stateOldV
   * (Arrays.asList(left_state_org_wrds),g_order,lm_l_cost,n_additional_bow1);
   * model_states.put(Chart.LM_L_STATE_SYM_ID, equiv_l_state); trans_cost +=
   * lm_l_cost[0];//finalized cost for the left state words cached_lm_l_bonus =
   * lm_l_cost[1]; //System.out.println("final cost: " + lm_l_cost[0]
   * +" ; bous cost: " + lm_l_cost[1]); }else{//do not use equivalent state
   * because the left_context_state will be used as part of the
   * right_context_state, which complicate the equivalance int[]
   * left_state_org_wrds = new int[l_size]; for(int f=0; f<l_size; f++)
   * left_state_org_wrds[f]=
   * (Integer)replaced_words.get(f);//left_state_org_wrds[f]=
   * (Integer)t_words.get(f); model_states.put(Chart.LM_L_STATE_SYM_ID,
   * left_state_org_wrds); cached_lm_l_bonus =
   * estimate_state_prob(model_states,false,false);//bonus function } }
   * 
   * //now compute probability for each word double res=0.0; int[] ngram_wrds;
   * 
   * //note: only the rightmost wrd can be <bo> int n_additional_bow =0;
   * if((Integer)replaced_words.get(replaced_words.size()-1)==Symbol.
   * BACKOFF_LEFT_LM_STATE_SYM_ID){ n_additional_bow = g_order -
   * lm_l_state_size;//TODO assume the last wrd is <bo>, how many additional
   * ngram is required? //n_additional_bow = 1; //System.out.println("n_add: " +
   * n_additional_bow); } for(int i=0; i<=replaced_words.size()-g_order;
   * i++){//complete order-ngrams only ngram_wrds = new int[g_order]; for(int
   * k=i; k<i+g_order; k++){ ngram_wrds[k-i]=(Integer)replaced_words.get(k); }
   * double tem;
   * if(ngram_wrds[g_order-1]==Symbol.BACKOFF_LEFT_LM_STATE_SYM_ID)//last ngram
   * tem=p_lm.get_prob_left_euqi_state(ngram_wrds, g_order,
   * n_additional_bow);//last ngram else
   * tem=p_lm.get_prob_left_euqi_state(ngram_wrds, g_order, 0); res +=tem;
   * //System.out.println("Prob is: " +tem + " c is: " +c + " string:" +
   * Symbol.get_string(ngram_wrds)); } trans_cost += -res; }
   * res_tbl.put(Chart.TRANSITION_COST_SYM_ID,trans_cost);
   * 
   * //time_step2 += Support.current_time()-start_step2; return res_tbl; }
   */
}
