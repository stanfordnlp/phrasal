package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.AbstractInferer;
import edu.stanford.nlp.mt.stats.SimilarityMeasures;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;

/**
 * Constrained output space for prefix decoding.
 * 
 * 
 * @author Spence Green
 *
 * @param <IString>
 * @param <String>
 */
public class PrefixOutputSpace implements OutputSpace<IString, String> {

  private static final Logger logger = LogManager.getLogger(PrefixOutputSpace.class.getName());

  // Hyperparameters
  private static final int MAX_ORDER = 2;
  private static final int ALPHA = 3;
  private static final float SOURCE_DELETE_PERC_BITEXT = 0.2f;
  private static final double TARGET_SIM_THRESHOLD = 0.8;
  
  private Sequence<IString> sourceSequence;
  private final Sequence<IString> allowablePrefix;
  private final int allowablePrefixLength;
  private final int sourceInputId;

  /**
   * Constructor.
   * 
   * @param sourceSequence
   * @param allowablePrefix
   * @param sourceInputId
   */
  public PrefixOutputSpace(Sequence<IString> allowablePrefix, int sourceInputId) {
    this.allowablePrefix = allowablePrefix;
    this.allowablePrefixLength = allowablePrefix.size();
    this.sourceInputId = sourceInputId;
  }


  @Override
  public void setSourceSequence(Sequence<IString> sourceSequence) {
    this.sourceSequence = sourceSequence;
  }

  @Override
  public void filter(List<ConcreteRule<IString, String>> ruleList, AbstractInferer<IString, String> inferer) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void filter(List<ConcreteRule<IString, String>> ruleList, AbstractInferer<IString, String> inferer, 
      InputProperties inputProperties) {
    // Add source deletion rules
    if ( ! (inferer.phraseGenerator instanceof DynamicTranslationModel)) {
      logger.warn("Prefix decoding requires DynamicTranslationModel");
      return;
    }

    // Collect prefix n-grams
    final Map<Sequence<IString>,List<RuleSpan>> prefixToSpan = new HashMap<>();
    final Map<RuleSpan,List<ConcreteRule<IString,String>>> spanToPrefix = new HashMap<>();
    for (int i = 0, psz = allowablePrefix.size(); i < psz; ++i) {
      for (int j = i+1, max = Math.min(psz, i+MAX_ORDER); j <= max; ++j) {
        Sequence<IString> tgt = allowablePrefix.subsequence(i, j);
        RuleSpan span = new RuleSpan(i, j);
        prefixToSpan.computeIfAbsent(tgt, k -> new ArrayList<>()).add(span);
        spanToPrefix.put(span, new ArrayList<>());
      }
    }
    
    // Collect gross statistics about the whole phrase query
    final CoverageSet targetCoverage = new CoverageSet();
    final CoverageSet sourceCoverage = new CoverageSet();
    final Map<RuleSpan,List<ConcreteRule<IString,String>>> spanToSource = new HashMap<>();
    final Map<Sequence<IString>, List<ConcreteRule<IString,String>>> unigramTargetRules = 
        new HashMap<>(sourceSequence.size());
    for (ConcreteRule<IString,String> rule : ruleList) {
      // Check for OOV
      if ( ! rule.abstractRule.phraseTableName.equals(UnknownWordPhraseGenerator.PHRASE_TABLE_NAME))
        sourceCoverage.or(rule.sourceCoverage);
      RuleSpan sourceSpan = new RuleSpan(rule.sourcePosition, rule.sourcePosition + rule.sourceCoverage.cardinality());
      spanToSource.computeIfAbsent(sourceSpan, k -> new ArrayList<>()).add(rule);
      List<RuleSpan> prefixSpans = prefixToSpan.getOrDefault(rule.abstractRule.target, Collections.emptyList());
      if (rule.abstractRule.target.size() == 1) 
        unigramTargetRules.computeIfAbsent(rule.abstractRule.target, k -> new ArrayList<>()).add(rule);
      for (RuleSpan prefixSpan : prefixSpans) {
        spanToPrefix.get(prefixSpan).add(rule);
        targetCoverage.set(prefixSpan.i, prefixSpan.j);
      }
    }
    
    List<DynamicTranslationModel<String>> tmList = new ArrayList<>(2);
    tmList.add((DynamicTranslationModel<String>) inferer.phraseGenerator);
    if (inputProperties.containsKey(InputProperty.ForegroundTM)) {
      tmList.add((DynamicTranslationModel<String>) inputProperties.get(InputProperty.ForegroundTM));
    }
    final String[] featureNames = (String[]) inferer.phraseGenerator.getFeatureNames().toArray();

    
    // TODO(spenceg): Should be for content words only.
    // Add source deletion rules
    final int[] cnt_f = new int[sourceSequence.size()];
    final int minCutoff = (int) (tmList.get(0).bitextSize() * SOURCE_DELETE_PERC_BITEXT);
    for (int i = 0, sz = sourceSequence.size(); i < sz; ++i) {
      IString sourceQuery = sourceSequence.get(i);
      cnt_f[i] = tmList.stream().mapToInt(tm -> tm.getSourceLexCount(sourceQuery)).sum();
      if (cnt_f[i] <= minCutoff) continue;
      
      final Sequence<IString> source = sourceSequence.subsequence(i,i+1);
      CoverageSet sourceSpanCoverage = new CoverageSet(sourceSequence.size());
      sourceSpanCoverage.set(i);
      int cnt_joint = tmList.stream().mapToInt(tm -> tm.getSourceUnalignedCount(sourceQuery)).sum();
      if (cnt_joint == 0) continue; // Never been deleted in the corpus
      int numNull = tmList.stream().mapToInt(tm -> tm.bitextSize()).sum();
      ConcreteRule<IString,String> syntheticRule = SyntheticRules.makeSyntheticRule(source, 
          Sequences.emptySequence(), sourceSpanCoverage, featureNames, inferer.scorer, inferer.featurizer, 
          cnt_joint, numNull, cnt_f[i], inputProperties, sourceSequence, sourceInputId);
      ruleList.add(syntheticRule);
      
      // WSGDEBUG
//      System.err.printf("P0 %s%n", syntheticRule);
    }
    
    // Try to cover target OOVs by querying the TM
    for (int i = targetCoverage.nextClearBit(0), pSz = allowablePrefix.size(); 
        i >= 0 && i < pSz; 
        i = targetCoverage.nextClearBit(i+1)) {

      final IString targetQuery = (IString) allowablePrefix.get(i);
      int cnt_e = tmList.stream().mapToInt(tm -> tm.getTargetLexCount(targetQuery)).sum();
      final Sequence<IString> targetSpan = (Sequence<IString>) allowablePrefix.subsequence(i, i+1);
      
      // Step 1. Missed due to sampling? Check TM.
      boolean addedRule = false;
      for (int j = 0, sSz = sourceSequence.size(); j < sSz; ++j) {
        IString sourceQuery = (IString) sourceSequence.get(j);
        double cnt_ef = tmList.stream().mapToInt(tm -> tm.getJointLexCount(sourceQuery, targetQuery)).sum();
        boolean isSourceOOV = ! sourceCoverage.get(j);
        if (isSourceOOV) {
          cnt_ef = 1e-4;
          if (cnt_e == 0) cnt_e = 1;
          if (cnt_f[j] == 0) cnt_f[j] = 1;
        }
        else if (cnt_ef == 0) continue;
        
        final Sequence<IString> sourceSpan = (Sequence<IString>) sourceSequence.subsequence(j,j+1);
        CoverageSet sourceSpanCoverage = new CoverageSet(sourceSequence.size());
        sourceSpanCoverage.set(j);
        ConcreteRule<IString,String> syntheticRule = (ConcreteRule<IString, String>) SyntheticRules.makeSyntheticRule(sourceSpan, 
            targetSpan, sourceSpanCoverage, featureNames, inferer.scorer, 
            inferer.featurizer, cnt_ef, cnt_e, cnt_f[j], inputProperties, 
            sourceSequence, sourceInputId);
        
        // WSGDEBUG
//        if (isSourceOOV) System.err.printf("P1 (OOV): %s%n", syntheticRule); 
//        else System.err.printf("P1: %s%n", syntheticRule);
        
        ruleList.add(syntheticRule);
        addedRule = true;
        
        // Book-keeping
        targetCoverage.set(i);
        RuleSpan srcSpan = new RuleSpan(syntheticRule.sourcePosition, syntheticRule.sourcePosition 
            + syntheticRule.sourceCoverage.cardinality());
        spanToSource.computeIfAbsent(srcSpan, k -> new ArrayList<>()).add(syntheticRule);
        List<RuleSpan> targetSpans = prefixToSpan.getOrDefault(syntheticRule.abstractRule.target, Collections.emptyList());
        for (RuleSpan tgtSpan : targetSpans) {
          spanToPrefix.get(tgtSpan).add(syntheticRule);
        }
      }
      
      // Step 2. Typo or morphological variant? 
      if (!addedRule) {
        String targetToken = targetQuery.toString();
        for (Sequence<IString> target : unigramTargetRules.keySet()) {
          if (SimilarityMeasures.jaccard(targetToken, target.toString()) > TARGET_SIM_THRESHOLD) {
            List<ConcreteRule<IString,String>> targetRules = unigramTargetRules.getOrDefault(target, Collections.emptyList());
            for (ConcreteRule<IString,String> r : targetRules) {
              ConcreteRule<IString,String> syntheticRule = (ConcreteRule<IString, String>) 
                  SyntheticRules.makeSyntheticRule(r, targetSpan, inferer.scorer, 
                  inferer.featurizer, sourceSequence, inputProperties, sourceInputId);
              ruleList.add(syntheticRule);
              addedRule = true;
              targetCoverage.set(i);
              
              // WSGDEBUG
//              System.err.printf("P2: %s%n", syntheticRule);
            }
          }
        }
      }
      
      // Step 3. Target insertion
      if (!addedRule) {
        List<Sequence<IString>> neighbors = new ArrayList<>(2);
        List<Sequence<IString>> bigrams = new ArrayList<>(2);
        if (i > 0) {
          Sequence<IString> neighbor = allowablePrefix.subsequence(i-1, i);
          neighbors.add(neighbor);
          Sequence<IString> bigram = allowablePrefix.subsequence(i-1, i+1);
          bigrams.add(bigram);
        }
        if (i < allowablePrefix.size() - 1) {
          Sequence<IString> neighbor = allowablePrefix.subsequence(i+1, i+2);
          neighbors.add(neighbor);
          Sequence<IString> bigram = allowablePrefix.subsequence(i, i+2);
          bigrams.add(bigram);
        }
        for (int k = 0; k < neighbors.size(); ++k) {
          Sequence<IString> neighbor = neighbors.get(k);
          Sequence<IString> bigram = bigrams.get(k);
          List<ConcreteRule<IString,String>> targetRules = unigramTargetRules.getOrDefault(neighbor, Collections.emptyList());
          for (ConcreteRule<IString,String> r : targetRules) {
            ConcreteRule<IString,String> syntheticRule = (ConcreteRule<IString, String>) 
                SyntheticRules.makeSyntheticRule(r, bigram, inferer.scorer, 
                    inferer.featurizer, sourceSequence, inputProperties, sourceInputId);
            ruleList.add(syntheticRule);
            addedRule = true;
            targetCoverage.set(i);

            // WSGDEBUG
//            System.err.printf("P3: %s%n", syntheticRule);
          }
        }
      }
    }
    
    if (targetCoverage.cardinality() != allowablePrefix.size()) {
      logger.warn("Incomplete coverage for prefix {} {}", targetCoverage, allowablePrefix);
    }
    
    // TODO(spenceg) Implement rule doctoring. Leave it here.
    
    // Add new phrasal rules
//    List<RuleSpan> rareSourceSpans = spanToSource.keySet().stream()
//        .sorted((x,y) ->  spanToSource.get(x).size() - spanToSource.get(y).size())
//        .filter(x -> spanToSource.get(x).size() <= ALPHA && x.size() <= MAX_ORDER)
//        .collect(Collectors.toList());
//
//    // Includes empty spans
//    List<RuleSpan> rareTargetSpans = spanToPrefix.keySet().stream()
//        .sorted((x,y) ->  spanToPrefix.get(x).size() - spanToPrefix.get(y).size())
//        .filter(x -> (spanToPrefix.get(x).size() > 0 && spanToPrefix.get(x).size() <= ALPHA) || (x.size() == 1 && spanToPrefix.get(x).size() == 0))
//        .collect(Collectors.toList());
//
//    // Doctor phrasal rules
//    for(RuleSpan tgtSpan : rareTargetSpans) {
//      List<ConcreteRule<IString,String>> tgtRules = spanToPrefix.get(tgtSpan);
//      
//      // WSGDEBUG
////      if (tgtRules.size() == 0) System.err.printf("%s**: %s%n", tgtSpan, allowablePrefix.get(tgtSpan.i));
////      else System.err.printf("%s: %s%n", tgtSpan, tgtRules);
//    }
////    System.err.println("===============");
//    for (RuleSpan sourceSpan : rareSourceSpans) {
//      List<ConcreteRule<IString,String>> srcRules = spanToSource.get(sourceSpan);
//      
//      // WSGDEBUG
////      System.err.printf("  %s%n", srcRules);
//      // TODO(spenceg) Compute feature values.      
//    }
        
    // WSGDEBUG
//    System.err.println("################");
  }

  @Override
  public boolean allowableContinuation(Featurizable<IString, String> featurizable,
      ConcreteRule<IString, String> rule) {
    final Sequence<IString> prefix = featurizable == null ? null : featurizable.targetSequence;
    return exactMatch(prefix, rule.abstractRule.target);
  }

  private boolean exactMatch(Sequence<IString> prefix, Sequence<IString> rule) {
    if (prefix == null) {
      return allowablePrefix.size() > rule.size() ? allowablePrefix.startsWith(rule) :
        rule.startsWith(allowablePrefix);

    } else {
      int prefixLength = prefix.size();
      int upperBound = Math.min(prefixLength + rule.size(), allowablePrefixLength);
      for (int i = 0; i < upperBound; i++) {
        IString next = i >= prefixLength ? rule.get(i-prefixLength) : prefix.get(i);
        if ( ! allowablePrefix.get(i).equals(next)) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public boolean allowableFinal(Featurizable<IString, String> featurizable) {
    // Allow everything except for the NULL hypothesis
    return featurizable != null;
  }

  @Override
  public List<Sequence<IString>> getAllowableSequences() {
    return Collections.singletonList(allowablePrefix);
  }

  @Override 
  public int getPrefixLength() {
    return allowablePrefixLength;
  }
  
//  private static class UnigramForm<TK> {
//    public final TK source;
//    public final TK target;
//    public UnigramForm(TK source, TK target) {
//      this.source = source;
//      this.target = target;
//    }
//    @Override
//    public int hashCode() {
//      return source.hashCode() ^ target.hashCode();
//    }
//    @Override
//    public boolean equals(Object o) {
//      if (this == o) return true;
//      else if (! (o instanceof UnigramForm)) return false;
//      else {
//        UnigramForm<TK> other = (UnigramForm<TK>) o;
//        return source.equals(other.source) && target.equals(other.target);
//      }
//    }
//  }
  
  private static class RuleSpan {
    public final int i; // inclusive
    public final int j; // exclusive
    public RuleSpan(int i, int j) {
      this.i = i;
      this.j = j;
    }
    public int size() { return j - i; }
    @Override
    public int hashCode() {
      return (i << 16) ^ (j*0xc2b2ae35);
    }
    @Override
    public String toString() {
      return String.format("%d %d", i, j);
    }
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      else if ( ! (o instanceof RuleSpan)) return false;
      else {
        RuleSpan other = (RuleSpan) o;
        return i == other.i && j == other.j;
      }
    }
  }
}
