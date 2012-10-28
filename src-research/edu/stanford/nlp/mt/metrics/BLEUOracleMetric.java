package edu.stanford.nlp.mt.metrics;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;

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
public class BLEUOracleMetric<TK,FV> extends BLEUMetric<TK, FV> {

  /**
   * Exponentially weighted moving average decay parameter.
   */
  public static final double DECAY = 0.9;

  public BLEUOracleMetric(List<List<Sequence<TK>>> referencesList) {
    super(referencesList);
  }
  
  public BLEUOracleMetric(List<List<Sequence<TK>>> referencesList, int order) {
    super(referencesList, order);
  }
  
  @Override
  public BLEUIncrementalMetric getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    return new BLEUOracleIncrementalMetric(nbestList);
  }

  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  @Override
  public BLEUIncrementalMetric getIncrementalMetric() {
    return new BLEUOracleIncrementalMetric(DEFAULT_MAX_NGRAM_ORDER);
  }

  /**
   * This metric is intended to be used only for sentence-level, smoothed BLEU
   * scores. The document level methods such as add() and score() will throw
   * an exception.
   * 
   * The method to use is smoothScore().
   * 
   * @author Spence Green
   *
   */
  public class BLEUOracleIncrementalMetric extends BLEUIncrementalMetric {

    /**
     * BLEU pseudo counts for smoothing.
     */
    private final double[] pseudoM;
    private final double[] pseudoN;
    private double pseudoRho;
    
    // Convenience data structure for computing the loss
    private final double[] NULL_COUNTS;
    
    public BLEUOracleIncrementalMetric(
        BLEUOracleIncrementalMetric otherMetric) {
      this(otherMetric.pseudoM.length);
      System.arraycopy(otherMetric.pseudoM, 0, pseudoM, 0, pseudoM.length);
      System.arraycopy(otherMetric.pseudoN, 0, pseudoN, 0, pseudoN.length);
      pseudoRho = otherMetric.pseudoRho;
    }

    public BLEUOracleIncrementalMetric(NBestListContainer<TK, FV> nbestList) {
      this(DEFAULT_MAX_NGRAM_ORDER);
    }

    public BLEUOracleIncrementalMetric(int order) {
      super();
      pseudoM = new double[order];
      pseudoN = new double[order];
      pseudoRho = 0.0;
      NULL_COUNTS = new double[order];
      
      initializePseudoCounts();
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
     * This is a threadsafe call.
     * 
     * Note that this score can be negative. In that case, it will be clamped to 0
     * by the hinge loss.
     * 
     * @param trans
     * @param nbestId
     * @return
     */
    public double smoothScore(Sequence<TK> trans, int nbestId) {
      assert nbestId >= 0 && nbestId < maxReferenceCounts.size();
      
      // Extract n-grams
      final Map<Sequence<TK>, Integer> maxRefCounts = maxReferenceCounts.get(nbestId);
      final Map<Sequence<TK>, Integer> hypCounts = Metrics.getNGramCounts(trans, order);
      
      // Calculate the BLEU statistics for this example
      final double[] m = new double[order];
      final double[] n = new double[order];
      // Rho should be the minimum reference length
      final double rho = ArrayMath.min(refLengths[nbestId]);
      
      for (Sequence<TK> ngram : hypCounts.keySet()) {
        double hypCount = hypCounts.get(ngram);
        double refCount = maxRefCounts.containsKey(ngram) ? maxRefCounts.get(ngram) : 0.0;
        double thisM = Math.min(hypCount, refCount);
        m[ngram.size()-1] += thisM;
        n[ngram.size()-1] += hypCount;
      }
      
      // Compute BLEU
      double score = 0.0;
      synchronized(this) {
        score = pseudoBLEU(m, n, rho) - pseudoBLEU(NULL_COUNTS, NULL_COUNTS, 0.0);
        score *= pseudoN[0];
        updatePseudoCounts(m, n, rho);
      }
      return score;
    }
    
    /**
     * Update the pseudo counts according to an exponential moving average.
     * 
     * @param m
     * @param n
     * @param rho
     */
    private void updatePseudoCounts(double[] m, double[] n, double rho) {
      ArrayMath.multiplyInPlace(pseudoM, 1.0-DECAY);
      ArrayMath.addMultInto(pseudoM, pseudoM, m, DECAY);
      ArrayMath.multiplyInPlace(pseudoN, 1.0-DECAY);
      ArrayMath.addMultInto(pseudoN, pseudoN, n, DECAY);
      pseudoRho *= (1.0 - DECAY);
      pseudoRho += (DECAY * rho);
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
        assert denom > 0.0;
        score += num / denom;
      }
      score *= 1.0 / order;
      score += Math.min(0.0, 1.0-((rho + pseudoRho) / (n[0] + pseudoN[0])));
      score = Math.exp(score);
      return score;
    }
    
    @Override
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new BLEUOracleIncrementalMetric(this);
    }
    
    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> trans) {
      throw new UnsupportedOperationException();
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      throw new UnsupportedOperationException();
    }

    @Override
    public double score() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public double partialScore() {
      throw new UnsupportedOperationException();
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(int nbestId,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      throw new UnsupportedOperationException();
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index, int nbestId,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      throw new UnsupportedOperationException();
    }

    @Override
    public double[] precisions() {
     throw new UnsupportedOperationException();
    }

    @Override
    public double brevityPenalty() {
      throw new UnsupportedOperationException();
    }
  }  
  
  /**
   * @param args
   */
  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.printf("java %s order ref1 [ref] < translations%n", BLEUOracleMetric.class.getName());
      System.exit(-1);
    }
    
    final int order = Integer.parseInt(args[0]);
    // WSGDEBUG
    String debugFilename = args[1];
    String[] newArgs = new String[args.length - 2];
    System.arraycopy(args, 2, newArgs, 0, args.length - 2);
    
//    String[] newArgs = new String[args.length - 1];
//    System.arraycopy(args, 1, newArgs, 0, args.length - 1);
    
    
    try {
      List<List<Sequence<IString>>> referencesList = Metrics.readReferences(newArgs);
      BLEUOracleMetric<IString,String> metric = new BLEUOracleMetric<IString,String>(referencesList, order);
      BLEUOracleMetric<IString,String>.BLEUOracleIncrementalMetric imetric = (BLEUOracleMetric<IString,String>.BLEUOracleIncrementalMetric) metric.getIncrementalMetric();
//      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
      // WSGDEBUG
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(debugFilename)));
      int lineId = 0;
      for (String line; (line = reader.readLine()) != null; ++lineId) {
        line = NISTTokenizer.tokenize(line).trim();
        Sequence<IString> translation = new RawSequence<IString>(
            IStrings.toIStringArray(line.split("\\s+")));
        double score = imetric.smoothScore(translation, lineId);
        System.out.printf("%d\t%.4f%n", lineId, score);
      }
      reader.close();
      
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
