package edu.stanford.nlp.mt.syntax.hiero;

import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.ArrayList;

/*Zhifei Li, <zhifei.work@gmail.com>
 * Johns Hopkins University
 */

/*public interfaces
 * TMGrammar: init and load the grammar
 * TrieNode: match symbol for next layer
 * RuleBin: get sorted rules
 * Rule: rule information
 * */

@SuppressWarnings({ "unchecked", "unused" })
public class HieroGrammarScorer_Trie {
  public static int MAX_N_RULES_SAME_FRENCH = 40;

  private int num_rule_read = 0;
  private int num_rule_pruned = 0;
  private int num_rule_bin = 0;
  private TrieNode root = null;

  static private double tem_estcost = 0.0;// debug

  /*
   * TMGrammar is composed by Trie nodes Each trie node has: (1) RuleBin: a list
   * of rules matching the french sides so far (2) a HashMap of next-layer trie
   * nodes, the next french word used as the key in HashMap
   */

  public HieroGrammarScorer_Trie() {
    root = new TrieNode();
  }

  public TrieNode get_root() {
    return root;
  }

  // normalize the grammar
  public void score_grammar() {

  }

  public void dump_grammar() {

  }

  public Rule add_rule(Rule p_rule) {
    num_rule_read++;
    // ######### identify the position, and insert the trinodes if necessary
    TrieNode pos = root;
    for (int k = 0; k < p_rule.french.length; k++) {
      int cur_sym_id = p_rule.french[k];
      TrieNode next_layer = pos.match_symbol(cur_sym_id);
      if (next_layer != null) {
        pos = next_layer;
      } else {
        TrieNode tem = new TrieNode();// next layer node
        if (pos.tbl_children == null)
          pos.tbl_children = new HashMap();
        pos.tbl_children.put(cur_sym_id, tem);
        pos = tem;
      }
    }
    // #########: now add the rule into the trinode
    if (pos.rule_bin == null) {
      pos.rule_bin = new RuleBin();
      num_rule_bin++;
    }
    pos.rule_bin.add_rule(p_rule);
    return p_rule;
  }

  // this method should be called such that all the rules in rulebin are sorted,
  // this will avoid synchronization for get_sorted_rules function
  private void ensure_grammar_sorted() {
    if (root != null)
      root.ensure_sorted();
  }

  protected void print_grammar() {
    System.out.println("###########Grammar###########");
    System.out.println(String.format(
        "####num_rules: %d; num_bins: %d; num_pruned: %d; sumest_cost: %.5f",
        num_rule_read, num_rule_bin, num_rule_pruned, tem_estcost));
    /*
     * if(root!=null) root.print_info(Support.DEBUG);
     */
  }

  public class TrieNode {
    private RuleBin rule_bin = null;
    private HashMap tbl_children = null;

    public TrieNode match_symbol(int sym_id) {// looking for the next layer
                                              // trinode corresponding to this
                                              // symbol
      if (tbl_children == null)
        return null;
      return (TrieNode) tbl_children.get(sym_id);
    }

    public RuleBin get_rule_bin() {
      return rule_bin;
    }

    public boolean is_no_child_trienodes() {
      return (tbl_children == null);
    }

    // recursive call, to make sure all rules are sorted
    private void ensure_sorted() {
      if (rule_bin != null)
        rule_bin.get_sorted_rules();
      if (tbl_children != null) {
        Object[] tem = tbl_children.values().toArray();
        for (int i = 0; i < tem.length; i++) {
          ((TrieNode) tem[i]).ensure_sorted();
        }
      }
    }

    private void print_info(int level) {
      System.out.println("###########TrieNode###########");
      if (rule_bin != null) {
        System.out.println("##### RuleBin(in TrieNode) is");
        rule_bin.print_info(level);
      }
      if (tbl_children != null) {
        Object[] tem = tbl_children.values().toArray();
        for (int i = 0; i < tem.length; i++) {
          System.out.println("##### ChildTrieNode(in TrieNode) is");
          ((TrieNode) tem[i]).print_info(level);
        }
      }
    }
  }

  // contain all rules with the same french side (and thus same arity)
  public class RuleBin {
    private PriorityQueue<Rule> heap_rules = null;
    private boolean sorted = false;
    private ArrayList<Rule> l_sorted_rules = new ArrayList();
    // int arity;
    private HashMap tbl_eng_rules = new HashMap();

    // TODO: now, we assume this function will be called only after all the
    // rules have been read
    // this method need to be synchronized as we will call this function only
    // after the decoding begins
    // to avoid the synchronized method, we should call this once the grammar is
    // finished
    // public synchronized ArrayList<Rule> get_sorted_rules(){
    public ArrayList<Rule> get_sorted_rules() {
      if (sorted == false) {// sort once
        l_sorted_rules.clear();
        while (heap_rules.size() > 0) {
          Rule t_r = heap_rules.poll();
          l_sorted_rules.add(0, t_r);
        }
        sorted = true;
        heap_rules = null;
      }
      return l_sorted_rules;
    }

    private void add_rule(Rule rl) {
      if (heap_rules == null)
        heap_rules = new PriorityQueue(1, Rule.FrequencyComparator);// TODO:
                                                                    // initial
                                                                    // capacity?
      String sig = rl.get_eng_signature();
      Rule old_rule = (Rule) tbl_eng_rules.get(sig);
      if (old_rule != null) {
        for (int i = 0; i < rl.feat_scores.length; i++)
          old_rule.feat_scores[i] += rl.feat_scores[i];
        // TODO ** this is too expenisve
        heap_rules.remove(old_rule);
        heap_rules.add(old_rule);
      } else {
        tbl_eng_rules.put(sig, rl);
        heap_rules.add(rl);
      }
      num_rule_pruned += run_pruning();
    }

    private int run_pruning() {
      int n_pruned = 0;
      while (heap_rules.size() > MAX_N_RULES_SAME_FRENCH) {
        n_pruned++;
        heap_rules.poll();
      }
      return n_pruned++;
    }

    // normalize the rulebin during phrase extraction
    private void score_rulebin() {

    }

    private void print_info(int level) {
      // Support.write_log_line(String.format("RuleBin, arity is %d",arity),level);
      ArrayList t_l = get_sorted_rules();
      for (int i = 0; i < t_l.size(); i++)
        ((Rule) t_l.get(i)).print_info();
    }
  }

  public static class Rule {
    // Rule formate: [Phrase] ||| french ||| english ||| feature scores
    public int lhs;// tag of this rule, state to upper layer
    public int[] french;
    public int[] english;
    public float[] feat_scores;// the feature scores for this rule
    public ArrayList alignments;

    // public int arity=0;//TODO: disk-grammar does not have this information,
    // so, arity-penalty feature is not supported in disk-grammar

    public Rule(int lhs_in, int[] fr_in, int[] eng_in) {
      lhs = lhs_in;
      french = fr_in;
      english = eng_in;
    }

    // prune grammar based on the relative frequence P(english|french)
    protected static Comparator FrequencyComparator = new Comparator() {
      public int compare(Object rule1, Object rule2) {
        float freq1 = ((Rule) rule1).feat_scores[0];// ??
        float freq2 = ((Rule) rule2).feat_scores[0];
        if (freq1 < freq2)
          return -1;
        else if (freq1 == freq2)
          return 0;
        else
          return 1;
      }
    };

    public String get_eng_signature() {
      StringBuffer res = new StringBuffer();
      res.append(lhs);
      res.append(" ");
      for (int i = 0; i < english.length; i++) {
        res.append(english[i]);
        res.append(" ");
      }
      return res.toString();
    }

    public void print_info() {
      // Support.write_log("Rule is: "+ lhs + " ||| " +
      // Support.arrayToString(french, " ") + " ||| " +
      // Support.arrayToString(english, " ") + " |||", level);
      System.out
          .print("Rule is: "
              + edu.stanford.nlp.mt.syntax.decoder.Symbol.get_string(lhs)
              + " ||| ");
      for (int i = 0; i < french.length; i++)
        System.out.print(edu.stanford.nlp.mt.syntax.decoder.Symbol
            .get_string(french[i]) + " ");
      System.out.print("||| ");
      for (int i = 0; i < english.length; i++)
        System.out.print(edu.stanford.nlp.mt.syntax.decoder.Symbol
            .get_string(english[i]) + " ");
      System.out.print("||| ");
      for (int i = 0; i < feat_scores.length; i++)
        System.out.print(" " + feat_scores[i]);
      System.out.print("\n");
    }
  }
}
