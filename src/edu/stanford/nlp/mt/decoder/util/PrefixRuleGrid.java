package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.stats.SimilarityMeasures;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IString;
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

  private static final Logger logger = LogManager.getLogger(PrefixRuleGrid.class.getName());
  
  // Method: rule adaptation
  private static final int MAX_ORDER = 3;
  private static final double ADAPTATION_THRESHOLD = 0.20;
  
  // Method: target side similarity
  private static final double SIM_THRESHOLD = 0.75;
  
  private List<List<ConcreteRule<TK,FV>>> index;
  private Map<Sequence<TK>,List<ConcreteRule<TK,FV>>> tgtUnigramToRule;
  private final CoverageSet targetCoverage;
  private final Sequence<TK> prefix;
  private final Sequence<TK> source;
  private final List<ConcreteRule<TK, FV>> originalList;
  
  // Low probability rules that can be adapted.
  private List<ConcreteRule<TK,FV>> adaptableRules;
  
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
    this.targetCoverage = new CoverageSet(prefix.size());
    this.originalList = ruleList;
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
    tgtUnigramToRule = new HashMap<>();
    index = new ArrayList<>(prefix.size());
    for (int i = 0, sz = prefix.size(); i < sz; ++i) index.add(new ArrayList<>());
    for (ConcreteRule<TK,FV> rule : ruleList) {
      if (rule.abstractRule.target.size() == 0) continue;
      List<Integer> matches = findAll(wordToPosition, rule.abstractRule.target);
      if (matches.size() > 0) {
        for (int i : matches) {
          targetCoverage.set(i, i + rule.abstractRule.target.size());
          index.get(i).add(rule);
          ++numRules;
        }
      } else if(rule.abstractRule.target.size() == 1) {
        List<ConcreteRule<TK,FV>> rulesForTgt = tgtUnigramToRule.get(rule.abstractRule.target);
        if (rulesForTgt == null) {
          rulesForTgt = new ArrayList<>();
          tgtUnigramToRule.put(rule.abstractRule.target, rulesForTgt);
        }
        rulesForTgt.add(rule);
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
   * Add synthetic rules to the grid.
   * 
   * @param coocTable
   */
  @SuppressWarnings("unchecked")
  public void augmentGrid(List<DynamicTranslationModel<FV>> tmList, String[] featureNames, Scorer<FV> scorer,
      FeatureExtractor<TK,FV> featurizer, InputProperties inputProperties, int sourceInputId) {

    // Augment with synthetic singleton rules
    for (int i = targetCoverage.nextClearBit(0), pSz = prefix.size(); 
        i >= 0 && i < pSz; 
        i = targetCoverage.nextClearBit(i+1)) {
        // ++i) {
      final List<ConcreteRule<TK,FV>> rulesForPosition = index.get(i);

      final IString targetQuery = (IString) prefix.get(i);
      final int cnt_e = tmList.stream().mapToInt(tm -> tm.getTargetLexCount(targetQuery)).sum();
      final Sequence<IString> targetSpan = (Sequence<IString>) prefix.subsequence(i, i+1);
      
      // Highest precision.
      // Maybe missed due to sampling in the TM. Check the TM.
      boolean addedRule = false;
      for (int j = 0, sSz = source.size(); j < sSz; ++j) {
        IString sourceQuery = (IString) source.get(j);
        final int cnt_ef = tmList.stream().mapToInt(tm -> tm.getJointLexCount(sourceQuery, targetQuery)).sum();
        if (cnt_ef == 0) continue;
        final int cnt_f = tmList.stream().mapToInt(tm -> tm.getSourceLexCount(sourceQuery)).sum();
        assert cnt_f > 0;
        final Sequence<IString> sourceSpan = (Sequence<IString>) source.subsequence(j,j+1);
        CoverageSet sourceSpanCoverage = new CoverageSet(source.size());
        sourceSpanCoverage.set(j);
        ConcreteRule<TK,FV> syntheticRule = (ConcreteRule<TK, FV>) SyntheticRules.makeSyntheticRule(sourceSpan, 
            targetSpan, sourceSpanCoverage, featureNames, (Scorer<String>) scorer, 
            (FeatureExtractor<IString,String>) featurizer, cnt_ef, cnt_e, cnt_f, inputProperties, 
            (Sequence<IString>) source, sourceInputId);
        System.err.printf("P1: %s%n", syntheticRule);
        rulesForPosition.add(syntheticRule);
        addedRule = true;
      }
      
      // Next highest precision.
      // Either a new word type, or a word type that hasn't been seen with anything in the source.
      // See if this word type is similar to anything in the query, e.g., maybe this is a mis-spelling.
      if (!addedRule) {
        final String queryStr = targetSpan.toString();
        for (Sequence<TK> tgt : tgtUnigramToRule.keySet()) {
          String candidateStr = tgt.toString();
          double score = SimilarityMeasures.jaccard(queryStr, candidateStr);
          if (score > SIM_THRESHOLD) {
            final List<ConcreteRule<TK,FV>> ruleList = tgtUnigramToRule.get(tgt);
            for (ConcreteRule<TK,FV> rule : ruleList) {
              ConcreteRule<TK,FV> syntheticRule = (ConcreteRule<TK, FV>) SyntheticRules.makeSyntheticRule((ConcreteRule<IString, String>) rule,
                  (Sequence<IString>) tgt, (Scorer<String>) scorer, (FeatureExtractor<IString,String>) featurizer, 
                  (Sequence<IString>) source, inputProperties, sourceInputId);
              System.err.printf("P2: %s%n", syntheticRule);
              rulesForPosition.add(syntheticRule);
              addedRule = true;
            }
          }
        }
      }
      
      // Lowest precision. Revert to target OOV model.
      if (!addedRule) {
        if (this.adaptableRules == null) populateAdaptableRules();
        for (ConcreteRule<TK,FV> rule : adaptableRules) {
          ConcreteRule<TK,FV> syntheticRule = (ConcreteRule<TK, FV>) SyntheticRules.makeSyntheticRule((ConcreteRule<IString, String>) rule,
              targetSpan, (Scorer<String>) scorer, (FeatureExtractor<IString,String>) featurizer, 
              (Sequence<IString>) source, inputProperties, sourceInputId);
          System.err.printf("P3: %s%n", syntheticRule);
          rulesForPosition.add(syntheticRule);
          addedRule = true;
        }
      }
      
      // Sort the augmented list
      Collections.sort(rulesForPosition);

      if (!addedRule) logger.warn("Could not create rule for token '{}' (position {})", targetQuery, i);
    }
  }

  private void populateAdaptableRules() {
    this.adaptableRules = new ArrayList<>();
    RuleGrid<TK,FV> ruleGrid = new RuleGrid<TK,FV>(this.originalList, source);
    for (int order = 1; order <= MAX_ORDER; ++order) {
      // Get posterior coverage
      List<RuleCoverage> coverage = new ArrayList<>(source.size());
      for (int i = 0, limit = source.size() - order; i <= limit; ++i) {
        int j = i + order - 1;
        final List<ConcreteRule<TK,FV>> rules = ruleGrid.get(i, j);
        if (rules.size() == 0) continue;
        double sumScore = 0.0;
        for (ConcreteRule<TK,FV> rule : rules) {
          sumScore += Math.exp(rule.isolationScore);
        }
        coverage.add(new RuleCoverage(i, j, rules.size(), sumScore));
      }
      // Sort by posterior probability
      Collections.sort(coverage);

      // Adapt rules in bottom quartile
      final int max = (int) Math.ceil(ADAPTATION_THRESHOLD * coverage.size());
      for (int i = 0; i < max; ++i) {
        RuleCoverage ruleCoverage = coverage.get(i);
        List<ConcreteRule<TK,FV>> rules = ruleGrid.get(ruleCoverage.i, ruleCoverage.j);
        adaptableRules.add(rules.get(0));
      }
    }
  }
  
  private static class RuleCoverage implements Comparable<RuleCoverage> {
    public final int i;
    public final int j;
    public final int numRules;
    public final double sumScore;
    public RuleCoverage(int i, int j, int numRules, double sumScore) {
      this.i = i;
      this.j = j;
      this.numRules = numRules;
      this.sumScore = sumScore;
    }
    @Override
    public int compareTo(RuleCoverage o) {
      // TODO(spenceg) Could compare based on histogram or score?
      return (int) Math.signum(sumScore - o.sumScore);
    }
    @Override
    public String toString() {
      return String.format("%d,%d %d %.2f", i, j, numRules, sumScore);
    }
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
    throw new RuntimeException("Not yet implemented");
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
