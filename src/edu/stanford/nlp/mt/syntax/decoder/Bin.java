package edu.stanford.nlp.mt.syntax.decoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

import edu.stanford.nlp.mt.syntax.decoder.HyperGraph.Deduction;
import edu.stanford.nlp.mt.syntax.decoder.HyperGraph.Item;
import edu.stanford.nlp.mt.syntax.decoder.TMGrammar.Rule;
import edu.stanford.nlp.mt.syntax.decoder.TMGrammar.RuleBin;

/* Zhifei Li, <zhifei.work@gmail.com>
 * Johns Hopkins University
 */

/*#################### Bin class
 * this class implement functions: (1) create items and deductions to construct a hyper-graph (maintaining backpointers), (2) evaluate model cost for items, (3) pruning
 * the Bin create Items, but not all Items will be used in the future in the hyper-graph*/

@SuppressWarnings("unchecked")
public class Bin {
  /*
   * we need always maintain the priority queue (worst first), so that we can do
   * prunning effiecientlyOn the other hand, we need the l_sorted_items only
   * when necessary
   */

  // NOTE: MIN-HEAP, we put the worst-cost item at the top of the heap by
  // manipulating the compare function
  // heap_items: the only purpose is to help deecide which items should be
  // removed from tbl_items during pruning
  private PriorityQueue<Item> heap_items = new PriorityQueue(1,
      Item.NegtiveCostComparator);// TODO: initial capacity?
  private HashMap tbl_items = new HashMap(); // to maintain uniqueness of items
  private HashMap tbl_super_items = new HashMap();// signature by lhs
  private ArrayList<Item> l_sorted_items = null;// sort values in
                                                // tbl_item_signature, we need
                                                // this list whenever necessary

  // pruning parameters
  public double best_item_cost = Symbol.IMPOSSIBLE_COST;// remember the cost of
                                                        // the best item in the
                                                        // bin
  public double cut_off_cost = Symbol.IMPOSSIBLE_COST; // cutoff=best_item_cost+relative_threshold
  int dead_items = 0;// num of corrupted items in heap_items, note that the item
                     // in tbl_items is always good
  Chart p_chart = null;

  public Bin(Chart chart) {
    p_chart = chart;
  }

  /*
   * compute cost and the states of this itemreturned ArrayList: total_cost,
   * additive_cost, transition_cost, bonus, list of states
   */
  public HashMap compute_item(Rule rl, ArrayList<Item> ants_items, int i, int j) {
    long start = Support.current_time();
    p_chart.n_called_compute_item++;
    double additive_cost = rl.statelesscost;

    ArrayList ants_states = new ArrayList();
    if (ants_items != null)
      for (Item ant : ants_items) {
        additive_cost += ant.best_deduction.best_cost;
        ants_states.add(ant.tbl_states);
      }
    HashMap res_tbl_item_states = new HashMap();
    double transition_cost = 0.0;// transition cost: the sum of costs of
                                 // non-stateless/non-contextual models
    double bonus = 0.0;
    for (Model m : p_chart.l_models) {// not just the stateful ones because
                                      // anything might have a prior
      long start2 = Support.current_time();
      if (m.stateless == false && m.contextual == false) {
        HashMap tem_tbl = m.transition(rl, ants_states, i, j, 0);
        transition_cost += ((Double) tem_tbl.get(Symbol.TRANSITION_COST_SYM_ID))
            .doubleValue() * m.weight;
        HashMap tem_states_tbl = (HashMap) tem_tbl
            .get(Symbol.ITEM_STATES_SYM_ID);
        bonus += ((Double) tem_tbl.get(Symbol.BONUS_SYM_ID)) * m.weight;// future
                                                                        // cost
                                                                        // estimation
        if (tem_states_tbl != null) {
          res_tbl_item_states.putAll(tem_states_tbl);
        }
      } else {
        bonus += 0; // TODO: future cost estimation is zero
      }
      m.time_consumed += Support.current_time() - start2;
    }
    double total_cost = additive_cost + transition_cost + bonus;

    HashMap res = new HashMap();
    res.put(Symbol.TOTAL_COST_SYM_ID, total_cost);
    res.put(Symbol.ADDITIVE_COST_SYM_ID, additive_cost);
    res.put(Symbol.TRANSITION_COST_SYM_ID, transition_cost);
    // res.put(BONUS,bonus); //NOT USED
    res.put(Symbol.ITEM_STATES_SYM_ID, res_tbl_item_states);
    // System.out.println("t_cost: " + total_cost + "; a_cost: " + additive_cost
    // +" ;t_cost: " + transition_cost +"; bonus: " + bonus );
    p_chart.g_time_compute_item += Support.current_time() - start;
    return res;
  }

  /*
   * add all the items with GOAL_SYM state into the goal bin the goal bin has
   * only one Item, which itself has many deductions only "goal bin" should call
   * this function
   */
  public void transit_to_goal(Bin bin1) {// bin1: the bin[0][n], this is not
                                         // goal bin
    l_sorted_items = new ArrayList();
    Item goal_item = null;
    for (Item item1 : bin1.get_sorted_items()) {
      if (item1.lhs == Symbol.GOAL_SYM_ID) {
        double cost = item1.best_deduction.best_cost;
        double final_transition_cost = 0.0;
        for (Model m_i : p_chart.l_models) {
          double mdcost = m_i.finaltransition(item1.tbl_states);
          final_transition_cost += mdcost * m_i.weight;
        }
        ArrayList l_ants = new ArrayList();
        l_ants.add(item1);
        Deduction dt = new Deduction(null, cost + final_transition_cost,
            final_transition_cost, l_ants);
        // Support.write_log_line(String.format("Goal item, total_cost: %.3f; ant_cost: %.3f; final_tran: %.3f; ",cost+final_transition_cost,cost,final_transition_cost),
        // Support.INFO);
        if (goal_item == null) {
          goal_item = new Item(0, p_chart.sent_len + 1, Symbol.GOAL_SYM_ID,
              null, dt, cost + final_transition_cost);
          l_sorted_items.add(goal_item);
        } else {
          goal_item.add_deduction_in_item(dt);
          if (goal_item.best_deduction.best_cost > dt.best_cost)
            goal_item.best_deduction = dt;
        }
      }
    }
    Support.write_log_line(String.format("Goal item, best cost is %.3f",
        goal_item.best_deduction.best_cost), Support.INFO);
    ensure_sorted();

    if (get_sorted_items().size() != 1) {
      Support
          .write_log_line(
              "warning: the goal_bin does not have exactly one item",
              Support.ERROR);
      System.exit(0);
    }
  }

  // axiom is for the zero-arity rules
  public void add_axiom(int i, int j, Rule rl) {
    HashMap tbl_states = compute_item(rl, null, i, j);
    add_deduction_in_bin(tbl_states, rl, i, j, null);
  }

  /*
   * add complete Items in Chart pruning inside this function
   */
  public void complete_cell(int i, int j, ArrayList<SuperItem> l_super_items,
      RuleBin rb) {// each super-item is a list of items
    ArrayList<Rule> l_rules = rb.get_sorted_rules();
    // System.out.println(String.format("Complet_cell is called, n_rules: %d ",
    // l_rules.size()));
    for (Rule rl : l_rules) {
      if (rb.get_arity() == 1) {
        SuperItem super_ant1 = l_super_items.get(0);
        // System.out.println(String.format("Complet_cell, size %d ",
        // super_ant1.l_items.size()));
        // rl.print_info(Support.DEBUG);
        for (Item it_ant1 : super_ant1.l_items) {
          ArrayList<Item> l_ants = new ArrayList<Item>();
          l_ants.add(it_ant1);
          HashMap tbl_states = compute_item(rl, l_ants, i, j);
          add_deduction_in_bin(tbl_states, rl, i, j, l_ants);
        }
      } else if (rb.get_arity() == 2) {
        SuperItem super_ant1 = l_super_items.get(0);
        SuperItem super_ant2 = l_super_items.get(1);
        // System.out.println(String.format("Complet_cell, size %d * %d ",
        // super_ant1.l_items.size(),super_ant2.l_items.size()));
        // rl.print_info(Support.DEBUG);
        for (Item it_ant1 : super_ant1.l_items) {
          for (Item it_ant2 : super_ant2.l_items) {
            // System.out.println(String.format("Complet_cell, ant1(%d, %d), ant2(%d, %d) ",it_ant1.i,it_ant1.j,it_ant2.i,it_ant2.j
            // ));
            ArrayList<Item> l_ants = new ArrayList<Item>();
            l_ants.add(it_ant1);
            l_ants.add(it_ant2);
            HashMap tbl_states = compute_item(rl, l_ants, i, j);
            add_deduction_in_bin(tbl_states, rl, i, j, l_ants);
          }
        }
      } else {
        System.out
            .println("Sorry, we can only deal with rules with at most TWO non-terminals");
        System.exit(0);
      }
    }
  }

  /*
   * add complete Items in Chart pruning inside this function
   */
  // TODO: our implementation do the prunining for each DotItem under each
  // grammar, not aggregated as in the python version
  // TODO: the implementation is little bit different from the description in
  // Liang'2007 ACL paper
  public void complete_cell_cube_prune(int i, int j,
      ArrayList<SuperItem> l_super_items, RuleBin rb) {// combinations: rules,
                                                       // antecent items
    PriorityQueue<CubePruneState> heap_cands = new PriorityQueue<CubePruneState>();// in
                                                                                   // the
                                                                                   // paper,
                                                                                   // it
                                                                                   // is
                                                                                   // called
                                                                                   // cand[v]
    HashMap cube_state_tbl = new HashMap();// rememeber which state has been
                                           // explored

    ArrayList<Rule> l_rules = rb.get_sorted_rules();
    if (l_rules == null || l_rules.size() <= 0)
      return;

    // seed the heap with best item
    Rule cur_rl = l_rules.get(0);
    ArrayList<Item> l_cur_ants = new ArrayList<Item>();
    for (SuperItem si : l_super_items)
      l_cur_ants.add(si.l_items.get(0)); // TODO: si.l_items must be sorted
    HashMap tbl_states = compute_item(cur_rl, l_cur_ants, i, j);

    int[] ranks = new int[1 + l_super_items.size()];// rule, ant items
    for (int d = 0; d < ranks.length; d++)
      ranks[d] = 1;

    CubePruneState best_state = new CubePruneState(tbl_states, ranks, cur_rl,
        l_cur_ants);
    heap_cands.add(best_state);
    cube_state_tbl.put(best_state.get_signature(), 1);
    // cube_state_tbl.put(best_state,1);

    // extend the heap
    Rule old_rl = null;
    Item old_item = null;
    int tem_c = 0;
    while (heap_cands.size() > 0) {
      tem_c++;
      CubePruneState cur_state = heap_cands.poll();
      cur_rl = cur_state.rule;
      l_cur_ants = new ArrayList<Item>(cur_state.l_ants);// critical to create a
                                                         // new list
      // cube_state_tbl.remove(cur_state.get_signature());//TODO, repeat
      add_deduction_in_bin(cur_state.tbl_states, cur_state.rule, i, j,
          cur_state.l_ants);// pre-pruning inside this function

      // if the best state is pruned, then all the remaining states should be
      // pruned away
      if (((Double) cur_state.tbl_states.get(Symbol.TOTAL_COST_SYM_ID))
          .doubleValue() > cut_off_cost + Decoder.fuzz1) {
        // n_prepruned += heap_cands.size();
        p_chart.n_prepruned_fuzz1 += heap_cands.size();
        /*
         * if(heap_cands.size()>1){gtem++;System.out.println("gtem is " +gtem +
         * "; size:" + heap_cands.size());}
         */
        break;
      }
      // extend the cur_state
      for (int k = 0; k < cur_state.ranks.length; k++) {
        // GET new_ranks
        int[] new_ranks = new int[cur_state.ranks.length];
        for (int d = 0; d < cur_state.ranks.length; d++)
          new_ranks[d] = cur_state.ranks[d];
        new_ranks[k] = cur_state.ranks[k] + 1;

        String new_sig = CubePruneState.get_signature(new_ranks);
        // check condtion
        if ((cube_state_tbl.containsKey(new_sig) == true)
            || (k == 0 && new_ranks[k] > l_rules.size())
            || (k != 0 && new_ranks[k] > l_super_items.get(k - 1).l_items
                .size())) {
          continue;
        }

        if (k == 0) {// slide rule
          old_rl = cur_rl;
          cur_rl = l_rules.get(new_ranks[k] - 1);
        } else {// slide ant
          old_item = l_cur_ants.get(k - 1);// conside k==0 is rule
          l_cur_ants.set(k - 1,
              l_super_items.get(k - 1).l_items.get(new_ranks[k] - 1));
        }

        HashMap tbl_states2 = compute_item(cur_rl, l_cur_ants, i, j);
        CubePruneState t_state = new CubePruneState(tbl_states2, new_ranks,
            cur_rl, l_cur_ants);

        // add state into heap
        cube_state_tbl.put(new_sig, 1);

        if (((Double) tbl_states2.get(Symbol.TOTAL_COST_SYM_ID)).doubleValue() < cut_off_cost
            + Decoder.fuzz2) {
          heap_cands.add(t_state);
        } else {
          // n_prepruned +=1;
          p_chart.n_prepruned_fuzz2 += 1;
        }
        // recover
        if (k == 0) {// rule
          cur_rl = old_rl;
        } else {// ant
          l_cur_ants.set(k - 1, old_item);
        }
      }
    }
  }

  private static class CubePruneState implements Comparable {
    int[] ranks;
    HashMap tbl_states;
    Rule rule;
    ArrayList<Item> l_ants;

    public CubePruneState(HashMap st, int[] ranks_in, Rule rl,
        ArrayList<Item> ants) {
      tbl_states = st;
      ranks = ranks_in;
      rule = rl;
      l_ants = new ArrayList<Item>(ants);// create a new vector is critical,
                                         // because l_cur_ants will change later
    }

    public CubePruneState(int[] ranks_in) {// fake: for equals
      ranks = ranks_in;
    }

    public static String get_signature(int[] ranks2) {
      StringBuffer res = new StringBuffer();
      if (ranks2 != null)
        for (int i = 0; i < ranks2.length; i++) {
          res.append(" ");
          res.append(ranks2[i]);
        }
      return res.toString();
    }

    public String get_signature() {
      return get_signature(ranks);
    }

    // natual order by cost
    public int compareTo(Object another) throws ClassCastException {
      if (!(another instanceof CubePruneState))
        throw new ClassCastException("An CubePruneState object expected.");
      if ((Double) this.tbl_states.get(Symbol.TOTAL_COST_SYM_ID) < (Double) ((CubePruneState) another).tbl_states
          .get(Symbol.TOTAL_COST_SYM_ID))
        return -1;
      else if ((Double) this.tbl_states.get(Symbol.TOTAL_COST_SYM_ID) == (Double) ((CubePruneState) another).tbl_states
          .get(Symbol.TOTAL_COST_SYM_ID))
        return 0;
      else
        return 1;
    }
  }

  public Item add_deduction_in_bin(HashMap tbl_states, Rule rl, int i, int j,
      ArrayList<Item> ants) {
    long start = Support.current_time();
    Item res = null;
    HashMap item_state_tbl = (HashMap) tbl_states
        .get(Symbol.ITEM_STATES_SYM_ID);
    double total_cost = ((Double) tbl_states.get(Symbol.TOTAL_COST_SYM_ID))
        .doubleValue();// total_cost=ADDITIVE_COST+TRANSITION_COST+bonus
    double transition_cost = ((Double) tbl_states
        .get(Symbol.TRANSITION_COST_SYM_ID)).doubleValue();// non-stateless
                                                           // transition cost
    double additive_cost = ((Double) tbl_states
        .get(Symbol.ADDITIVE_COST_SYM_ID)).doubleValue();
    // double bonus = ((Double)tbl_states.get(BONUS)).doubleValue();//not used
    double total_cost2 = transition_cost + additive_cost; // total_cost2=total_cost-bonus;
    if (should_prune(total_cost) == false) {
      Deduction dt = new Deduction(rl, total_cost2, transition_cost, ants);
      Item item = new Item(i, j, rl.lhs, item_state_tbl, dt, total_cost);
      add_deduction(item);
      // Support.write_log_line(String.format("add an deduction with arity %d",
      // rl.arity),Support.DEBUG);
      // rl.print_info(Support.DEBUG);
      res = item;
    } else {
      p_chart.n_prepruned++;
      // Support.write_log_line(String.format("Prepruned an deduction with arity %d",
      // rl.arity),Support.INFO);
      // rl.print_info(Support.INFO);
      res = null;
    }
    p_chart.g_time_add_deduction += Support.current_time() - start;
    return res;
  }

  /*
   * each item has a list of deductions need to check whether the item is
   * already exist, if yes, just add the deductions
   */
  private boolean add_deduction(Item new_item) {
    boolean res = false;
    Item old_item = (Item) tbl_items.get(new_item.get_signature());
    if (old_item != null) {// have an item with same states, combine items
      p_chart.n_merged++;
      if (new_item.est_total_cost < old_item.est_total_cost) {
        // the position of old_item in the heap_items may change, basically, we
        // should remove the old_item, and re-insert it (linear time, this is
        // too expense)
        old_item.is_dead = true;// heap_items.remove(old_item);
        dead_items++;
        new_item.add_deductions_in_item(old_item.l_deductions);
        add_new_item(new_item); // this will update the HashMap , so that the
                                // old_item is destroyed
        res = true;
      } else {
        old_item.add_deductions_in_item(new_item.l_deductions);
      }
    } else {// first time item
      p_chart.n_added++;// however, this item may not be used in the future due
                        // to pruning in the hyper-graph
      add_new_item(new_item);
      res = true;
    }
    cut_off_cost = find_min(best_item_cost + Decoder.relative_threshold,
        Symbol.IMPOSSIBLE_COST);
    run_pruning();
    return res;
  }

  // this function is called only there is no such item in the tbl
  private void add_new_item(Item item) {
    tbl_items.put(item.get_signature(), item);// add/replace the item
    l_sorted_items = null; // reset the list
    heap_items.add(item);

    // since l_sorted_items==null, this is not necessary because we will always
    // call ensure_sorted to reconstruct the tbl_super_items
    // add a super-items if necessary
    SuperItem si = (SuperItem) tbl_super_items.get(item.lhs);
    if (si == null) {
      si = new SuperItem(item.lhs);
      tbl_super_items.put(item.lhs, si);
    }
    si.l_items.add(item);

    if (item.est_total_cost < best_item_cost) {
      best_item_cost = item.est_total_cost;
    }
  }

  public void print_info(int level) {
    Support.write_log_line(String.format(
        "#### Stat of Bin, n_items=%d, n_super_items=%d", tbl_items.size(),
        tbl_super_items.size()), level);
    ensure_sorted();
    for (Item it : l_sorted_items)
      it.print_info(level);
  }

  private boolean should_prune(double total_cost) {
    // Support.write_log_line("cut_off_cost: "+cut_off_cost +" real: "+
    // total_cost, Support.INFO);
    return (total_cost >= cut_off_cost);
  }

  private double find_min(double a, double b) {
    return (a <= b) ? a : b;
  }

  private void run_pruning() {
    // Support.write_log_line(String.format("Pruning: heap size: %d; n_dead_items: %d",
    // heap_items.size(),dead_items ), Support.DEBUG);
    if (heap_items.size() == dead_items) {// TODO:clear the heap, and reset
                                          // dead_items??
      heap_items.clear();
      dead_items = 0;
      return;
    }
    while (heap_items.size() - dead_items > Decoder.max_n_items // bin limit
                                                                // pruning
        || heap_items.peek().est_total_cost >= cut_off_cost) {// relative
                                                              // threshold
                                                              // pruning
      Item worst_item = heap_items.poll();
      if (worst_item.is_dead == true)// clear the corrupted item
        dead_items--;
      else {
        tbl_items.remove(worst_item.get_signature());// always make tbl_items
                                                     // current
        p_chart.n_pruned++;
        // Support.write_log_line(String.format("Run_pruning: %d; cutoff=%.3f, realcost: %.3f",p_chart.n_pruned,cut_off_cost,worst_item.est_total_cost),
        // Support.INFO);
      }
    }
    if (heap_items.size() - dead_items == Decoder.max_n_items) {// TODO:??
      cut_off_cost = find_min(cut_off_cost, heap_items.peek().est_total_cost
          + Symbol.EPSILON);
    }
  }

  /*
   * get a sorted list of Items in the bin, and also make sure the list of items
   * in any SuperItem is sorted, this will be called only necessary, which means
   * that the list is not always sorted mainly needed for goal_bin and
   * cube-pruning
   */
  private void ensure_sorted() {
    if (l_sorted_items == null) {
      // get a sorted items ArrayList
      Object[] t_col = tbl_items.values().toArray();
      Arrays.sort(t_col);
      l_sorted_items = new ArrayList<Item>();
      for (int c = 0; c < t_col.length; c++)
        l_sorted_items.add((Item) t_col[c]);
      // TODO: we cannot create new SuperItem here because the DotItem link to
      // them

      // update tbl_super_items
      ArrayList<SuperItem> tem_list = new ArrayList<SuperItem>(
          tbl_super_items.values());
      for (SuperItem t_si : tem_list)
        t_si.l_items.clear();

      for (Item it : l_sorted_items) {
        SuperItem si = ((SuperItem) tbl_super_items.get(it.lhs));
        if (si == null) {// sanity check
          Support.write_log_line("Does not have super Item, have to exist",
              Support.ERROR);
          System.exit(0);
        }
        si.l_items.add(it);
      }

      ArrayList<Integer> to_remove = new ArrayList<Integer>();
      // note: some SuperItem may not contain any items any more due to pruning
      for (Iterator e = tbl_super_items.keySet().iterator(); e.hasNext();) {
        Integer k = (Integer) e.next();
        if (((SuperItem) tbl_super_items.get(k)).l_items.size() <= 0) {
          to_remove.add(k);// note that: we cannot directly do the remove,
                           // because it will throw
                           // ConcurrentModificationException
          // System.out.println("have zero items in superitem " + k);
          // tbl_super_items.remove(k);
        }
      }
      for (Integer t : to_remove)
        tbl_super_items.remove(t);
    }
  }

  public ArrayList<Item> get_sorted_items() {
    ensure_sorted();
    return l_sorted_items;
  }

  public HashMap get_sorted_super_items() {
    ensure_sorted();
    return tbl_super_items;
  }

  public Item getitem(int pos) {// not used
    ensure_sorted();
    return l_sorted_items.get(pos);
  }

  /* list of items that have the same lhs but may have different LM states */
  public class SuperItem {
    int lhs;// state
    ArrayList<Item> l_items = new ArrayList<Item>();

    public SuperItem(int lhs_in) {
      lhs = lhs_in;
    }
  }
}
