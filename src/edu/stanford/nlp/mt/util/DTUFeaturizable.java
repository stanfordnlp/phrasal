package edu.stanford.nlp.mt.util;

import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.pt.DTURule;
import edu.stanford.nlp.mt.pt.Rule;

/**
 * @author Michel Galley
 */
public class DTUFeaturizable<TK, FV> extends Featurizable<TK, FV> {

  private final int segmentIdx;
  public final Rule<TK> abstractOption;

  public DTUFeaturizable(Derivation<TK, FV> hypothesis,
      Rule<TK> abstractOption, int sourceInputId,
      int nbStatefulFeaturizers, RawSequence<TK> targetPhrase,
      boolean hasPendingPhrases, int segmentIdx) {
    super(hypothesis, sourceInputId, nbStatefulFeaturizers, targetPhrase,
        hasPendingPhrases, segmentIdx > 0);
    this.segmentIdx = segmentIdx;
    this.abstractOption = abstractOption;
    assert (targetPhrase.size() > 0);
  }

  public DTUFeaturizable(Sequence<TK> sourceSequence,
      InputProperties sourceInputProperties, ConcreteRule<TK,FV> concreteRule, int sourceInputId, int dtuId) {
    super(sourceSequence, sourceInputProperties, concreteRule, sourceInputId,
        ((DTURule<TK>) concreteRule.abstractRule).dtus[dtuId]);
    this.abstractOption = null;
    this.segmentIdx = 0;
    assert (targetPhrase.size() > 0);
  }

  @Override
  public int getSegmentIdx() {
    return segmentIdx;
  }

  @Override
  public int getSegmentNumber() {
    if (derivation.rule.abstractRule instanceof DTURule) {
      return ((DTURule<TK>) derivation.rule.abstractRule).dtus.length;
    }
    return 1;
  }
}
