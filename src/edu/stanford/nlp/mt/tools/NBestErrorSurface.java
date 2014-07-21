package edu.stanford.nlp.mt.tools;

import java.io.PrintStream;
import java.util.List;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.mt.util.FlatNBestList;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.CorpusLevelMetricFactory;
import edu.stanford.nlp.mt.metrics.MetricUtils;

/**
 * 
 * @author danielcer
 * 
 */
public class NBestErrorSurface {
  static public final int gridSize = 25;

  static public void main(String[] args) throws Exception {
    if (args.length != 7) {
      System.err
          .println("Usage:\n\tjava ...NBestErrorSurface (eval metric) (refs) "
              + "(n-best) (weights) (feature1|||min,max) (feature2|||min,max) "
              + "(out prefix)\n");
      System.exit(-1);
    }
    String evalMetricFn = args[0];
    String refsFn = args[1];
    String nbestFn = args[2];
    String weightsFn = args[3];
    String feature1Field = args[4];
    String feature2Field = args[5];
    String outPrefix = args[6];

    Index<String> featureIndex = new HashIndex<String>();

    List<List<Sequence<IString>>> references = MetricUtils.readReferences(IOTools.fileNamesFromPathPrefix(refsFn));
    EvaluationMetric<IString, String> eval = CorpusLevelMetricFactory.newMetric(evalMetricFn, references);
    
    FlatNBestList nbest = new FlatNBestList(nbestFn, featureIndex);
    Counter<String> wts = IOTools.readWeights(weightsFn, featureIndex);
    String feature1Name = feature1Field.split("\\|\\|\\|")[0];
    String feature2Name = feature2Field.split("\\|\\|\\|")[0];
    double feature1Min = Double.parseDouble(feature1Field.split("\\|\\|\\|")[1]
        .split(",")[0]);
    double feature1Max = Double.parseDouble(feature1Field.split("\\|\\|\\|")[1]
        .split(",")[1]);
    double feature2Min = Double.parseDouble(feature2Field.split("\\|\\|\\|")[1]
        .split(",")[0]);
    double feature2Max = Double.parseDouble(feature2Field.split("\\|\\|\\|")[1]
        .split(",")[1]);
    System.err.println("Weights\n==================");
    System.err.println(wts);
    System.err.println();
    System.err.printf("feature1: %s min: %f max: %f\n", feature1Name,
        feature1Min, feature1Max);
    System.err.printf("feature2: %s min: %f max: %f\n", feature2Name,
        feature2Min, feature2Max);
    System.err.println();
    System.err.println("Generating Grid\n");

    PrintStream pstrm;

    // generate axis file .f1.dat
    pstrm = new PrintStream(outPrefix + ".f1.dat");
    double deltaf1 = (feature1Max - feature1Min) / gridSize;
    for (int i = 0; i < gridSize; i++) {
      pstrm.println(feature1Min + i * deltaf1);
    }
    pstrm.close();

    // generate axis file .f2.dat
    pstrm = new PrintStream(outPrefix + ".f2.dat");
    double deltaf2 = (feature2Max - feature2Min) / gridSize;
    for (int i = 0; i < gridSize; i++) {
      pstrm.println(feature2Min + i * deltaf2);
    }
    pstrm.close();

    // generate X file .Z.dat
    pstrm = new PrintStream(outPrefix + ".Z.dat");
    for (int i = 0; i < gridSize; i++) {
      double f1Val = feature1Min + i * deltaf1;
      wts.setCount(feature1Name, f1Val);
      System.err.print(".");
      for (int j = 0; j < gridSize; j++) {
        double f2Val = feature2Min + j * deltaf2;
        wts.setCount(feature2Name, f2Val);
        List<ScoredFeaturizedTranslation<IString, String>> trans = MERT
            .transArgmax(nbest, wts);
        double e = eval.score(trans);
        pstrm.print(e);
        if (j + 1 < gridSize)
          pstrm.print(" ");
      }
      pstrm.println();
    }
    pstrm.close();
  }
}
