package edu.stanford.nlp.mt.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import edu.stanford.nlp.util.EditDistance;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.ArraySequence;

public class WERMetric<TK, FV> extends AbstractMetric<TK, FV> {
  final List<List<Sequence<TK>>> referencesList;

  public WERMetric(List<List<Sequence<TK>>> referencesList) {
    this.referencesList = referencesList;
  }

  @Override
  public WERIncrementalMetric getIncrementalMetric() {
    return new WERIncrementalMetric();
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
    return 0.0;
  }

  // Map editDistanceCache = new HashMap();
  static final Map<IString, IString> collapseMap = new HashMap<IString, IString>();

  static {
    collapseMap.put(new IString("el"), new IString("l"));
    collapseMap.put(new IString("en"), new IString("n"));
    collapseMap.put(new IString("sh"), new IString("zh"));
    collapseMap.put(new IString("ao"), new IString("aa"));
    collapseMap.put(new IString("ih"), new IString("ix"));
    collapseMap.put(new IString("ah"), new IString("ax"));
    collapseMap.put(new IString("sil"), new IString("epi"));
    collapseMap.put(new IString("cl"), new IString("epi"));
    collapseMap.put(new IString("vcl"), new IString("epi"));
  }

  public class WERIncrementalMetric implements
      IncrementalEvaluationMetric<TK, FV> {
    List<Double> wordEdits = new ArrayList<Double>();
    List<Double> refLengths = new ArrayList<Double>();
    EditDistance editDistance = new EditDistance(false);
    double editSum = 0;
    double lengthSum = 0;

    private void collapseObjects(Object[] arr) {
      for (int i = 0; i < arr.length; i++) {
        assert (arr[i] instanceof IString);
        IString arrI = (IString) arr[i];
        Object collapseTo = collapseMap.get(arrI);
        if (collapseTo != null) {
          arr[i] = collapseTo;
        }
      }
    }

    private double[] minimumEditDistance(int id, Sequence<TK> seq) {
      /*
       * String key = String.format("%d||| %s\n", id, seq);
       * 
       * double[] cacheVal = (double[])editDistanceCache.get(key); if (cacheVal
       * != null) { System.err.println("Using cached value\n"); return cacheVal;
       * }
       */

      Object[] outArr = (new ArraySequence<TK>(seq)).elements();
      collapseObjects(outArr);
      double minEd = Double.POSITIVE_INFINITY;
      double refCount = 0;
      double minErr = Double.POSITIVE_INFINITY;
      for (Sequence<TK> ref : referencesList.get(id)) {
        Object[] refArr = (new ArraySequence<TK>(ref)).elements();
        collapseObjects(refArr);
        double ed = editDistance.score(outArr, refArr);
        double err = ed / refArr.length;
        // System.err.printf("%s\n%s\n(%f/%d)=%f\n",
        // seq,ref,ed,refArr.length,err);
        if (minErr > err) {
          minErr = err;
          minEd = ed;
          refCount = refArr.length;
        }
      }
      return new double[] { minEd, refCount };
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> trans) {
      return add(trans == null ? null : trans.translation);
    }
    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        Sequence<TK> trans) {
      if (trans == null) {
        wordEdits.add(0.0);
        refLengths.add(0.0);
        return this;
      }
      int id = wordEdits.size();
      double[] minEdPair = minimumEditDistance(id, trans);
      wordEdits.add(-minEdPair[0]);
      refLengths.add(minEdPair[1]);
      editSum += -minEdPair[0];
      lengthSum += minEdPair[1];
      return this;
    }

    @Override
    public double maxScore() {
      return 0.0;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int id,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      double[] minEdPair = minimumEditDistance(id, trans.translation);
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

    @Override
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new WERIncrementalMetric();
    }
    
    @Override
    public String scoreDetails() {
      return "None";
    }
  }
}
