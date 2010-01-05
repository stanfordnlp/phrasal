package edu.stanford.nlp.mt.reranker;

/**
 * This class computes <b>Negative</b> TER score.
 * @author Pi-Chuan Chang
 */
public class TER implements Scorer {
  double sumMinEdits = 0;
  double sumAvgLen = 0;

  public void reset() {
    sumMinEdits = 0;
    sumAvgLen = 0;
  }
  
  public void add(Stats stats) {
    EditStats tstats = (EditStats)stats;
    sumMinEdits += tstats.minEdits;
    sumAvgLen += tstats.avgLen;
  }

  public void sub(Stats stats) {
    EditStats tstats = (EditStats)stats;
    sumMinEdits -= tstats.minEdits;
    sumAvgLen -= tstats.avgLen;
  }

  public double score() {
    if (sumAvgLen==0) {
      return Double.NEGATIVE_INFINITY;
    }
    return - sumMinEdits / sumAvgLen;
  }
}
