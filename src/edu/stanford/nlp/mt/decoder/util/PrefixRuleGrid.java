package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.CoverageSet;
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

  private static final Logger logger = LogManager.getLogger(PrefixRuleGrid.class.getName());
  
  private List<List<ConcreteRule<TK,FV>>> index;
  private final CoverageSet targetCoverage;
  private final Sequence<TK> prefix;
  
  /**
   * Constructor.
   * 
   * @param ruleList
   * @param source
   * @param prefix
   */
  public PrefixRuleGrid(List<ConcreteRule<TK, FV>> ruleList, Sequence<TK> source, 
      Sequence<TK> prefix) {
    this.prefix = prefix;
    this.targetCoverage = new CoverageSet(prefix.size());
    sortRules(ruleList);
  }

  /**
   * Organize the list of rules by target.
   * 
   * @return 
   */
  private void sortRules(List<ConcreteRule<TK, FV>> ruleList) {
    // Make prefix word type -> position
    Map<TK,List<Integer>> wordToPosition = new HashMap<>();
    for (int i = 0, limit = prefix.size(); i < limit; ++i) {
      TK token = prefix.get(i);
      List<Integer> positionList = wordToPosition.get(token);
      if (positionList == null) {
        positionList = new ArrayList<>();
        wordToPosition.put(token, positionList);
      }
      positionList.add(i);
    }
    
    // Find the prefix rules
    int numRules = 0;
    index = new ArrayList<>(prefix.size());
    for (int i = 0, sz = prefix.size(); i < sz; ++i) index.add(new ArrayList<>());
    for (ConcreteRule<TK,FV> rule : ruleList) {
      if (rule.abstractRule.target.size() == 0) {
        continue; // Source deletion rule
      } else {
        List<Integer> matches = findAll(wordToPosition, rule.abstractRule.target);
        for (int i : matches) {
          int end = Math.min(prefix.size(), i + rule.abstractRule.target.size());
          targetCoverage.set(i, end);
          index.get(i).add(rule);
          ++numRules;
        }
      }
    }
    logger.info("# prefix rules: {}/{}", numRules, ruleList.size());
  }

  /**
   * Targets can match past the end of the prefix.
   * 
   * @param wordToPosition
   * @param target
   * @return
   */
  private List<Integer> findAll(Map<TK, List<Integer>> wordToPosition, Sequence<TK> target) {
    return wordToPosition.getOrDefault(target.get(0), Collections.emptyList()).stream().filter(pIdx -> {
      for (int i = 0, sz = target.size(), psz = prefix.size(); i < sz && pIdx+i < psz; ++i) {
        if ( ! target.get(i).equals(prefix.get(pIdx+i))) {
          return false;
        }
      }
      return true;
    }).collect(Collectors.toList());
  }
  
  /**
   * Return the prefix coverage.
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
    return index.get(startPos);
  }
}
