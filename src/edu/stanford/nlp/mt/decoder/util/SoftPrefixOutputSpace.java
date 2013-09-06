package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
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
 * TODO(spenceg): 
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
  private final int maxPrefixLength;
  
  private final List<Set<TK>> sourceOptions;
  private final int sourceInputId;
  
  public SoftPrefixOutputSpace(Sequence<TK> sourceSequence, Sequence<TK> allowablePrefix, int sourceInputId) {
    this.sourceSequence = sourceSequence;
    this.sourceLength = sourceSequence.size();
    this.allowablePrefix = allowablePrefix;
    this.maxPrefixLength = allowablePrefix.size();
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
    for (ConcreteRule<TK,FV> rule : ruleList) {
      Sequence<TK> source = rule.abstractRule.source;
      Sequence<TK> target = rule.abstractRule.target;
      if (source.size() == 1 && target.size() == 1) {
        int sourceIndex = rule.sourcePosition;
        insert(rule, sourceIndex, sortedUnigramRules);
      }
      for (TK token : target) {
        targetVocabulary.add(token);
      }
      if (rule.isolationScore < minScore) {
        minScore = rule.isolationScore;
      }
    }

    // Iterate over target prefix
    // Insert synthetic rules for OOVs in target prefix
    Set<Sequence<TK>> targetOOVs = Generics.newHashSet();
    for (int i = 0; i < maxPrefixLength; ++i) {
      if ( ! targetVocabulary.contains(allowablePrefix.get(i))) {
        targetOOVs.add(allowablePrefix.subsequence(i,i+1));
      }
    }
    
    // Iterate over source
    for (int i = 0; i < sourceLength; ++i) {
      // Extract source/target translation alternatives
      List<ConcreteRule<TK,FV>> topRules = sortedUnigramRules.get(i);
      for (ConcreteRule<TK,FV> rule : topRules) {
        sourceOptions.get(i).add(rule.abstractRule.target.get(0));
      }
      
      // Insert synthetic target rules
      for (Sequence<TK> targetOOV : targetOOVs) {
        ConcreteRule<TK,FV> syntheticRule = makeSyntheticRule(sourceSequence.subsequence(i,i+1), targetOOV, i, minScore - 1e-2, ruleList.get(0));
        ruleList.add(syntheticRule);
      }
    }
    return ruleList;
  }

  private ConcreteRule<TK, FV> makeSyntheticRule(Sequence<TK> source, Sequence<TK> target, int sourceIndex, 
      double ruleScore, ConcreteRule<TK, FV> rulePrototype) {
    String[] phraseScoreNames = rulePrototype.abstractRule.phraseScoreNames;
    float[] scores = new float[phraseScoreNames.length];
    // TODO(spenceg): Should we assign a unique score (via FlatPhraseTable) to synthetic
    // rules? Right now this only affects the LR featurizers. An id of -1 will just force
    // the LR featurizers not to add any feature scores.
    final int id = -1;
    Rule<TK> abstractRule = new Rule<TK>(id, scores, phraseScoreNames,
        new RawSequence<TK>(target), new RawSequence<TK>(source),
        PhraseAlignment.getPhraseAlignment("0-0"));

    CoverageSet sourceCoverage = new CoverageSet();
    sourceCoverage.set(sourceIndex);
    ConcreteRule<TK,FV> rule = new ConcreteRule<TK,FV>(abstractRule,
        sourceCoverage, null, null, sourceSequence, 
        rulePrototype.phraseTableName, sourceInputId);
    rule.isolationScore = ruleScore;
    return rule;
  }

  /**
   * Insertion sort of a rule into a sorted list.
   * 
   * @param rule
   * @param sourceIndex
   * @param sortedUnigramRules
   */
  private void insert(ConcreteRule<TK, FV> rule, int sourceIndex,
      List<List<ConcreteRule<TK, FV>>> sortedUnigramRules) {
    if (sortedUnigramRules == null || sourceIndex > sortedUnigramRules.size()
        || sortedUnigramRules.get(sourceIndex) == null) {
      throw new RuntimeException("Rule array does not match source dimension");
    }
    List<ConcreteRule<TK,FV>> topRules = sortedUnigramRules.get(sourceIndex);
    int numRules = topRules.size();
    for (int i = 0; i < numRules; ++i) {
      ConcreteRule<TK,FV> alternative = topRules.get(i);
      if (rule.isolationScore > alternative.isolationScore || numRules < MAX_OPTIONS_PER_TOKEN) {
        topRules.add(i, rule);
      }
    }
    if (topRules.size() > MAX_OPTIONS_PER_TOKEN) {
      topRules.remove(MAX_OPTIONS_PER_TOKEN);
    }
  }

  @Override
  public boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteRule<TK, FV> rule) {
    final Sequence<TK> prefix = featurizable == null ? null : featurizable.targetPrefix;
    
    // Exact match decoding check
    if ( ! exactMatch(prefix, rule.abstractRule.target)) {
      // TODO(spenceg): Add a fuzzy match cost to increase recall?
      final int commonSpanLength = (int) Math.min(prefix.size(), allowablePrefix.size());
      int i;
      for (i = 0; i < commonSpanLength; ++i) {
        TK allowableToken = allowablePrefix.get(i);
        TK targetToken = prefix.get(i);
        if ( ! allowableToken.equals(targetToken)) {
          int[] sIndices= featurizable.t2sAlignmentIndex[i];
          for (int sIndex : sIndices) {
            if ( ! sourceOptions.get(sIndex).contains(targetToken)) {
              return false;
            }
          }
        }
      } 
    }    
    return true;
  }

  private boolean exactMatch(Sequence<TK> prefix, Sequence<TK> rule) {
    if (prefix == null) {
      if (allowablePrefix.startsWith(rule)) {
        return true;
      }

    } else if (allowablePrefix.startsWith(prefix)) {
      int ruleLength = rule.size();
      int prefixLength = prefix.size();
      for (int i = 0; i < ruleLength; i++) {
        if (!allowablePrefix.get(prefixLength + i).equals(rule.get(i))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean allowableFinal(Featurizable<TK, FV> featurizable) {
    // TODO(spenceg): I think that allowableContinuation is sufficient. Don't
    // need a final check.
    return true;
  }

  @Override
  public List<Sequence<TK>> getAllowableSequences() {
    // null has the semantics of the full (unconstrained) target output space.
    // This is what we want for prefix decoding because we don't pruning to happen
    // at the point of the phrase table query.
    return null;
  }
}
