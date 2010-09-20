package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.*;

import java.util.*;

import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;

public class SyntacticPhraseReorderingFeatureExtractor extends
    AbstractFeatureExtractor {

  private int[][] min_E_for_F_range, max_E_for_F_range;
  private int fsize;

  private static final boolean DEBUG = true;

  public IntPair englishRangeForForeignRange(int f1, int f2) {
    int e1 = Integer.MAX_VALUE, e2 = -1;

    f1 = (f1 < 0) ? 0 : f1;
    f2 = (f2 < 0) ? 0 : f2;
    f1 = (f1 >= fsize) ? fsize - 1 : f1;
    f2 = (f2 >= fsize) ? fsize - 1 : f2;

    for (int fp1 = f1; fp1 <= f2; fp1++) {
      for (int fp2 = fp1; fp2 <= f2; fp2++) {
        if (min_E_for_F_range[fp1][fp2] < e1) {
          e1 = min_E_for_F_range[fp1][fp2];
        }
        if (max_E_for_F_range[fp1][fp2] > e2) {
          e2 = max_E_for_F_range[fp1][fp2];
        }
      }
    }

    return new IntPair(e1, e2);
  }

  @Override
  public boolean needAlGrid() {
    return true;
  }

  private void collectStatsFromGrid(AlignmentGrid alGrid) {
    fsize = alGrid.fsize();
    List<AlignmentTemplateInstance> allAlTemps = alGrid.getAlTemps();
    AlignmentTemplateInstance[][] alTemp_min_E_for_F_range, alTemp_max_E_for_F_range;

    min_E_for_F_range = new int[fsize][fsize];
    max_E_for_F_range = new int[fsize][fsize];
    alTemp_min_E_for_F_range = new AlignmentTemplateInstance[fsize][fsize];
    alTemp_max_E_for_F_range = new AlignmentTemplateInstance[fsize][fsize];

    // initialize
    for (int i = 0; i < fsize; i++) {
      for (int j = 0; j < fsize; j++) {
        min_E_for_F_range[i][j] = Integer.MAX_VALUE;
        max_E_for_F_range[i][j] = -1;
      }
    }

    for (AlignmentTemplateInstance t : allAlTemps) {
      int f1 = t.fStartPos();
      int f2 = t.fEndPos();
      int e1 = t.eStartPos();
      int e2 = t.eEndPos();
      if (DEBUG)
        System.err.printf("Processing t - f : e [%d-%d] [%d-%d]\n",
            t.fStartPos(), t.fEndPos(), t.eStartPos(), t.eEndPos());
      if (DEBUG)
        System.err.printf("Processing t : %s\n", t.toString(false));
      if (e1 < min_E_for_F_range[f1][f2]) {
        min_E_for_F_range[f1][f2] = e1;
        alTemp_min_E_for_F_range[f1][f2] = t;
        if (DEBUG)
          System.err.printf("min_E_for_F_range[%d][%d] -> %d\n", f1, f2, e1);
      }
      if (e2 > max_E_for_F_range[f1][f2]) {
        max_E_for_F_range[f1][f2] = e2;
        alTemp_max_E_for_F_range[f1][f2] = t;
        if (DEBUG)
          System.err.printf("max_E_for_F_range[%d][%d] -> %d\n", f1, f2, e2);
      }
    }

    if (DEBUG) {
      for (int i = 0; i < fsize; i++) {
        for (int j = 0; j < fsize; j++) {
          if (min_E_for_F_range[i][j] <= max_E_for_F_range[i][j]) {
            if (DEBUG)
              System.err.printf("Precompute: f[%d-%d] --> e[%d-%d]\n", i, j,
                  min_E_for_F_range[i][j], max_E_for_F_range[i][j]);
            StringBuilder fseq = new StringBuilder();
            StringBuilder eseq = new StringBuilder();

            Sequence<IString> f = alTemp_min_E_for_F_range[i][j]
                .getSentencePair().f();
            Sequence<IString> e = alTemp_min_E_for_F_range[i][j]
                .getSentencePair().e();

            for (int idx = i; idx <= j; idx++) {
              fseq.append(f.get(idx).toString()).append(" ");
            }
            for (int idx = min_E_for_F_range[i][j]; idx <= max_E_for_F_range[i][j]; idx++) {
              eseq.append(e.get(idx).toString()).append(" ");
            }

            if (DEBUG)
              System.err.printf("Precompute: %s--> %s\n", fseq.toString(),
                  eseq.toString());
          }
        }
      }
    }
  }

  public IntPair extendPhraseRage(IntPair phraseRange, int begin_phrase,
      int end_phrase) {
    if (phraseRange.getSource() > phraseRange.getTarget()) {
      phraseRange = englishRangeForForeignRange(begin_phrase - 1, end_phrase);
      System.err.printf("PB: mod f range [%d-%d] --> e range [%d-%d]\n",
          begin_phrase - 1, end_phrase, phraseRange.getSource(),
          phraseRange.getTarget());
      phraseRange = englishRangeForForeignRange(begin_phrase, end_phrase + 1);
      System.err.printf("PB: mod f range [%d-%d] --> e range [%d-%d]\n",
          begin_phrase, end_phrase + 1, phraseRange.getSource(),
          phraseRange.getTarget());
      phraseRange = englishRangeForForeignRange(begin_phrase - 1,
          end_phrase + 1);
      System.err.printf("PB: mod f range [%d-%d] --> e range [%d-%d]\n",
          begin_phrase - 1, end_phrase + 1, phraseRange.getSource(),
          phraseRange.getTarget());
    }
    return phraseRange;
  }

  @Override
  public void featurizeSentence(SymmetricalWordAlignment sent, String info,
      AlignmentGrid alGrid) {

    collectStatsFromGrid(alGrid);

    // start checking
    fsize = alGrid.fsize();

    IntPair[][] ips = new IntPair[fsize][fsize];

    for (int i = 0; i < fsize; i++)
      for (int j = 0; j < fsize; j++)
        ips[i][j] = englishRangeForForeignRange(i, j);

    for (int i = 0; i < fsize; i++) {
      for (int j = 0; j < fsize; j++) {
        if (i > j)
          continue;
        IntPair ip = ips[i][j];
        for (int i2 = i; i2 <= j; i2++) {
          for (int j2 = i2; j2 <= j; j2++) {
            IntPair ip2 = ips[i2][j2];
            if (ip.getSource() > ip.getTarget()) {
              if (!(ip2.getSource() > ip2.getTarget())) {
                throw new RuntimeException("ip2=null, ip1 should be null too");
              }
            } else {
              if (ip2.getSource() > ip2.getTarget()) {
                // redundant:
                // continue;
              } else {
                // ip should contains more than ip2
                if (ip.getSource() > ip2.getSource()
                    || ip.getTarget() < ip2.getTarget()) {
                  throw new RuntimeException("ip should contains more than ip2");
                }
              }
            }
          }
        }
      }
    }
    // end checking

    System.err
        .println("PICHUAN: in SyntacticPhraseReorderingFeatureExtractor.java");
    System.err.println("------------------------------------------------");
    VerbPhraseBoundary pb = new VerbPhraseBoundary(info);

    Map<IntQuadruple, String> boundaries = pb.getBoundaries();

    for (Map.Entry<IntQuadruple, String> e : boundaries.entrySet()) {
      IntQuadruple ranges = e.getKey();
      String str = e.getValue();
      int begin_firstPhrase = ranges.getSource();
      int end_firstPhrase = ranges.getMiddle();
      int begin_secondPhrase = ranges.getTarget();
      int end_secondPhrase = ranges.getTarget2();
      IntPair wholePhraseRange = englishRangeForForeignRange(begin_firstPhrase,
          end_secondPhrase);
      IntPair firstPhraseRange = englishRangeForForeignRange(begin_firstPhrase,
          end_firstPhrase);
      IntPair secondPhraseRange = englishRangeForForeignRange(
          begin_secondPhrase, end_secondPhrase);
      System.err.println("PB:" + ranges + "\t" + str);
      System.err.printf("PB: f0 range [%d-%d] --> e0 range [%d-%d]\n",
          begin_firstPhrase, end_secondPhrase, wholePhraseRange.getSource(),
          wholePhraseRange.getTarget());

      // wholePhraseRange =
      // extendPhraseRage(wholePhraseRange,begin_firstPhrase,end_secondPhrase);

      System.err.printf("PB: f1 range [%d-%d] --> e1 range [%d-%d]\n",
          begin_firstPhrase, end_firstPhrase, firstPhraseRange.getSource(),
          firstPhraseRange.getTarget());

      // firstPhraseRange =
      // extendPhraseRage(firstPhraseRange,begin_firstPhrase,end_firstPhrase);

      System.err.printf("PB: f2 range [%d-%d] --> e2 range [%d-%d]\n",
          begin_secondPhrase, end_secondPhrase, secondPhraseRange.getSource(),
          secondPhraseRange.getTarget());

      // secondPhraseRange =
      // extendPhraseRage(secondPhraseRange,begin_secondPhrase,end_secondPhrase);

      addCountToGoodness(wholePhraseRange, str, 0);
      addCountToGoodness(firstPhraseRange, str, 1);
      addCountToGoodness(secondPhraseRange, str, 2);

      String type = checkPhraseDistortionType(firstPhraseRange,
          secondPhraseRange);
      // if (type.equals("OTHER")) {
      System.err.printf("DEBUG: type=%s, %s\t%s\n", type, firstPhraseRange,
          secondPhraseRange);

      distortionTypeCounter.incrementCount(str, type);
    }
  }

  static String checkPhraseDistortionType(IntPair firstPhraseRange,
      IntPair secondPhraseRange) {
    int begin_firstPhrase = firstPhraseRange.getSource();
    int end_firstPhrase = firstPhraseRange.getTarget();
    int begin_secondPhrase = secondPhraseRange.getSource();
    int end_secondPhrase = secondPhraseRange.getTarget();

    if (begin_firstPhrase > end_firstPhrase
        || begin_secondPhrase > end_secondPhrase) {
      return "BAD";
    }

    if (end_firstPhrase + 1 == begin_secondPhrase) {
      return "MONOTONE";
    }
    if (end_secondPhrase + 1 == begin_firstPhrase) {
      return "SWAP";
    }

    if (end_firstPhrase < begin_secondPhrase) {
      return "MONOTONE-DISCONTINUOUS";
    }
    if (end_secondPhrase < begin_firstPhrase) {
      return "SWAP-DISCONTINUOUS";
    }

    if (begin_firstPhrase < begin_secondPhrase
        && end_firstPhrase < end_secondPhrase
        && end_firstPhrase >= begin_secondPhrase) {
      return "MONOTONE-OVERLAP";
    }
    if (begin_firstPhrase > begin_secondPhrase
        && end_firstPhrase > end_secondPhrase
        && end_secondPhrase >= begin_firstPhrase) {
      return "SWAP-OVERLAP";
    }

    return "OTHER";
  }

  TwoDimensionalCounter<String, Boolean> goodnessCounter = new TwoDimensionalCounter<String, Boolean>();

  TwoDimensionalCounter<String, String> distortionTypeCounter = new TwoDimensionalCounter<String, String>();

  void addCountToGoodness(IntPair ip, String label, int suffix) {
    int i1 = ip.getSource();
    int i2 = ip.getTarget();
    boolean isGood = true;
    if (i1 > i2) {
      isGood = false;
    }

    StringBuilder sb = new StringBuilder();
    sb.append(label).append("-").append(suffix);
    goodnessCounter.incrementCount(sb.toString(), isGood);
  }

  @Override
  public void report() {
    System.out
        .println("-----REPORT from SyntacticPhraseReorderingFeatureExtractor-----");
    System.out.printf("\t\ttrue\tfalse\ttrue\tfalse\n");
    for (String k1 : goodnessCounter.firstKeySet()) {
      ClassicCounter<Boolean> counter = goodnessCounter.getCounter(k1);
      double pTrue = 100.0 * counter.getCount(true) / counter.totalCount();
      double pFalse = 100.0 * counter.getCount(false) / counter.totalCount();
      System.out.printf("%s\t%.2f%%\t%.2f%%\t%.1f\t%.1f\n", k1, pTrue, pFalse,
          counter.getCount(true), counter.getCount(false));
    }
    System.out
        .println("---------------------------------------------------------------");
    for (String k1 : distortionTypeCounter.firstKeySet()) {
      ClassicCounter<String> counter = distortionTypeCounter.getCounter(k1);
      System.out.printf("%s\n", k1);
      for (String k2 : counter.keySet()) {
        double count = counter.getCount(k2);
        double perc = 100.0 * count / counter.totalCount();
        System.out.printf("\t%.2f%%\t%.1f\t%s\n", perc, count, k2);
      }
    }

    System.out
        .println("---------------------------------------------------------------");
  }

  public static boolean needBatchAlTemp() {
    return false;
  }
}
