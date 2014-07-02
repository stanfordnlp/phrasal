package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.stats.Counter;

/**
 * @author danielcer
 */
public class RandomNBestPoint extends AbstractBatchOptimizer {

  boolean better;

  public RandomNBestPoint(MERT mert, boolean better) {
    super(mert);
    this.better = better;
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;

    for (int noProgress = 0; noProgress < MERT.NO_PROGRESS_LIMIT;) {
      Counter<String> dir;
      List<ScoredFeaturizedTranslation<IString, String>> rTrans;
      dir = MERT.summarizedAllFeaturesVector(rTrans = (better ? mert
          .randomBetterTranslations(nbest, wts, emetric) : mert
          .randomTranslations(nbest)));

      System.err.printf("Random n-best point score: %.5f\n",
          emetric.score(rTrans));
      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      double eval = MERT.evalAtPoint(nbest, newWts, emetric);
      double ssd = MERT.wtSsd(wts, newWts);
      if (ssd < MERT.NO_PROGRESS_SSD)
        noProgress++;
      else
        noProgress = 0;
      System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd,
          noProgress);
      wts = newWts;
    }
    return wts;
  }
}