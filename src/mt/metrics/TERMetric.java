package mt.metrics;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import mt.base.NBestListContainer;
import mt.base.RawSequence;
import mt.base.ScoredFeaturizedTranslation;
import mt.base.Sequence;
import mt.decoder.recomb.RecombinationFilter;
import mt.decoder.util.State;

import mt.reranker.ter.TERcalc;
import mt.reranker.ter.TERalignment;


public class TERMetric<TK, FV> extends AbstractMetric<TK, FV> {
  final List<List<Sequence<TK>>> referencesList;

  enum EditType { ins, del, sub, sft };
  boolean countEdits = true;

  public TERMetric(List<List<Sequence<TK>>> referencesList, boolean countEdits) {
    this.referencesList = referencesList;
    this.countEdits = countEdits;
  }

  public TERMetric(List<List<Sequence<TK>>> referencesList) {
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

  public double calcTER(ScoredFeaturizedTranslation<TK, FV> trans, int idx, double[] editCounts) {
    List<Sequence<TK>> refs = referencesList.get(idx);
    double best = Double.POSITIVE_INFINITY;
    TERalignment bestAl = null;
    String hyp = trans.translation.toString();
    for (Sequence<TK> ref : refs) {
      TERalignment terAl = TERcalc.TER(hyp, ref.toString());
      double ter = terAl.score();
      if (ter < best) {
        best = ter;
        bestAl = terAl;
      }
    }
    if(editCounts != null) {
      bestAl.scoreDetails();
      editCounts[EditType.ins.ordinal()] += bestAl.numIns;
      editCounts[EditType.del.ordinal()] += bestAl.numDel;
      editCounts[EditType.sub.ordinal()] += bestAl.numSub;
      editCounts[EditType.sft.ordinal()] += bestAl.numSft;
    }
    return best;
  }

  public class TERIncrementalMetric implements IncrementalEvaluationMetric<TK,FV> {
    double[] scores = new double[referencesList.size()];
    boolean[] nulls = new boolean[referencesList.size()];
    double[] editCounts = null;
    double scoreTotal = 0;
    int cnt = 0;
    int nullCnt = 0;

    public TERIncrementalMetric() { 
      if(countEdits)
        editCounts = new double[EditType.values().length];
    }

    public TERIncrementalMetric(TERIncrementalMetric p) {
      scores = p.scores.clone();
      cnt = p.cnt;
      scoreTotal = p.scoreTotal;
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
        scoreTotal += scores[cnt] = calcTER(trans, cnt, editCounts);
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
      if(countEdits)
        throw new RuntimeException("TERMetric: can't both use edit counts and replace().");
      scoreTotal -= scores[index];
      if (trans == null) {
        scores[index] = 0;
        if (!nulls[index]) {
          nulls[index] = true;
          nullCnt++;
        }
      } else {
        if (nulls[index]) {
          nulls[index] = false;
          nullCnt--;
        }
        scoreTotal += scores[index] = calcTER(trans, index, null);
      }
      return this;
    }

    @Override
    public double score() {
      return -scoreTotal/(cnt-nullCnt);
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
		public TERIncrementalMetric clone() {
      return new TERIncrementalMetric(this);
    }

    public double insCount() { return editCounts[EditType.ins.ordinal()]; }
    public double delCount() { return editCounts[EditType.del.ordinal()]; }
    public double subCount() { return editCounts[EditType.sub.ordinal()]; }
    public double sftCount() { return editCounts[EditType.sft.ordinal()]; }
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage:\n\tjava TERMetric (ref 1) (ref 2) ... (ref n) < canidateTranslations\n");
      System.exit(-1);
    }
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args);

    TERMetric<IString,String> ter = new TERMetric<IString,String>(referencesList);
    TERMetric<IString,String>.TERIncrementalMetric incMetric = ter.getIncrementalMetric();

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in));

    for (String line; (line = reader.readLine()) != null; ) {
      line = line.replaceAll("\\s+$", "");
      line = line.replaceAll("^\\s+", "");
      Sequence<IString> translation = new RawSequence<IString>(IStrings.toIStringArray(line.split("\\s+")));
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(translation, null, 0);
      incMetric.add(tran);
    }

    reader.close();

    System.out.printf("TER = %.3f\n", 100*incMetric.score());
  }

}
