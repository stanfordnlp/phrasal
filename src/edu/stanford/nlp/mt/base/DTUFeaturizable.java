package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.mt.decoder.util.Derivation;

/**
 * @author Michel Galley
 */
public class DTUFeaturizable<TK, FV> extends Featurizable<TK, FV> {

  private final int segmentIdx;
  public final Rule<TK> abstractOption;

  public DTUFeaturizable(Derivation<TK, FV> hypothesis,
      Rule<TK> abstractOption, int sourceInputId,
      int nbStatefulFeaturizers, RawSequence<TK> toks,
      boolean hasPendingPhrases, int segmentIdx) {
    super(hypothesis, sourceInputId, nbStatefulFeaturizers, toks,
        retrieveDTUTokens(hypothesis, toks), hasPendingPhrases, segmentIdx > 0);
    this.segmentIdx = segmentIdx;
    this.abstractOption = abstractOption;
    assert (targetPhrase.size() > 0);
  }

  public DTUFeaturizable(Sequence<TK> foreignSequence,
      ConcreteRule<TK,FV> concreteOpt, int sourceInputId, int dtuId) {
    super(foreignSequence, concreteOpt, sourceInputId,
        ((DTURule<TK>) concreteOpt.abstractRule).dtus[dtuId]);
    this.abstractOption = null;
    this.segmentIdx = 0;
    assert (targetPhrase.size() > 0);
  }

  protected static <TK, FV> Object[] retrieveDTUTokens(Derivation<TK, FV> h,
      RawSequence<TK> newTokens) {
    int pos = 0;
    Featurizable<TK, FV> preceedingF = h.preceedingDerivation.featurizable;
    int sz = newTokens.size();
    if (preceedingF != null)
      sz += preceedingF.targetPrefixRaw.elements.length;
    Object[] tokens = new Object[sz];
    if (preceedingF != null) {
      Object[] preceedingTokens = preceedingF.targetPrefixRaw.elements;
      System.arraycopy(preceedingTokens, 0, tokens, 0,
          pos = preceedingTokens.length);
    }

    System.arraycopy(newTokens.elements, 0, tokens, pos,
        newTokens.elements.length);
    return tokens;
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
