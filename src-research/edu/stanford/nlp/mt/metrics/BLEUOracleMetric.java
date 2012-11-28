package edu.stanford.nlp.mt.metrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Implements the oracle smooth BLEU metric of Watanabe et al. (2007) with
 * extensions described in Chiang (2012).
 *
 * The underlying incremental metric is thread-safe.
 *
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class BLEUOracleMetric<TK,FV> implements SentenceLevelMetric<TK, FV> {

  private static final boolean DEBUG = false;

  /**
   * Exponentially weighted moving average decay parameter.
   */
  public static final double DECAY = 0.9;

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

  public BLEUOracleMetric() {
    this(DEFAULT_ORDER);
  }
  
  public BLEUOracleMetric(int order) {
    this.order = order;
    pseudoM = new double[order];
    pseudoN = new double[order];
    pseudoRho = 0.0;
    NULL_COUNTS = new double[order];

    //      initializePseudoCounts();
    // Uninformed prior on the pseudo counts
    Arrays.fill(pseudoM, 1.0);
    Arrays.fill(pseudoN, 1.0);
    pseudoRho = 1.0;
    
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
   * Randomly initialize the pseudocounts so that the smoothed score is defined
   * for the first call to smoothScore;
   */
  private void initializePseudoCounts() {
    Random random = new Random();
    int pseudoRefLength = 20;
    pseudoRho = pseudoRefLength;
    for (int i = 0; i < pseudoM.length; ++i) {
      int numMatches = random.nextInt(pseudoRefLength) + 1;
      pseudoM[i] = numMatches;
      pseudoN[i] = numMatches + ((numMatches == pseudoRefLength) ? 0.0 : random.nextInt(pseudoRefLength - numMatches));
    }
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
    return score(sourceId, references, translation, false);
  }

  @Override
  public synchronized void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
    assert sourceId >= 0;
    score(sourceId, references, translation, true);
  }
  
  public synchronized double score(int sourceId, List<Sequence<TK>> references,
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

    // Compute BLEU
    double scoreWithExample = pseudoBLEU(m, n, rho);
//    double scoreNoExample = pseudoBLEU(NULL_COUNTS, NULL_COUNTS, 0.0);
    double scoreNoExample = 0.0;
    final double score = pseudoN[0] * (scoreWithExample - scoreNoExample);
    if (DEBUG) {
      System.err.println("BLEUwith: " + scoreWithExample);
      System.err.println("BLEUno:   " + scoreNoExample);
    }

    // Only update the counts after computing the score for this example
    if (updateCounts) {
      updatePseudoCounts(m, n, rho);
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
      pseudoM[i] = DECAY*(m[i]+pseudoM[i]);
      pseudoN[i] = DECAY*(n[i]+pseudoN[i]);
    }
    pseudoRho = DECAY*(rho + pseudoRho);
    
    if (DEBUG) {
      System.err.println("M: " + Arrays.toString(m));
      System.err.println("M-hat: " + Arrays.toString(pseudoM));
      System.err.println("N: " + Arrays.toString(n));
      System.err.println("N-hat: " + Arrays.toString(pseudoN));
      System.err.println("Rho-hat: " + pseudoRho);
      System.err.println();
    }
  }

  /**
   * Compute smoothed BLEU according to Lin and Och (2004).
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
    return score;
  }


  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.printf("java %s order ref1 [ref] < translations%n", BLEUOracleMetric.class.getName());
      return;
    }

    final int order = Integer.parseInt(args[0]);
    String[] newArgs = new String[args.length - 1];
    System.arraycopy(args, 1, newArgs, 0, args.length - 1);

    try {
      List<List<Sequence<IString>>> referencesList = Metrics.readReferences(newArgs);
      SentenceLevelMetric<IString,String> metric = new BLEUOracleMetric<IString,String>(order);
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
