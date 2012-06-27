package edu.stanford.nlp.mt.tools;

import java.util.*;

import static java.lang.System.*;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;

/**
 * Minimum Bayes Risk decoding
 *
 * @author danielcer
 *
 */
public class MinimumBayesRisk {

   public static final boolean VERBOSE = false;

   public static void main(String[] argv) throws Exception {
      if (argv.length != 4) {
         err.println(
             "Usage:\n\tjava ...MinimumBayesRisk (scale) (risk/utility) (metric) (n-best list)");
         exit(-1);
      }
      double scale = Double.parseDouble(argv[0]);
      boolean risk;
      if ("risk".equals(argv[1])) {
         risk = true;
      } else if ("utility".equals(argv[1])) {
         risk = false;
      } else {
        throw new RuntimeException(String.format("Second argument, %s, should be either 'risk' or 'utility'", argv[1]));
      }
      String metricName = argv[2];
      FlatNBestList nbestlists = new FlatNBestList(argv[3]);
      int idx = -1; 
      for (List<ScoredFeaturizedTranslation<IString,String>> nbestlist :
         nbestlists.nbestLists()) { idx++;
         double[] nbestScores = new double[nbestlist.size()];
         
         for (ScoredFeaturizedTranslation<IString,String> refTrans : nbestlist) 
         { 
           List<List<Sequence<IString>>> fakeRef = Arrays.asList(
               Arrays.asList(refTrans.translation));
           EvaluationMetric<IString,String> metric =
              EvaluationMetricFactory.newMetric(metricName,fakeRef);
           
           int hypI = -1;
           for (ScoredFeaturizedTranslation<IString,String> hyp : nbestlist) 
           { hypI++;
             double metricScore = metric.score(Arrays.asList(hyp)); 
           
             double fracHypScore = metricScore * Math.exp(scale*refTrans.score);
             nbestScores[hypI] += fracHypScore; 
             if (VERBOSE) {
               System.err.printf("hyp(%d): %s\n", hypI, hyp);
               System.err.printf("scale: %f\n", scale);
               System.err.printf("score: %f\n", hyp.score);
               System.err.printf("metricScore: %f\n", metricScore);
               System.err.printf("fracHypScore: %f\n", fracHypScore);
               System.err.printf("nbestScores[%d]: %f\n", hypI, nbestScores[hypI]);
             }
           }
         }
         int hypI = -1;
         List<Pair<Double,ScoredFeaturizedTranslation<IString,String>>> 
         rescoredNBestList = new ArrayList<Pair<Double,ScoredFeaturizedTranslation<IString,String>>>(nbestlist.size());
         for (ScoredFeaturizedTranslation<IString,String> hyp : nbestlist) {
           hypI++;
           rescoredNBestList.add(new Pair<Double,ScoredFeaturizedTranslation<IString,String>>(nbestScores[hypI], hyp));
         }
         Collections.sort(rescoredNBestList);
         if (!risk) {
            Collections.reverse(rescoredNBestList);
         }
         for (Pair<Double,ScoredFeaturizedTranslation<IString,String>> entry : rescoredNBestList) {
           out.printf("%d ||| %s ||| %e\n", idx, 
              entry.second().translation, entry.first());
         }
      }
   }
}
