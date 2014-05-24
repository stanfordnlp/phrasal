package edu.stanford.nlp.mt.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bbn.mt.ter.TERalignment;
import com.bbn.mt.ter.TERcalc;

import edu.stanford.nlp.mt.util.Sequence;

/**
 * Sentence-level TER metric for use in online tuning.
 *
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class SLTERMetric<TK,FV> implements SentenceLevelMetric<TK, FV> {

  public static final boolean VERBOSE = false;

  @Override
  public double score(int sourceId, Sequence<TK> source, List<Sequence<TK>> references, Sequence<TK> translation) {
    
    // uniq references to prevent (expensive) redundant calculation.
    Set<Sequence<TK>> uniqRefs = new HashSet<Sequence<TK>>(references);

    /**
     * This implements TER with length scaling per Chiang's standard
     * transformation of BLEU (see <code>BLEUGain</code>). We also follow
     * Snover et al. (2009) recommendation (p. 3) for how to compute TER with multiple
     * references, namely to select the reference for which the least number of
     * absolute edits is required. Combining these two recommendations amounts to
     * simply ignoring the denominator for the TER calculation.
     */
    final String hyp = translation.toString();
    double bestTER = Double.POSITIVE_INFINITY;
    for (Sequence<TK> refSeq : uniqRefs) {
      String ref = refSeq.toString();
      double ter;
      synchronized(TERcalc.class) {
        TERalignment align = TERcalc.TER(hyp, ref);
        ter = align.numEdits;
      }
      //        ter = align.numEdits / align.numWords;
      if (ter < bestTER) {
        bestTER = ter;
      }
    }
    
    return -bestTER;
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
