package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.mt.metrics.ter.TERalignment;
import edu.stanford.nlp.mt.metrics.ter.TERcalc;
import edu.stanford.nlp.math.ArrayMath;

import java.io.*;

/**
 * A standalone N-best reranker.<br>
 * Usage: <code>java edu.stanford.nlp.mt.reranker.NBest (datadescriptor)</code>
 * <h3>Data Descriptor</h3> The data descriptor should look like:<br>
 * <code>/u/nlp/data/gale/n-best-reranking/reranker/mt03/datadescriptor.txt</code>
 * <p>
 * Valid fields in the data descriptor:
 * <ul>
 * <li>LoadOffset: by default, the data number starts at 0. If there's an
 * offset, use "LoadOffset" to override it. For example:
 * <code>LoadOffset: 100</code>
 * <li>TrainRange: For example: <code>Trainrange: 0-89</code>. If LoadOffset was
 * set to 100, then the traning examples are actually 100 to 189
 * <li>DevRange: similar to TrainRange
 * <li>HypothesisScores: a gzipped file that has the hypothesis scores. The
 * format of the file is: sentId,hypId score. For example:<br>
 * 
 * <pre>
 * 100,0 0.25
 * 100,1 0.13
 * ...
 * </pre>
 * 
 * <li>FeatureSets: one or more gzipped files that have features for each
 * hypothesis.
 * <li>NBestList: this is optional. If provided, the Reranker can access the
 * original sentences, thus can output predicted sentences for later use.
 * </ul>
 * Note: HypothesisScores and FeatureSets can be generated from
 * {@link LegacyFeatureExtractor}.
 * <h3>Useful properties</h3>
 * <ul>
 * <li>-DfeatureIndex=featureIndex.txt
 * <li>-DpredictedIndices=predictedIndices.txt
 * <li>-Dpredicted=predictedSentences.txt : you have to specify "NBestList" to
 * output the predicted sentences.
 * </ul>
 * 
 * @author Pi-Chuan Chang
 * @author Dan Cer
 */

public class NBest {

  private static final TERcalc ter = new TERcalc();

  public static final String FEATURE_INDEX_FILENAME_PROP = "featureIndex";
  // null implies that, by default, no predicted indices file is generated
  public static final String DEFAULT_FEATURE_INDEX_FILENAME = null;

  public static final String PREDICTED_INDICES_FILENAME_PROP = "predictedIndices";
  public static final String DEFAULT_PREDICTED_INDICES_FILENAME = null;

  public static final String PREDICTED_FILENAME_PROP = "predicted";
  public static final String DEFAULT_PREDICTED_FILENAME = null;

  static public void outputPredictedIndices(
      AbstractOneOfManyClassifier classifier,
      MyList<CompactHypothesisList> lchl, int range[], PrintWriter pr)
      throws Exception {
    // makes the mapping
    int mapping[] = new int[lchl.size()];
    int ctr = 0;
    for (int i = range[0]; i <= range[1]; i++) {
      if (i >= range[2] && i <= range[3]) {
        continue;
      }
      if (ctr >= lchl.size()) {
        throw new RuntimeException("unmatched lchl and range\n");
      }
      mapping[ctr++] = i;
    }

    if (ctr != lchl.size()) {
      throw new RuntimeException("unmatched lchl and range\n");
    }

    int[] bestChoices = classifier.getBestPrediction(lchl);
    for (int i = 0; i < lchl.size(); i++) {
      int choice = bestChoices[i];
      pr.println(mapping[i] + "," + choice);
    }
  }

  static public void outputPredictedIndices(
      AbstractOneOfManyClassifier classifier, DataSet dataSet, String results)
      throws Exception {
    PrintWriter pr = new PrintWriter(new BufferedWriter(new FileWriter(results,
        false)));

    MyList<CompactHypothesisList> lchl = dataSet.getTrainingSet();
    int[] range = dataSet.getTrainRange();
    outputPredictedIndices(classifier, lchl, range, pr);
    lchl = dataSet.getDevSet();
    range = dataSet.getDevRange();
    outputPredictedIndices(classifier, lchl, range, pr);
    pr.close();
  }

  static public void outputPredictedSents(
      AbstractOneOfManyClassifier classifier, DataSet dataSet, String results)
      throws Exception {
    if (dataSet.nbests == null) {
      System.err
          .println("Warning: NBestList wasn't provided. Therefore predicted sentences cannot be output.");
      return;
    }

    PrintWriter pr = new PrintWriter(new BufferedWriter(new FileWriter(results,
        false)));
    MyList<CompactHypothesisList> lchl = dataSet.getTrainingSet();
    int[] range = dataSet.getTrainRange();
    outputPredictedSents(classifier, lchl, range, pr, dataSet);
    lchl = dataSet.getDevSet();
    range = dataSet.getDevRange();
    outputPredictedSents(classifier, lchl, range, pr, dataSet);
    pr.close();
  }

  static public void outputPredictedSents(
      AbstractOneOfManyClassifier classifier,
      MyList<CompactHypothesisList> lchl, int range[], PrintWriter pr,
      DataSet dataSet) throws Exception {
    // makes the mapping
    int mapping[] = new int[lchl.size()];
    int ctr = 0;
    for (int i = range[0]; i <= range[1]; i++) {
      if (i >= range[2] && i <= range[3]) {
        continue;
      }
      if (ctr >= lchl.size()) {
        throw new RuntimeException("unmatched lchl and range\n");
      }
      mapping[ctr++] = i;
    }

    if (ctr != lchl.size()) {
      throw new RuntimeException("unmatched lchl and range\n");
    }

    int[] bestChoices = classifier.getBestPrediction(lchl);
    for (int i = 0; i < lchl.size(); i++) {
      int choice = bestChoices[i];
      pr.println(dataSet.getFromNBest(mapping[i], choice));
    }
  }

  static double computeCorpusBleu(int[] indices, int offset, DataSet dataSet) {
    Scorer scorer = new Bleu();
    for (int i = 0; i < indices.length; i++) {
      System.err.println("DEBUG: calling dataSet.getFromNBest(" + i + "+"
          + offset + ", " + indices[i]);
      String[] sentence = dataSet.getFromNBest(i + offset, indices[i]).split(
          "\\s+");
      String[] refS = dataSet.getFromRefs(i + offset);
      String[][] refs = new String[refS.length][];
      for (int refI = 0; refI < refS.length; refI++) {
        refs[refI] = refS[refI].split("\\s+");
      }
      SegStats s = new SegStats(sentence, refs);
      scorer.add(s);
    }
    return scorer.score();
  }

  static double computeCorpusBleu(int[] indices, int[] range_exclude,
      DataSet dataSet) {
    // make the range first..
    int mapping[] = new int[indices.length];
    int ctr = 0;
    System.err.printf("DEBUG: %d %d %d %d\n", range_exclude[0],
        range_exclude[1], range_exclude[2], range_exclude[3]);
    System.err.println("DEBUG: indices.length=" + indices.length);
    for (int i = range_exclude[0]; i <= range_exclude[1]; i++) {
      if (i >= range_exclude[2] && i <= range_exclude[3]) {
        continue;
      }
      if (ctr >= indices.length) {
        throw new RuntimeException("unmatched indices and range_exclude\n");
      }
      mapping[ctr++] = i;
    }

    if (ctr != indices.length) {
      throw new RuntimeException("unmatched indices and range_exclude\n");
    }

    Scorer scorer = new Bleu();
    for (int i = 0; i < indices.length; i++) {
      String[] sentence = dataSet.getFromNBest(mapping[i], indices[i]).split(
          "\\s+");
      String[] refS = dataSet.getFromRefs(mapping[i]);
      String[][] refs = new String[refS.length][];
      for (int refI = 0; refI < refS.length; refI++) {
        refs[refI] = refS[refI].split("\\s+");
      }
      SegStats s = new SegStats(sentence, refs);
      scorer.add(s);
    }
    return scorer.score();
  }

  static double computeCorpusTER(int[] indices, int[] range_exclude,
      DataSet dataSet) {
    // make the range first..
    int mapping[] = new int[indices.length];
    int ctr = 0;
    for (int i = range_exclude[0]; i <= range_exclude[1]; i++) {
      if (i >= range_exclude[2] && i <= range_exclude[3]) {
        continue;
      }
      if (ctr >= indices.length) {
        throw new RuntimeException("unmatched indices and range_exclude\n");
      }
      mapping[ctr++] = i;
    }

    if (ctr != indices.length) {
      throw new RuntimeException("unmatched indices and range_exclude\n");
    }

    double sumEdits = 0.0;
    double sumLen = 0.0;

    for (int i = 0; i < indices.length; i++) {
      String sentence = dataSet.getFromNBest(mapping[i], indices[i]);
      String[] refS = dataSet.getFromRefs(mapping[i]);

      double minEdits = Double.MAX_VALUE;
      double sumlen = 0.0;
      for (int j = 0; j < refS.length; j++) {
        TERalignment result = ter.TER(sentence, refS[j]);
        // System.err.println(j+"\t"+refS[j]);
        minEdits = Math.min(result.numEdits, minEdits);
        sumlen += result.numWords;
      }
      double avglen = sumlen / refS.length;
      sumEdits += minEdits;
      sumLen += avglen;
    }
    return sumEdits / sumLen;
  }

  static double computeCorpusTER(int[] indices, int offset, DataSet dataSet) {
    double sumEdits = 0.0;
    double sumLen = 0.0;

    for (int i = 0; i < indices.length; i++) {
      String sentence = dataSet.getFromNBest(i + offset, indices[i]);
      String[] refS = dataSet.getFromRefs(i + offset);

      double minEdits = Double.MAX_VALUE;
      double sumlen = 0.0;
      for (int j = 0; j < refS.length; j++) {
        TERalignment result = ter.TER(sentence, refS[j]);
        // System.err.println(j+"\t"+refS[j]);
        minEdits = Math.min(result.numEdits, minEdits);
        sumlen += result.numWords;
      }
      double avglen = sumlen / refS.length;
      sumEdits += minEdits;
      sumLen += avglen;
    }
    return sumEdits / sumLen;
  }

  static public int[] getOraclePrediction(MyList<CompactHypothesisList> lchl) {
    int[] oraclePrediction = new int[lchl.size()];
    for (int i = 0; i < lchl.size(); i++) {
      double[] bleus = lchl.get(i).getScores();
      int bestBleu = ArrayMath.argmax(bleus);
      oraclePrediction[i] = bestBleu;
    }
    return oraclePrediction;
  }

  static public int[] getWorstPrediction(MyList<CompactHypothesisList> lchl) {
    int[] worstPrediction = new int[lchl.size()];
    for (int i = 0; i < lchl.size(); i++) {
      double[] bleus = lchl.get(i).getScores();
      int worstBleu = ArrayMath.argmin(bleus);
      worstPrediction[i] = worstBleu;
    }
    return worstPrediction;
  }

  static public void displayEvaluationStats(
      AbstractOneOfManyClassifier classifier,
      MyList<CompactHypothesisList> examples, int offset, DataSet dataSet) {

    double loglikelihood = 0;

    int[] bestChoices_tieLast = classifier.getBestPrediction(examples, true);
    int[] bestChoices_tieFirst = classifier.getBestPrediction(examples, false);
    int[] oracleChoices = getOraclePrediction(examples);
    int[] randomChoices = AbstractOneOfManyClassifier
        .getRandPrediction(examples);
    int[] systemChoices = new int[examples.size()];
    int[] worstChoices = getWorstPrediction(examples);

    loglikelihood = classifier.getLogLikelihood(examples);

    if (classifier instanceof MCLPairLearner) {
      MCLPairLearner mpl = (MCLPairLearner) classifier;
      int[] stats = mpl.getAccuracy(examples, offset, dataSet);
      System.out.println("Pairwise Stats:");
      System.out.println("Correct=" + stats[0]);
      System.out.println("All=" + stats[1]);
      System.out.println("Acc=" + (double) stats[0] / stats[1]);

    }

    double predictedBleu_tieLast = computeCorpusBleu(bestChoices_tieLast,
        offset, dataSet);
    double predictedTER_tieLast = computeCorpusTER(bestChoices_tieLast, offset,
        dataSet);
    double predictedCL_tieLast = classifier.getLogLikelihood(examples,
        bestChoices_tieLast);

    double predictedBleu_tieFirst = computeCorpusBleu(bestChoices_tieFirst,
        offset, dataSet);
    double predictedTER_tieFirst = computeCorpusTER(bestChoices_tieFirst,
        offset, dataSet);
    double predictedCL_tieFirst = classifier.getLogLikelihood(examples,
        bestChoices_tieFirst);

    double oracleBleu = computeCorpusBleu(oracleChoices, offset, dataSet);
    double oracleTER = computeCorpusTER(oracleChoices, offset, dataSet);
    double oracleCL = classifier.getLogLikelihood(examples, oracleChoices);

    double worstBleu = computeCorpusBleu(worstChoices, offset, dataSet);
    double worstTER = computeCorpusTER(worstChoices, offset, dataSet);
    double worstCL = classifier.getLogLikelihood(examples, worstChoices);

    double randomBleu = computeCorpusBleu(randomChoices, offset, dataSet);
    double randomTER = computeCorpusTER(randomChoices, offset, dataSet);
    double randomCL = classifier.getLogLikelihood(examples, randomChoices);

    double systemBleu = computeCorpusBleu(systemChoices, offset, dataSet);
    double systemTER = computeCorpusTER(systemChoices, offset, dataSet);
    double systemCL = classifier.getLogLikelihood(examples, systemChoices);
    for (int i = 0; i < systemChoices.length; i++) {
      System.err.println(i + "," + oracleChoices[i]);
    }

    System.out.printf("\tConditional Log Likelihood: %.4f\n", loglikelihood);
    System.out
        .printf(
            "\tPredicted Sentences corpus BLEU score (if tied, choose first): %.4f\n",
            predictedBleu_tieFirst);
    System.out
        .printf(
            "\tPredicted Sentences corpus TER score (if tied, choose first): %.4f\n",
            predictedTER_tieFirst);
    System.out.printf(
        "\tPredicted Sentences CL (if tied, choose first): %.4f\n",
        predictedCL_tieFirst);

    System.out
        .printf(
            "\tPredicted Sentences corpus BLEU score (if tied, choose last) : %.4f\n",
            predictedBleu_tieLast);
    System.out
        .printf(
            "\tPredicted Sentences corpus TER score (if tied, choose last) : %.4f\n",
            predictedTER_tieLast);
    System.out.printf(
        "\tPredicted Sentences CL (if tied, choose last) : %.4f\n",
        predictedCL_tieLast);

    System.out
        .printf(
            "\tOracle corpus BLEU score (choosing the best sentences by the scores provided): %.4f\n",
            oracleBleu);
    System.out
        .printf(
            "\tOracle corpus TER score (choosing the best sentences by the scores provided): %.4f\n",
            oracleTER);
    System.out
        .printf(
            "\tOracle CL (choosing the best sentences by the scores provided): %.4f\n",
            oracleCL);

    System.out
        .printf(
            "\tWorst corpus BLEU score (choosing the worst sentences by the scores provided): %.4f\n",
            worstBleu);
    System.out
        .printf(
            "\tWorst corpus TER score (choosing the worst sentences by the scores provided): %.4f\n",
            worstTER);
    System.out
        .printf(
            "\tWorst CL (choosing the worst sentences by the scores provided): %.4f\n",
            worstCL);

    System.out.printf("\tSystem selected Corpus BLEU score : %.4f\n",
        systemBleu);
    System.out.printf("\tSystem selected Corpus TER score : %.4f\n", systemTER);
    System.out.printf("\tSystem selected CL : %.4f\n", systemCL);

    System.out.printf("\tRandomly selected Corpus BLEU score : %.4f\n",
        randomBleu);
    System.out.printf("\tRandomly selected Corpus TER score : %.4f\n",
        randomTER);
    System.out.printf("\tRandomly selected CL : %.4f\n", randomCL);
  }

  static public void displayEvaluationStats(
      AbstractOneOfManyClassifier classifier,
      MyList<CompactHypothesisList> examples, int[] range, DataSet dataSet) {

    double loglikelihood = 0;

    int[] bestChoices_tieLast = classifier.getBestPrediction(examples, true);
    int[] bestChoices_tieFirst = classifier.getBestPrediction(examples, false);
    int[] oracleChoices = getOraclePrediction(examples);
    int[] randomChoices = AbstractOneOfManyClassifier
        .getRandPrediction(examples);
    int[] systemChoices = new int[examples.size()];
    int[] worstChoices = getWorstPrediction(examples);

    loglikelihood = classifier.getLogLikelihood(examples);
    /*
     * if (classifier instanceof MCLPairLearner) { MCLPairLearner mpl =
     * (MCLPairLearner)classifier; int[] stats = mpl.getAccuracy(examples,
     * range, dataSet); System.out.println("Pairwise Stats:");
     * System.out.println("Correct="+stats[0]);
     * System.out.println("All="+stats[1]);
     * System.out.println("Acc="+(double)stats[0]/stats[1]);
     * 
     * }
     */

    double predictedBleu_tieLast = computeCorpusBleu(bestChoices_tieLast,
        range, dataSet);
    double predictedTER_tieLast = computeCorpusTER(bestChoices_tieLast, range,
        dataSet);
    double predictedCL_tieLast = classifier.getLogLikelihood(examples,
        bestChoices_tieLast);

    double predictedBleu_tieFirst = computeCorpusBleu(bestChoices_tieFirst,
        range, dataSet);
    double predictedTER_tieFirst = computeCorpusTER(bestChoices_tieFirst,
        range, dataSet);
    double predictedCL_tieFirst = classifier.getLogLikelihood(examples,
        bestChoices_tieFirst);

    double oracleBleu = computeCorpusBleu(oracleChoices, range, dataSet);
    double oracleTER = computeCorpusTER(oracleChoices, range, dataSet);
    double oracleCL = classifier.getLogLikelihood(examples, oracleChoices);

    double worstBleu = computeCorpusBleu(worstChoices, range, dataSet);
    double worstTER = computeCorpusTER(worstChoices, range, dataSet);
    double worstCL = classifier.getLogLikelihood(examples, worstChoices);

    double randomBleu = computeCorpusBleu(randomChoices, range, dataSet);
    double randomTER = computeCorpusTER(randomChoices, range, dataSet);
    double randomCL = classifier.getLogLikelihood(examples, randomChoices);

    double systemBleu = computeCorpusBleu(systemChoices, range, dataSet);
    double systemTER = computeCorpusTER(systemChoices, range, dataSet);
    double systemCL = classifier.getLogLikelihood(examples, systemChoices);
    for (int i = 0; i < systemChoices.length; i++) {
      System.err.println(i + "," + oracleChoices[i]);
    }

    System.out.printf("\tConditional Log Likelihood: %.4f\n", loglikelihood);
    System.out
        .printf(
            "\tPredicted Sentences corpus BLEU score (if tied, choose first): %.4f\n",
            predictedBleu_tieFirst);
    System.out
        .printf(
            "\tPredicted Sentences corpus TER score (if tied, choose first): %.4f\n",
            predictedTER_tieFirst);
    System.out.printf(
        "\tPredicted Sentences CL (if tied, choose first): %.4f\n",
        predictedCL_tieFirst);

    System.out
        .printf(
            "\tPredicted Sentences corpus BLEU score (if tied, choose last) : %.4f\n",
            predictedBleu_tieLast);
    System.out
        .printf(
            "\tPredicted Sentences corpus TER score (if tied, choose last) : %.4f\n",
            predictedTER_tieLast);
    System.out.printf(
        "\tPredicted Sentences CL (if tied, choose last) : %.4f\n",
        predictedCL_tieLast);

    System.out
        .printf(
            "\tOracle corpus BLEU score (choosing the best sentences by the scores provided): %.4f\n",
            oracleBleu);
    System.out
        .printf(
            "\tOracle corpus TER score (choosing the best sentences by the scores provided): %.4f\n",
            oracleTER);
    System.out
        .printf(
            "\tOracle CL (choosing the best sentences by the scores provided): %.4f\n",
            oracleCL);

    System.out
        .printf(
            "\tWorst corpus BLEU score (choosing the worst sentences by the scores provided): %.4f\n",
            worstBleu);
    System.out
        .printf(
            "\tWorst corpus TER score (choosing the worst sentences by the scores provided): %.4f\n",
            worstTER);
    System.out
        .printf(
            "\tWorst CL (choosing the worst sentences by the scores provided): %.4f\n",
            worstCL);

    System.out.printf("\tSystem selected Corpus BLEU score : %.4f\n",
        systemBleu);
    System.out.printf("\tSystem selected Corpus TER score : %.4f\n", systemTER);
    System.out.printf("\tSystem selected CL : %.4f\n", systemCL);

    System.out.printf("\tRandomly selected Corpus BLEU score : %.4f\n",
        randomBleu);
    System.out.printf("\tRandomly selected Corpus TER score : %.4f\n",
        randomTER);
    System.out.printf("\tRandomly selected CL : %.4f\n", randomCL);
  }

  static class myFilenameFilter implements FilenameFilter {
    String pat;

    myFilenameFilter(String pat) {
      this.pat = pat;
    }

    public boolean accept(File dir, String name) {
      return (name.endsWith("." + pat) || name.matches(pat));
    }
  }

  public static void displayUsage() {
    System.err
        .println("Usage:\n\tjava edu.stanford.nlp.mt.reranker.NBest (data descriptor)");
    System.err
        .println("\nExample Descriptors:\n\n"
            + "* Heiro n-best:\n"
            + "  /u/nlp/data/gale/scr/n-best-reranking/reranker/datadescriptor.txt\n");
  }

  /**
   * @param args
   *          first argument: datadescriptor
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      displayUsage();
      System.exit(-1);
    }

    DataSet dataSet = DataSet.load(args[0]);

    MyList<CompactHypothesisList> trainSet = dataSet.getTrainingSet();
    int[] range = dataSet.getTrainRange();
    AbstractOneOfManyClassifier classifier = AbstractOneOfManyClassifier
        .factory();
    System.err.println("Training classifier on train range: " + range[0] + "-"
        + range[1] + ", excluding the dev range " + range[2] + "-" + range[3]
        + " if overlapped.");
    System.err.println("trainSet.size=" + trainSet.size());
    classifier.learn(trainSet);
    // classifier.learn(dataSet);

    // Wrap all non-loglinear models using LogLinearProbabilisticWrapper
    if (!classifier.isLogLinear()) {
      System.out.printf("Wrapping %s using LogLinearProbalisticWrapper",
          classifier.getClass().getName());
      double origLL = classifier.getLogLikelihood(dataSet.getTrainingSet());
      classifier = new LogLinearProbabilisticWrapper(classifier);
      // technically this should be on a dev set
      classifier.learn(trainSet);
      System.out.println("Done.");
      double newLL = classifier.getLogLikelihood(dataSet.getTrainingSet());
      System.out
          .printf(
              "Log Likelihood - Original: %f Trained: %f  %% Improvement: %.2f %%\n",
              origLL, newLL, 100 * (origLL - newLL) / origLL);
    }

    // If appropriate, write out feature/feature-weight pairs
    // sorted by feature-weight magnitude
    String featureIndexFn = System.getProperty(FEATURE_INDEX_FILENAME_PROP,
        DEFAULT_FEATURE_INDEX_FILENAME);
    if (featureIndexFn != null) {
      classifier.displayWeights(featureIndexFn, true);
    }

    System.out.printf("\nTraining Stats:\n");
    int[] trainRange = dataSet.getTrainRange();
    System.out.printf("(range: %d-%d, excluding %d-%d if overlapped)\n",
        trainRange[0], trainRange[1], trainRange[2], trainRange[3]);
    System.err.println("DEBUG INFO: test on train");
    if (dataSet.nbests == null || dataSet.refs == null) {
      System.out
          .println("Warning: NBestList and/or RefList are not present. Therefore corpus BLEU cannot be evaluted.");
    } else {
      // displayEvaluationStats(classifier, dataSet.getTrainingSet(),
      // dataSet.getOffset()+trainRange[0], dataSet);
      displayEvaluationStats(classifier, dataSet.getTrainingSet(),
          dataSet.getTrainRange(), dataSet);
    }

    System.out.printf("Dev Stats:\n");
    int[] devRange = dataSet.getDevRange();
    System.out.printf("(range: %d-%d)\n", devRange[0], devRange[1]);
    System.err.println("DEBUG INFO: test on dev");
    if (dataSet.nbests == null || dataSet.refs == null) {
      System.out
          .println("Warning: NBestList and/or RefList are not present. Therefore corpus BLEU cannot be evaluted.");
    } else {
      displayEvaluationStats(classifier, dataSet.getDevSet(),
          dataSet.getDevRange(), dataSet);
    }

    // output results for evaluation...
    String predictedIndicesFn = System.getProperty(
        PREDICTED_INDICES_FILENAME_PROP, DEFAULT_PREDICTED_INDICES_FILENAME);
    if (predictedIndicesFn != null) {
      outputPredictedIndices(classifier, dataSet, predictedIndicesFn);
      // outputPredictedIndices(classifier, dataSet.getDevSet(),
      // predictedIndicesFn);
    }

    // output results for evaluation...
    String predictedFn = System.getProperty(PREDICTED_FILENAME_PROP,
        DEFAULT_PREDICTED_FILENAME);
    if (predictedFn != null) {
      outputPredictedSents(classifier, dataSet, predictedFn);
    }
  }
}
