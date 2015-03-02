package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.CorpusLevelMetricFactory;
import edu.stanford.nlp.mt.util.BasicNBestList;
import edu.stanford.nlp.mt.util.BasicNBestEntry;
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
    Map<String,Integer> argDefs = new HashMap<>();
    argDefs.put("o", 1);
    argDefs.put("m", 1);
    argDefs.put("s", 1);
    return argDefs;
  }

  private static class Processor implements ThreadsafeProcessor<List<BasicNBestEntry>, List<Pair<Double, String>>> {
    private final String metricName;
    private final boolean risk;
    private final double scale;

    Processor(String in_metricName, boolean in_risk, double in_scale) {
      metricName = in_metricName;
      risk = in_risk;
      scale = in_scale;
    }

    // Class is threadsafe for concurrent calls.
    public ThreadsafeProcessor<List<BasicNBestEntry>, List<Pair<Double, String>>> newInstance() {
      return this;
    }

    public List<Pair<Double, String>> process(List<BasicNBestEntry> nbestlist) {
      double[] nbestScores = new double[nbestlist.size()];

      for (BasicNBestEntry refTrans : nbestlist) 
      { 
        @SuppressWarnings("unchecked")
        List<List<Sequence<IString>>> fakeRef = Arrays.asList(
            Arrays.asList(refTrans.getTokens()));
        EvaluationMetric<IString,String> metric =
            CorpusLevelMetricFactory.newMetric(metricName,fakeRef);

        int hypI = -1;
        for (BasicNBestEntry hyp : nbestlist) 
        { hypI++;
        @SuppressWarnings("unchecked")
        double metricScore = metric.scoreSeq(Arrays.asList(hyp.getTokens())); 

        double fracHypScore = metricScore * Math.exp(scale*refTrans.getScore());
        nbestScores[hypI] += fracHypScore; 
        if (VERBOSE) {
          System.err.printf("hyp(%d): %s\n", hypI, hyp);
          System.err.printf("scale: %f\n", scale);
          System.err.printf("score: %f\n", hyp.getScore());
          System.err.printf("metricScore: %f\n", metricScore);
          System.err.printf("fracHypScore: %f\n", fracHypScore);
          System.err.printf("nbestScores[%d]: %f\n", hypI, nbestScores[hypI]);
        }
        }
      }
      int hypI = -1;
      List<Pair<Double,String>>
      rescoredNBestList = new ArrayList<Pair<Double,String>>(nbestlist.size());
      for (BasicNBestEntry hyp : nbestlist) {
        hypI++;
        rescoredNBestList.add(new Pair<Double,String>(nbestScores[hypI], hyp.getLine()));
      }
      Collections.sort(rescoredNBestList);
      if (!risk) {
        Collections.reverse(rescoredNBestList);
      }
      return rescoredNBestList;
    }
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
    BasicNBestList nbestlists = new BasicNBestList(filename);
    MulticoreWrapper<List<BasicNBestEntry>, List<Pair<Double, String>>> wrapper = 
      new MulticoreWrapper<List<BasicNBestEntry>, List<Pair<Double, String>>>(0, new Processor(metricName, risk, scale), true);
    for (List<BasicNBestEntry> nbestlist : nbestlists) {
      wrapper.put(nbestlist);
      while (wrapper.peek()) {
        DumpRescored(wrapper.poll());
      }
    }
    wrapper.join();
    while (wrapper.peek()) {
      DumpRescored(wrapper.poll());
    }
  }

  private static void DumpRescored(List<Pair<Double, String>> rescoredNBestList) {
    for (Pair<Double,String> entry : rescoredNBestList) {
      System.out.println(entry.second());
    }
  }
}
