package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.LexCoocTable;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Sorts a rule list according to the target.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class PrefixRuleGrid<TK,FV> {

  private final List<List<ConcreteRule<TK,FV>>> ruleLists;
  private final CoverageSet sourceCoverage;
  private final CoverageSet targetCoverage;
  private final Sequence<TK> prefix;
  private final Sequence<TK> source;
  private final BitSet isSorted;

  /**
   * Constructor.
   * 
   * @param ruleList
   * @param source
   * @param prefix
   */
  public PrefixRuleGrid(List<ConcreteRule<TK, FV>> ruleList, Sequence<TK> source, 
      Sequence<TK> prefix) {
    this.source = source;
    this.prefix = prefix;
    this.ruleLists = IntStream.range(0, prefix.size()).mapToObj(i -> new ArrayList<ConcreteRule<TK,FV>>())
        .collect(Collectors.toList());
    this.sourceCoverage = new CoverageSet();
    this.targetCoverage = new CoverageSet();
    this.isSorted = new BitSet();
    sortRules(ruleList);
  }

  private void sortRules(List<ConcreteRule<TK, FV>> ruleList) {
    for (ConcreteRule<TK,FV> rule : ruleList) {
      List<Integer> matches = findAll(rule.abstractRule.target);
      if (matches.size() > 0) {
        sourceCoverage.or(rule.sourceCoverage);
        matches.stream().forEach(i -> {
          targetCoverage.set(i, i + rule.abstractRule.target.size());
          ruleLists.get(i).add(rule);
        });
      }
    }
  }

  private List<Integer> findAll(Sequence<TK> targetSubstr) {
    
    // TODO(spenceg) Must include rules that run off the end of the prefix.

    // Naive O(n^2) implementation
//    return IntStream.range(0, prefix.size()).
    
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Add synthetic rules to the grid.
   * 
   * @param coocTable
   */
  public void augmentGrid(LexCoocTable coocTable) {
    // Augment with synthetic singleton rules
  }

  /**
   * Add synthetic rules to the grid.
   * 
   * @param translationModel
   * @param inputProperties
   * @param scorer
   */
  public void augmentGrid(DynamicTranslationModel<FV> translationModel, InputProperties inputProperties,
      Scorer<FV> scorer) {
    // Augment with the full translation model
  }

  /**
   * Source coverage.
   * 
   * @return
   */
  public CoverageSet getSourceCoverage() {
    return sourceCoverage;
  }

  /**
   * Target coverage.
   * 
   * @return
   */
  public CoverageSet getTargetCoverage() { 
    return targetCoverage;
  }

  /**
   * Get rules for the start element in the prefix.
   * 
   * @param startPos
   * @return
   */
  public List<ConcreteRule<TK,FV>> get(int startPos) {
    if ( ! isSorted.get(startPos)) {
      Collections.sort(ruleLists.get(startPos));
      isSorted.set(startPos);
    }
    return ruleLists.get(startPos);
  }
}
