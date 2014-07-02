package edu.stanford.nlp.mt.tools;

import java.util.List;

import edu.stanford.nlp.mt.decoder.util.DenseScorer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.CorpusLevelMetricFactory;
import edu.stanford.nlp.mt.metrics.Metrics;
import edu.stanford.nlp.mt.metrics.ScorerWrapperEvaluationMetric;
import edu.stanford.nlp.mt.tune.GreedyMultiTranslationMetricMax;
import edu.stanford.nlp.mt.util.FlatNBestList;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * NBestArgmax utility - a command line tool for extracting the argmax
 * translations from a n-best list for a given set of weights.
 * 
 * The argmax translations can be optionally scored using a specified evaluation
 * metric and set of reference translations.
 * 
 * @author danielcer
 * 
 */
public class NBestArgmax {

  static public void usage() {
    System.err
        .printf("Usage:\n\tjava edu.stanford.nlp.mt.tool.NBestArgmax (nbest list) (wts file) [evaluationMetric:refs] > argmaxTrans 2> evaluationScore");
  }

  static public void main(String[] args) throws Exception {

    if (args.length != 2 && args.length != 3) {
      usage();
      System.exit(-1);
    }

    String nbestFilename = args[0];
    String wtsFilename = args[1];
    String evalArg = args.length == 3 ? args[2] : null;

    Scorer<String> wts = new DenseScorer(wtsFilename);
    FlatNBestList nbestlists = new FlatNBestList(nbestFilename);

    EvaluationMetric<IString, String> eval = null;

    if (evalArg != null) {
      String[] fields = evalArg.split(":");
      List<List<Sequence<IString>>> references = Metrics.readReferences(IOTools.fileNamesFromPathPrefix(fields[1]));
      eval = CorpusLevelMetricFactory.newMetric(fields[0], references);
    }
    GreedyMultiTranslationMetricMax<IString, String> argmaxByScore = new GreedyMultiTranslationMetricMax<IString, String>(
        new ScorerWrapperEvaluationMetric<IString, String>(wts));

    List<ScoredFeaturizedTranslation<IString, String>> argmaxTrans = argmaxByScore
        .maximize(nbestlists);

    for (ScoredFeaturizedTranslation<IString, String> trans : argmaxTrans) {
      System.out.println(trans);
      // System.out.println(trans.translation);
    }

    if (eval != null)
      System.err.printf("Eval score: %f\n", eval.score(argmaxTrans));
  }
}
