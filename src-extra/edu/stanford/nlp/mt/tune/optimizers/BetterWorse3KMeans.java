package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
public class BetterWorse3KMeans extends AbstractBatchOptimizer {

  static enum Cluster3 {
    better, worse, same
  }

  public static enum Cluster3LearnType {
    betterWorse, betterSame, betterPerceptron, allDirs
  }

  Cluster3LearnType lType;

  public BetterWorse3KMeans(MERT mert, Cluster3LearnType lType) {
    super(mert);
    this.lType = lType;
  }

  @SuppressWarnings({ "unchecked" })
  @Override
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
      Counter<String> sameVec = new ClassicCounter<String>(
          Counters.L2Normalize(MERT.summarizedAllFeaturesVector(current)));
      int sameClusterCnt = 0;

      double baseScore = incEval.score();
      System.err.printf("baseScore: %f\n", baseScore);
      int lI = -1;
      List<Counter<String>> allPoints = new ArrayList<Counter<String>>();
      List<Cluster3> inBetterCluster = new ArrayList<Cluster3>();
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
        lI++;
        for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          incEval.replace(lI, tran);
          Counter<String> feats = Counters.L2Normalize(MERT
              .summarizedAllFeaturesVector(Arrays.asList(tran)));
          if (incEval.score() >= baseScore) {
            betterVec.addAll(feats);
            betterClusterCnt++;
            inBetterCluster.add(Cluster3.better);
          } else {
            worseVec.addAll(feats);
            worseClusterCnt++;
            inBetterCluster.add(Cluster3.worse);
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
      System.err.printf("Initial Same Vec:\n%s\n", sameVec);

      // k-means loop
      Set<String> keys = new HashSet<String>();
      keys.addAll(betterVec.keySet());
      keys.addAll(worseVec.keySet());
      int changes = keys.size();
      for (int clustIter = 0; changes != 0; clustIter++) {
        changes = 0;
        Counter<String> newBetterVec = new ClassicCounter<String>();
        Counter<String> newSameVec = new ClassicCounter<String>();
        Counter<String> newWorseVec = new ClassicCounter<String>();
        betterClusterCnt = 0;
        worseClusterCnt = 0;
        sameClusterCnt = 0;
        for (int i = 0; i < allPoints.size(); i++) {
          Counter<String> pt = allPoints.get(i);
          double pDist = 0;
          double nDist = 0;
          double sDist = 0;
          for (String k : keys) {
            double pd = betterVec.getCount(k) - pt.getCount(k);
            pDist += pd * pd;
            double nd = worseVec.getCount(k) - pt.getCount(k);
            nDist += nd * nd;
            double sd = sameVec.getCount(k) - pt.getCount(k);
            sDist += sd * sd;
          }

          if (pDist < nDist && pDist < sDist) {
            newBetterVec.addAll(pt);
            betterClusterCnt++;
            if (inBetterCluster.get(i) != Cluster3.better) {
              inBetterCluster.set(i, Cluster3.better);
              changes++;
            }
          } else if (sDist < nDist) {
            newSameVec.addAll(pt);
            sameClusterCnt++;
            if (inBetterCluster.get(i) != Cluster3.same) {
              inBetterCluster.set(i, Cluster3.same);
              changes++;
            }
          } else {
            newWorseVec.addAll(pt);
            worseClusterCnt++;
            if (inBetterCluster.get(i) != Cluster3.worse) {
              inBetterCluster.set(i, Cluster3.worse);
              changes++;
            }
          }
        }
        System.err
            .printf(
                "Cluster Iter: %d Changes: %d BetterClust: %d WorseClust: %d SameClust: %d\n",
                clustIter, changes, betterClusterCnt, worseClusterCnt,
                sameClusterCnt);
        Counters.multiplyInPlace(newBetterVec, 1.0 / betterClusterCnt);
        Counters.multiplyInPlace(newWorseVec, 1.0 / worseClusterCnt);
        Counters.multiplyInPlace(newSameVec, 1.0 / sameClusterCnt);
        betterVec = newBetterVec;
        worseVec = newWorseVec;
        sameVec = newSameVec;
        System.err.printf("Better Vec:\n%s\n", betterVec);
        System.err.printf("Worse Vec:\n%s\n", worseVec);
        System.err.printf("Same Vec:\n%s\n", sameVec);
      }

      Counter<String> dir = new ClassicCounter<String>();
      if (betterClusterCnt != 0)
        dir.addAll(betterVec);

      switch (lType) {
      case betterPerceptron:
        Counter<String> c = Counters.L2Normalize(MERT
            .summarizedAllFeaturesVector(current));
        Counters.multiplyInPlace(c, Counters.L2Norm(betterVec));
        Counters.subtractInPlace(dir, c);
        System.out.printf("betterPerceptron");
        System.out.printf("current:\n%s\n\n", c);
        break;
      case betterSame:
        System.out.printf("betterSame");
        System.out.printf("sameVec:\n%s\n\n", sameVec);
        if (sameClusterCnt != 0)
          Counters.subtractInPlace(dir, sameVec);
        break;

      case betterWorse:
        System.out.printf("betterWorse");
        System.out.printf("worseVec:\n%s\n\n", worseVec);
        if (worseClusterCnt != 0)
          Counters.subtractInPlace(dir, worseVec);
        break;
      }

      MERT.normalize(dir);
      System.err.printf("iter: %d\n", iter);
      System.err.printf("Better cnt: %d\n", betterClusterCnt);
      System.err.printf("SameClust: %d\n", sameClusterCnt);
      System.err.printf("Worse cnt: %d\n", worseClusterCnt);
      System.err.printf("Better Vec:\n%s\n\n", betterVec);
      System.err.printf("l2: %f\n", Counters.L2Norm(betterVec));
      System.err.printf("Worse Vec:\n%s\n\n", worseVec);
      System.err.printf("Same Vec:\n%s\n\n", sameVec);
      System.err.printf("Dir:\n%s\n\n", dir);

      Counter<String> newWts;
      if (lType != Cluster3LearnType.allDirs) {
        newWts = mert.lineSearch(nbest, wts, dir, emetric);
      } else {
        Counter<String> c = Counters.L2Normalize(MERT
            .summarizedAllFeaturesVector(current));
        Counters.multiplyInPlace(c, Counters.L2Norm(betterVec));

        newWts = wts;

        // Better Same
        dir = new ClassicCounter<String>(betterVec);
        Counters.subtractInPlace(dir, sameVec);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Better Perceptron
        dir = new ClassicCounter<String>(betterVec);
        Counters.subtractInPlace(dir, c);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Better Worse
        dir = new ClassicCounter<String>(betterVec);
        Counters.subtractInPlace(dir, worseVec);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Same Worse
        dir = new ClassicCounter<String>(sameVec);
        Counters.subtractInPlace(dir, worseVec);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Same Perceptron
        dir = new ClassicCounter<String>(sameVec);
        Counters.subtractInPlace(dir, c);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Perceptron Worse
        dir = new ClassicCounter<String>(c);
        Counters.subtractInPlace(dir, worseVec);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);
      }
      System.err.printf("new wts:\n%s\n\n", newWts);
      double ssd = MERT.wtSsd(wts, newWts);
      wts = newWts;
      System.err.printf("ssd: %f\n", ssd);
      if (ssd < MERT.NO_PROGRESS_SSD)
        break;
    }

    return wts;
  }
}