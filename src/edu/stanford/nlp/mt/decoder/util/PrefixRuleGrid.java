package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
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
  
  private final List<List<ConcreteRule<TK,FV>>> index;
  private final CoverageSet sourceCoverage;
  private final CoverageSet targetCoverage;
  private final Sequence<TK> prefix;
  private final Sequence<TK> source;

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
    // Make table
    Map<TK,List<Integer>> indexTable = new HashMap<>();
    for (int i = 0, limit = prefix.size(); i < limit; ++i) {
      TK token = prefix.get(i);
      if ( ! indexTable.containsKey(token)) {
        indexTable.put(token, new ArrayList<>());
      }
      indexTable.get(token).add(i);
    }
    
    // Filter the rules
    int numRules = 0;
    List<List<ConcreteRule<TK,FV>>> ruleIndex = IntStream.range(0, prefix.size())
        .mapToObj(i -> new ArrayList<ConcreteRule<TK,FV>>()).collect(Collectors.toList());
    for (ConcreteRule<TK,FV> rule : ruleList) {
      List<Integer> matches = findAll(indexTable, rule.abstractRule.target);
      if (matches.size() > 0) {
        sourceCoverage.or(rule.sourceCoverage);
        for (int i : matches) {
          targetCoverage.set(i, i + rule.abstractRule.target.size());
          ruleIndex.get(i).add(rule);
          ++numRules;
        }
      }
    }
    logger.info("# prefix rules: {}/{}", numRules, ruleList.size());
    return ruleIndex;
  }

  private List<Integer> findAll(Map<TK, List<Integer>> indexTable, Sequence<TK> target) {
    return indexTable.getOrDefault(target.get(0), Collections.emptyList()).stream().filter(pIdx -> {
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
  public void augmentGrid(DynamicTranslationModel<FV> backgroundModel, 
      DynamicTranslationModel<FV> foregroundModel, Scorer<FV> scorer,
      FeatureExtractor<TK,FV> featurizer, InputProperties inputProperties, 
      int sourceInputId) {
    final String[] featureNames = (String[]) backgroundModel.getFeatureNames().toArray();

    // Augment with synthetic singleton rules
    for (int i = 0, pSz = prefix.size(); 
        i >= 0 && i < pSz; 
        //i = targetCoverage.nextClearBit(i+1)) {
        ++i) {

      IString targetQuery = (IString) prefix.get(i);
      int tgtIdBackground = backgroundModel.getTMVocabularyId(targetQuery);
      int tgtIdForeground = foregroundModel == null ? -1 : foregroundModel.getTMVocabularyId(targetQuery);
      final int cnt_e = backgroundModel.coocTable.getTgtMarginal(tgtIdBackground)
          + (foregroundModel == null ? 0 : foregroundModel.coocTable.getTgtMarginal(tgtIdForeground));
      boolean isTargetOOV = cnt_e == 0;
      final Sequence<IString> targetSpan = (Sequence<IString>) prefix.subsequence(i, i+1);

      for (int j = 0, sSz = source.size(); j < sSz; ++j) {
        IString sourceQuery = (IString) source.get(j);
        int srcIdBack = backgroundModel.getTMVocabularyId(sourceQuery);
        int srcIdFore = foregroundModel == null ? -1 : foregroundModel.getTMVocabularyId(sourceQuery);
        int cnt_f = backgroundModel.coocTable.getSrcMarginal(srcIdBack) +
            (foregroundModel == null ? 0 : foregroundModel.coocTable.getSrcMarginal(srcIdFore));
        final Sequence<IString> sourceSpan = (Sequence<IString>) source.subsequence(j,j+1);
        double cnt_ef = backgroundModel.coocTable.getJointCount(srcIdBack, tgtIdBackground)
            + (foregroundModel == null ? 0 : foregroundModel.coocTable.getJointCount(srcIdFore, tgtIdForeground));

        int cntE = isTargetOOV ? 1 : cnt_e;
        if (cnt_f == 0) cnt_f = 1;
        if (cnt_ef == 0.0) cnt_ef = 1e-4;
        CoverageSet sourceSpanCoverage = new CoverageSet(source.size());
        sourceSpanCoverage.set(j);
        ConcreteRule<TK,FV> syntheticRule = (ConcreteRule<TK, FV>) SyntheticRules.makeSyntheticRule(sourceSpan, 
            targetSpan, sourceSpanCoverage, featureNames, (Scorer<String>) scorer, 
            (FeatureExtractor<IString,String>) featurizer, cnt_ef, cntE, cnt_f, inputProperties, 
            (Sequence<IString>) source, sourceInputId, targetCoverage.get(i));
        index.get(i).add(syntheticRule);
        
        // WSGDEBUG
//        System.err.printf("SYNTH: %s e %d f %d ef %f%n", syntheticRule.toString(), cntE, cnt_f, cnt_ef);
      }
    }
    
    // Sort the final list of rules
    index.stream().forEach(l -> Collections.sort(l));
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
