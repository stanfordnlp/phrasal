package edu.stanford.nlp.mt.tools;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import edu.stanford.nlp.mt.metrics.MetricUtils;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.util.Pair;

/**
 * Compare two system outputs (baseline and contrastive), and display sentences
 * with biggest BLEU score difference.
 * 
 * @author Michel Galley
 */
public class BLEUSorter {

  static double minDelta;
  static int minLength, maxLength;

  static public void main(String args[]) throws IOException {
    if (args.length != 7) {
      System.err
          .println("Usage:\n\tjava BLEUSorter (ref prefix) (hyp 1) (hyp 2) (info) (minDelta) (minLength) (maxLength) (info)\n");
      System.exit(-1);
    }

    List<List<Sequence<IString>>> referencesList = MetricUtils
        .readReferencesFromRoot(args[0]);

    BLEUMetric<IString, String> bleu1 = new BLEUMetric<IString, String>(
        referencesList, true);
    BLEUMetric<IString, String> bleu2 = new BLEUMetric<IString, String>(
        referencesList, true);
    BLEUMetric<IString, String>.BLEUIncrementalMetric incMetric1 = bleu1
        .getIncrementalMetric();
    BLEUMetric<IString, String>.BLEUIncrementalMetric incMetric2 = bleu2
        .getIncrementalMetric();

    List<Sequence<IString>> hyps1 = readHypotheses(args[1], incMetric1);
    List<Sequence<IString>> hyps2 = readHypotheses(args[2], incMetric2);
    List<Sequence<IString>> info = readInfo(args[3]);

    minDelta = Double.parseDouble(args[4]);
    minLength = Integer.parseInt(args[5]);
    maxLength = Integer.parseInt(args[6]);

    List<Integer> sentIds = sortSentencesByScore(hyps1, hyps2, incMetric1,
        incMetric2);

    for (int sentId : sentIds) {
      double score1 = 100 * incMetric1.computeLocalSmoothScore(
          hyps1.get(sentId), sentId);
      double score2 = 100 * incMetric2.computeLocalSmoothScore(
          hyps2.get(sentId), sentId);
      int sz1 = hyps1.get(sentId).size();
      int sz2 = hyps2.get(sentId).size();
      int avgSz = (sz1 + sz2) / 2;
      if (avgSz >= minLength && avgSz <= maxLength)
        System.out.printf("%d\t%.3f\t%.3f\t(%d)\t%.3f\t(%d)\t%s\n", sentId,
            score2 - score1, score1, sz1, score2, sz2, info.get(sentId));
    }
  }

  static List<Integer> sortSentencesByScore(List<Sequence<IString>> hyps1,
      List<Sequence<IString>> hyps2,
      BLEUMetric<IString, String>.BLEUIncrementalMetric incMetric1,
      BLEUMetric<IString, String>.BLEUIncrementalMetric incMetric2) {
    List<Pair<Double, Integer>> scores = new ArrayList<Pair<Double, Integer>>();

    for (int sentId = 0; sentId < hyps1.size(); ++sentId) {
      double score1 = incMetric1.computeLocalSmoothScore(hyps1.get(sentId),
          sentId);
      double score2 = incMetric2.computeLocalSmoothScore(hyps2.get(sentId),
          sentId);
      boolean add;
      if (score1 == 0.0) {
        add = (score2 > 0.0);
      } else {
        add = Math.abs(score2 / score1 - 1.0) > minDelta;
      }
      if (add)
        // Only print sentence if difference is significant enough:
        scores.add(new Pair<Double, Integer>(score2 - score1, sentId));
    }
    Collections.sort(scores, (el1, el2) -> el1.first().compareTo(el2.first()));
    List<Integer> sentIds = new ArrayList<Integer>();
    for (Pair<Double, Integer> el : scores) {
      sentIds.add(el.second());
    }
    return sentIds;
  }

  static List<Sequence<IString>> readHypotheses(String fileName,
      BLEUMetric<IString, String>.BLEUIncrementalMetric incMetric)
      throws IOException {
    LineNumberReader reader = new LineNumberReader(new FileReader(fileName));
    List<Sequence<IString>> hyps = new ArrayList<Sequence<IString>>();
    for (String line; (line = reader.readLine()) != null;) {
      Sequence<IString> translation = IStrings.tokenize(line);
      hyps.add(translation);
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
          translation, null, 0);
      incMetric.add(tran);
    }
    reader.close();
    return hyps;
  }

  static List<Sequence<IString>> readInfo(String fileName) throws IOException {
    LineNumberReader reader = new LineNumberReader(new FileReader(fileName));
    List<Sequence<IString>> info = new ArrayList<Sequence<IString>>();
    for (String line; (line = reader.readLine()) != null;) {
      Sequence<IString> translation = new ArraySequence<IString>(
          IStrings.toIStringArray(line.split("\\s+")));
      info.add(translation);
    }
    reader.close();
    return info;
  }
}
