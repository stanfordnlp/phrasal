package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.Sequence;

/**
 * Convex length metric. Similar to the BLEU brevity penalty. The basic penalty
 * is in the range [0,1], with 0 indicating a perfect match. The penalty is scaled by the
 * reference length.
 * 
 * @author Spence Green
 *
 */
public class LengthMetric<TK,FV> implements SentenceLevelMetric<TK, FV> {

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
    
    double value = hypLength >= refLength ? refLength / hypLength : hypLength / refLength;
    value = 1.0 - value;
    // Convex transform
    value *= value;
    
    // Chiang trick: scale by the reference length
    // Negate like SLTERMetric
    return -1.0 * value * refLength;
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
