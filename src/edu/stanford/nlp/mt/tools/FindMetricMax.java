package edu.stanford.nlp.mt.tools;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.metrics.*;
import edu.stanford.nlp.mt.tune.*;
import edu.stanford.nlp.mt.util.FlatNBestList;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.ListTopMultiTranslationMetricMax;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;

/**
 * 
 * @author danielcer
 * 
 */
public class FindMetricMax {
  static public String BLEU_METRIC_OPT = "bleu";
  static public String GREEDY_SEARCH_OPT = "greedy";
  static public String TOP_LIST_OPT = "toplist";
  static public String HILLCLIMBING_OPT = "hillclimbing";
  static public String AGENDA_OPT = "agenda";
  static public String BEAM_OPT = "beam";

  /**
	 * 
	 */
  private FindMetricMax() {
  }

  /**
	 * 
	 */
  static public void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err
          .printf("Usage:\n\tjava ...FindMetricMax (metric) (search algorithm) (n-best list)\n");

      System.err.printf("\nSupported Metrics:\n");
      System.err.printf("\t%s:ref0,ref1,ref2...\n", BLEU_METRIC_OPT);

      System.err.printf("\nSupported Search Algorithms:\n");
      System.err.printf("\t%s\n", GREEDY_SEARCH_OPT);
      System.err.printf("\t%s\n", TOP_LIST_OPT);
      System.err.printf("\t%s\n", HILLCLIMBING_OPT);
      System.exit(-1);
    }

    String metricStr = args[0];
    String searchAlgorithmStr = args[1];
    String nbestListFilename = args[2];

    EvaluationMetric<IString, String> metric = null;

    if (metricStr.startsWith(BLEU_METRIC_OPT + ":")) {
      String[] fileNames = metricStr.split(":")[1].split(",");
      metric = new BLEUMetric<IString, String>(
          Metrics.readReferences(fileNames));
    } else {
      throw new RuntimeException(String.format("Unrecognized metric: '%s'",
          metric));
    }

    MultiTranslationMetricMax<IString, String> searchAlgorithm;

    if (AGENDA_OPT.equals(searchAlgorithmStr)) {
      searchAlgorithm = new AgendaMultiTranslationMetricMax<IString, String>(
          metric);
    } else if (BEAM_OPT.equals(searchAlgorithmStr)
        || searchAlgorithmStr.matches(BEAM_OPT + ":.*")) {
      String[] fields = searchAlgorithmStr.split(":");
      if (fields.length == 1) {
        searchAlgorithm = new BeamMultiTranslationMetricMax<IString, String>(
            metric);
      } else if (fields.length == 2) {
        int beamSize = Integer.parseInt(fields[1]);
        searchAlgorithm = new BeamMultiTranslationMetricMax<IString, String>(
            metric, beamSize);
      } else {
        throw new RuntimeException(String.format(
            "%s search only accepts one parameter.", BEAM_OPT));
      }
    } else if (HILLCLIMBING_OPT.equals(searchAlgorithmStr)) {
      searchAlgorithm = new HillClimbingMultiTranslationMetricMax<IString, String>(
          metric);
    } else if (GREEDY_SEARCH_OPT.equals(searchAlgorithmStr)) {
      searchAlgorithm = new GreedyMultiTranslationMetricMax<IString, String>(
          metric);
    } else if (TOP_LIST_OPT.equals(searchAlgorithmStr)) {
      searchAlgorithm = new ListTopMultiTranslationMetricMax<IString, String>();
    } else {
      throw new RuntimeException(String.format(
          "Unrecognized search algorithm: '%s'", searchAlgorithmStr));
    }

    NBestListContainer<IString, String> nbestLists = new FlatNBestList(
        nbestListFilename);
    List<ScoredFeaturizedTranslation<IString, String>> maxFeaturizedTranslations = searchAlgorithm
        .maximize(nbestLists);
    for (ScoredFeaturizedTranslation<IString, String> featurizedTranslation : maxFeaturizedTranslations) {
      System.out.println(featurizedTranslation.translation);
    }

    double score = metric.score(maxFeaturizedTranslations);
    System.err.printf("Best score: %.3f (x100: %.3f)\n", score, score * 100.0);
  }
}
