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
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;

/**
 * Constrained output space for prefix decoding. Uses the phrase table
 * to allow alternate translations for prefixes.
 * 
 * 
 * @author Spence Green
 *
 * @param <IString>
 * @param <String>
 */
public class SoftPrefixOutputSpace implements OutputSpace<IString, String> {

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
  public SoftPrefixOutputSpace(Sequence<IString> allowablePrefix, int sourceInputId) {
    this.allowablePrefix = allowablePrefix;
    this.allowablePrefixLength = allowablePrefix.size();
    this.sourceInputId = sourceInputId;
  }


  @Override
  public void setSourceSequence(Sequence<IString> sourceSequence) {
    this.sourceSequence = sourceSequence;
  }

  @Override
  public void filter(List<ConcreteRule<IString, String>> ruleList, 
      AbstractInferer<IString, String> inferer) {
    filter(ruleList, inferer, null);
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Override
  public void filter(List<ConcreteRule<IString, String>> ruleList, 
      AbstractInferer<IString, String> inferer, InputProperties inputProperties) {
    if (! (inferer.phraseGenerator instanceof DynamicTranslationModel))
      throw new RuntimeException("SoftPrefixOutputSpace only compatible with DynamicTranslationModel");

    // New strategy with the dynamic TM
    DynamicTranslationModel<String> backgroundModel = (DynamicTranslationModel<String>) inferer.phraseGenerator;
    DynamicTranslationModel<String> foregroundModel = inputProperties.containsKey(InputProperty.ForegroundTM) ? 
        (DynamicTranslationModel) inputProperties.get(InputProperty.ForegroundTM) : null;

    final String[] featureNames = (String[]) backgroundModel.getFeatureNames().toArray();

    // Target OOVs, Target insertions, target unigrams
    for (int j = 0, tgtLength = allowablePrefix.size(); j < tgtLength; ++j) {
      IString targetQuery = allowablePrefix.get(j);
      int tgtIdBackground = backgroundModel.getTMVocabularyId(targetQuery);
      int tgtIdForeground = foregroundModel == null ? -1 : foregroundModel.getTMVocabularyId(targetQuery);
      final int cnt_e = backgroundModel.coocTable.getTgtMarginal(tgtIdBackground)
          + (foregroundModel == null ? 0 : foregroundModel.coocTable.getTgtMarginal(tgtIdForeground));
      boolean isTargetOOV = cnt_e == 0;
      final Sequence<IString> target = allowablePrefix.subsequence(j, j+1);

      for (int i = 0, srcLength = sourceSequence.size(); i < srcLength; ++i) {
        IString sourceQuery = sourceSequence.get(i);
        int srcIdBack = backgroundModel.getTMVocabularyId(sourceQuery);
        int srcIdFore = foregroundModel == null ? -1 : foregroundModel.getTMVocabularyId(sourceQuery);
        int cnt_f = backgroundModel.coocTable.getSrcMarginal(srcIdBack) +
            (foregroundModel == null ? 0 : foregroundModel.coocTable.getSrcMarginal(srcIdFore));
        final boolean isSourceOOV = cnt_f == 0;
        final Sequence<IString> source = sourceSequence.subsequence(i,i+1);

        CoverageSet sourceCoverage = new CoverageSet(srcLength);
        sourceCoverage.set(i);
        int cntE = isTargetOOV ? 1 : cnt_e;
        int cntF = isSourceOOV ? 1 : cnt_f;
        int cnt_joint = backgroundModel.coocTable.getJointCount(srcIdBack, tgtIdBackground)
            + (foregroundModel == null ? 0 : foregroundModel.coocTable.getJointCount(srcIdFore, tgtIdForeground));
        if (cnt_joint == 0) cnt_joint = 1;
        ConcreteRule<IString,String> syntheticRule = SyntheticRules.makeSyntheticRule(source, target, 
            sourceCoverage, featureNames, inferer.scorer, inferer.featurizer, 
            cnt_joint, cntE, cntF, inputProperties, sourceSequence, sourceInputId);
        ruleList.add(syntheticRule);
      }
    }
  }

  @Override
  public boolean allowableContinuation(Featurizable<IString, String> featurizable,
      ConcreteRule<IString, String> rule) {
    final Sequence<IString> prefix = featurizable == null ? null : featurizable.targetPrefix;
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
