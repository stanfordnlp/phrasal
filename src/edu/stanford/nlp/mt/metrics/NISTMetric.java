package edu.stanford.nlp.mt.metrics;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;
import edu.stanford.nlp.mt.tools.NISTTokenizer;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;

/**
 * 
 * @author Michel Galley
 * 
 * @param <TK>
 */
public class NISTMetric<TK, FV> extends AbstractMetric<TK, FV> {
  static public final int DEFAULT_MAX_NGRAM_ORDER = 10;

  final List<Counter<Sequence<TK>>> maxReferenceCounts;
  Counter<Sequence<TK>> ngramInfo;
  final int[][] refLengths;
  final int order;

  public void setNgramInfo(Counter<Sequence<TK>> i) {
    ngramInfo = i;
  }

  public Counter<Sequence<TK>> getNgramInfo() {
    return ngramInfo;
  }

  public NISTMetric(List<List<Sequence<TK>>> referencesList) {
    this.order = DEFAULT_MAX_NGRAM_ORDER;
    maxReferenceCounts = new ArrayList<Counter<Sequence<TK>>>(
        referencesList.size());
    refLengths = new int[referencesList.size()][];
    initReferences(referencesList);
    initNgramWeights(referencesList);
  }

  private void initReferences(List<List<Sequence<TK>>> referencesList) {
    int listSz = referencesList.size();
    for (int listI = 0; listI < listSz; listI++) {
      List<Sequence<TK>> references = referencesList.get(listI);

      int refsSz = references.size();
      if (refsSz == 0) {
        throw new RuntimeException(String.format(
            "No references found for data point: %d\n", listI));
      }

      refLengths[listI] = new int[refsSz];
      Counter<Sequence<TK>> maxReferenceCount = Metrics.getMaxNGramCounts(
          references, order);
      maxReferenceCounts.add(maxReferenceCount);
      refLengths[listI][0] = references.get(0).size();

      for (int refI = 1; refI < refsSz; refI++) {
        refLengths[listI][refI] = references.get(refI).size();
        Counter<Sequence<TK>> altCounts = Metrics.getNGramCounts(
            references.get(refI), order);
        for (Sequence<TK> sequence : new HashSet<Sequence<TK>>(
            altCounts.keySet())) {
          double cnt = maxReferenceCount.getCount(sequence);
          double altCnt = altCounts.getCount(sequence);
          if (cnt < altCnt) {
            maxReferenceCount.setCount(sequence, altCnt);
          }
        }
      }
    }
  }

  private void initNgramWeights(List<List<Sequence<TK>>> referencesList) {
    int len = 0;
    Counter<Sequence<TK>> allNgrams = new ClassicCounter<Sequence<TK>>();
    for (List<Sequence<TK>> references : referencesList) {
      for (Sequence<TK> reference : references) {
        len += reference.size();
        Counter<Sequence<TK>> altCounts = Metrics.getNGramCounts(
            reference, order);
        addToCounts(allNgrams, altCounts);
      }
    }
    ngramInfo = Metrics.getNGramInfo(allNgrams, len);
  }

  static private <TK> void addToCounts(Counter<Sequence<TK>> counter,
      Counter<Sequence<TK>> otherCounter) {
    Counters.addInPlace(counter, otherCounter);
  }

  @Override
  public NISTIncrementalMetric getIncrementalMetric() {
    return new NISTIncrementalMetric();
  }

  @Override
  public NISTIncrementalMetric getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    return new NISTIncrementalMetric(nbestList);
  }

  /**
	 * 
	 */
  private double averageReferenceLength(int index) {
    double sum = 0.0;
    for (int i = 0; i < refLengths[index].length; i++) {
      sum += refLengths[index][i];
    }
    return sum / refLengths[index].length;
  }

  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  public class NISTIncrementalMetric implements
      NgramPrecisionIncrementalMetric<TK, FV> {
    final List<Sequence<TK>> sequences;
    final double[] matchCounts = new double[order];
    final double[] possibleMatchCounts = new double[order];
    final double[][] futureMatchCounts;
    final double[][] futurePossibleCounts;
    double r;
    int c;

    @Override
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new NISTIncrementalMetric(this);
    }

    public double[] precisions() {
      double[] r = new double[order];
      for (int i = 0; i < r.length; i++) {
        r[i] = matchCounts[i] / possibleMatchCounts[i];
      }
      return r;
    }

    NISTIncrementalMetric() {
      futureMatchCounts = null;
      futurePossibleCounts = null;
      r = 0;
      c = 0;
      this.sequences = new ArrayList<Sequence<TK>>(maxReferenceCounts.size());
    }

    NISTIncrementalMetric(NBestListContainer<TK, FV> nbest) {
      r = 0;
      c = 0;
      List<List<ScoredFeaturizedTranslation<TK, FV>>> nbestLists = nbest
          .nbestLists();

      futureMatchCounts = new double[nbestLists.size()][];
      futurePossibleCounts = new double[nbestLists.size()][];
      for (int i = 0; i < futureMatchCounts.length; i++) {
        futureMatchCounts[i] = new double[order];
        futurePossibleCounts[i] = new double[order];
        for (int j = 0; j < order; j++) {
          futurePossibleCounts[i][j] = Integer.MAX_VALUE;
        }
        List<ScoredFeaturizedTranslation<TK, FV>> nbestList = nbestLists.get(i);
        for (ScoredFeaturizedTranslation<TK, FV> tran : nbestList) {
          int seqSz = tran.translation.size();
          if (futurePossibleCounts[i][0] > seqSz) {
            for (int j = 0; j < order; j++) {
              futurePossibleCounts[i][j] = possibleMatchCounts(j, seqSz);
            }
          }
          Counter<Sequence<TK>> canidateCounts = Metrics.getNGramCounts(
              tran.translation, order);
          Metrics.clipCounts(canidateCounts, maxReferenceCounts.get(i));
          double[] localCounts = localMatchCounts(canidateCounts);
          for (int j = 0; j < order; j++) {
            if (futureMatchCounts[i][j] < localCounts[j]) {
              futureMatchCounts[i][j] = localCounts[j];
            }
          }

        }
      }

      System.err.println("Estimated Future Match Counts");
      for (int i = 0; i < futureMatchCounts.length; i++) {
        System.err.printf("%d:", i);
        for (int j = 0; j < futureMatchCounts[i].length; j++) {
          System.err.printf(" %f/%f", futureMatchCounts[i][j],
              futurePossibleCounts[i][j]);
        }
        System.err.println();
      }

      this.sequences = new ArrayList<Sequence<TK>>(maxReferenceCounts.size());
    }

    /**
		 * 
		 */
    private NISTIncrementalMetric(NISTIncrementalMetric m) {
      this.futureMatchCounts = m.futureMatchCounts;
      this.futurePossibleCounts = m.futurePossibleCounts;
      this.r = m.r;
      this.c = m.c;
      System.arraycopy(m.matchCounts, 0, matchCounts, 0, m.matchCounts.length);
      System.arraycopy(m.possibleMatchCounts, 0, possibleMatchCounts, 0,
          m.possibleMatchCounts.length);
      this.sequences = new ArrayList<Sequence<TK>>(m.sequences);
    }

    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      throw new UnsupportedOperationException();
    }

    private double possibleMatchCounts(int order, int length) {
      int d = length - order;
      return d >= 0 ? d : 0;
    }

    private double[] localMatchCounts(Counter<Sequence<TK>> clippedCounts) {
      double[] counts = new double[order];
      for (Sequence<TK> ngram : clippedCounts.keySet()) {
        double cnt = clippedCounts.getCount(ngram);
        if (cnt > 0) {
          int len = ngram.size();
          if (ngramInfo.containsKey(ngram))
            counts[len - 1] += cnt * ngramInfo.getCount(ngram);
          else
            System.err.println("Missing key for " + ngram.toString());
        }
      }
      return counts;
    }

    private void incCounts(Counter<Sequence<TK>> clippedCounts,
        Sequence<TK> sequence, int mul) {
      int seqSz = sequence.size();
      for (int i = 0; i < order; i++) {
        possibleMatchCounts[i] += mul * possibleMatchCounts(i, seqSz);
      }

      double[] localCounts = localMatchCounts(clippedCounts);
      for (int i = 0; i < order; i++) {
        // System.err.printf("local Counts[%d]: %d\n", i, localCounts[i]);
        matchCounts[i] += mul * localCounts[i];
      }
    }

    private void incCounts(Counter<Sequence<TK>> clippedCounts,
        Sequence<TK> sequence) {
      incCounts(clippedCounts, sequence, 1);
    }

    private void decCounts(Counter<Sequence<TK>> clippedCounts,
        Sequence<TK> sequence) {
      incCounts(clippedCounts, sequence, -1);
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> tran) {
      return add(tran == null ? null : tran.translation);
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        Sequence<TK> tran) {
      int pos = sequences.size();
      if (pos >= maxReferenceCounts.size()) {
        throw new RuntimeException(String.format(
            "Attempt to add more candidates, %d, than references, %d.",
            pos + 1, maxReferenceCounts.size()));
      }
      if (tran != null) {
        Counter<Sequence<TK>> canidateCounts = Metrics.getNGramCounts(
            tran, order);
        Metrics.clipCounts(canidateCounts, maxReferenceCounts.get(pos));
        sequences.add(tran);
        incCounts(canidateCounts, tran);
        c += tran.size();
        r += averageReferenceLength(pos);
      } else {
        sequences.add(null);
      }
      return this;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      if (index > sequences.size()) {
        throw new IndexOutOfBoundsException(String.format("Index: %d >= %d",
            index, sequences.size()));
      }
      Counter<Sequence<TK>> canidateCounts = (trans == null ? new ClassicCounter<Sequence<TK>>()
          : Metrics.getNGramCounts(trans.translation, order));
      Metrics.clipCounts(canidateCounts, maxReferenceCounts.get(index));
      if (sequences.get(index) != null) {
        Counter<Sequence<TK>> oldCanidateCounts = Metrics.getNGramCounts(
            sequences.get(index), order);
        Metrics.clipCounts(oldCanidateCounts, maxReferenceCounts.get(index));
        decCounts(oldCanidateCounts, sequences.get(index));
        c -= sequences.get(index).size();
        r -= averageReferenceLength(index);
      }
      sequences.set(index, (trans == null ? null : trans.translation));
      if (trans != null) {
        incCounts(canidateCounts, trans.translation);
        c += sequences.get(index).size();
        r += averageReferenceLength(index);
      }

      return this;
    }

    public double score() {
      return brevityPenalty() * ngramPrecisionScore();
    }

    public double ngramPrecisionScore() {
      double ngramPrecisionScore = 0;
      double[] precisions = ngramPrecisions();
      for (int i = 0; i < order; i++) {
        double p = precisions[i];
        ngramPrecisionScore += !Double.isNaN(p) ? p : 0;
      }
      return ngramPrecisionScore;
    }

    /**
		 * 
		 */
    public double[] ngramPrecisions() {
      double[] p = new double[matchCounts.length];
      for (int i = 0; i < matchCounts.length; i++) {
        double matchCount = matchCounts[i];
        double possibleMatchCount = possibleMatchCounts[i];
        if (futureMatchCounts != null) {
          double futureMatchCountsLength = futureMatchCounts.length;
          for (int j = sequences.size(); j < futureMatchCountsLength; j++) {
            matchCount += futureMatchCounts[j][i];
            possibleMatchCount += futurePossibleCounts[j][i];
          }
        }
        p[i] = (1.0 * matchCount) / (possibleMatchCount);
      }
      return p;
    }

    /**
		 *
		 */
    public double[][] ngramPrecisionCounts() {
      double[][] counts = new double[matchCounts.length][];
      for (int i = 0; i < matchCounts.length; i++) {
        counts[i] = new double[2];
        counts[i][0] = matchCounts[i];
        counts[i][1] = possibleMatchCounts[i];
      }
      return counts;
    }

    public double brevityPenalty() {
      double ratio = c / r;
      if (ratio >= 1.0)
        return 1.0;
      if (ratio <= 0.0)
        return 0.0;
      double ratio_x = 1.5, score_x = .5;
      double beta = -Math.log(score_x) / Math.log(ratio_x) / Math.log(ratio_x);
      return Math.exp(-beta * Math.log(ratio) * Math.log(ratio));
    }

    public void printScores() {
      double[] ngramPrecisions = ngramPrecisions();
      System.out.printf("NIST = %.3f, ", score());
      for (int i = 0; i < ngramPrecisions.length; i++) {
        if (i != 0) {
          System.out.print("/");
        }
        System.out.printf("%.3f", ngramPrecisions[i]);
      }
      System.out.printf(" (BP=%.3f, ration=%.3f %d/%d)\n", brevityPenalty(),
          ((1.0 * candidateLength()) / effectiveReferenceLength()),
          candidateLength(), (int) effectiveReferenceLength());

      System.out.printf("\nPrecision Details:\n");
      double[][] precCounts = ngramPrecisionCounts();
      for (int i = 0; i < ngramPrecisions.length; i++) {
        System.out.printf("\t%d:%.3f/%d\n", i, precCounts[i][0],
            (int) precCounts[i][1]);
      }
    }

    /**
		 * 
		 */
    public int candidateLength() {
      return c;
    }

    /**
		 * 
		 */
    public double effectiveReferenceLength() {
      return r;
    }

    @Override
    public int size() {
      return sequences.size();
    }

    @Override
    public double maxScore() {
      return 1.0;
    }

    @Override
    public State<IncrementalEvaluationMetric<TK, FV>> parent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public double partialScore() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int depth() {
      return sequences.size();
    }

    @Override
    public String scoreDetails() {
      return "None";
    }
  }

  static public void main(String args[]) throws IOException {
    if (args.length == 0) {
      System.err
          .println("Usage:\n\tjava NISTMetric (ref 1) (ref 2) ... (ref n) < canidateTranslations\n");
      System.exit(-1);
    }
    boolean doNIST = true;
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args, doNIST);
    System.out.printf("Metric: NIST metric with %d references (lower is better)%n", args.length);
    
    NISTMetric<IString, String> bleu = new NISTMetric<IString, String>(
        referencesList);
    NISTMetric<IString, String>.NISTIncrementalMetric incMetric = bleu
        .getIncrementalMetric();

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    for (String line; (line = reader.readLine()) != null;) {
      if (doNIST) line = NISTTokenizer.tokenize(line);
      Sequence<IString> translation = IStrings.tokenize(line);
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
          translation, null, 0);
      incMetric.add(tran);
    }

    reader.close();
    incMetric.printScores();
  }

  @Override
  public double maxScore() {
    return 1.0;
  }
}
