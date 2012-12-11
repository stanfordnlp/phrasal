package edu.stanford.nlp.mt.metrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Implements the oracle smooth BLEU metric of Watanabe et al. (2007) with
 * extensions described in Chiang (2012).
 * 
 * Also implements sentence-level smoothed BLEU according to Lin and Och (2004).
 *
 * NOTE: The underlying incremental metric is thread-safe. However, if calls to score() and update()
 * are not coordinated by the caller, then this metric is unstable.
 *
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class BLEUOracleCost<TK,FV> implements SentenceLevelMetric<TK, FV> {

  private static final boolean DEBUG = true;

  /**
   * Default n-gram order
   */
  public static final int DEFAULT_ORDER = 4;
  
  /**
   * BLEU n-gram order
   */
  private final int order;
  
  /**
   * BLEU pseudo counts for smoothing.
   */
  private final double[] pseudoM;
  private final double[] pseudoN;
  private double pseudoRho;
  
  // Convenience data structure for computing the loss
  private final double[] NULL_COUNTS;

  // Caches for faster computation of the scores
  Map<Integer,Map<Sequence<TK>, Integer>> maxRefCounts;
  Map<Integer,Integer> maxRefLengths;

  // Cherry and Foster (2012) oracle document
  private final boolean doCherryScoring;
  
  /**
   * Exponentially weighted moving average decay parameter.
   */
  private final double decay;

  public BLEUOracleCost() {
    this(DEFAULT_ORDER, false);
  }
  
  public BLEUOracleCost(int order, boolean doCherryScoring) {
    this.order = order;
    this.doCherryScoring = doCherryScoring;
    this.decay = doCherryScoring ? 0.999 : 0.9;
    
    pseudoM = new double[order];
    pseudoN = new double[order];
    pseudoRho = 0.0;
    initPseudoCounts();

    NULL_COUNTS = new double[order];

    // Initialize the caches
    maxRefCounts = new HashMap<Integer,Map<Sequence<TK>,Integer>>();
    maxRefLengths = new HashMap<Integer,Integer>();
  }

  private Map<Sequence<TK>,Integer> getMaxRefCounts(int sourceId, List<Sequence<TK>> references) {
    if ( ! maxRefCounts.containsKey(sourceId)) {
      Map<Sequence<TK>, Integer> counts = Metrics.getMaxNGramCounts(references, order);
      maxRefCounts.put(sourceId, counts);
    }
    return maxRefCounts.get(sourceId);
  }
  
  /**
   * Should be the minimum reference length.
   * 
   * @param sourceId
   * @param references
   * @return
   */
  private int getMinRefLength(int sourceId, List<Sequence<TK>> references) {
    if ( ! maxRefLengths.containsKey(sourceId)) {
      int minLength = Integer.MAX_VALUE;
      for (Sequence<TK> ref : references) {
        if (ref.size() < minLength) {
          minLength = ref.size();
        }
      }
      maxRefLengths.put(sourceId, minLength);
    }
    return maxRefLengths.get(sourceId);
  }
  
  /**
   * Initialize pseudo counts according to Lin and Och (2004).
   */
  private void initPseudoCounts() {
    Arrays.fill(pseudoM, 1.0);
    pseudoM[0] = 0.0;
    Arrays.fill(pseudoN, 1.0);
    pseudoN[0] = 0.0;
    pseudoRho = 0.0;
  }

  
  /**
   * Compute the sentence-level BLEU-based loss according to Chiang (2012).
   *
   * Note that this score can be negative. In that case, it will be clamped to 0
   * by the hinge loss.
   *
   * @param trans
   * @param nbestId
   * @return
   */
  @Override
  public synchronized double score(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
    assert sourceId >= 0;
    assert references != null && translation != null;
    return score(sourceId, references, translation, false);
  }

  @Override
  public synchronized void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
    assert sourceId >= 0;
    assert references != null && translation != null;
    score(sourceId, references, translation, true);
  }
  
  private double score(int sourceId, List<Sequence<TK>> references,
        Sequence<TK> translation, boolean updateCounts) {
    // Extract n-grams
    final Map<Sequence<TK>, Integer> maxRefCounts = getMaxRefCounts(sourceId, references);
    final Map<Sequence<TK>, Integer> hypothesisCounts = Metrics.getNGramCounts(translation, order);
    
    // Calculate the BLEU statistics for this example
    final double[] m = new double[order];
    final double[] n = new double[order];
    final double rho = getMinRefLength(sourceId, references);

    for (Sequence<TK> ngram : hypothesisCounts.keySet()) {
      double numProposed = hypothesisCounts.get(ngram);
      double refCount = maxRefCounts.containsKey(ngram) ? maxRefCounts.get(ngram) : 0.0;
      double numMatches = Math.min(numProposed, refCount);
      final int ngramIdx = ngram.size() - 1;
      m[ngramIdx] += numMatches;
      n[ngramIdx] += numProposed;
    }

    // Smoothed BLEU according to the current pseudocounts
    final double smoothBLEU = pseudoBLEU(m, n, rho);
    double score = 0.0;
    if (doCherryScoring) {
      // This value is a *gain*
      score = smoothBLEU * pseudoN[0];
      
    } else {
      // Chiang (2012) cost  
      double scoreNoExample = pseudoBLEU(NULL_COUNTS, NULL_COUNTS, 0.0);
      // This value is a *cost*
      score = pseudoN[0] * (smoothBLEU - scoreNoExample);
    }
    
    if (updateCounts) {
      updatePseudoCounts(m, n, rho);
    } else if (DEBUG) {
      synchronized(System.err) {
        System.err.println("Smooth BLEU:\t" + smoothBLEU);
        System.err.println("Scaled score:\t" + score);
      }
    }

    return score;
  }

  /**
   * Update the pseudo counts according to an exponentially weighted moving average.
   * Chiang (2008) calls this EWMA, but Chiang (2012) calls it "exponential decay." Those don't
   * seem to be the same thing?
   *
   * @param m
   * @param n
   * @param rho
   */
  private void updatePseudoCounts(double[] m, double[] n, double rho) {
    assert m.length == n.length;
    for (int i = 0; i < m.length; ++i) {
      pseudoM[i] = decay*pseudoM[i] + m[i];
      pseudoN[i] = decay*pseudoN[i] + n[i];
    }
    pseudoRho = (decay*pseudoRho) + rho;
    
    if (DEBUG) {
      synchronized(System.err) {
        System.err.println("M: " + Arrays.toString(m));
        System.err.println("M-hat: " + Arrays.toString(pseudoM));
        System.err.println("N: " + Arrays.toString(n));
        System.err.println("N-hat: " + Arrays.toString(pseudoN));
        System.err.println("Rho-hat: " + pseudoRho);
        System.err.println();
      }
    }
  }

  /**
   * Compute smoothed BLEU according to Lin and Och (2004). If any of the n-gram precisions are 0.0,
   * then the method will return 0.0.
   *
   * @param m
   * @param n
   * @param rho
   * @return
   */
  private double pseudoBLEU(double[] m, double[] n, double rho) {
    double score = 0.0;
    for (int i = 0; i < m.length; ++i) {
      double num = m[i] + pseudoM[i];
      double denom = n[i] + pseudoN[i];
      assert num > 0.0;
      assert denom > 0.0;
      score += Math.log(num / denom);
    }
    score *= 1.0 / order;
    score += Math.min(0.0, 1.0-((rho + pseudoRho) / (n[0] + pseudoN[0])));
    score = Math.exp(score);
    return (Double.isInfinite(score) || Double.isNaN(score)) ? 0.0 : score;
  }


  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.printf("java %s order ref1 [ref] < translations%n", BLEUOracleCost.class.getName());
      return;
    }

    final int order = Integer.parseInt(args[0]);
    String[] newArgs = new String[args.length - 1];
    System.arraycopy(args, 1, newArgs, 0, args.length - 1);

    try {
      List<List<Sequence<IString>>> referencesList = Metrics.readReferences(newArgs);
      SentenceLevelMetric<IString,String> metric = new BLEUOracleCost<IString,String>(order,true);
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
      int lineId = 0;
      for (String line; (line = reader.readLine()) != null; ++lineId) {
        line = NISTTokenizer.tokenize(line).trim();
        Sequence<IString> translation = new RawSequence<IString>(
            IStrings.toIStringArray(line.split("\\s+")));
        List<Sequence<IString>> references = referencesList.get(lineId);
        double score = metric.score(lineId, references, translation);
        System.out.printf("%d\t%.4f%n", lineId, score);
        metric.update(lineId, references, translation);
      }
      reader.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
