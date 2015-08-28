package edu.stanford.nlp.mt.metrics;

import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.mt.util.TokenUtils;

/**
 * Sentence-level BLEU after prefix.
 * 
 * This metric is well-defined even if the hypothesis and the reference
 * don't share a prefix.
 * 
 * IMPORTANT: Assumes that the prefix file is the first entry in the set of
 * references.
 * 
 * @author Spence Green
 *
 */
public class SLBLEUAfterPrefix<TK,FV> implements SentenceLevelMetric<TK, FV> {

  private static final int DEFAULT_ORDER = 4;
  
  private final int order;
  private final boolean scaleLength;
  
  /**
   * Constructor.
   */
  public SLBLEUAfterPrefix() {
    this(DEFAULT_ORDER, true);
  }
  
  /**
   * Constructor.
   * 
   * @param order
   * @param scaleLength
   */
  public SLBLEUAfterPrefix(int order, boolean scaleLength) {
    this.order = order;
    this.scaleLength = scaleLength;
  }

  @Override
  public double score(int sourceId, Sequence<TK> source,
      List<Sequence<TK>> references, Sequence<TK> translation) {

    Sequence<TK> prefix = new ArraySequence<>(references.get(0));
    
    List<Sequence<TK>> modifiedRefs = references.stream().skip(1).map(r -> {
      Sequence<TK> masked = new ArraySequence<>(r);
      TK[] elements = masked.elements();
      for (int i = 0, sz = prefix.size(); i < sz; i++) {
        elements[i] = (TK) TokenUtils.NULL_TOKEN;
      }
      return masked;
    }).collect(Collectors.toList());
    
    double score = BLEUMetric.computeLocalSmoothScore(translation, modifiedRefs, order, false);
    
    if (scaleLength) {
      // Take the min reference length, skipping the prefix file
      int minLength = references.stream().skip(1).mapToInt(s -> s.size()).min().getAsInt();
      // Scale the score by the min reference length
      score *= minLength;
    }
    return score;
  }

  @Override
  public boolean isThreadsafe() {
    return true;
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {}
}
