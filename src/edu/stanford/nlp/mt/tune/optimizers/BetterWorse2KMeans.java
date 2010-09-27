package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * @author danielcer
 */
public class BetterWorse2KMeans extends AbstractNBestOptimizer {

  boolean perceptron;
  boolean useWts;

  public BetterWorse2KMeans(MERT mert, boolean perceptron, boolean useWts) {
    super(mert);
    this.perceptron = perceptron;
    this.useWts = useWts;
  }

  @Override
  @SuppressWarnings({ "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
        .nbestLists();
    Counter<String> wts = initialWts;

    for (int iter = 0;; iter++) {
      List<ScoredFeaturizedTranslation<IString, String>> current = MERT
          .transArgmax(nbest, wts);
      IncrementalEvaluationMetric<IString, String> incEval = emetric
          .getIncrementalMetric();
      for (ScoredFeaturizedTranslation<IString, String> tran : current) {
        incEval.add(tran);
      }
      Counter<String> betterVec = new ClassicCounter<String>();
      int betterClusterCnt = 0;
      Counter<String> worseVec = new ClassicCounter<String>();
      int worseClusterCnt = 0;
      double baseScore = incEval.score();
      System.err.printf("baseScore: %f\n", baseScore);
      int lI = -1;
      List<Counter<String>> allPoints = new ArrayList<Counter<String>>();
      List<Boolean> inBetterCluster = new ArrayList<Boolean>();
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
        lI++;
        for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          incEval.replace(lI, tran);
          Counter<String> feats = Counters.L2Normalize(MERT
              .summarizedAllFeaturesVector(Arrays.asList(tran)));
          if (incEval.score() >= baseScore) {
            betterVec.addAll(feats);
            betterClusterCnt++;
            inBetterCluster.add(true);
          } else {
            worseVec.addAll(feats);
            worseClusterCnt++;
            inBetterCluster.add(false);
          }
          allPoints.add(feats);
        }
        incEval.replace(lI, current.get(lI));
      }

      System.err.printf("Better cnt: %d\n", betterClusterCnt);
      System.err.printf("Worse cnt: %d\n", worseClusterCnt);

      Counters.multiplyInPlace(betterVec, 1.0 / betterClusterCnt);
      Counters.multiplyInPlace(worseVec, 1.0 / worseClusterCnt);

      System.err.printf("Initial Better Vec:\n%s\n", betterVec);
      System.err.printf("Initial Worse Vec:\n%s\n", worseVec);

      // k-means loop
      Set<String> keys = new HashSet<String>();
      keys.addAll(betterVec.keySet());
      keys.addAll(worseVec.keySet());
      int changes = keys.size();
      for (int clustIter = 0; changes != 0; clustIter++) {
        changes = 0;
        Counter<String> newBetterVec = new ClassicCounter<String>();
        Counter<String> newWorseVec = new ClassicCounter<String>();
        betterClusterCnt = 0;
        worseClusterCnt = 0;
        for (int i = 0; i < allPoints.size(); i++) {
          Counter<String> pt = allPoints.get(i);
          double pDist = 0;
          double nDist = 0;
          for (String k : keys) {
            double pd = betterVec.getCount(k) - pt.getCount(k);
            pDist += pd * pd;
            double nd = worseVec.getCount(k) - pt.getCount(k);
            nDist += nd * nd;
          }
          if (pDist <= nDist) {
            newBetterVec.addAll(pt);
            betterClusterCnt++;
            if (!inBetterCluster.get(i)) {
              inBetterCluster.set(i, true);
              changes++;
            }
          } else {
            newWorseVec.addAll(pt);
            worseClusterCnt++;
            if (inBetterCluster.get(i)) {
              inBetterCluster.set(i, false);
              changes++;
            }
          }
        }
        System.err.printf(
            "Cluster Iter: %d Changes: %d BetterClust: %d WorseClust: %d\n",
            clustIter, changes, betterClusterCnt, worseClusterCnt);
        Counters.multiplyInPlace(newBetterVec, 1.0 / betterClusterCnt);
        Counters.multiplyInPlace(newWorseVec, 1.0 / worseClusterCnt);
        betterVec = newBetterVec;
        worseVec = newWorseVec;
        System.err.printf("Better Vec:\n%s\n", betterVec);
        System.err.printf("Worse Vec:\n%s\n", worseVec);
      }

      Counter<String> dir = new ClassicCounter<String>();
      if (betterClusterCnt != 0)
        dir.addAll(betterVec);
      if (perceptron) {
        if (useWts) {
          Counter<String> normWts = new ClassicCounter<String>(wts);
          Counters.L2Normalize(normWts);
          Counters.multiplyInPlace(normWts, Counters.L2Norm(betterVec));
          System.err.printf("Subing wts:\n%s\n", normWts);
          Counters.subtractInPlace(dir, normWts);
          System.err.printf("l2: %f\n", Counters.L2Norm(normWts));
        } else {
          Counter<String> c = Counters.L2Normalize(MERT
              .summarizedAllFeaturesVector(current));
          Counters.multiplyInPlace(c, Counters.L2Norm(betterVec));
          System.err.printf("Subing current:\n%s\n", c);
          Counters.subtractInPlace(dir, c);
          System.err.printf("l2: %f\n", Counters.L2Norm(c));
        }
      } else {
        if (worseClusterCnt != 0)
          Counters.subtractInPlace(dir, worseVec);
      }
      MERT.normalize(dir);
      System.err.printf("iter: %d\n", iter);
      System.err.printf("Better cnt: %d\n", betterClusterCnt);
      System.err.printf("Worse cnt: %d\n", worseClusterCnt);
      System.err.printf("Better Vec:\n%s\n\n", betterVec);
      System.err.printf("l2: %f\n", Counters.L2Norm(betterVec));
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