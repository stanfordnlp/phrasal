package edu.stanford.nlp.mt.decoder.util;

import java.util.BitSet;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.util.Generics;

/**
 * Implements a sorted bundle of hypothesis and an assorted list of scored hypotheses. Generates
 * successors given a phrase rule list.
 * 
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class HyperedgeBundle<TK,FV> {

  private final List<Derivation<TK,FV>> itemList;
  private final List<ConcreteRule<TK,FV>> sortedRuleList;
  private BitSet expandedItems;

  /**
   * Assumes that the list of translation rules has already been sorted.
   * 
   * @param sortedRuleList
   */
  public HyperedgeBundle(List<Derivation<TK,FV>> sortedDerivationList, 
      List<ConcreteRule<TK,FV>> sortedRuleList) {
    this.sortedRuleList = sortedRuleList;
    this.itemList = sortedDerivationList;
    this.expandedItems = new BitSet();
  }

  /**
   * Returned unsorted, ungenerated successors to this antecedent. This list
   * will have a length in the range [0,2].
   * 
   * @param antecedent
   * @return
   */
  public List<Consequent<TK,FV>> nextSuccessors(Consequent<TK,FV> antecedent) {
    List<Consequent<TK,FV>> consequentList = Generics.newArrayList(2);
    if (expandedItems.cardinality() == 0) {
      // Top-left corner of the grid
      assert antecedent == null || (antecedent.itemId < 0 && antecedent.ruleId < 0);
      consequentList.add(new Consequent<TK,FV>(itemList.get(0), sortedRuleList.get(0), this, 0, 0));
      expandedItems.set(0);

    } else {
      // Move down in the grid
      int lastItem = antecedent.itemId;
      int lastRule = antecedent.ruleId;
      int succItem = getIndex(lastItem+1, lastRule);
      if ( ! expandedItems.get(succItem) && lastItem+1 < itemList.size()) {
        consequentList.add(new Consequent<TK,FV>(itemList.get(lastItem+1), sortedRuleList.get(lastRule), 
            this, lastItem+1, lastRule));
        expandedItems.set(succItem);
      }
      // Move right in the grid
      int succRule = getIndex(lastItem, lastRule+1);
      if ( ! expandedItems.get(succRule) && lastRule+1 < sortedRuleList.size()) {
        consequentList.add(new Consequent<TK,FV>(itemList.get(lastItem), sortedRuleList.get(lastRule+1), 
            this, lastItem, lastRule+1));
        expandedItems.set(succRule);
      }
    }
    return consequentList;
  }

  private int getIndex(int itemId, int ruleId) {
    // Row-major order
    return itemId * sortedRuleList.size() + ruleId;
  }

  @Override
  public String toString() {
    return String.format("#items: %d  #rules: %d  coverage: %s", 
        itemList.size(), sortedRuleList.size(), expandedItems.toString());
  }

  public static class Consequent<TK,FV> {
    public final Derivation<TK,FV> antecedent;
    public final ConcreteRule<TK,FV> rule;
    public final HyperedgeBundle<TK, FV> bundle;
    private final int itemId;
    private final int ruleId;
    public Consequent(Derivation<TK,FV> antecedent, 
        ConcreteRule<TK,FV> rule,
        HyperedgeBundle<TK,FV> bundle,
        int itemId,
        int ruleId) {
      this.antecedent = antecedent;
      this.rule = rule;
      this.bundle = bundle;
      this.itemId = itemId;
      this.ruleId = ruleId;
    }

    @Override
    public String toString() {
      return String.format("itemId: %d  ruleId: %d", itemId, ruleId);
    }
  }
}
