package mt.reranker;

import java.util.HashMap;

/**
 * A holder class that represents the n-gram statistics of a segment
 * when compared to a set of references. Once computed, these can be
 * added to and removed from a Bleu object with minor computation, but
 * creating them from String[] arrays requires a little bit of effort.
 */
public class SegStats implements Stats {
  public int len;
  public int closestRefLen;
  public int[] correct; // correct ngram counts
  public int[] total;   // total ngram counts

  public SegStats(String[] sent, String[][] refs) {
    int closestRefLen = 0;
    for(int i = 0; i < refs.length; i++) if(Math.abs(sent.length - refs[i].length) < Math.abs(sent.length - closestRefLen)) closestRefLen = refs[i].length;
    
    initComplex(sent.length, closestRefLen, NGram.distribution(sent), NGram.maxDistribution(refs), 1, 4);
  }

  public SegStats(int sentLen, int closestRefLen, HashMap<NGram, Integer> sentNGrams, HashMap<NGram, Integer> refNGrams) {
    this(sentLen, closestRefLen, sentNGrams, refNGrams, 1, 4);
  }

  // calculate closest and total from HashMaps of ngram counts
  public SegStats(int sentLen, int closestRefLen, HashMap<NGram, Integer> sentNGrams, HashMap<NGram, Integer> refNGrams, int ngramMin, int ngramMax) {
    initComplex(sentLen, closestRefLen, sentNGrams, refNGrams, ngramMin, ngramMax);
  }
  
  private void initComplex(int sentLen, int closestRefLen, HashMap<NGram, Integer> sentNGrams, HashMap<NGram, Integer> refNGrams, int ngramMin, int ngramMax) {
    correct = new int[ngramMax - ngramMin + 1];
    total = new int[ngramMax - ngramMin + 1];
      
    for(NGram ngram : sentNGrams.keySet()) {
      int num = sentNGrams.get(ngram);
      total[ngram.size - ngramMin] += num;
      correct[ngram.size - ngramMin] += Math.min(num, refNGrams.containsKey(ngram) ? refNGrams.get(ngram) : 0);
    }

    initTrivial(sentLen, closestRefLen, correct, total);
  }

  public SegStats(int len, int closestRefLen, int[] correct, int[] total) {
    initTrivial(len, closestRefLen, correct, total);
  }


  private void initTrivial(int len, int closestRefLen, int[] correct, int[] total) {
    this.len = len;
    this.closestRefLen = closestRefLen;
    this.correct = correct;
    this.total = total;
  }
}
