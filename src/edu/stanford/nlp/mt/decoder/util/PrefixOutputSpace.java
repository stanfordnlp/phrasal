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
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
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

  private static final int MAX_ORDER = 2;
  private static final int ALPHA = 3;
  private static final float SOURCE_DELETE_PERC_BITEXT = 0.2f;
  
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

  @Override
  public void filter(List<ConcreteRule<IString, String>> ruleList, AbstractInferer<IString, String> inferer, 
      InputProperties inputProperties) {
    // Add source deletion rules
    if ( ! (inferer.phraseGenerator instanceof DynamicTranslationModel)) {
      logger.warn("Prefix decoding requires DynamicTranslationModel");
      return;
    }

    // Collect target n-grams
    final Map<Sequence<IString>,List<RuleSpan>> tgtToSpan = new HashMap<>();
    final Map<RuleSpan,List<ConcreteRule<IString,String>>> spanToTarget = new HashMap<>();
    for (int i = 0, psz = allowablePrefix.size(); i < psz; ++i) {
      for (int j = i+1, max = Math.min(psz, i+MAX_ORDER); j <= max; ++j) {
        Sequence<IString> tgt = allowablePrefix.subsequence(i, j);
        RuleSpan span = new RuleSpan(i, j);
        tgtToSpan.computeIfAbsent(tgt, k -> new ArrayList<>()).add(span);
        spanToTarget.put(span, new ArrayList<>());
      }
    }
    
    // Collect gross statistics about the phrase query
    final CoverageSet targetCoverage = new CoverageSet();
    final CoverageSet sourceCoverage = new CoverageSet();
//    Counter<UnigramForm<IString>> surfaceUnigramForms = new ClassicCounter<>(ruleList.size());
    final Map<RuleSpan,List<ConcreteRule<IString,String>>> spanToSource = new HashMap<>();
    for (ConcreteRule<IString,String> rule : ruleList) {
      // Check for OOV
      if ( ! rule.abstractRule.phraseTableName.equals(UnknownWordPhraseGenerator.PHRASE_TABLE_NAME))
        sourceCoverage.or(rule.sourceCoverage);
      
//      if (rule.abstractRule.source.size() == 1 && rule.abstractRule.target.size() == 1) {
//        surfaceUnigramForms.incrementCount(new UnigramForm<IString>(rule.abstractRule.source.get(0), 
//            rule.abstractRule.target.get(0)));
//      }
      RuleSpan sourceSpan = new RuleSpan(rule.sourcePosition, rule.sourcePosition + rule.sourceCoverage.cardinality());
      spanToSource.computeIfAbsent(sourceSpan, k -> new ArrayList<>()).add(rule);
      List<RuleSpan> targetSpans = tgtToSpan.getOrDefault(rule.abstractRule.target, Collections.emptyList());
      for (RuleSpan targetSpan : targetSpans) {
        spanToTarget.get(targetSpan).add(rule);
        targetCoverage.set(targetSpan.i, targetSpan.j);
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
      sourceCoverage.set(i);
      int cnt_joint = tmList.stream().mapToInt(tm -> tm.getSourceUnalignedCount(sourceQuery)).sum();
      if (cnt_joint == 0) continue; // Never been deleted in the corpus
      int numNull = tmList.stream().mapToInt(tm -> tm.bitextSize()).sum();
      ConcreteRule<IString,String> syntheticRule = SyntheticRules.makeSyntheticRule(source, 
          Sequences.emptySequence(), sourceSpanCoverage, featureNames, inferer.scorer, inferer.featurizer, 
          cnt_joint, numNull, cnt_f[i], inputProperties, sourceSequence, sourceInputId);
      
      // WSGDEBUG
      System.err.printf("SDL %s%n", syntheticRule);
      ruleList.add(syntheticRule);
    }
    
    // Try to cover target OOVs by querying the TM
    for (int i = targetCoverage.nextClearBit(0), pSz = allowablePrefix.size(); 
        i >= 0 && i < pSz; 
        i = targetCoverage.nextClearBit(i+1)) {
//         ++i) {

      final IString targetQuery = (IString) allowablePrefix.get(i);
      int cnt_e = tmList.stream().mapToInt(tm -> tm.getTargetLexCount(targetQuery)).sum();
      final Sequence<IString> targetSpan = (Sequence<IString>) allowablePrefix.subsequence(i, i+1);
      
      // Maybe missed due to sampling in the TM. Check the TM.
      boolean addedRule = false;
      for (int j = 0, sSz = sourceSequence.size(); j < sSz; ++j) {
        IString sourceQuery = (IString) sourceSequence.get(j);
//        if (surfaceUnigramForms.getCount(new UnigramForm<IString>(sourceQuery, targetQuery)) > 0.0) continue;
        double cnt_ef = tmList.stream().mapToInt(tm -> tm.getJointLexCount(sourceQuery, targetQuery)).sum();
        boolean isSourceOOV = ! sourceCoverage.get(j);
        if (isSourceOOV) {
          cnt_ef = 1e-4;
          if (cnt_e == 0) cnt_e = 1;
          if (cnt_f[j] == 0) cnt_f[j] =1;
        }
        else if (cnt_ef == 0) continue;
//        int cnt_f = tmList.stream().mapToInt(tm -> tm.getSourceLexCount(sourceQuery)).sum();
        
        final Sequence<IString> sourceSpan = (Sequence<IString>) sourceSequence.subsequence(j,j+1);
        CoverageSet sourceSpanCoverage = new CoverageSet(sourceSequence.size());
        sourceSpanCoverage.set(j);
        ConcreteRule<IString,String> syntheticRule = (ConcreteRule<IString, String>) SyntheticRules.makeSyntheticRule(sourceSpan, 
            targetSpan, sourceSpanCoverage, featureNames, inferer.scorer, 
            inferer.featurizer, cnt_ef, cnt_e, cnt_f[j], inputProperties, 
            sourceSequence, sourceInputId);
        
        // WSGDEBUG
        if (isSourceOOV) System.err.printf("P1 (OOV): %s%n", syntheticRule); 
        else System.err.printf("P1: %s%n", syntheticRule);
        
        ruleList.add(syntheticRule);
        addedRule = true;
        // Book-keeping
//        surfaceUnigramForms.incrementCount(new UnigramForm<IString>(syntheticRule.abstractRule.source.get(0), 
//            syntheticRule.abstractRule.target.get(0)));
        RuleSpan srcSpan = new RuleSpan(syntheticRule.sourcePosition, syntheticRule.sourcePosition 
            + syntheticRule.sourceCoverage.cardinality());
        spanToSource.computeIfAbsent(srcSpan, k -> new ArrayList<>()).add(syntheticRule);
        List<RuleSpan> targetSpans = tgtToSpan.getOrDefault(syntheticRule.abstractRule.target, Collections.emptyList());
        for (RuleSpan tgtSpan : targetSpans) {
          spanToTarget.get(tgtSpan).add(syntheticRule);
        }
      }
      
      // Cooc table lookup failed. 
      // TODO(spenceg) Try to treat this as a target insertion?
      if (!addedRule) {
        
      }
    }
    
    // Add new phrasal rules
    List<RuleSpan> rareSourceSpans = spanToSource.keySet().stream()
        .sorted((x,y) ->  spanToSource.get(x).size() - spanToSource.get(y).size())
        .filter(x -> spanToSource.get(x).size() <= ALPHA && x.size() <= MAX_ORDER)
        .collect(Collectors.toList());

    // Includes empty spans
    List<RuleSpan> rareTargetSpans = spanToTarget.keySet().stream()
        .sorted((x,y) ->  spanToTarget.get(x).size() - spanToTarget.get(y).size())
        .filter(x -> (spanToTarget.get(x).size() > 0 && spanToTarget.get(x).size() <= ALPHA) || (x.size() == 1 && spanToTarget.get(x).size() == 0))
        .collect(Collectors.toList());

    // Doctor phrasal rules
    for(RuleSpan tgtSpan : rareTargetSpans) {
      List<ConcreteRule<IString,String>> tgtRules = spanToTarget.get(tgtSpan);
      
      // WSGDEBUG
      if (tgtRules.size() == 0) System.err.printf("%s**: %s%n", tgtSpan, allowablePrefix.get(tgtSpan.i));
      else System.err.printf("%s: %s%n", tgtSpan, tgtRules);
    }
    System.err.println("===============");
    for (RuleSpan sourceSpan : rareSourceSpans) {
      List<ConcreteRule<IString,String>> srcRules = spanToSource.get(sourceSpan);
      
      // WSGDEBUG
      System.err.printf("  %s%n", srcRules);
      // TODO(spenceg) Compute feature values.      
    }
        
    // WSGDEBUG
    System.err.println("################");
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
  
  private static class UnigramForm<TK> {
    public final TK source;
    public final TK target;
    public UnigramForm(TK source, TK target) {
      this.source = source;
      this.target = target;
    }
    @Override
    public int hashCode() {
      return source.hashCode() ^ target.hashCode();
    }
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      else if (! (o instanceof UnigramForm)) return false;
      else {
        UnigramForm<TK> other = (UnigramForm<TK>) o;
        return source.equals(other.source) && target.equals(other.target);
      }
    }
  }
  
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
