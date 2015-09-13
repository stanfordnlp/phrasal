package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
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
  
  private static final double SIM_THRESHOLD = 0.75;
  
  private final List<List<ConcreteRule<TK,FV>>> index;
  private final CoverageSet sourceCoverage;
  private final CoverageSet targetCoverage;
  private final Sequence<TK> prefix;
  private final Sequence<TK> source;

  // WSGDEBUG
  private Map<Sequence<TK>, List<ConcreteRule<TK,FV>>> tgtToRule;
  
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
    this.sourceCoverage = new CoverageSet();
    this.targetCoverage = new CoverageSet();
    this.index = sortRules(ruleList);
  }

  /**
   * Organize the list of rules by target.
   * 
   * @return 
   */
  private List<List<ConcreteRule<TK, FV>>> sortRules(List<ConcreteRule<TK, FV>> ruleList) {
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
    
    // WSGDEBUG
    this.tgtToRule = new HashMap<>();
    
    // Filter the rules
    int numRules = 0, auxRules = 0;
    List<List<ConcreteRule<TK,FV>>> ruleIndex = new ArrayList<>(prefix.size());
    for (int i = 0, sz = prefix.size(); i < sz; ++i) ruleIndex.add(new ArrayList<>());
    for (ConcreteRule<TK,FV> rule : ruleList) {
      List<Integer> matches = findAll(wordToPosition, rule.abstractRule.target);
      if (matches.size() > 0) {
        sourceCoverage.or(rule.sourceCoverage);
        for (int i : matches) {
          targetCoverage.set(i, i + rule.abstractRule.target.size());
          ruleIndex.get(i).add(rule);
          ++numRules;
        }
      } else {
        if (rule.abstractRule.target.size() == 1) {
          List<ConcreteRule<TK,FV>> tgtRules = tgtToRule.get(rule.abstractRule.target);
          if (tgtRules == null) {
            tgtRules = new LinkedList<>();
            tgtToRule.put(rule.abstractRule.target, tgtRules);
          }
          tgtRules.add(rule);
          ++auxRules;
        }
      }
    }
    logger.info("# prefix rules: {}/{}  # aux rules: {}/{} ", numRules, ruleList.size(), auxRules, ruleList.size());
    return ruleIndex;
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
      FeatureExtractor<TK,FV> featurizer, InputProperties inputProperties, 
      int sourceInputId) {

    // Augment with synthetic singleton rules
    for (int i = targetCoverage.nextClearBit(0), pSz = prefix.size(); 
        i >= 0 && i < pSz; 
        i = targetCoverage.nextClearBit(i+1)) {
        // ++i) {

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
        final Sequence<IString> sourceSpan = (Sequence<IString>) source.subsequence(j,j+1);
        CoverageSet sourceSpanCoverage = new CoverageSet(source.size());
        sourceSpanCoverage.set(j);
        ConcreteRule<TK,FV> syntheticRule = (ConcreteRule<TK, FV>) SyntheticRules.makeSyntheticRule(sourceSpan, 
            targetSpan, sourceSpanCoverage, featureNames, (Scorer<String>) scorer, 
            (FeatureExtractor<IString,String>) featurizer, cnt_ef, cnt_e, cnt_f, inputProperties, 
            (Sequence<IString>) source, sourceInputId, targetCoverage.get(i));
        index.get(i).add(syntheticRule);
        addedRule = true;
      }
      
      // Next highest precision.
      // Either a new word type, or a word type that hasn't been seen with anything in the source.
      // See if this word type is similar to anything in the query, e.g., maybe this is a mis-spelling.
      if (!addedRule) {
        final String queryStr = targetSpan.toString();
        for (Sequence<TK> tgt : tgtToRule.keySet()) {
          String candidateStr = tgt.toString();
          double score = SimilarityMeasures.jaccard(queryStr, candidateStr);
          if (score > SIM_THRESHOLD) {
            List<ConcreteRule<TK,FV>> ruleList = tgtToRule.get(tgt);
            for (ConcreteRule<TK,FV> rule : ruleList) {
              rule.abstractRule.target = (Sequence<TK>) targetSpan;
              index.get(i).add(rule);
              addedRule = true;
            }
          }
        }
      }
      
      // Lowest precision. Revert to target OOV model.
      if (!addedRule) {
        
      }
      
      
      if (!addedRule) logger.warn("Could not create rule for token '{}' (position {})", targetQuery, i);
    }
    
    // Sort the final list of rules
    for (List<ConcreteRule<TK,FV>> l : index) Collections.sort(l);
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
    return index.get(startPos);
  }
}
