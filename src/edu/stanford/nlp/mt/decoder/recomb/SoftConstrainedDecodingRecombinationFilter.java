package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.decoder.util.Derivation;

/**
 * 
 * @author Joern Wuebker
 * 
 * @param <TK>
 * @param <FV>
 */
public class SoftConstrainedDecodingRecombinationFilter<TK, FV> implements
    RecombinationFilter<Derivation<TK, FV>> {

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public boolean combinable(Derivation<TK, FV> hypA, Derivation<TK, FV> hypB) {
    // In prefix-constrained decoding, we do not want to recombine hypotheses that cover
    // different portions of the specified prefix
    // this can be checked by a simple length comparison
    // In prefix-constrained decoding, we only care about the length if the prefix is not completely covered yet
    
    if(hypA.prefixCompleted && hypB.prefixCompleted)
      return true;
    
    return hypA.length == hypB.length;
    
  }

  @Override
  public long recombinationHashCode(Derivation<TK, FV> hyp) {
    if(hyp.prefixCompleted)
      return Long.MAX_VALUE;
    
    // otherwise the length is a perfect hash function
    return hyp.length;
  }

}
