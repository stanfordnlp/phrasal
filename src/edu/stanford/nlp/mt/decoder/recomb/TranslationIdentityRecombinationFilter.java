package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.decoder.util.Derivation;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class TranslationIdentityRecombinationFilter<TK, FV> implements
    RecombinationFilter<Derivation<TK, FV>> {
  boolean noisy = false;

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public boolean combinable(Derivation<TK, FV> hypA, Derivation<TK, FV> hypB) {
    if (hypA.featurizable == null && hypB.featurizable == null)
      return true;
    if (hypA.featurizable == null)
      return false;
    if (hypB.featurizable == null)
      return false;

    if (noisy) {
      System.err.printf("%s vs. %s\n", hypA.featurizable.targetPrefix,
          hypB.featurizable.targetPrefix);
      System.err.printf("tirf equal: %s\n",
          hypA.featurizable.targetPrefix
              .equals(hypB.featurizable.targetPrefix));
    }
    return hypA.featurizable.targetPrefix
        .equals(hypB.featurizable.targetPrefix);
  }

  @Override
  public long recombinationHashCode(Derivation<TK, FV> hyp) {
    if (hyp.featurizable == null) {
      return 0;
    }
    return hyp.featurizable.targetPrefix.longHashCode();
  }

}
