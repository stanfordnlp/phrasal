package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * @author danielcer
 */
public class RandomPairs extends AbstractNBestOptimizer {

  public RandomPairs(MERT mert) {
    super(mert);
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;

    for (int noProgress = 0; noProgress < MERT.NO_PROGRESS_LIMIT;) {
      Counter<String> dir;
      List<ScoredFeaturizedTranslation<IString, String>> rTrans1, rTrans2;

      dir = MERT.summarizedAllFeaturesVector(rTrans1 = mert
          .randomTranslations(nbest));
      Counter<String> counter = MERT.summarizedAllFeaturesVector(rTrans2 = mert
          .randomTranslations(nbest));
      Counters.subtractInPlace(dir, counter);

      System.err.printf("Pair scores: %.5f %.5f\n", emetric.score(rTrans1),
          emetric.score(rTrans2));

      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      double eval = MERT.evalAtPoint(nbest, newWts, emetric);

      double ssd = 0;
      for (String k : newWts.keySet()) {
        double diff = wts.getCount(k) - newWts.getCount(k);
        ssd += diff * diff;
      }
      System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd,
          noProgress);
      wts = newWts;
      if (ssd < MERT.NO_PROGRESS_SSD)
        noProgress++;
      else
        noProgress = 0;
    }
    return wts;
  }
}