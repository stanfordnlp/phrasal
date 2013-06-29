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

import meteor.*;

public class METEORMetric<TK, FV> extends AbstractMetric<TK, FV> {
  final List<List<Sequence<TK>>> referencesList;

  enum EditType {
    ins, del, sub, sft
  }

  static {
    System.loadLibrary("MeteorWrapper");
  }

  MeteorScorer ms;

  public METEORMetric(List<List<Sequence<TK>>> referencesList) {
    this.referencesList = referencesList;
    ms = new MeteorScorer("en", (int) MeteorScorer.NORMALIZE_NO_PUNCT, true,
        "/u/nlp/packages/METEOR/meteor-0.8.1-dmcer.net/wn");

  }

  public METEORMetric(List<List<Sequence<TK>>> referencesList, double alpha,
      double beta, double gamma) {
    this.referencesList = referencesList;
    ms = new MeteorScorer("en", (int) MeteorScorer.NORMALIZE_NO_PUNCT, true,
        "/u/nlp/packages/METEOR/meteor-0.8.1-dmcer.net/wn", alpha, beta, gamma);
  }

  @Override
  public METEORIncrementalMetric getIncrementalMetric() {
    return new METEORIncrementalMetric();
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

  Map<String, Double> meteorCache = new HashMap<String, Double>();

  public double calcMETEOR(ScoredFeaturizedTranslation<TK, FV> trans, int idx) {
    List<Sequence<TK>> refsSeq = referencesList.get(idx);
    String key = String.format("%d|||%s", idx, trans.translation.toString());
    Double meteorScore = meteorCache.get(key);

    if (meteorScore == null) {
      double best = Double.NEGATIVE_INFINITY;
      String hyp = trans.translation.toString();

      for (Sequence<TK> ref : refsSeq) {
        MeteorTestSet ts = new MeteorTestSet();
        ts.addTest(hyp);
        ts.addRef(ref.toString());
        ms.scoreSet(ts);
        double score = ts.getFinalScore();
        if (score > best) {
          best = score;
        }
      }
      meteorCache.put(key, best);
      meteorScore = best;
    }

    return meteorScore;
  }

  public class METEORIncrementalMetric implements
      IncrementalEvaluationMetric<TK, FV> {
    double[] scores = new double[referencesList.size()];
    boolean[] nulls = new boolean[referencesList.size()];

    double scoresTotal = 0;
    int cnt = 0;
    int nullCnt = 0;

    public METEORIncrementalMetric() {
    }

    public String scoreDetails() {
      return "None";
    }
    
    public METEORIncrementalMetric(METEORIncrementalMetric p) {
      scores = p.scores.clone();
      cnt = p.cnt;
      scoresTotal = p.scoresTotal;
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
        scores[cnt] = calcMETEOR(trans, cnt);
        scoresTotal += scores[cnt];
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
      scoresTotal -= scores[index];
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
        scores[index] = calcMETEOR(trans, index);
        scoresTotal += scores[index];
      }
      return this;
    }

    @Override
    public double score() {
      return scoresTotal / (cnt - nullCnt);
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
      return new METEORIncrementalMetric(this);
    }

  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err
          .println("Usage:\n\tjava METEORMetric (ref 1) (ref 2) ... (ref n) < canidateTranslations\n");
      System.exit(-1);
    }
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args);

    METEORMetric<IString, String> meteor; // = null;
    if (System.getProperty("abg") != null) {
      String[] fields = System.getProperty("abg").split(":");
      double alpha = Double.parseDouble(fields[0]);
      double beta = Double.parseDouble(fields[1]);
      double gamma = Double.parseDouble(fields[2]);
      System.err.printf("Using alpha: %f beta: %f gamma: %f\n", alpha, beta,
          gamma);
      meteor = new METEORMetric<IString, String>(referencesList, alpha, beta,
          gamma);
    } else {
      meteor = new METEORMetric<IString, String>(referencesList);
    }
    METEORMetric<IString, String>.METEORIncrementalMetric incMetric = meteor
        .getIncrementalMetric();

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    for (String line; (line = reader.readLine()) != null;) {
      Sequence<IString> translation = IStrings.tokenize(line);
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
          translation, null, 0);
      incMetric.add(tran);
    }

    reader.close();

    System.out.printf("METEOR = %.3f\n", 100 * incMetric.score());
  }

}
