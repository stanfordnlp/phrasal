package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class LinearDistortionRecombinationFilter<TK, FV> implements
    RecombinationFilter<Hypothesis<TK, FV>> {

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  /**
	 * 
	 */
  private int lastOptionForeignEdge(Hypothesis<TK, FV> hyp) {
    if (hyp.translationOpt == null) {
      return 0;
    }
    return hyp.translationOpt.sourceCoverage.length();
  }

  @Override
  public boolean combinable(Hypothesis<TK, FV> hypA, Hypothesis<TK, FV> hypB) {
    return lastOptionForeignEdge(hypA) == lastOptionForeignEdge(hypB);
  }

  @Override
  public long recombinationHashCode(Hypothesis<TK, FV> hyp) {
    return lastOptionForeignEdge(hyp);
  }

}
