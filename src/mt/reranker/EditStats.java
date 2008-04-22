package mt.reranker;

import mt.reranker.ter.TERalignment;
import mt.reranker.ter.TERcalc;

/**
 * @author Pi-Chuan Chang
 */
public class EditStats implements Stats {
  double minEdits = 0;
  double avgLen = 0;

  public EditStats(String sentence, String[] refS) {
    minEdits = Double.MAX_VALUE;
    double sumlen = 0.0;
    for(int j = 0; j < refS.length; j++) {
      TERalignment result = TERcalc.TER(sentence, refS[j]);
      minEdits = Math.min(result.numEdits, minEdits);
      sumlen += result.numWords;
    }
    this.avgLen = sumlen/refS.length;
  }
}
