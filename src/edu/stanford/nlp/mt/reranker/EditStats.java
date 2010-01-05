package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.mt.metrics.ter.TERalignment;
import edu.stanford.nlp.mt.metrics.ter.TERcalc;

/**
 * @author Pi-Chuan Chang
 */
public class EditStats implements Stats {
  double minEdits = 0;
  double avgLen = 0;

  private static final TERcalc ter = new TERcalc();

  public EditStats(String sentence, String[] refS) {
    minEdits = Double.MAX_VALUE;
    double sumlen = 0.0;
    for(int j = 0; j < refS.length; j++) {
      TERalignment result = ter.TER(sentence, refS[j]);
      minEdits = Math.min(result.numEdits, minEdits);
      sumlen += result.numWords;
    }
    this.avgLen = sumlen/refS.length;
  }
}
