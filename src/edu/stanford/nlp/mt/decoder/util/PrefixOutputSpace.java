package edu.stanford.nlp.mt.decoder.util;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.AbstractInferer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.InputProperties;

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
    if ( ! (inferer.phraseGenerator instanceof DynamicTranslationModel)) return;
    DynamicTranslationModel<String> tm = (DynamicTranslationModel<String>) inferer.phraseGenerator;
    final String[] featureNames = (String[]) inferer.phraseGenerator.getFeatureNames().toArray();
    for (int i = 0, sz = sourceSequence.size(); i < sz; ++i) {
      IString sourceQuery = sourceSequence.get(i);
      int cnt_f = tm.getSourceLexCount(sourceQuery);
      final boolean isSourceOOV = cnt_f == 0;
      if (isSourceOOV) continue;
      final Sequence<IString> source = sourceSequence.subsequence(i,i+1);
      CoverageSet sourceCoverage = new CoverageSet(sourceSequence.size());
      sourceCoverage.set(i);
      int cnt_joint = tm.getSourceUnalignedCount(sourceQuery);
      if (cnt_joint == 0) continue;
      ConcreteRule<IString,String> syntheticRule = SyntheticRules.makeSyntheticRule(source, Sequences.emptySequence(), 
          sourceCoverage, featureNames, inferer.scorer, inferer.featurizer, 
          cnt_joint, tm.bitextSize(), cnt_f, inputProperties, sourceSequence, sourceInputId);
//      System.err.printf("SDL %s%n", syntheticRule);
      ruleList.add(syntheticRule);
    }
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
}
