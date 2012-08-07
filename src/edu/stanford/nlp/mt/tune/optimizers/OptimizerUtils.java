package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.FlatNBestList;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * 
 * @author daniel cer
 *
 */
public class OptimizerUtils {
  public static <TK, FV> double[][] incontextMetricScores(
      NBestListContainer<TK, FV> nbest,
      List<ScoredFeaturizedTranslation<TK, FV>> context,
      EvaluationMetric<TK, FV> emetric) {
    IncrementalEvaluationMetric<TK, FV> incMetric = emetric
        .getIncrementalMetric();

    for (ScoredFeaturizedTranslation<TK, FV> trans : context) {
      incMetric.add(trans);
    }

    List<List<ScoredFeaturizedTranslation<TK, FV>>> nbestLists = nbest
        .nbestLists();
    double[][] incontextScores = new double[nbestLists.size()][];

    for (int i = 0; i < incontextScores.length; i++) {
      List<ScoredFeaturizedTranslation<TK, FV>> nbestList = nbestLists.get(i);
      incontextScores[i] = new double[nbestList.size()];
      for (int j = 0; j < incontextScores[i].length; j++) {
        ScoredFeaturizedTranslation<TK, FV> trans = nbestList.get(j);
        incMetric.replace(i, trans);
        incontextScores[i][j] = incMetric.score();
      }
      incMetric.replace(i, context.get(i));
    }
    return incontextScores;
  }

  public static <TK, FV> double[][] calcDeltaMetric(NBestListContainer<TK, FV> nbest,
      List<ScoredFeaturizedTranslation<TK, FV>> base,
      EvaluationMetric<TK, FV> emetric) {
    double baseScore = emetric.score(base);
    double[][] incontextScores = incontextMetricScores(nbest, base, emetric);
    double[][] deltaScores = new double[incontextScores.length][];
    for (int i = 0; i < incontextScores.length; i++) {
      deltaScores[i] = new double[incontextScores[i].length];
      for (int j = 0; j < incontextScores[i].length; j++) {
        deltaScores[i][j] = baseScore - incontextScores[i][j];
      }
    }
    return deltaScores;
  }
  
  public static <TK, FV> int[][] deltaMetricToSortedIndicies(final double[][] deltaMetric) {
    class DeltaIndex implements Comparable<DeltaIndex>{
       public int i, j;
       
       public DeltaIndex(int i, int j) {
         this.i = i; this.j = j;
       }
       
      @Override
      public int compareTo(DeltaIndex o) {
        double diff = deltaMetric[i][j] - deltaMetric[o.i][o.j];
        if (diff < 0) return -1; 
        else if (diff > 0) return 1;
        else return 0;
      }
      
      
       
    };
    int[][] indices = new int[deltaMetric.length][];
    for (int i = 0; i < indices.length; i++) {      
      indices[i] = new int[deltaMetric[i].length];
      List<DeltaIndex> sortList = new ArrayList<DeltaIndex>(indices[i].length);
      for (int j = 0; j < indices[i].length; j++) {
        sortList.add(new DeltaIndex(i,j));
      }
      Collections.sort(sortList);
      for (int j = 0; j< indices[i].length; j++) {
        indices[i][j] = sortList.get(j).j;
      }
    }
    return indices;
  }

  public static String[] getWeightNamesFromCounter(Counter<String> wts) {
     List<String> names = new ArrayList<String>(wts.keySet());
     Collections.sort(names);
     return names.toArray(new String[0]);
  }
  
  public static Counter<String> getWeightCounterFromArray(String[] weightNames,
      double[] wtsArr) {
    Counter<String> wts = new ClassicCounter<String>();
    for (int i = 0; i < weightNames.length; i++) {
      wts.setCount(weightNames[i], wtsArr[i]);
    }
    return wts;
  }

  public static double[] getWeightArrayFromCounter(String[] weightNames,
      Counter<String> wts) {
    double[] wtsArr = new double[weightNames.length];
    for (int i = 0; i < wtsArr.length; i++) {
      wtsArr[i] = wts.getCount(weightNames[i]);
    }
    return wtsArr;
  }

  public static double norm2DoubleArray(double[] v) {
    double normSum = 0;
    for (double d : v) {
      normSum += d * d;
    }
    return Math.sqrt(normSum);
  }

  public static double sumSquareDoubleArray(double[] v) {
    double sum = 0;
    for (double d : v) {
      sum += d * d;
    }
    return sum;
  }

  public static double scoreTranslation(Counter<String> wts,
      ScoredFeaturizedTranslation<IString, String> trans) {
    double s = 0;
    for (FeatureValue<String> fv : trans.features) {
      s += fv.value * wts.getCount(fv.name);
    }
    return s;
  }

  public static double[] scoreTranslations(Counter<String> wts,
      List<ScoredFeaturizedTranslation<IString, String>> translations) {
    double[] scores = new double[translations.size()];
    for (int j = 0; j < scores.length; j++) {
      scores[j] = OptimizerUtils.scoreTranslation(wts, translations.get(j));
    }
    
    return scores;
  }

  public static double maxFromDoubleArray(double[] arr) {
    double max = Double.NEGATIVE_INFINITY;
    for (double d : arr) {
      if (d > max)
        max = d;
    }
    return max;
  }

  public static double softMaxFromDoubleArray(double[] arr) {
    double B = maxFromDoubleArray(arr);
    double sum = 0;
    for (double d : arr) {
      sum += Math.exp(d - B);
    }
    return B + Math.log(sum);
  }
  
  public static <T> Counter<T> featureValueCollectionToCounter(Collection<FeatureValue<T>> c) {
    Counter<T> counter = new ClassicCounter<T>();
    
    for (FeatureValue<T> fv : c) {
      counter.incrementCount(fv.name, fv.value);
    }
    
    return counter;
  }
  
  public static Set<String> featureWhiteList(FlatNBestList nbest, int minSegmentCount) {
    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestlists = nbest.nbestLists();
    Counter<String> featureSegmentCounts = new ClassicCounter<String>();
    for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestlists) {
        Set<String> segmentFeatureSet = new HashSet<String>();
        for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
           for (FeatureValue<String> feature : trans.features) {
             segmentFeatureSet.add(feature.name);
           }
        }
        for (String featureName : segmentFeatureSet) {
          featureSegmentCounts.incrementCount(featureName);
        }
    }
    return Counters.keysAbove(featureSegmentCounts, minSegmentCount -1);
  }
}