package edu.stanford.nlp.mt.tune.optimizers;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * @author danielcer
 */
public class BetterWorseCentroids extends AbstractBatchOptimizer {

  boolean useCurrentAsWorse;
  boolean useOnlyBetter;

  public BetterWorseCentroids(MERT mert, boolean useCurrentAsWorse,
      boolean useOnlyBetter) {
    super(mert);
    this.useCurrentAsWorse = useCurrentAsWorse;
    this.useOnlyBetter = useOnlyBetter;
  }

  @Override
  @SuppressWarnings({ "unchecked" })
  public Counter<String> optimize(Counter<String> wts) {

    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
        .nbestLists();
    // Counter<String> wts = initialWts;

    for (int iter = 0;; iter++) {
      List<ScoredFeaturizedTranslation<IString, String>> current = MERT
          .transArgmax(nbest, wts);
      IncrementalEvaluationMetric<IString, String> incEval = emetric
          .getIncrementalMetric();
      for (ScoredFeaturizedTranslation<IString, String> tran : current) {
        incEval.add(tran);
      }
      Counter<String> betterVec = new ClassicCounter<String>();
      int betterCnt = 0;
      Counter<String> worseVec = new ClassicCounter<String>();
      int worseCnt = 0;
      double baseScore = incEval.score();
      System.err.printf("baseScore: %f\n", baseScore);
      int lI = -1;
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
        lI++;
        for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          incEval.replace(lI, tran);
          if (incEval.score() >= baseScore) {
            betterCnt++;
            betterVec.addAll(MERT.normalize(MERT
                .summarizedAllFeaturesVector(Arrays.asList(tran))));
          } else {
            worseCnt++;
            worseVec.addAll(MERT.normalize(MERT
                .summarizedAllFeaturesVector(Arrays.asList(tran))));
          }
        }
        incEval.replace(lI, current.get(lI));
      }
      MERT.normalize(betterVec);
      if (useCurrentAsWorse)
        worseVec = MERT.summarizedAllFeaturesVector(current);
      MERT.normalize(worseVec);
      Counter<String> dir = new ClassicCounter<String>(betterVec);
      if (!useOnlyBetter)
        Counters.subtractInPlace(dir, worseVec);
      MERT.normalize(dir);
      System.err.printf("iter: %d\n", iter);
      System.err.printf("Better cnt: %d\n", betterCnt);
      System.err.printf("Worse cnt: %d\n", worseCnt);
      System.err.printf("Better Vec:\n%s\n\n", betterVec);
      System.err.printf("Worse Vec:\n%s\n\n", worseVec);
      System.err.printf("Dir:\n%s\n\n", dir);
      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      System.err.printf("new wts:\n%s\n\n", wts);
      double ssd = MERT.wtSsd(wts, newWts);
      wts = newWts;
      System.err.printf("ssd: %f\n", ssd);
      if (ssd < MERT.NO_PROGRESS_SSD)
        break;
    }

    return wts;
  }
}