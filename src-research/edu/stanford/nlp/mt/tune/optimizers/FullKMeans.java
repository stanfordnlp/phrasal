package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.FlatNBestList;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.ErasureUtils;

/**
 * @author danielcer
 */
public class FullKMeans extends AbstractBatchOptimizer {

  static FlatNBestList lastNbest;
  static List<Counter<String>> lastKMeans;
  static Counter<String> lastWts;

  int K;
  boolean clusterToCluster;

  public FullKMeans(MERT mert, int K, boolean clusterToCluster) {
    super(mert);
    this.K = K;
    this.clusterToCluster = clusterToCluster;
  }

  @Override
  @SuppressWarnings({ "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
        .nbestLists();

    List<Counter<String>> kMeans = new ArrayList<Counter<String>>(K);
    int[] clusterCnts = new int[K];

    if (nbest == lastNbest) {
      kMeans = lastKMeans;
      if (clusterToCluster)
        return lastWts;
    } else {
      int vecCnt = 0;
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists)
        for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          ErasureUtils.noop(tran);
          vecCnt++;
        }

      List<Counter<String>> allVecs = new ArrayList<Counter<String>>(vecCnt);
      int[] clusterIds = new int[vecCnt];

      for (int i = 0; i < K; i++)
        kMeans.add(new ClassicCounter<String>());

      // Extract all feature vectors & use them to seed the clusters;
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
        for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          Counter<String> feats = Counters.L2Normalize(MERT
              .summarizedAllFeaturesVector(Arrays.asList(tran)));
          int clusterId = random.nextInt(K);
          clusterIds[kMeans.size()] = clusterId;
          allVecs.add(feats);
          kMeans.get(clusterId).addAll(feats);
          clusterCnts[clusterId]++;
        }
      }

      // normalize cluster vectors
      for (int i = 0; i < K; i++)
        Counters.divideInPlace(kMeans.get(i), clusterCnts[i]);

      // K-means main loop
      for (int changes = vecCnt; changes != 0;) {
        changes = 0;
        int[] newClusterCnts = new int[K];
        List<Counter<String>> newKMeans = new ArrayList<Counter<String>>(K);
        for (int i = 0; i < K; i++)
          newKMeans.add(new ClassicCounter<String>());

        for (int i = 0; i < vecCnt; i++) {
          Counter<String> feats = allVecs.get(i);
          double minDist = Double.POSITIVE_INFINITY;
          int bestCluster = -1;
          for (int j = 0; j < K; j++) {
            double dist = 0;
            Set<String> keys = new HashSet<String>(feats.keySet());
            keys.addAll(kMeans.get(j).keySet());
            for (String key : keys) {
              double d = feats.getCount(key) - kMeans.get(j).getCount(key);
              dist += d * d;
            }
            if (dist < minDist) {
              bestCluster = j;
              minDist = dist;
            }
          }
          newKMeans.get(bestCluster).addAll(feats);
          newClusterCnts[bestCluster]++;
          if (bestCluster != clusterIds[i])
            changes++;
          clusterIds[i] = bestCluster;
        }

        // normalize new cluster vectors
        for (int i = 0; i < K; i++)
          Counters.divideInPlace(newKMeans.get(i), newClusterCnts[i]);

        // some output for the user
        System.err.printf("Cluster Vectors:\n");
        for (int i = 0; i < K; i++) {
          System.err.printf(
              "%d:\nCurrent (l2: %f):\n%s\nPrior(l2: %f):\n%s\n\n", i,
              Counters.L2Norm(newKMeans.get(i)), newKMeans.get(i),
              Counters.L2Norm(kMeans.get(i)), kMeans.get(i));
        }
        System.err.printf("\nCluster sizes:\n");
        for (int i = 0; i < K; i++) {
          System.err.printf("\t%d: %d (prior: %d)\n", i, newClusterCnts[i],
              clusterCnts[i]);
        }

        System.err.printf("Changes: %d\n", changes);

        // swap in new clusters
        kMeans = newKMeans;
        clusterCnts = newClusterCnts;
      }
    }

    lastKMeans = kMeans;
    lastNbest = nbest;

    // main optimization loop
    System.err.printf("Begining optimization\n");
    Counter<String> wts = new ClassicCounter<String>(initialWts);
    if (clusterToCluster) {
      Counter<String> bestWts = null;
      double bestEval = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < K; i++) {
        if (clusterCnts[i] == 0)
          continue;
        for (int j = i + 1; j < K; j++) {
          if (clusterCnts[j] == 0)
            continue;
          System.err.printf("seach pair: %d->%d\n", j, i);
          Counter<String> dir = new ClassicCounter<String>(kMeans.get(i));
          Counters.subtractInPlace(dir, kMeans.get(j));
          Counter<String> eWts = mert.lineSearch(nbest, kMeans.get(j), dir,
              emetric);
          double eval = MERT.evalAtPoint(nbest, eWts, emetric);
          if (eval > bestEval) {
            bestEval = eval;
            bestWts = eWts;
          }
          System.err.printf("new eval: %f best eval: %f\n", eval, bestEval);
        }
      }
      System.err.printf("new wts:\n%s\n\n", bestWts);
      wts = bestWts;
    } else {
      for (int iter = 0;; iter++) {
        ErasureUtils.noop(iter);
        Counter<String> newWts = new ClassicCounter<String>(wts);
        for (int i = 0; i < K; i++) {
          List<ScoredFeaturizedTranslation<IString, String>> current = MERT
              .transArgmax(nbest, newWts);
          Counter<String> c = Counters.L2Normalize(MERT
              .summarizedAllFeaturesVector(current));
          Counter<String> dir = new ClassicCounter<String>(kMeans.get(i));
          Counters.subtractInPlace(dir, c);

          System.err.printf("seach perceptron to cluster: %d\n", i);
          newWts = mert.lineSearch(nbest, newWts, dir, emetric);
          System.err.printf("new eval: %f\n",
              MERT.evalAtPoint(nbest, newWts, emetric));
          for (int j = i; j < K; j++) {
            dir = new ClassicCounter<String>(kMeans.get(i));
            if (j != i) {
              System.err.printf("seach pair: %d<->%d\n", j, i);
              Counters.subtractInPlace(dir, kMeans.get(j));
            } else {
              System.err.printf("seach singleton: %d\n", i);
            }

            newWts = mert.lineSearch(nbest, newWts, dir, emetric);
            System.err.printf("new eval: %f\n",
                MERT.evalAtPoint(nbest, newWts, emetric));
          }
        }
        System.err.printf("new wts:\n%s\n\n", newWts);
        double ssd = MERT.wtSsd(wts, newWts);
        wts = newWts;
        System.err.printf("ssd: %f\n", ssd);
        if (ssd < MERT.NO_PROGRESS_SSD)
          break;
      }
    }

    lastWts = wts;
    return wts;
  }
}