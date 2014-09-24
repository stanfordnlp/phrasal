package edu.stanford.nlp.mt.tune.optimizers;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.DenseScorer;
import edu.stanford.nlp.mt.metrics.ScorerWrapperEvaluationMetric;
import edu.stanford.nlp.mt.tune.GreedyMultiTranslationMetricMax;
import edu.stanford.nlp.mt.tune.HillClimbingMultiTranslationMetricMax;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.mt.util.FlatNBestList;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * @author danielcer
 */
public class PointwisePerceptron extends AbstractBatchOptimizer {

  public PointwisePerceptron(MERT mert) {
    super(mert);
  }

  @SuppressWarnings({ "unchecked" })
  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    List<ScoredFeaturizedTranslation<IString, String>> targets = (new HillClimbingMultiTranslationMetricMax<IString, String>(
        emetric)).maximize(nbest);

    Counter<String> wts = new ClassicCounter<String>(initialWts);

    int changes = 0, totalChanges = 0, iter = 0;

    do {
      for (int i = 0; i < targets.size(); i++) {
        // get current classifier argmax
        Scorer<String> scorer = new DenseScorer(wts, MERT.featureIndex);
        GreedyMultiTranslationMetricMax<IString, String> argmaxByScore = new GreedyMultiTranslationMetricMax<IString, String>(
            new ScorerWrapperEvaluationMetric<IString, String>(scorer));
        List<List<ScoredFeaturizedTranslation<IString, String>>> nbestSlice = Arrays
            .asList(nbest.nbestLists().get(i));
        List<ScoredFeaturizedTranslation<IString, String>> current = argmaxByScore
            .maximize(new FlatNBestList(nbestSlice));
        Counter<String> dir = MERT.summarizedAllFeaturesVector(Arrays
            .asList(targets.get(i)));
        Counters
            .subtractInPlace(dir, MERT.summarizedAllFeaturesVector(current));
        Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
        double ssd = MERT.wtSsd(wts, newWts);
        System.err.printf(
            "%d.%d - ssd: %e changes(total: %d iter: %d) apply: %f\n", iter, i,
            ssd, totalChanges, changes,
            MERT.evalAtPoint(nbest, newWts, emetric));
        wts = newWts;
        if (ssd >= MERT.NO_PROGRESS_SSD) {
          changes++;
          totalChanges++;
        }
      }
      iter++;
    } while (changes != 0);

    return wts;
  }
}