package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.Generics;

/**
 * Constrained output space for prefix decoding. Uses the phrase table
 * to allow alternate translations for prefixes.
 * 
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class SoftPrefixOutputSpace<TK,FV> implements OutputSpace<TK, FV> {

  // Hyperparameters
  private static final int MAX_OPTIONS_PER_TOKEN = 3;
  
  private final Sequence<TK> sourceSequence;
  private final int sourceLength;
  private final Sequence<TK> allowablePrefix;
  private final int allowablePrefixLength;
  
  private final List<Set<TK>> sourceOptions;
  private final int sourceInputId;
  
  public SoftPrefixOutputSpace(Sequence<TK> sourceSequence, Sequence<TK> allowablePrefix, int sourceInputId) {
    this.sourceSequence = sourceSequence;
    this.sourceLength = sourceSequence.size();
    this.allowablePrefix = allowablePrefix;
    this.allowablePrefixLength = allowablePrefix.size();
    this.sourceInputId = sourceInputId;
    
    sourceOptions = Generics.newArrayList(sourceLength);
    for (int i = 0; i < sourceLength; ++i) {
      sourceOptions.add(new HashSet<TK>(MAX_OPTIONS_PER_TOKEN));
    }
  }

  @Override
  public List<ConcreteRule<TK, FV>> filter(List<ConcreteRule<TK, FV>> ruleList) {
    List<List<ConcreteRule<TK,FV>>> sortedUnigramRules = 
        new ArrayList<List<ConcreteRule<TK,FV>>>(sourceLength);
    for (int i = 0; i < sourceLength; ++i) {
      sortedUnigramRules.add(new LinkedList<ConcreteRule<TK,FV>>());
    }
    
    // Extract statistics from the rule list
    Set<TK> targetVocabulary = Generics.newHashSet(sourceLength * 10);
    double minScore = Double.POSITIVE_INFINITY;
    List<FeatureValue<FV>> cachedFeatureList = null;
    for (ConcreteRule<TK,FV> rule : ruleList) {
//      Sequence<TK> source = rule.abstractRule.source;
      Sequence<TK> target = rule.abstractRule.target;
//      if (source.size() == 1 && target.size() == 1) {
//        int sourceIndex = rule.sourcePosition;
//        sortedUnigramRules.get(sourceIndex).add(rule);
//      }
      for (TK token : target) {
        targetVocabulary.add(token);
      }
      if (rule.isolationScore < minScore) {
        minScore = rule.isolationScore;
        cachedFeatureList = rule.cachedFeatureList;
      }
    }

    // Iterate over target prefix
    // Insert synthetic rules for OOVs in target prefix
    Set<Sequence<TK>> targetOOVs = Generics.newHashSet();
    for (int i = 0; i < allowablePrefixLength; ++i) {
      if ( ! targetVocabulary.contains(allowablePrefix.get(i))) {
        targetOOVs.add(allowablePrefix.subsequence(i,i+1));
      }
    }
    
    // Iterate over source
    for (int i = 0; i < sourceLength; ++i) {
      // Insert synthetic target rules
      for (Sequence<TK> targetOOV : targetOOVs) {
        ConcreteRule<TK,FV> syntheticRule = makeSyntheticRule(sourceSequence.subsequence(i,i+1), targetOOV, 
            i, minScore - 1e-2, cachedFeatureList, ruleList.get(0));
        ruleList.add(syntheticRule);
      }
    }
    return ruleList;
  }

  private ConcreteRule<TK, FV> makeSyntheticRule(Sequence<TK> source, Sequence<TK> target, int sourceIndex, 
      double ruleScore, List<FeatureValue<FV>> cachedFeatureList, ConcreteRule<TK, FV> rulePrototype) {
    String[] phraseScoreNames = rulePrototype.abstractRule.phraseScoreNames;
    float[] scores = new float[phraseScoreNames.length];
    Arrays.fill(scores, -99.0f);
    Rule<TK> abstractRule = new Rule<TK>(scores, phraseScoreNames,
        new RawSequence<TK>(target), new RawSequence<TK>(source),
        PhraseAlignment.getPhraseAlignment("(0)"));

    CoverageSet sourceCoverage = new CoverageSet();
    sourceCoverage.set(sourceIndex);
    ConcreteRule<TK,FV> rule = new ConcreteRule<TK,FV>(abstractRule,
        sourceCoverage, null, null, sourceSequence, 
        rulePrototype.phraseTableName, sourceInputId);
    rule.isolationScore = ruleScore;
    rule.cachedFeatureList = Generics.newLinkedList(cachedFeatureList);
    return rule;
  }

  @Override
  public boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteRule<TK, FV> rule) {
    final Sequence<TK> prefix = featurizable == null ? null : featurizable.targetPrefix;
    return exactMatch(prefix, rule.abstractRule.target);
  }

  private boolean exactMatch(Sequence<TK> prefix, Sequence<TK> rule) {
    if (prefix == null) {
      return allowablePrefix.startsWith(rule);

    } else {
      int prefixLength = prefix.size();
      int upperBound = Math.min(prefixLength + rule.size(), allowablePrefixLength);
      for (int i = 0; i < upperBound; i++) {
        TK next = i >= prefixLength ? rule.get(i-prefixLength) : prefix.get(i);
        if ( ! allowablePrefix.get(i).equals(next)) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public boolean allowableFinal(Featurizable<TK, FV> featurizable) {
    // Allow everything except for the NULL hypothesis
    return featurizable != null;
  }

  @Override
  public List<Sequence<TK>> getAllowableSequences() {
    // null has the semantics of the full (unconstrained) target output space.
    // This is what we want for prefix decoding because we don't pruning to happen
    // at the point of the phrase table query.
    return null;
  }
}
