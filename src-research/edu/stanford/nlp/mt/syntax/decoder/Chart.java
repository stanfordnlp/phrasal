package edu.stanford.nlp.mt.syntax.decoder;

import java.util.ArrayList;
import java.util.HashMap;
import edu.stanford.nlp.mt.syntax.decoder.DotChart.DotItem;
import edu.stanford.nlp.mt.syntax.decoder.HyperGraph.Item;
import edu.stanford.nlp.mt.syntax.decoder.TMGrammar.Rule;
import edu.stanford.nlp.mt.syntax.decoder.TMGrammar.RuleBin;
import edu.stanford.nlp.mt.syntax.decoder.TMGrammar.TrieNode;

/* Zhifei Li, <zhifei.work@gmail.com>
 * Johns Hopkins University
 */

/*#################### Chart class
 * this class implements chart-parsing: cky loop over bins, and identify applicable rules in each cell
 * the combination operation will be done in Bin
 * */

/*Signatures of class:
 * Bin: i, j
 * SuperItem (used for CKY check): i,j, lhs
 * Item (or node): i,j, lhs, edge ngrams
 * Deduction (and node)*/

/* index of sentences: start from zero
 * index of cell: cell (i,j) represent span of words indexed [i,j-1] where i is in [0,n-1] and j is in [1,n]
 * */
@SuppressWarnings({ "unchecked", "unused" })
public class Chart {
  public TMGrammar[] l_grammars;
  public DotChart[] l_dotcharts;// each grammar should have a dotchart
                                // associated with it
  public Bin[][] l_bins;// note that in some cell, it might be null
  public Bin goal_bin;
  public int[] sentence;// a list of foreign words
  public int sent_len;// foreign sent len
  public int sent_id;

  // decoder-wide variables
  ArrayList<Model> l_models;

  // statistics
  int gtem = 0;
  int n_prepruned = 0;// how many items have been pruned away because its cost
                      // is greater than the cutoff in calling
                      // chart.add_deduction_in_chart()
  int n_prepruned_fuzz1 = 0;
  int n_prepruned_fuzz2 = 0;
  int n_pruned = 0;
  int n_merged = 0;
  int n_added = 0;
  int n_dotitem_added = 0;// note: there is no pruning in dot-item
  int n_called_compute_item = 0;

  public void print_info(int level) {
    Support
        .write_log_line(
            String
                .format(
                    "ADD: %d; MERGED: %d; pruned: %d; pre-pruned: %d ,fuzz1: %d, fuzz2: %d; n_dotitem_added: %d",
                    n_added, n_merged, n_pruned, n_prepruned,
                    n_prepruned_fuzz1, n_prepruned_fuzz2, n_dotitem_added),
            level);
  }

  // time-profile variables, debug purpose
  long g_time_compute_item = 0;
  long g_time_add_deduction = 0;
  static long g_time_lm = 0;
  static long g_time_score_sent = 0;
  static long g_time_check_nonterminal = 0;
  static long g_time_kbest_extract = 0;

  public Chart(int[] sentence_in, ArrayList<Model> models, int sent_id1) {
    sentence = sentence_in;
    sent_len = sentence.length;
    l_models = models;
    l_bins = new Bin[sent_len][sent_len + 1];
    sent_id = sent_id1;
    goal_bin = new Bin(this);
  }

  // add un-translated wrds into the chart as item (with large cost)
  // TODO: grammar specific?
  public void seed(TMGrammar[] grs, ArrayList<Integer> default_nonterminals,
      String[] sentence_str) {
    l_grammars = grs;
    l_dotcharts = new DotChart[l_grammars.length];// each grammar will have a
                                                  // dot chart
    for (int i = 0; i < l_grammars.length; i++) {
      l_dotcharts[i] = new DotChart(sentence, l_grammars[i], this);
      l_dotcharts[i].seed();
    }
    // add OOV rules
    // TODO: the transition cost for phrase model, arity penalty, word penalty
    // are all zero, except the LM cost
    for (int i = 0; i < sent_len; i++) {
      for (int lhs : default_nonterminals) {// create a rule, but do not add
                                            // into the grammar trie
        Rule r = new TMGrammar_Memory.Rule_Memory(lhs,
            Symbol.add_terminal_symbol(sentence_str[i]), Symbol.UNTRANS_SYM_ID);// TODO:
                                                                                // change
                                                                                // onwer
        add_axiom(i, i + 1, r);
      }
    }
    System.out.println("####finisehd seeding");
  }

  // construct the hypergraph with the help from DotChart
  public HyperGraph expand() {
    // long start = System.currentTimeMillis();
    long time_step1 = 0, time_step2 = 0, time_step3 = 0, time_step4 = 0;
    for (int width = 1; width <= sent_len; width++) {
      for (int i = 0; i <= sent_len - width; i++) {
        int j = i + width;
        // Support.write_log_line(String.format("Process span (%d, %d)",i,j),
        // Support.DEBUG);

        // (1)### expand the cell in dotchart
        // long start_step1= Support.current_time();
        // Support.write_log_line("Step 1: expance cell", Support.DEBUG);
        for (int k = 0; k < l_grammars.length; k++) {
          l_dotcharts[k].expand_cell(i, j);
        }
        // Support.write_log_line(String.format("n_dotitem= %d",n_dotitem_added),
        // Support.INFO);
        // time_step1 += Support.current_time()-start_step1;

        // (2)### populate COMPLETE rules into Chart: the regular CKY part
        // long start_step2= Support.current_time();
        // Support.write_log_line("Step 2: add complte items into Chart",
        // Support.DEBUG);
        for (int k = 0; k < l_grammars.length; k++) {
          if (l_grammars[k].filter_span(i, j, sent_len)
              && l_dotcharts[k].l_dot_bins[i][j] != null) {
            for (DotItem dt : l_dotcharts[k].l_dot_bins[i][j].l_dot_items) {
              if (dt.tnode.get_rule_bin() != null) {// have rules under this
                                                    // trienode
                if (dt.tnode.get_rule_bin().get_arity() == 0) {// rules without
                                                               // any
                                                               // non-terminal
                  ArrayList<Rule> l_rules = dt.tnode.get_rule_bin()
                      .get_sorted_rules();
                  for (Rule rl : l_rules)
                    add_axiom(i, j, rl);
                } else {// rules with non-terminal
                  if (Decoder.use_cube_prune == true)
                    complete_cell_cube_prune(i, j, dt, dt.tnode.get_rule_bin());
                  else
                    complete_cell(i, j, dt, dt.tnode.get_rule_bin());// populate
                                                                     // chart.bin[i][j]
                                                                     // with
                                                                     // rules
                                                                     // from
                                                                     // dotchart[i][j]
                }
              }
            }
          }
        }
        // time_step2 += Support.current_time()-start_step2;

        // (3)### process unary rules (e.g., S->X, NP->NN), just add these items
        // in chart, assume acyclic
        // long start_step3= Support.current_time();
        // Support.write_log_line("Step 3: add unary items into Chart",
        // Support.DEBUG);
        for (int k = 0; k < l_grammars.length; k++) {
          if (l_grammars[k].filter_span(i, j, sent_len))
            add_unary_items(l_grammars[k], i, j);// single-branch path
        }
        // time_step3 += Support.current_time()-start_step3;

        // (4)### in dot_cell(i,j), add dot-items that start from the /complete/
        // superIterms in chart_cell(i,j)
        // long start_step4= Support.current_time();
        // Support.write_log_line("Step 4: init new dot-items that starts from complete items in this cell",
        // Support.DEBUG);
        for (int k = 0; k < l_grammars.length; k++) {
          if (l_grammars[k].filter_span(i, j, sent_len)) {
            l_dotcharts[k].start_dotitems(i, j);
          }
        }
        // time_step4 += Support.current_time()-start_step4;

        // (5)### sort the items in the cell: for pruning purpose
        // Support.write_log_line(String.format("After Process span (%d, %d), called:= %d",i,j,n_called_compute_item),
        // Support.INFO);
        if (l_bins[i][j] != null) {
          // l_bins[i][j].print_info(Support.INFO);
          ArrayList<Item> l_s_its = l_bins[i][j].get_sorted_items();// this is
                                                                    // required

          /*
           * sanity check with this cell int sum_d=0; double sum_c =0.0; double
           * sum_total=0.0; for(Item t_item : l_s_its){
           * if(t_item.l_deductions!=null) sum_d += t_item.l_deductions.size();
           * sum_c += t_item.best_deduction.best_cost; sum_total +=
           * t_item.est_total_cost; } //System.out.println(String.format(
           * "n_items =%d; n_deductions: %d; s_cost: %.3f; c_total: %.3f",
           * l_bins[i][j].tbl_items.size(),sum_d,sum_c,sum_total));
           */
        }
        // print_info(Support.INFO);
      }
    }
    print_info(Support.INFO);

    // transition_final: setup a goal item, which may have many deductions
    if (l_bins[0][sent_len] != null) {
      goal_bin.transit_to_goal(l_bins[0][sent_len]);// update goal_bin
    } else {
      Support.write_log_line("No complet item in the cell(0,n)", Support.ERROR);
      System.exit(0);
    }

    // debug purpose
    // long sec_consumed = (System.currentTimeMillis() -start)/1000;
    // Support.write_log_line("######Expand time consumption: "+ sec_consumed,
    // Support.INFO);
    // Support.write_log_line(String.format("Step1: %d; step2: %d; step3: %d; step4: %d",
    // time_step1, time_step2, time_step3, time_step4), Support.INFO);

    /*
     * Support.write_log_line(String.format(
     * "t_compute_item: %d; t_add_deduction: %d;",
     * g_time_compute_item/1000,g_time_add_deduction/1000), Support.INFO);
     * for(Model m: l_models){ Support.write_log_line("Model cost: " +
     * m.time_consumed/1000, Support.INFO); }
     */

    // Support.write_log_line(String.format("t_lm: %d; t_score_lm: %d; t_check_nonterminal: %d",
    // g_time_lm, g_time_score_sent, g_time_check_nonterminal), Support.INFO);
    // LMModel tm_lm = (LMModel)l_models.get(0);
    // Support.write_log_line(String.format("LM lookupwords1, step1: %d; step2: %d; step3: %d",tm_lm.time_step1,tm_lm.time_step2,tm_lm.time_step3),Support.INFO);
    // debug end

    return new HyperGraph(goal_bin.get_sorted_items().get(0));
  }

  // agenda based extension: this is necessary in case more than two unary rules
  // can be applied in topological order s->x; ss->s
  // for unary rules like s->x, once x is complete, then s is also complete
  private int add_unary_items(TMGrammar gr, int i, int j) {
    Bin chart_bin = l_bins[i][j];
    if (chart_bin == null)
      return 0;
    int res = 0;
    ArrayList t_queue = new ArrayList(chart_bin.get_sorted_items());// init
                                                                    // queue
    while (t_queue.size() > 0) {
      Item it = (Item) t_queue.remove(0);
      TrieNode child_tnode = gr.get_root().match_symbol(it.lhs);// match rule
                                                                // and complete
                                                                // part
      if (child_tnode != null && child_tnode.get_rule_bin() != null
          && child_tnode.get_rule_bin().get_arity() == 1) {// have unary rules
                                                           // under this
                                                           // trienode
        ArrayList<Item> l_ants = new ArrayList<Item>();
        l_ants.add(it);
        ArrayList<Rule> l_rules = child_tnode.get_rule_bin().get_sorted_rules();
        for (Rule rl : l_rules) {// for each unary rules
          HashMap tbl_states = chart_bin.compute_item(rl, l_ants, i, j);
          Item res_item = chart_bin.add_deduction_in_bin(tbl_states, rl, i, j,
              l_ants);
          if (res_item != null) {
            t_queue.add(res_item);
            res++;
          }
        }
      }
    }
    return res;
  }

  // axiom is for rules with zero-arity
  private void add_axiom(int i, int j, Rule rl) {
    if (l_bins[i][j] == null)
      l_bins[i][j] = new Bin(this);
    l_bins[i][j].add_axiom(i, j, rl);
  }

  private void complete_cell(int i, int j, DotItem dt, RuleBin rb) {
    if (l_bins[i][j] == null)
      l_bins[i][j] = new Bin(this);
    l_bins[i][j].complete_cell(i, j, dt.l_ant_super_items, rb);// combinations:
                                                               // rules,
                                                               // antecent items
  }

  private void complete_cell_cube_prune(int i, int j, DotItem dt, RuleBin rb) {
    if (l_bins[i][j] == null)
      l_bins[i][j] = new Bin(this);
    l_bins[i][j].complete_cell_cube_prune(i, j, dt.l_ant_super_items, rb);// combinations:
                                                                          // rules,
                                                                          // antecent
                                                                          // items
  }
}
