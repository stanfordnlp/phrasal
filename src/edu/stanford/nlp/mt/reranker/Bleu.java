package edu.stanford.nlp.mt.reranker;

/**
 * An incremental implementation of BLEU. SegStats objects (each one
 * representing the statistics on a translation segment) can be added and
 * removed arbitrarily, and at each point a corpus score can be computed.
 * 
 * Synopsis:
 * 
 * Bleu bleu;
 * 
 * for(int i = 0; i < corpusLength(); i++) { String[] sentence = getSentence();
 * String[][] refs = getRefs(); s = new SegStats(s, r); bleu.add(s); }
 * 
 * double score = bleu.score();
 * 
 * The vast majority of the computation goes into creating the SetStats objects
 * from String arrays. If you are doing something non-trivial, try and keep
 * those around. You can even serialize them if need be as they only depend on
 * the corpus and references.
 * 
 **/
public class Bleu implements Scorer {
  private int ngram_min, ngram_max;
  private double[] weights;

  private double[] precNum;
  private double[] precDen;
  private double refLen;
  private double hypLen;

  public Bleu(int ngram_min, int ngram_max, double[] weights) {
    this.ngram_min = ngram_min;
    this.ngram_max = ngram_max;
    this.weights = weights;
    if ((ngram_max - ngram_min + 1) != weights.length)
      throw new RuntimeException("one weight per ngram size, buddy");
    reset();
  }

  public Bleu() {
    this(1, 4, new double[] { 0.25, 0.25, 0.25, 0.25 });
  }

  public void reset() {
    precNum = new double[ngram_max - ngram_min + 1];
    precDen = new double[ngram_max - ngram_min + 1];
    refLen = hypLen = 0;
  }

  public void add(Stats stats) {
    SegStats sstats = (SegStats) stats;
    for (int i = ngram_min; i <= ngram_max; i++) {
      precNum[i - ngram_min] += sstats.correct[i - ngram_min];
      precDen[i - ngram_min] += sstats.total[i - ngram_min];
    }

    hypLen += sstats.len;
    refLen += sstats.closestRefLen;
  }

  public void sub(Stats stats) {
    SegStats sstats = (SegStats) stats;
    for (int i = ngram_min; i <= ngram_max; i++) {
      precNum[i - ngram_min] -= sstats.correct[i - ngram_min];
      precDen[i - ngram_min] -= sstats.total[i - ngram_min];
    }

    hypLen -= sstats.len;
    refLen -= sstats.closestRefLen;
  }

  public double[] rawNGramScores() {
    double[] scores = new double[ngram_max - ngram_min + 1];
    for (int i = ngram_min; i <= ngram_max; i++) {
      if (precNum[i - ngram_min] > 0) {
        scores[i - ngram_min] = Math.log(precNum[i - ngram_min]
            / precDen[i - ngram_min]);
        // System.out.println("scores[" + (i - ngram_min) + "] = Math.log(" +
        // precNum[i - ngram_min] + " / " + precDen[i - ngram_min] + ") = " +
        // scores[i - ngram_min]);
      } else
        scores[i - ngram_min] = Double.NEGATIVE_INFINITY;
    }
    return scores;
  }

  public double[] rawNGramNumerators() {
    double[] scores = new double[ngram_max - ngram_min + 1];
    for (int i = ngram_min; i <= ngram_max; i++) {
      scores[i - ngram_min] = precNum[i - ngram_min];
    }
    return scores;
  }

  public double[] rawNGramDenominators() {
    double[] scores = new double[ngram_max - ngram_min + 1];
    for (int i = ngram_min; i <= ngram_max; i++) {
      scores[i - ngram_min] = precDen[i - ngram_min];
    }
    return scores;
  }

  public double BP() {
    if (hypLen < refLen)
      return Math.exp(1.0 - (refLen / hypLen));
    else
      return 1.0;
  }

  public double score() {
    double[] ngramScores = rawNGramScores();
    double bp = BP();

    double score = 0.0;
    for (int i = ngram_min; i <= ngram_max; i++)
      score += (ngramScores[i - ngram_min] * weights[i - ngram_min]);

    return Math.exp(score) * bp;
  }
}
