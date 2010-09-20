package edu.stanford.nlp.mt.metrics;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.PositionIndependentDistance;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;

public class PERMetric<TK, FV> extends AbstractMetric<TK, FV> {
  final List<List<Sequence<TK>>> referencesList;

  public PERMetric(List<List<Sequence<TK>>> referencesList) {
    this.referencesList = referencesList;
  }

  @Override
  public PERIncrementalMetric getIncrementalMetric() {
    return new PERIncrementalMetric();
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

  // //////////////////////////
  public class PERIncrementalMetric implements
      IncrementalEvaluationMetric<TK, FV> {
    List<Double> wordEdits = new ArrayList<Double>();
    List<Double> refLengths = new ArrayList<Double>();
    PositionIndependentDistance positionIndependentDistance = new PositionIndependentDistance();
    double editSum = 0;
    double lengthSum = 0;

    private double[] minimumPositionIndependentDistance(int id, Sequence<TK> seq) {

      Object[] outArr = (new RawSequence<TK>(seq)).elements;
      double minEd = Double.POSITIVE_INFINITY;
      double refCount = 0;
      double minErr = Double.POSITIVE_INFINITY;
      for (Sequence<TK> ref : referencesList.get(id)) {
        Object[] refArr = (new RawSequence<TK>(ref)).elements;
        double ed = positionIndependentDistance.score(outArr, refArr);
        double err = ed / refArr.length;
        // System.err.printf("%s\n%s\n(%f/%d)=%f\n",
        // seq,ref,ed,refArr.length,err);
        if (minErr > err) {
          minErr = err;
          minEd = ed;
          refCount = refArr.length;
        }
      }
      // editDistanceCache.put(key,retVal);
      return new double[] { minEd, refCount };
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> trans) {
      if (trans == null) {
        wordEdits.add(0.0);
        refLengths.add(0.0);
        return this;
      }
      int id = wordEdits.size();
      double[] minEdPair = minimumPositionIndependentDistance(id,
          trans.translation);
      wordEdits.add(-minEdPair[0]);
      refLengths.add(minEdPair[1]);
      editSum += -minEdPair[0];
      lengthSum += minEdPair[1];
      return this;
    }

    @Override
    public double maxScore() {
      return 1.0;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int id,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      double[] minEdPair = minimumPositionIndependentDistance(id,
          trans.translation);
      editSum -= wordEdits.get(id);
      lengthSum -= refLengths.get(id);
      editSum += -minEdPair[0];
      lengthSum += minEdPair[1];
      wordEdits.set(id, -minEdPair[0]);
      refLengths.set(id, minEdPair[1]);
      return this;
    }

    @Override
    public double score() {
      if (lengthSum == 0)
        return 0;
      System.err.printf("(edits:%f)/(ref length: %f)=%f\n", editSum, lengthSum,
          (editSum / lengthSum));
      return (editSum / lengthSum);
    }

    @Override
    public int size() {
      return wordEdits.size();
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

    public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new PERIncrementalMetric();
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err
          .println("Usage:\n\tjava PERMetric (ref 1) (ref 2) ... (ref n) < canidateTranslations\n");
      System.exit(-1);
    }
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args);

    PERMetric<IString, String> PER = new PERMetric<IString, String>(
        referencesList);
    PERMetric<IString, String>.PERIncrementalMetric incMetric = PER
        .getIncrementalMetric();

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    for (String line; (line = reader.readLine()) != null;) {
      line = NISTTokenizer.tokenize(line);
      line = line.replaceAll("\\s+$", "");
      line = line.replaceAll("^\\s+", "");
      Sequence<IString> translation = new RawSequence<IString>(
          IStrings.toIStringArray(line.split("\\s+")));
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
          translation, null, 0);
      incMetric.add(tran);
    }

    reader.close();

    System.out.printf("PER = %.3f %%\n", 100 * incMetric.score());
  }
}
