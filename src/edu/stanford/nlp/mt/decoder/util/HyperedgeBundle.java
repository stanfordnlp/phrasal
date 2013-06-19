package edu.stanford.nlp.mt.decoder.util;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
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

  private List<Hypothesis<TK,FV>> itemList;
  private final List<ConcreteTranslationOption<TK,FV>> sortedRuleList;
  private boolean isLocked = false;
  private BitSet expandedItems;

  // Defines the location in the cube from which to generate
  // the next successors
  private int lastItem = -1;
  private int lastRule = -1;

  /**
   * Assumes that the list of translation rules has already been sorted.
   * 
   * @param sortedRuleList
   */
  public HyperedgeBundle(List<ConcreteTranslationOption<TK,FV>> sortedRuleList) {
    this.sortedRuleList = sortedRuleList;
    this.itemList = Generics.newArrayList();
  }

  public boolean updateItem(Hypothesis<TK, FV> oldHypothesis,
      Hypothesis<TK, FV> newHypothesis) {
    int numItems = itemList.size();
    for (int i = 0; i < numItems; ++i) {
      if (itemList.get(i) == oldHypothesis) {
        itemList.set(i, newHypothesis);
        return true;
      }
    }
    return false;
  }
  
  /**
   * Add a hypothesis to this bundle.
   * 
   * @param hypothesis
   */
  public void add(Hypothesis<TK,FV> hypothesis) {
    if (isLocked) {
      throw new RuntimeException("Cannot expand a locked bundle");
    }
    itemList.add(hypothesis);
  }

  /**
   * This method must be called after the final call to add() and
   * before the first call to nextSuccessors().
   */
  public void lock() {
    Collections.sort(itemList);
    expandedItems = new BitSet();
    isLocked = true;
  }

  /**
   * Mark the last best consequent according to combination costs.
   * 
   * @param antecedent
   */
  public void updateLastBestScoredConsequent(Consequent<TK,FV> antecedent) {
    lastItem = antecedent.itemId;
    lastRule = antecedent.ruleId;
  }
  
  /**
   * Returned unsorted, ungenerated successors to this antecedent. This list
   * will have a length in the range [0,2].
   * 
   * @param antecedent
   * @return
   */
  public List<Consequent<TK,FV>> nextSuccessors() {
    if (! isLocked) {
      throw new RuntimeException("Cannot expand successors before locking bundle");
    }
    List<Consequent<TK,FV>> consequentList = Generics.newArrayList(2);
    if (expandedItems.cardinality() == 0) {
      // Top-left corner of the grid
      lastItem = 0;
      lastRule = 0;
      consequentList.add(new Consequent<TK,FV>(itemList.get(0), sortedRuleList.get(0), this, lastItem, lastRule));
      expandedItems.set(0);

    } else {
      // Move down in the grid
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
    return String.format("#items: %d  #rules: %d  cube: (%d,%d) coverage: %s", 
        itemList.size(), sortedRuleList.size(), lastItem, lastRule, expandedItems.toString());
  }

  public static class Consequent<TK,FV> {
    public final Hypothesis<TK,FV> antecedent;
    public final ConcreteTranslationOption<TK,FV> rule;
    public final HyperedgeBundle<TK, FV> bundle;
    private final int itemId;
    private final int ruleId;
    public Consequent(Hypothesis<TK,FV> antecedent, 
        ConcreteTranslationOption<TK,FV> rule,
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
