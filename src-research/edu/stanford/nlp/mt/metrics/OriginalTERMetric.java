package edu.stanford.nlp.mt.metrics;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;

import com.bbn.mt.ter.TERcalc;
import com.bbn.mt.ter.TERalignment;

public class OriginalTERMetric<TK, FV> extends AbstractTERMetric<TK, FV> {
  final List<List<Sequence<TK>>> referencesList;

  public static boolean VERBOSE = false;

  enum EditType {
    ins, del, sub, sft
  }

  boolean countEdits = false;

  public OriginalTERMetric(List<List<Sequence<TK>>> referencesList,
      boolean countEdits) {
    this.referencesList = referencesList;
    this.countEdits = countEdits;
  }

  public OriginalTERMetric(List<List<Sequence<TK>>> referencesList) {
    this.referencesList = referencesList;
  }

  @Override
  public TERIncrementalMetric getIncrementalMetric() {
    return new TERIncrementalMetric();
  }

  @Override
  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    throw new UnsupportedOperationException();
  }

  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double maxScore() {
    return 1.0;
  }

  Map<String, TERalignment> terCache = new HashMap<String, TERalignment>();

  public TERalignment calcTER(ScoredFeaturizedTranslation<TK, FV> trans,
      int idx, double[] editCounts) {
    List<Sequence<TK>> refsSeq = referencesList.get(idx);
    // String[] refs = new String[refsSeq.size()];
    String key = String.format("%d|||%s", idx, trans.translation.toString());
    TERalignment bestAl = terCache.get(key);

    if (bestAl == null) {
      double best = Double.POSITIVE_INFINITY;
      String hyp = trans.translation.toString();
      // for (int i = 0; i < refs.length; i++) {
      // refs[i] = refsSeq.get(i).toString();
      // }
      // System.err.printf("Hyp: %s\n", hyp);

      int totalWords = 0;
      for (Sequence<TK> ref : refsSeq) {
        TERalignment terAl = TERcalc.TER(hyp, ref.toString());
        totalWords += terAl.numWords;
        // System.err.printf("ter: %f\n", ter);
        // System.err.printf(":Edits: %s Len: %s\n", terAl.numEdits,
        // terAl.numWords);
        if (terAl.numEdits < best) {
          best = terAl.numEdits;
          bestAl = terAl;
        }
      }
      assert (bestAl != null);
      bestAl.numWords = totalWords / (double) refsSeq.size();
      terCache.put(key, bestAl);

      // Member variables no longer needed; free some memory:
      bestAl.hyp = null;
      bestAl.ref = null;
      bestAl.allshifts = null;
      bestAl.aftershift = null;
      bestAl.bestRef = null;
      if (editCounts != null)
        bestAl.alignment = null;
    }

    if (editCounts != null) {
      bestAl.scoreDetails();
      editCounts[EditType.ins.ordinal()] += bestAl.numIns;
      editCounts[EditType.del.ordinal()] += bestAl.numDel;
      editCounts[EditType.sub.ordinal()] += bestAl.numSub;
      editCounts[EditType.sft.ordinal()] += bestAl.numSft;
    }

    return bestAl;
  }

  public class TERIncrementalMetric implements
      IncrementalEvaluationMetric<TK, FV> {
    TERalignment[] aligns = new TERalignment[referencesList.size()];
    boolean[] nulls = new boolean[referencesList.size()];
    double[] editCounts = null;

    double editsTotal = 0;
    double numWordsTotal = 0;
    int cnt = 0;
    int nullCnt = 0;

    public TERIncrementalMetric() {
      if (countEdits)
        editCounts = new double[EditType.values().length];
    }

    public String scoreDetails() {
      return "None";
    }
    
    public TERIncrementalMetric(TERIncrementalMetric p) {
      aligns = p.aligns.clone();
      cnt = p.cnt;
      editsTotal = p.editsTotal;
      numWordsTotal = p.numWordsTotal;
      nullCnt = p.nullCnt;
      nulls = p.nulls;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> trans) {
      if (trans == null) {
        nulls[cnt++] = true;
        nullCnt++;
      } else {
        aligns[cnt] = calcTER(trans, cnt, editCounts);
        editsTotal += aligns[cnt].numEdits;
        numWordsTotal += aligns[cnt].numWords;
        cnt++;
      }
      return this;
    }

    @Override
    public double maxScore() {
      return 1.0;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      if (countEdits)
        throw new RuntimeException(
            "TERMetric: can't both use edit counts and replace().");
      if (aligns[index] != null) {
        editsTotal -= aligns[index].numEdits;
        numWordsTotal -= aligns[index].numWords;
      }
      if (trans == null) {
        aligns[index] = null;
        if (!nulls[index]) {
          nulls[index] = true;
          nullCnt++;
        }
      } else {
        if (nulls[index]) {
          nulls[index] = false;
          nullCnt--;
        }
        aligns[index] = calcTER(trans, index, null);
        editsTotal += aligns[index].numEdits;
        numWordsTotal += aligns[index].numWords;
      }
      return this;
    }

    @Override
    public double score() {
      if (VERBOSE)
        System.err.printf("(%s/%s)\n", editsTotal, numWordsTotal);
      return -editsTotal / (numWordsTotal);
    }

    @Override
    public int size() {
      return cnt;
    }

    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int depth() {
      throw new UnsupportedOperationException();
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
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new TERIncrementalMetric(this);
    }

  }

  public static void main(String[] args) throws IOException {

    if (args.length == 0) {
      System.err
          .println("Usage:\n\tjava TERMetric (ref 1) (ref 2) ... (ref n) < canidateTranslations\n");
      System.exit(-1);
    }
    VERBOSE = true;
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args);

    OriginalTERMetric<IString, String> ter = new OriginalTERMetric<IString, String>(
        referencesList);
    OriginalTERMetric<IString, String>.TERIncrementalMetric incMetric = ter
        .getIncrementalMetric();

    if (System.getProperty("fastTER") != null) {
      System.err.println("beam width: " + DEFAULT_TER_BEAM_WIDTH);
      System.err.println("ter shift dist: " + DEFAULT_TER_SHIFT_DIST);
      TERcalc.setBeamWidth(DEFAULT_TER_BEAM_WIDTH);
      TERcalc.setShiftDist(DEFAULT_TER_SHIFT_DIST);
    }

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    for (String line; (line = reader.readLine()) != null;) {
      Sequence<IString> translation = IStrings.tokenize(line);
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
          translation, null, 0);
      incMetric.add(tran);
    }

    reader.close();

    System.out.printf("TER = %.3f\n", 100 * incMetric.score());
  }

  @Override
  public void enableFastTER() {
    if (System.getProperty("fastTER") != null) {
      System.err.println("beam width: " + DEFAULT_TER_BEAM_WIDTH);
      System.err.println("ter shift dist: " + DEFAULT_TER_SHIFT_DIST);
      TERcalc.setBeamWidth(DEFAULT_TER_BEAM_WIDTH);
      TERcalc.setShiftDist(DEFAULT_TER_SHIFT_DIST);
    }
  }

  @Override
  public void setBeamWidth(int beamWidth) {
    TERcalc.setBeamWidth(beamWidth);
  }

  @Override
  public void setShiftDist(int maxShiftDist) {
    TERcalc.setShiftDist(maxShiftDist);
  }

}
