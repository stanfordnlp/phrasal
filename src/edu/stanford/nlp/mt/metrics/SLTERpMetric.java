package edu.stanford.nlp.mt.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bbn.mt.terp.TERalignment;
import com.bbn.mt.terp.TERcost;
import com.bbn.mt.terp.TERcalc;

import edu.stanford.nlp.mt.util.Sequence;

/**
 * Sentence-level TERp metric for use in online tuning.
 *
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class SLTERpMetric<TK,FV> implements SentenceLevelMetric<TK, FV> {

  public static final int DEFAULT_BEAM_SIZE = 20;
  public static final boolean VERBOSE = false;

  private final TERcost terCost;
  private final int beamSize;
  

  public SLTERpMetric() {
    this(DEFAULT_BEAM_SIZE);
  } 

  public SLTERpMetric(int beamSize) {
    terCost = new TERcost();
    this.beamSize = beamSize; 
  }

  @Override
  public double score(int sourceId, Sequence<TK> source, List<Sequence<TK>> references, Sequence<TK> translation) {
    TERcalc terCalc = new TERcalc(terCost);
    terCalc.BEAM_WIDTH = beamSize;
    
    // uniq references to prevent (expensive) redundant calculation.
    Set<Sequence<TK>> uniqRefs = new HashSet<Sequence<TK>>(references);

    /**
     * This implements TERp with length scaling per Chiang's standard
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
      TERalignment align = terCalc.TER(hyp, ref);
      //        ter = align.numEdits / align.numWords;
      double ter = align.numEdits;
      if (ter < bestTER) {
        bestTER = ter;
      }
      if (VERBOSE) {
        System.err.printf("ref: %s%n", ref);
        System.err.printf("numEdits: %f%n", align.numEdits);
        System.err.printf("numWords: %f%n", align.numWords);
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
