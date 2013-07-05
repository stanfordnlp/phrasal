package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.util.DenseScorer;
import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;
import edu.stanford.nlp.mt.tune.EValueLearningScorer;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * @author danielcer
 */

public class OldCerStyleOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = false;

  public OldCerStyleOptimizer(MERT mert) {
    super(mert);
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;
    double finalEval;
    int iter = 0;
    double initialEval = MERT.evalAtPoint(nbest, wts, emetric);
    System.out.printf("Initial (Pre-optimization) Score: %f\n", initialEval);
    for (;; iter++) {
      Counter<String> dEl = new ClassicCounter<String>();
      IncrementalEvaluationMetric<IString, String> incEvalMetric = emetric
          .getIncrementalMetric();
      Counter<String> scaledWts = new ClassicCounter<String>(wts);
      Counters.normalize(scaledWts);
      Counters.multiplyInPlace(scaledWts, 0.01);
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
          .nbestLists()) {
        if (incEvalMetric.size() > 0)
          incEvalMetric.replace(incEvalMetric.size() - 1, null);
        incEvalMetric.add(null);
        // List<ScoredFeaturizedTranslation<IString, String>> sfTrans =
        // nbestlist;
        List<Collection<FeatureValue<String>>> featureVectors = new ArrayList<Collection<FeatureValue<String>>>(
            nbestlist.size());
        double[] us = new double[nbestlist.size()];
        int pos = incEvalMetric.size() - 1;
        for (ScoredFeaturizedTranslation<IString, String> sfTran : nbestlist) {
          incEvalMetric.replace(pos, sfTran);
          us[featureVectors.size()] = incEvalMetric.score();
          featureVectors.add(sfTran.features);
        }

        dEl.addAll(EValueLearningScorer.dEl(new DenseScorer(scaledWts,
            MERT.featureIndex), featureVectors, us));
      }

      Counters.normalize(dEl);

      // System.out.printf("Searching %s\n", dEl);
      Counter<String> wtsdEl = mert.lineSearch(nbest, wts, dEl, emetric);
      double evaldEl = MERT.evalAtPoint(nbest, wtsdEl, emetric);

      double eval;
      Counter<String> oldWts = wts;
      eval = evaldEl;
      wts = wtsdEl;

      double ssd = 0;
      for (String k : wts.keySet()) {
        double diff = oldWts.getCount(k) - wts.getCount(k);
        ssd += diff * diff;
      }

      System.out.printf("Global max along dEl dir(%d): %f wts ssd: %f\n", iter,
          eval, ssd);

      if (ssd < MERT.NO_PROGRESS_SSD) {
        finalEval = eval;
        break;
      }
    }

    System.out.printf("Final iters: %d %f->%f\n", iter, initialEval, finalEval);
    return wts;
  }
}