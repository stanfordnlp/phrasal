package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class TranslationIdentityRecombinationFilter<TK, FV> implements
    RecombinationFilter<Hypothesis<TK, FV>> {
  boolean noisy = false;

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public boolean combinable(Hypothesis<TK, FV> hypA, Hypothesis<TK, FV> hypB) {
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
  public long recombinationHashCode(Hypothesis<TK, FV> hyp) {
    if (hyp.featurizable == null) {
      return 0;
    }
    return hyp.featurizable.targetPrefix.longHashCode();
  }

}
