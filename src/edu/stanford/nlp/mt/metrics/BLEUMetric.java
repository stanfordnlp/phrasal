package edu.stanford.nlp.mt.metrics;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;

import edu.stanford.nlp.util.Pair;

/**
 *
 * @author danielcer
 *
 * @param <TK>
 */
public class BLEUMetric<TK, FV> extends AbstractMetric<TK, FV> {
  public static final int DEFAULT_MAX_NGRAM_ORDER = 4;

  public static final double LENGTH_BIAS = Double.parseDouble(System
      .getProperty("bleuLengthBias", "1"));

  final List<Map<Sequence<TK>, Integer>> maxReferenceCounts;
  final int[][] refLengths;
  final int order;
  final double multiplier;
  final boolean smooth;

  private static final boolean printLocalScores = (System
      .getProperty("printLocalScores") != null);

  static boolean enableCache = true;
  private final Map<Pair<Integer, Integer>, Double> smoothScoreCache = new HashMap<Pair<Integer, Integer>, Double>();

  private static int possibleMatchCounts(int order, int length) {
    int d = length - order;
    return d >= 0 ? d : 0;
  }

  private static <TK> int[] localMatchCounts(Map<Sequence<TK>, Integer> clippedCounts, int order) {
    int[] counts = new int[order];
    for (Map.Entry<Sequence<TK>, Integer> entry : clippedCounts.entrySet()) {
      int cnt = entry.getValue();
      if (cnt > 0) {
        int len = entry.getKey().size();
        counts[len - 1] += cnt;
      }
    }

    return counts;
  }

  private static int bestMatchLength(int[] refLengths, int candidateLength) {
    int best = refLengths[0];
    for (int i = 1; i < refLengths.length; i++) {
      if (Math.abs(candidateLength - best) > Math.abs(candidateLength
          - refLengths[i])) {
        best = refLengths[i];
      }
    }
    return (int) (LENGTH_BIAS * best);
  }


  /** Compute a sentence-level smoothed BLEU score in the standard way.
   *  This method follows:
   *  Lin, Chin-Yew and Franz J. Och. 2004. ORANGE: A Method for Evaluating
   *  Automatic Evaluation Metrics for Machine Translation.
   *  COLING 2004, Proceedings of the 20th International Conference on
   *  Computational Linguistics, Geneva, Switzerland. 501–507.
   *
   * @param strSeq The candidate translation
   * @param strRefs A list of reference translations
   * @param order The largest n-gram size used in the BLEU score calculation (e.g., 4)
   * @return The smoothed BLEU score
   */
  public static double computeLocalSmoothScore(String strSeq, List<String> strRefs, int order) {
    Sequence<String> seq = new SimpleSequence<String>(strSeq.split("\\s+"));
    List<Sequence<String>> refs = new ArrayList<Sequence<String>>(strRefs.size());
    for (int i = 0; i < strRefs.size(); i++) {
      refs.add(new SimpleSequence<String>(strRefs.get(i).split("\\s+")));
    }
    return computeLocalSmoothScore(seq, refs, order);
  }

  public static <TK> double computeLocalSmoothScore(Sequence<TK> seq, List<Sequence<TK>> refs, int order) {
    Map<Sequence<TK>, Integer> candidateCounts = Metrics.getNGramCounts(seq,
        order);
    Map<Sequence<TK>, Integer> maxReferenceCount = Metrics.getMaxNGramCounts(refs, order);

    Metrics.clipCounts(candidateCounts, maxReferenceCount);
    int seqSz = seq.size();
    int[] localPossibleMatchCounts = new int[order];
    for (int i = 0; i < order; i++) {
      localPossibleMatchCounts[i] = possibleMatchCounts(i, seqSz);
    }

    int[] localCounts = localMatchCounts(candidateCounts,order);
    int localC = seq.size();
    int[] refLengths = new int[refs.size()];
    for (int i = 0; i < refLengths.length; i++) {
      refLengths[i] = refs.get(i).size();
    }
    int localR = bestMatchLength(refLengths, seq.size());

    double localLogBP;
    if (localC < localR) {
      localLogBP = 1 - localR / (1.0 * localC);
    } else {
      localLogBP = 0.0;
    }

    double[] localPrecisions = new double[order];
    for (int i = 0; i < order; i++) {
      if (i == 0) {
        localPrecisions[i] = (1.0 * localCounts[i])
            / localPossibleMatchCounts[i];
      } else {
        localPrecisions[i] = (localCounts[i] + 1.0)
            / (localPossibleMatchCounts[i] + 1.0);
      }
    }
    double localNgramPrecisionScore = 0;
    for (int i = 0; i < order; i++) {
      localNgramPrecisionScore += (1.0 / order)
          * Math.log(localPrecisions[i]);
    }

    // System.err.printf("BLEUS: %e logbp %e logPrec %e Prec %e\n",
    // Math.exp(localLogBP + localNgramPrecisionScore), localLogBP,
    // localNgramPrecisionScore, Math.exp(localNgramPrecisionScore));
    final double localScore = Math.exp(localLogBP + localNgramPrecisionScore);
    return localScore;
  }

  /**
   *
   */
  public BLEUMetric(double multiplier, List<List<Sequence<TK>>> referencesList) {
    this.order = DEFAULT_MAX_NGRAM_ORDER;
    maxReferenceCounts = new ArrayList<Map<Sequence<TK>, Integer>>(
        referencesList.size());
    refLengths = new int[referencesList.size()][];
    init(referencesList);
    this.multiplier = multiplier;
    smooth = referencesList.size() == 1;
  }

  public BLEUMetric(List<List<Sequence<TK>>> referencesList) {
    this(referencesList, referencesList.size() == 1);
  }

  /**
   *
   */
  public BLEUMetric(List<List<Sequence<TK>>> referencesList, boolean smooth) {
    this.order = DEFAULT_MAX_NGRAM_ORDER;
    maxReferenceCounts = new ArrayList<Map<Sequence<TK>, Integer>>(
        referencesList.size());
    refLengths = new int[referencesList.size()][];
    multiplier = 1;
    init(referencesList);
    this.smooth = referencesList.size() == 1 || smooth;
  }

  /**
   *
   */
  public BLEUMetric(List<List<Sequence<TK>>> referencesList, int order,
      boolean smooth) {
    this.order = order;
    maxReferenceCounts = new ArrayList<Map<Sequence<TK>, Integer>>(
        referencesList.size());
    refLengths = new int[referencesList.size()][];
    multiplier = 1;
    init(referencesList);
    this.smooth = referencesList.size() == 1 || smooth;
  }

  public BLEUMetric(List<List<Sequence<TK>>> referencesList, int order) {
    this.order = order;
    maxReferenceCounts = new ArrayList<Map<Sequence<TK>, Integer>>(
        referencesList.size());
    refLengths = new int[referencesList.size()][];
    multiplier = 1;
    init(referencesList);
    smooth = referencesList.size() == 1;
  }

  private void init(List<List<Sequence<TK>>> referencesList) {
    int listSz = referencesList.size();

    for (int listI = 0; listI < listSz; listI++) {
      List<Sequence<TK>> references = referencesList.get(listI);

      Map<Sequence<TK>, Integer> maxReferenceCount = Metrics.getMaxNGramCounts(
          references, order);
      maxReferenceCounts.add(maxReferenceCount);
      
      int refsSz = references.size();
      assert refsSz > 0;
      refLengths[listI] = new int[refsSz];
      for (int j = 0; j < refsSz; ++j) {
        refLengths[listI][j] = references.get(j).size();
      }
    }
  }

  @Override
  public BLEUIncrementalMetric getIncrementalMetric() {
    return new BLEUIncrementalMetric();
  }

  @Override
  public BLEUIncrementalMetric getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    return new BLEUIncrementalMetric(nbestList);
  }

  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    return new BLEUIncrementalMetricRecombinationFilter<TK, FV>();
  }

  private static int maxIncrementalId = 0;

  public class BLEUIncrementalMetric implements
      NgramPrecisionIncrementalMetric<TK, FV>,
      IncrementalNBestEvaluationMetric<TK, FV> {
    private final int id = maxIncrementalId++;
    protected List<Sequence<TK>> sequences;
    double smoothSum = 0;
    int smoothCnt = 0;
    final int[] matchCounts = new int[order];
    final int[] possibleMatchCounts = new int[order];
    final int[][] futureMatchCounts;
    final int[][] futurePossibleCounts;
    int r, c;

    @Override
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new BLEUIncrementalMetric(this);
    }

    @Override
    public double[] precisions() {
      double[] r = new double[order];
      for (int i = 0; i < r.length; i++) {
        r[i] = matchCounts[i] / (double) possibleMatchCounts[i];
      }
      return r;
    }

    BLEUIncrementalMetric() {
      futureMatchCounts = null;
      futurePossibleCounts = null;
      r = 0;
      c = 0;
      this.sequences = new ArrayList<Sequence<TK>>(maxReferenceCounts.size());
    }

    BLEUIncrementalMetric(NBestListContainer<TK, FV> nbest) {
      r = 0;
      c = 0;
      List<List<ScoredFeaturizedTranslation<TK, FV>>> nbestLists = nbest
          .nbestLists();

      futureMatchCounts = new int[nbestLists.size()][];
      futurePossibleCounts = new int[nbestLists.size()][];
      for (int i = 0; i < futureMatchCounts.length; i++) {
        futureMatchCounts[i] = new int[order];
        futurePossibleCounts[i] = new int[order];
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
          Map<Sequence<TK>, Integer> candidateCounts = Metrics.getNGramCounts(
              tran.translation, order);
          Metrics.clipCounts(candidateCounts, maxReferenceCounts.get(i));
          int[] localCounts = localMatchCounts(candidateCounts,order);
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
          System.err.printf(" %d/%d", futureMatchCounts[i][j],
              futurePossibleCounts[i][j]);
        }
        System.err.println();
      }

      this.sequences = new ArrayList<Sequence<TK>>(maxReferenceCounts.size());
    }

    public double getMultiplier() {
      return multiplier;
    }

    /**
     *
     */
    private BLEUIncrementalMetric(BLEUIncrementalMetric m) {
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
      BLEUIncrementalMetric otherBIM = (BLEUIncrementalMetric) o;

      int maxNonZeroP = maxNonZeroPrecision();
      int otherMaxNonZeroP = otherBIM.maxNonZeroPrecision();
      if (maxNonZeroP != otherMaxNonZeroP) {
        return maxNonZeroP - otherMaxNonZeroP;
      }

      double diff = logScore(maxNonZeroP) - otherBIM.logScore(maxNonZeroP);

      /*
       * double nonLogdiff = score() - otherBIM.score(); if (Math.signum(diff)
       * != Math.signum(nonLogdiff)) { System.err.printf("%d vs. %d\n",
       * maxNonZeroP, otherMaxNonZeroP); System.err.printf("%f vs. %f\n", diff,
       * nonLogdiff); System.err.printf("log brev: %f & %f\n",
       * logBrevityPenalty(), ((BLEUIncrementalMetric)o).logBrevityPenalty());
       *
       * for (double p : ngramPrecisions()) { System.err.printf("\t%f\n", p); }
       * System.err.printf("----\n"); for (double p :
       * ((BLEUIncrementalMetric)o).ngramPrecisions()) {
       * System.err.printf("\t%f\n", p); } System.exit(-1); }
       */
      if (diff != 0) {
        return (int) Math.signum(diff);
      }
      return id - ((BLEUIncrementalMetric) o).id;
    }

    private void incCounts(Map<Sequence<TK>, Integer> clippedCounts,
        Sequence<TK> sequence, int mul) {
      int seqSz = sequence.size();
      for (int i = 0; i < order; i++) {
        possibleMatchCounts[i] += mul * possibleMatchCounts(i, seqSz);
      }

      int[] localCounts = localMatchCounts(clippedCounts,order);
      for (int i = 0; i < order; i++) {
        // System.err.printf("local Counts[%d]: %d\n", i, localCounts[i]);
        matchCounts[i] += mul * localCounts[i];
      }
    }

    private void incCounts(Map<Sequence<TK>, Integer> clippedCounts,
        Sequence<TK> sequence) {
      incCounts(clippedCounts, sequence, 1);
    }

    private void decCounts(Map<Sequence<TK>, Integer> clippedCounts,
        Sequence<TK> sequence) {
      incCounts(clippedCounts, sequence, -1);
    }

    private double getLocalSmoothScore(Sequence<TK> seq, int pos, int nbestId) {
      if (!enableCache || nbestId < 0)
        return computeLocalSmoothScore(seq, pos);
      Pair<Integer, Integer> pair = new Pair<Integer, Integer>(pos, nbestId);
      Double cached = smoothScoreCache.get(pair);
      if (cached == null) {
        cached = computeLocalSmoothScore(seq, pos);
        smoothScoreCache.put(pair, cached);
      }
      return cached;
    }

    public double computeLocalSmoothScore(Sequence<TK> seq, int pos) {
      Map<Sequence<TK>, Integer> candidateCounts = Metrics.getNGramCounts(seq,
          order);
      Metrics.clipCounts(candidateCounts, maxReferenceCounts.get(pos));
      int seqSz = seq.size();
      int[] localPossibleMatchCounts = new int[order];
      for (int i = 0; i < order; i++) {
        localPossibleMatchCounts[i] = possibleMatchCounts(i, seqSz);
      }

      int[] localCounts = localMatchCounts(candidateCounts,order);
      int localC = seq.size();
      int localR = bestMatchLength(refLengths[pos], seq.size());

      double localLogBP;
      if (localC < localR) {
        localLogBP = 1 - localR / (1.0 * localC);
      } else {
        localLogBP = 0.0;
      }

      double[] localPrecisions = new double[order];
      for (int i = 0; i < order; i++) {
        if (i == 0) {
          localPrecisions[i] = (1.0 * localCounts[i])
              / localPossibleMatchCounts[i];
        } else {
          localPrecisions[i] = (localCounts[i] + 1.0)
              / (localPossibleMatchCounts[i] + 1.0);
        }
      }
      double localNgramPrecisionScore = 0;
      for (int i = 0; i < order; i++) {
        localNgramPrecisionScore += (1.0 / order)
            * Math.log(localPrecisions[i]);
      }

      // System.err.printf("BLEUS: %e logbp %e logPrec %e Prec %e\n",
      // Math.exp(localLogBP + localNgramPrecisionScore), localLogBP,
      // localNgramPrecisionScore, Math.exp(localNgramPrecisionScore));
      final double localScore = Math.exp(localLogBP + localNgramPrecisionScore);
      if (printLocalScores)
        System.out.printf("%d %f\n", pos, localScore);
      return localScore;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> tran) {
      return add(-1, tran);
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(int nbestId,
        ScoredFeaturizedTranslation<TK, FV> tran) {
      int pos = sequences.size();
      if (pos >= maxReferenceCounts.size()) {
        throw new RuntimeException(String.format(
            "Attempt to add more candidates, %d, than references, %d.",
            pos + 1, maxReferenceCounts.size()));
      }

      if (smooth) {
        if (tran != null) {
          sequences.add(tran.translation);
          smoothSum += getLocalSmoothScore(tran.translation, pos, nbestId);
          smoothCnt++;
        } else {
          sequences.add(null);
        }
      } else {
        if (tran != null) {
          Map<Sequence<TK>, Integer> candidateCounts = Metrics.getNGramCounts(
              tran.translation, order);
          Metrics.clipCounts(candidateCounts, maxReferenceCounts.get(pos));
          sequences.add(tran.translation);
          incCounts(candidateCounts, tran.translation);
          c += tran.translation.size();
          r += bestMatchLength(refLengths[pos], tran.translation.size());
        } else {
          sequences.add(null);
        }
      }
      return this;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      return replace(index, -1, trans);
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index, int nbestId,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      if (index >= sequences.size()) {
        for (int i = sequences.size(); i < index; i++)
          add(null);
        add(trans);
      }
      Map<Sequence<TK>, Integer> candidateCounts = null;
      if (smooth) {
        if (sequences.get(index) != null) {
          smoothSum -= getLocalSmoothScore(sequences.get(index), index, nbestId);
          smoothCnt--;
        }
      } else {
        candidateCounts = (trans == null ? new HashMap<Sequence<TK>, Integer>()
            : Metrics.getNGramCounts(trans.translation, order));
        Metrics.clipCounts(candidateCounts, maxReferenceCounts.get(index));
        if (sequences.get(index) != null) {
          Map<Sequence<TK>, Integer> oldCandidateCounts = Metrics
              .getNGramCounts(sequences.get(index), order);
          Metrics.clipCounts(oldCandidateCounts, maxReferenceCounts.get(index));
          decCounts(oldCandidateCounts, sequences.get(index));
          c -= sequences.get(index).size();
          r -= bestMatchLength(refLengths[index], sequences.get(index).size());
        }
      }

      sequences.set(index, (trans == null ? null : trans.translation));

      if (smooth) {
        if (trans != null) {
          smoothSum += getLocalSmoothScore(trans.translation, index, nbestId);
          smoothCnt++;
        }
      } else {
        if (trans != null) {
          incCounts(candidateCounts, trans.translation);
          c += sequences.get(index).size();
          r += bestMatchLength(refLengths[index], sequences.get(index).size());
        }
      }

      return this;
    }

    @Override
    public double score() {
      double s;
      if (smooth) {
        s = smoothSum / smoothCnt;
      } else {
        s = multiplier * Math.exp(logScore());
      }
      return (Double.isNaN(s) ? 0 : s);
      // return (s != s ? 0 : s);
    }

    /**
     *
     */
    public double logScore() {
      return logScore(matchCounts.length);
    }

    private double logScore(int max) {
      double ngramPrecisionScore = 0;

      double[] precisions = ngramPrecisions();
      double wt = 1.0 / max;
      for (int i = 0; i < max; i++) {
        ngramPrecisionScore += wt * Math.log(precisions[i]);
      }
      return logBrevityPenalty() + ngramPrecisionScore;
    }

    /**
     *
     */
    private int maxNonZeroPrecision() {
      double[] precisions = ngramPrecisions();
      for (int i = precisions.length - 1; i >= 0; i--) {
        if (precisions[i] != 0)
          return i;
      }
      return -1;
    }

    /**
     *
     */
    public double[] ngramPrecisions() {
      double[] p = new double[matchCounts.length];
      for (int i = 0; i < matchCounts.length; i++) {
        int matchCount = matchCounts[i];
        int possibleMatchCount = possibleMatchCounts[i];
        if (futureMatchCounts != null) {
          int futureMatchCountsLength = futureMatchCounts.length;
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
    public int[][] ngramPrecisionCounts() {
      int[][] counts = new int[matchCounts.length][];
      for (int i = 0; i < matchCounts.length; i++) {
        counts[i] = new int[2];
        counts[i][0] = matchCounts[i];
        counts[i][1] = possibleMatchCounts[i];
      }
      return counts;
    }

    /**
     *
     */
    public double logBrevityPenalty() {
      if (c < r) {
        return 1 - r / (1.0 * c);
      }
      return 0.0;
    }

    @Override
    public double brevityPenalty() {
      return Math.exp(logBrevityPenalty());
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
    public int effectiveReferenceLength() {
      return r;
    }

    @Override
    public double maxScore() {
      return multiplier * 1.0;
    }

    @Override
    public int size() {
      return sequences.size();
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

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err
          .println("Usage:\n\tjava BLEUMetric [-order #] (ref 1) (ref 2) ... (ref n) < candidateTranslations\n");
      System.exit(-1);
    }

    int BLEUOrder = -1;
    if (args[0].equals("-order")) {
      BLEUOrder = Integer.parseInt(args[1]);
      String[] newArgs = new String[args.length - 2];
      System.arraycopy(args, 2, newArgs, 0, args.length - 2);
      args = newArgs;
    }
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args);

    BLEUMetric<IString, String> bleu;
    if (BLEUOrder != -1) {
      bleu = new BLEUMetric<IString, String>(referencesList, BLEUOrder,
          System.getProperty("smoothBLEU") != null);
    } else {
      bleu = new BLEUMetric<IString, String>(referencesList,
          System.getProperty("smoothBLEU") != null);
    }
    BLEUMetric<IString, String>.BLEUIncrementalMetric incMetric = bleu
        .getIncrementalMetric();

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    for (String line; (line = reader.readLine()) != null; ) {
      line = NISTTokenizer.tokenize(line).trim();
      Sequence<IString> translation = new RawSequence<IString>(
          IStrings.toIStringArray(line.split("\\s+")));
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
          translation, null, 0);
      incMetric.add(tran);
    }

    reader.close();

    double[] ngramPrecisions = incMetric.ngramPrecisions();
    System.out.printf("BLEU = %.3f, ", 100 * incMetric.score());
    for (int i = 0; i < ngramPrecisions.length; i++) {
      if (i != 0) {
        System.out.print("/");
      }
      System.out.printf("%.3f", ngramPrecisions[i] * 100);
    }
    System.out.printf(" (BP=%.3f, ration=%.3f %d/%d)\n", incMetric
        .brevityPenalty(), ((1.0 * incMetric.candidateLength()) / incMetric
        .effectiveReferenceLength()), incMetric.candidateLength(), incMetric
        .effectiveReferenceLength());

    System.out.printf("\nPrecision Details:\n");
    int[][] precCounts = incMetric.ngramPrecisionCounts();
    for (int i = 0; i < ngramPrecisions.length; i++) {
      System.out.printf("\t%d:%d/%d\n", i, precCounts[i][0], precCounts[i][1]);
    }
  }

  
  @Override
  public double maxScore() {
    return 1.0;
  }
}

class BLEUIncrementalMetricRecombinationFilter<TK, FV> implements
    RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> {

  @Override
  public Object clone() throws CloneNotSupportedException {
    super.clone();
    throw new RuntimeException();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public boolean combinable(IncrementalEvaluationMetric<TK, FV> oA,
      IncrementalEvaluationMetric<TK, FV> oB) {

    BLEUMetric<TK, FV>.BLEUIncrementalMetric hypA = (BLEUMetric.BLEUIncrementalMetric) oA;
    BLEUMetric<TK, FV>.BLEUIncrementalMetric hypB = (BLEUMetric.BLEUIncrementalMetric) oB;

    if (hypA.r != hypB.r)
      return false;
    if (hypA.c != hypB.c)
      return false;

    for (int i = 0; i < hypA.matchCounts.length; i++) {
      if (hypA.matchCounts[i] != hypB.matchCounts[i]) {
        return false;
      }

      if (hypA.possibleMatchCounts[i] != hypB.possibleMatchCounts[i]) {
        return false;
      }
    }
    return true;
  }

  @Override
  public long recombinationHashCode(IncrementalEvaluationMetric<TK, FV> o) {
    @SuppressWarnings("rawtypes")
    BLEUMetric.BLEUIncrementalMetric hyp = (BLEUMetric.BLEUIncrementalMetric) o;

    int hashCode = hyp.r + 31 * hyp.c;

    for (int possibleMatchCount : hyp.possibleMatchCounts) {
      hashCode *= 31;
      hashCode += possibleMatchCount;
    }

    for (int matchCount : hyp.matchCounts) {
      hashCode *= 31;
      hashCode += matchCount;
    }

    return hashCode;
  }

}
