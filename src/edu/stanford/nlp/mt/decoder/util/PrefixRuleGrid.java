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
  
  private List<List<List<ConcreteRule<TK,FV>>>> index;
  private final CoverageSet targetCoverage;
  private final Sequence<TK> prefix;
  int maxSourceLength = 0;
  
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
   * Organize the list of rules by target position and source cardinality.
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
    for (int i = 0, sz = prefix.size(); i < sz; ++i) index.add(new ArrayList<>(4));
    List<ConcreteRule<TK, FV>> filteredRuleList = new ArrayList<>(ruleList.size()/2);
    for (ConcreteRule<TK,FV> rule : ruleList) {
      if(prefix.contains(rule.abstractRule.target))
        filteredRuleList.add(rule);
    }

    Collections.sort(filteredRuleList);
    
    for (ConcreteRule<TK,FV> rule : filteredRuleList) {
      if (rule.abstractRule.target.size() == 0) {
        continue; // Source deletion rule
      } else {
        List<Integer> matches = findAll(wordToPosition, rule.abstractRule.target);
        for (int i : matches) {
          int end = Math.min(prefix.size(), i + rule.abstractRule.target.size());
          targetCoverage.set(i, end);
          int sourceLength = rule.abstractRule.source.size();
          while(index.get(i).size() < sourceLength) index.get(i).add(new ArrayList<>());
          index.get(i).get(sourceLength - 1).add(rule);
          if(sourceLength > maxSourceLength) maxSourceLength = sourceLength;
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
   * Get rules for the start element in the prefix and given source phrase length.
   * 
   * @param startPos
   * @param sourceLength
   * @return
   */
  public List<ConcreteRule<TK,FV>> get(int startPos, int sourceLength) {
    return index.get(startPos).get(sourceLength - 1);
  }
  
  /**
   * Get a specific rule for the start element in the prefix and given source phrase length.
   * source phrase lengths have -1 semantics: sourceLength == 0 => 1 source word
   *                                          sourceLength == 1 => 2 source words etc.
   * 
   * @param startPos
   * @param sourceLength
   * @param rulePosition
   * @return
   */
  public ConcreteRule<TK,FV> get(int startPos, int sourceLength, int rulePosition) {
    if(index.get(startPos).size() <= sourceLength ||
        index.get(startPos).get(sourceLength).size() <= rulePosition) return null;
    return index.get(startPos).get(sourceLength).get(rulePosition);
  }
  
  public int maxSourceLength() {
    return maxSourceLength;
  }
}
