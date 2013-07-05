package edu.stanford.nlp.mt.base;

/**
 * Utilities for Featurizables
 * 
 * @author danielcer
 * 
 */
public class Featurizables {
  /**
   * 
   * @param <TK>
   * @param <FV>
   */
  public static <TK, FV> int locationOfSwappedPhrase(Featurizable<TK, FV> f) {
    return f.derivation.sourceCoverage.nextSetBit(f.sourcePosition
        + f.sourcePhrase.size());
  }

  public static <TK, FV> int endLocationOfSwappedPhrase(Featurizable<TK, FV> f) {
    int startloc = locationOfSwappedPhrase(f);
    if (startloc == -1)
      return -1;

    return f.derivation.sourceCoverage.nextClearBit(startloc) - 1;
  }
}
