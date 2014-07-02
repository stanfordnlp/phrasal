package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.CorpusLevelMetricFactory;
import edu.stanford.nlp.mt.util.FlatNBestList;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Minimum Bayes Risk decoding.
 *
 * @author danielcer
 *
 */
public class MinimumBayesRisk {

  public static final boolean VERBOSE = false;

  private static final double DEFAULT_SCALE = 0.1;
  private static final String DEFAULT_METRIC = "bleu";

  private static String usage() {
    String nl = System.getProperty("line.separator");
    StringBuilder sb = new StringBuilder();
    sb.append("Usage: java ").append(MinimumBayesRisk.class.getName()).append(" n-best-list > re-ranked-n-best").append(nl)
    .append(nl)
    .append(" Options:").append(nl)
    .append("   -o str     : Orientation of the scores [risk|utility] (default: utility)").append(nl)
    .append("   -m str     : Metric (default: ").append(DEFAULT_METRIC).append(")").append(nl)
    .append("   -s num     : Scale parameter (default: ").append(DEFAULT_SCALE).append(")").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = Generics.newHashMap();
    argDefs.put("o", 1);
    argDefs.put("m", 1);
    argDefs.put("s", 1);
    return argDefs;
  }

  /**
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.print(usage());
      System.exit(-1);
    }

    Properties options = StringUtils.argsToProperties(args, argDefs());
    final double scale = PropertiesUtils.getDouble(options, "s", DEFAULT_SCALE);
    final String orientation = options.getProperty("o", "utility");
    final boolean risk = "risk".equals(orientation);
    final String metricName = options.getProperty("m", DEFAULT_METRIC);

    final String filename = options.getProperty("");
    System.err.print("Loading n-best list...");
    FlatNBestList nbestlists = new FlatNBestList(filename);
    System.err.println("done!");
    System.err.println("Decoding...");
    int idx = -1; 
    for (List<ScoredFeaturizedTranslation<IString,String>> nbestlist :
      nbestlists.nbestLists()) { idx++;
      double[] nbestScores = new double[nbestlist.size()];

      for (ScoredFeaturizedTranslation<IString,String> refTrans : nbestlist) 
      { 
        @SuppressWarnings("unchecked")
        List<List<Sequence<IString>>> fakeRef = Arrays.asList(
            Arrays.asList(refTrans.translation));
        EvaluationMetric<IString,String> metric =
            CorpusLevelMetricFactory.newMetric(metricName,fakeRef);

        int hypI = -1;
        for (ScoredFeaturizedTranslation<IString,String> hyp : nbestlist) 
        { hypI++;
        @SuppressWarnings("unchecked")
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
        System.out.printf("%d ||| %s ||| %e%n", idx, 
            entry.second().translation, entry.first());
      }
    }
  }
}
