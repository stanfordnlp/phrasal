package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.DenseScorer;
import edu.stanford.nlp.mt.metrics.ScorerWrapperEvaluationMetric;
import edu.stanford.nlp.mt.tune.HillClimbingMultiTranslationMetricMax;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.mt.tune.MultiTranslationMetricMax;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * @author danielcer
 */
public class RandomAltPairs extends AbstractNBestOptimizer {
  static final boolean FORCE_BETTER_DEFAULT = true;
  final boolean forceBetter;

  public RandomAltPairs(MERT mert, String[] args) {
    super(mert);
    if (args.length == 0) {
      forceBetter = FORCE_BETTER_DEFAULT;
    } else {
      forceBetter = Boolean.parseBoolean(args[0]);
    }
  }
  public RandomAltPairs(MERT mert, boolean forceBetter) {
    super(mert);
    this.forceBetter = forceBetter;
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    System.err.printf("RandomAltPairs forceBetter = %b\n", forceBetter);
    Counter<String> wts = initialWts;

    for (int noProgress = 0; noProgress < MERT.NO_PROGRESS_LIMIT;) {
      Counter<String> dir;
      List<ScoredFeaturizedTranslation<IString, String>> rTrans;
      Scorer<String> scorer = new DenseScorer(wts, MERT.featureIndex);

      dir = MERT.summarizedAllFeaturesVector(rTrans = (forceBetter ? mert
          .randomBetterTranslations(nbest, wts, emetric) : mert
          .randomTranslations(nbest)));
      Counter<String> newWts1 = mert.lineSearch(nbest, wts, dir, emetric); // search toward random better translation
            
      MultiTranslationMetricMax<IString, String> oneBestSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(
          new ScorerWrapperEvaluationMetric<IString, String>(scorer));
      List<ScoredFeaturizedTranslation<IString, String>> oneBest = oneBestSearch
          .maximize(nbest);
      
      Counters.subtractInPlace(dir, wts);

      System.err.printf("Random alternate score: %.5f \n",
          emetric.score(rTrans));

      Counter<String> newWts = mert.lineSearch(nbest, newWts1, dir, emetric);
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