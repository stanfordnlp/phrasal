package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;

/**
 * @author Michel Galley
 */
public class DTUFeaturizable<TK,FV> extends Featurizable<TK,FV> {

  private final int segmentIdx;
  public final TranslationOption<TK> abstractOption;

  @SuppressWarnings("unchecked")
	public DTUFeaturizable(Hypothesis<TK,FV> hypothesis, TranslationOption<TK> abstractOption, int translationId, int nbStatefulFeaturizers, RawSequence<TK> toks, boolean hasPendingPhrases, int segmentIdx) {
    super(hypothesis, translationId, nbStatefulFeaturizers, toks, retrieveDTUTokens(hypothesis, toks), hasPendingPhrases, segmentIdx > 0);
    this.segmentIdx = segmentIdx;
    this.abstractOption = abstractOption;
    assert(translatedPhrase.size() > 0);
  }

  public DTUFeaturizable(Sequence<TK> foreignSequence, ConcreteTranslationOption<TK> concreteOpt, int translationId, int dtuId) {
    super(foreignSequence, concreteOpt, translationId,
          ((DTUOption<TK>)concreteOpt.abstractOption).dtus[dtuId]);
    this.abstractOption = null;
    this.segmentIdx = 0;
    assert(translatedPhrase.size() > 0);
  }

  protected static <TK,FV> Object[] retrieveDTUTokens(Hypothesis<TK,FV> h, RawSequence<TK> newTokens) {
    int pos = 0;
    Featurizable<TK,FV> preceedingF = h.preceedingHyp.featurizable;
    int sz = newTokens.size();
    if (preceedingF != null)
      sz += preceedingF.partialTranslationRaw.elements.length;
    Object[] tokens = new Object[sz];
		if (preceedingF != null) {
			Object[] preceedingTokens = preceedingF.partialTranslationRaw.elements;
			System.arraycopy(preceedingTokens, 0, tokens, 0, pos=preceedingTokens.length);
		}

		System.arraycopy(newTokens.elements, 0, tokens, pos, newTokens.elements.length);
    return tokens;
  }

  @Override
  protected void augmentAlignments(ConcreteTranslationOption<TK> concreteOpt) {
    /* effectively disable augmentAlignments */
  }

  @Override
  public int getSegmentIdx() { return segmentIdx; }

  @Override
  public int getSegmentNumber() {
    if (hyp.translationOpt.abstractOption instanceof DTUOption) {
      return ((DTUOption)hyp.translationOpt.abstractOption).dtus.length;
    }
    return 1;
  }
}
