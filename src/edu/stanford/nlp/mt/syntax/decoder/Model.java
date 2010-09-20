package edu.stanford.nlp.mt.syntax.decoder;

import java.util.ArrayList;
import java.util.HashMap;

import edu.stanford.nlp.mt.syntax.decoder.TMGrammar.Rule;

/* Zhifei Li, <zhifei.work@gmail.com>
 * Johns Hopkins University
 */

/*provide ways to calculate cost based on rule and state information*/
@SuppressWarnings("unchecked")
public class Model {
  boolean stateless = false; // rule cost can be calculated from rule alone,
                             // note: stateless==true implies that
                             // contextual==true
  boolean contextual = false; // rule cost can be calculated from rule and
                              // i,j,j1 alone, some models in lexical.py are in
                              // this type (contextual)
  double weight = 0.0;
  long time_consumed = 0; // for debug purpose

  public Model(double w) {
    weight = w;
  }

  /* estimate depend only on the rule itself and the foreign sentence */
  // only used in TMGrammar.estimate_rule()
  public double estimate(Rule r) {
    HashMap res = transition(r, null, -1, -1, -1);
    if (res == null || res.get(Symbol.TRANSITION_COST_SYM_ID) == null)// TODO
      return 0.0;
    else
      return ((Double) res.get(Symbol.TRANSITION_COST_SYM_ID)).doubleValue();
  }

  // depends on finate state informations
  // return transition_cost, and state
  // antstates: states of this model in ant items
  // NOTE: for model that is non-stateless/non-contexual, it must return a tbl
  // with Symbol.TRANSITION_COST and Symbol.ITEM_STATES
  // only used by chart.nbest_extract, chart.compute_item,
  // chart.transition_final, chart.prepare_rulebin
  public HashMap transition(Rule rule, ArrayList antstates, int i, int j, int j1) {
    return null;
  }

  public double finaltransition(HashMap state) {// state: this model's state
    return 0.0;
  }

  public static class WordPenalty extends Model {
    double omega = Math.log10(Math.E);// TODO

    public WordPenalty(double w) {
      super(w);
      stateless = true;
    }

    @Override
    public HashMap transition(Rule r, ArrayList antstates, int i, int j, int j1) {
      HashMap res = new HashMap();
      double transition_cost = estimate(r);
      res.put(Symbol.TRANSITION_COST_SYM_ID, transition_cost);
      return res;
    }

    @Override
    public double estimate(Rule r) {
      return omega * (r.english.length - r.arity);
    }
  }

  public static class PhraseModel extends Model {
    /*
     * the feature will be activated only when the owner is the same as the
     * rule, we need an owner to distinguish different feature in different
     * phrase table/source
     */
    int owner;
    int column = -1;// zero-indexed

    public PhraseModel(int ow, int cl, double w) {// zero-indexed
      super(w);
      stateless = true;
      column = cl;
      owner = ow;
    }

    // antstates: states of this model in ant items
    @Override
    public HashMap transition(Rule r, ArrayList antstates, int i, int j, int j1) {
      HashMap res = new HashMap();
      double transition_cost = estimate(r);
      res.put(Symbol.TRANSITION_COST_SYM_ID, transition_cost);
      return res;
    }

    @Override
    public double estimate(Rule r) {
      // Support.write_log_line("model owner: " + owner +
      // "; rule owner: "+r.owner, Support.INFO);
      if (owner == r.owner) {
        if (column < r.feat_scores.length)
          return r.feat_scores[column];
        else
          return 0.0;
      } else
        return 0.0;
    }
  }

  public static class PhrasePenalty extends Model {
    int owner;
    double alpha = Math.log10(Math.E);

    public PhrasePenalty(int ow, double w) {
      super(w); // default behaviror: if we do not call a super constructor,
                // then it will automally call a super() without any parameter
      owner = ow;
      stateless = true;
    }

    @Override
    public HashMap transition(Rule r, ArrayList antstates, int i, int j, int j1) {
      HashMap res = new HashMap();
      double transition_cost = estimate(r);
      res.put(Symbol.TRANSITION_COST_SYM_ID, transition_cost);
      return res;
    }

    @Override
    public double estimate(Rule r) {
      // Support.write_log_line("model owner: " + owner +
      // "; rule owner: "+r.owner, Support.DEBUG);
      if (owner == r.owner)
        return alpha;
      else
        return 0.0;

    }
  }

  public static class ArityPhrasePenalty extends Model {
    // when the rule.arity is in the range, then this feature is activated
    int owner;
    double alpha = Math.log10(Math.E);
    int min_arity = 0;
    int max_arity = 0;

    public ArityPhrasePenalty(int ow, int min, int max, double w) {
      super(w);
      owner = ow;
      stateless = true;
      min_arity = min;
      max_arity = max;

    }

    @Override
    public HashMap transition(Rule r, ArrayList antstates, int i, int j, int j1) {// only
                                                                                  // depends
                                                                                  // on
                                                                                  // rule
      HashMap res = new HashMap();
      // TODO: merge with estimate()
      // double transition_cost=estimate(r);
      double transition_cost = 0.0;
      if (owner == r.owner && r.arity >= min_arity && r.arity <= max_arity)
        transition_cost = alpha;
      res.put(Symbol.TRANSITION_COST_SYM_ID, transition_cost);
      return res;
    }

    @Override
    public double estimate(Rule r) {
      // System.out.println("owner is" + r.owner +" arity " +r.arity);
      // r.print_info(Support.INFO);
      // if(owner.compareTo(r.owner)==0 )//TODO: wrong implementation in hiero,
      // should check the arity
      if (owner == r.owner && r.arity >= min_arity && r.arity <= max_arity)// TODO:
                                                                           // correct
                                                                           // way?
        return alpha;
      else
        return 0.0;
    }
  }
}
