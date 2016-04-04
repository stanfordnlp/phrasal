package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.Sequence;

/**
 * The BLEU length penalty.
 * 
 * @author Joern Wuebker
 *
 */
public class LengthPenaltyBleu<TK,FV> implements SentenceLevelMetric<TK, FV> {

  private int bestMatchLength(List<Sequence<TK>> refs, int candidateLength) {
    int best = Integer.MAX_VALUE;
    for (Sequence<TK> ref : refs) {
      if (Math.abs(candidateLength - best) > Math.abs(candidateLength
          - ref.size())) {
        best = ref.size();
      }
    }
    return best;
  }
  
  @Override
  public double score(int sourceId, Sequence<TK> source,
      List<Sequence<TK>> references, Sequence<TK> translation) {
    double refLength = (double) bestMatchLength(references, translation.size());
    double hypLength = (double) translation.size();
    
    if(refLength <= hypLength) return 1.0;
    
    else return Math.exp(1 - (refLength / hypLength));
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
  }

  @Override
  public boolean isThreadsafe() {
    return true;
  }
}
