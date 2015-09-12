package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.decoder.util.Derivation;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class SourceCoverageRecombinationFilter<TK, FV> implements
    RecombinationFilter<Derivation<TK, FV>> {

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public boolean combinable(Derivation<TK, FV> hypA, Derivation<TK, FV> hypB) {
    return hypA.sourceCoverage.equals(hypB.sourceCoverage);
  }

  @Override
  public long recombinationHashCode(Derivation<TK, FV> hyp) {
    return hyp.sourceCoverage.hashCode();
  }
}
