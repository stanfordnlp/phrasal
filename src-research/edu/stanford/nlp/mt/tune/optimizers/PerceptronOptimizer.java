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
public class PerceptronOptimizer extends AbstractBatchOptimizer {

  public PerceptronOptimizer(MERT mert) {
    super(mert);
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    List<ScoredFeaturizedTranslation<IString, String>> target = (new HillClimbingMultiTranslationMetricMax<IString, String>(
        emetric)).maximize(nbest);
    Counter<String> targetFeatures = MERT.summarizedAllFeaturesVector(target);
    Counter<String> wts = initialWts;

    while (true) {
      Scorer<String> scorer = new DenseScorer(wts, MERT.featureIndex);
      MultiTranslationMetricMax<IString, String> oneBestSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(
          new ScorerWrapperEvaluationMetric<IString, String>(scorer));
      List<ScoredFeaturizedTranslation<IString, String>> oneBest = oneBestSearch
          .maximize(nbest);
      Counter<String> dir = MERT.summarizedAllFeaturesVector(oneBest);
      Counters.multiplyInPlace(dir, -1.0);
      dir.addAll(targetFeatures);
      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      double ssd = 0;
      for (String k : newWts.keySet()) {
        double diff = wts.getCount(k) - newWts.getCount(k);
        ssd += diff * diff;
      }
      wts = newWts;
      if (ssd < MERT.NO_PROGRESS_SSD)
        break;
    }
    return wts;
  }
}