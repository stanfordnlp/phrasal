package edu.stanford.nlp.mt.metrics;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bbn.mt.terp.TERalignment;
import com.bbn.mt.terp.TERcalc;
import com.bbn.mt.terp.TERcost;

import edu.stanford.nlp.mt.tools.NISTTokenizer;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class SLTERGain<TK,FV> implements SentenceLevelMetric<TK, FV> {

  public static final int DEFAULT_BEAM_SIZE = 20;

  private final TERcost terCost = new TERcost();
  private final int beamSize = DEFAULT_BEAM_SIZE;
  
  @Override
  public double score(int sourceId, Sequence<TK> source,
      List<Sequence<TK>> references, Sequence<TK> translation) {

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
    TERcalc terCalc = new TERcalc(terCost);
    terCalc.BEAM_WIDTH = beamSize;
    final String hyp = translation.toString();
    double bestTER = Double.POSITIVE_INFINITY;
    int refLen = 0;
    for (Sequence<TK> refSeq : uniqRefs) {
      String ref = refSeq.toString();
      TERalignment align = terCalc.TER(hyp, ref);
      //        ter = align.numEdits / align.numWords;
      double ter = align.numEdits;
      if (ter < bestTER) {
        bestTER = ter;
        refLen = refSeq.size();
      }
    }
    bestTER /= (double) refLen;
    bestTER = Math.max(0.0, 1.0 - bestTER);
    return bestTER*refLen;
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
  }

  @Override
  public boolean isThreadsafe() {
    return true;
  }

  /**
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err
      .printf("Usage: java %s (ref 1) (ref 2) ... (ref n) < candidateTranslations%n", SLTERGain.class.getName());
      System.exit(-1);
    }
    final boolean doNIST = true;
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args, doNIST);
    System.out.printf("Metric: Sentence-level TER GAIN with %d references (higher is better)%n", args.length);

    SLTERGain<IString,String> metric = new SLTERGain<IString,String>();
    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    int i = 0;
    for (String line; (line = reader.readLine()) != null;) {
      if (doNIST) line = NISTTokenizer.tokenize(line);
      Sequence<IString> translation = IStrings.tokenize(line);
      double score = metric.score(i, null, referencesList.get(i), translation);
      System.out.printf("%d\t%.3f%n", i, score);
      ++i;
    }
    reader.close();
  }
}
