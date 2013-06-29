package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.decoder.util.Derivation;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class LinearDistortionRecombinationFilter<TK, FV> implements
    RecombinationFilter<Derivation<TK, FV>> {

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  /**
	 * 
	 */
  private int lastOptionForeignEdge(Derivation<TK, FV> hyp) {
    if (hyp.rule == null) {
      return 0;
    }
    return hyp.rule.sourceCoverage.length();
  }

  @Override
  public boolean combinable(Derivation<TK, FV> hypA, Derivation<TK, FV> hypB) {
    return lastOptionForeignEdge(hypA) == lastOptionForeignEdge(hypB);
  }

  @Override
  public long recombinationHashCode(Derivation<TK, FV> hyp) {
    return lastOptionForeignEdge(hyp);
  }

}
