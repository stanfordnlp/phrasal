package mt.base;

import mt.decoder.util.Hypothesis;

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
    /*
    if(!(concreteOpt.abstractOption instanceof DTUOption)) {
      super.augmentAlignments(concreteOpt);
      return;
    }
    DTUOption dtuOpt = (DTUOption) concreteOpt.abstractOption;
    int foreignSz = concreteOpt.foreignCoverage.length()-concreteOpt.foreignCoverage.nextSetBit(0);
    for (int j=0; j<dtuOpt.dtus.length; ++j) {
      int transSz = dtuOpt.dtus[j].elements.length;
      int limit;
      int[] range =  new int[2];
      range[PHRASE_START] = foreignPosition;
      range[PHRASE_END]   = foreignPosition + foreignSz;
      limit = translationPosition+transSz;
      for (int i = translationPosition; i < limit; i++) {
        t2fAlignmentIndex[i] = range;
      }

      range =  new int[2];
      range[PHRASE_START] = translationPosition;
      range[PHRASE_END]   = translationPosition+transSz;
      limit = foreignPosition+foreignSz;
      for (int i = foreignPosition; i < limit; i++) {
        if(concreteOpt.foreignCoverage.get(i))
          f2tAlignmentIndex[i] = range;
      }
    }
    */
  }
}
