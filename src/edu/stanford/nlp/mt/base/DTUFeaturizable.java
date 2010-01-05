package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;

/**
 * @author Michel Galley
 */
public class DTUFeaturizable<TK,FV> extends Featurizable<TK,FV> {

  public final boolean targetOnly;
  public final TranslationOption<TK> abstractOption;

  @SuppressWarnings("unchecked")
	public DTUFeaturizable(Hypothesis<TK,FV> hypothesis, TranslationOption<TK> abstractOption, int translationId, int nbStatefulFeaturizers, RawSequence<TK> toks, boolean notYetDone, boolean targetOnly) {
    super(hypothesis, translationId, nbStatefulFeaturizers, toks, retrieveDTUTokens(hypothesis, toks), notYetDone, targetOnly);
    this.targetOnly = targetOnly;
    this.abstractOption = abstractOption;
  }

  public DTUFeaturizable(Sequence<TK> foreignSequence, ConcreteTranslationOption<TK> concreteOpt, int translationId, int dtuId) {
    super(foreignSequence, concreteOpt, translationId,
          ((DTUOption<TK>)concreteOpt.abstractOption).dtus[dtuId]);
    this.abstractOption = null;
    this.targetOnly = false;
  }

  protected static <TK,FV> Object[] retrieveDTUTokens(Hypothesis<TK,FV> h, RawSequence<TK> newTokens) {
    int pos = 0;
    Featurizable<TK,FV> preceedingF = h.preceedingHyp.featurizable;
    int sz = newTokens.size();
    if(preceedingF != null)
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
    // TODO
  }
}
