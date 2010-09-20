package edu.stanford.nlp.mt.metrics;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * Linear combination of any number of metrics, e.g., BLEU minus TER.
 * Note that this class does not support some of the advanced features of
 * BLEUMetric, such as recombination based on identical ngram counts.
 * 
 * @author Michel Galley
 */
public class LinearCombinationMetric<TK,FV> extends AbstractMetric<TK,FV> {

  enum MetricType { full, bp, precision }

  final boolean DEBUG = System.getProperty("debugLinearCombination") != null;

  final double[] weights;
  final EvaluationMetric<TK,FV>[] metrics;
  final MetricType[] metricTypes;
  final Map<TK,FV>[] metricProperties;

  @SuppressWarnings("unchecked")
	public LinearCombinationMetric(double[] weights, EvaluationMetric<TK,FV>... metrics) {
    System.err.printf("LinearCombinationMetric: weights=%s metrics=%s\n", 
      Arrays.toString(weights), Arrays.toString(metrics));
    if(weights.length != metrics.length)
      throw new IllegalArgumentException();
    this.weights = weights;
    this.metrics = metrics;
		int sz = metrics.length;
		this.metricTypes = new MetricType[sz];
		this.metricProperties = new Map[sz];
    for(int i=0; i<sz; ++i)
      metricTypes[i] = MetricType.full;
  }

  public void setWeights(double[] w) {
    assert(w.length == weights.length);
    System.arraycopy(w,0,weights,0,w.length);
  }

  public double maxScore() {
    double maxScore = 0.0;
    for(int i=0; i<weights.length; ++i)
      maxScore += weights[i]*maxScore(i);
    return maxScore;
  }

	private double maxScore(int i) {
    switch(metricTypes[i]) {
      case full:
        return metrics[i].maxScore();
      case bp:
      case precision:
      default:
        return 1.0;
    }
  }

  @Override
	public LCIncrementalMetric getIncrementalMetric() {
		return new LCIncrementalMetric();
	}

  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric(NBestListContainer<TK, FV> nbestList) {
    return new LCIncrementalMetric(nbestList);
  }

  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  public class LCIncrementalMetric implements IncrementalEvaluationMetric<TK,FV> {

    List<IncrementalEvaluationMetric<TK,FV>> iems = new ArrayList<IncrementalEvaluationMetric<TK,FV>>();

    LCIncrementalMetric() {
      for(EvaluationMetric<TK,FV> metric :  metrics)
        iems.add(metric.getIncrementalMetric());
    }

    @SuppressWarnings("unchecked")
    LCIncrementalMetric(LCIncrementalMetric o) {
      List<IncrementalEvaluationMetric<TK,FV>> oiems = o.iems;
      for(IncrementalEvaluationMetric<TK,FV> oiem : oiems) {
        try {
          iems.add((IncrementalEvaluationMetric<TK,FV>) oiem.clone());
        } catch (CloneNotSupportedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    LCIncrementalMetric(NBestListContainer<TK, FV> nbest) {
      for(EvaluationMetric<TK,FV> metric :  metrics)
        iems.add(metric.getIncrementalMetric(nbest));
    }

    public IncrementalEvaluationMetric<TK, FV> add(ScoredFeaturizedTranslation<TK, FV> trans) {
      for(IncrementalEvaluationMetric<TK,FV> iem :  iems)
        iem.add(trans);
      return this;
    }

    public IncrementalEvaluationMetric<TK, FV> replace(int index, ScoredFeaturizedTranslation<TK, FV> trans) {
      for(IncrementalEvaluationMetric<TK,FV> iem :  iems)
        iem.replace(index, trans);
      return this;
    }

    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      throw new UnsupportedOperationException();
    }

    public double score() {
      double score = 0.0;
      for(int i=0; i<weights.length; ++i) {
        double scorei = score(i);
        score += weights[i] * scorei;
        if(DEBUG) System.err.printf("w[%d]=%f\tscore[%d]=%f\n", i, weights[i], i, scorei);
      }
      if(DEBUG) System.err.printf("score=%f\n", score);
      return score;
    }

    private double score(int i) {
      IncrementalEvaluationMetric<TK,FV> m = iems.get(i); 
      switch(metricTypes[i]) {
        case full:
					return m.score();
        case bp:
        case precision:
        default:
          throw new RuntimeException("Not yet implemented.");
      }
    }

    public double partialScore() {
      throw new UnsupportedOperationException();
    }

    public State<IncrementalEvaluationMetric<TK, FV>> parent() {
      throw new UnsupportedOperationException();
    }

    public int depth() {
      throw new UnsupportedOperationException();
    }

    public double maxScore() {
      double maxScore = 0.0;
      for(int i=0; i<weights.length; ++i)
      maxScore += weights[i]*maxScore(i);
      return maxScore;
    }

    private double maxScore(int i) {
      switch(metricTypes[i]) {
        case full:
          return iems.get(i).maxScore();
        case bp:
        case precision:
        default:
          return 1.0;
      }
    }

    private boolean checkSize(int size) {
      for(int i=1; i<iems.size(); ++i)
        if(iems.get(i).size() != size)
          return false;
      return true;
    }

    public int size() {
      int sz = iems.get(0).size();
      assert(checkSize(sz));
      return sz;
    }

    @Override
		public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new LCIncrementalMetric(this);
    }
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage:\n\tjava LinearCombinationMetric (ref 1) (ref 2) ... (ref n) < canidateTranslations\n");
      System.exit(-1);
    }
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args, true);

    TERMetric<IString,String> ter = new TERMetric<IString,String>(referencesList);
    TERMetric<IString,String>.TERIncrementalMetric terIncMetric = ter.getIncrementalMetric();

		BLEUMetric<IString,String> bleu = new BLEUMetric<IString,String>(referencesList,true);
		BLEUMetric<IString,String>.BLEUIncrementalMetric bleuIncMetric = bleu.getIncrementalMetric();

		LinearCombinationMetric<IString,String> lc = new LinearCombinationMetric<IString,String>(new double[] {1.0,1.0}, bleu, ter);
		LinearCombinationMetric<IString,String>.LCIncrementalMetric lcIncMetric = lc.getIncrementalMetric();

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in));

    for (String line; (line = reader.readLine()) != null; ) {
      line = NISTTokenizer.tokenize(line);
      line = line.replaceAll("\\s+$", "");
      line = line.replaceAll("^\\s+", "");
      Sequence<IString> translation = new RawSequence<IString>(IStrings.toIStringArray(line.split("\\s+")));
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(translation, null, 0);
      terIncMetric.add(tran);
      bleuIncMetric.add(tran);
      lcIncMetric.add(tran);
    }
    reader.close();

    double d = (lcIncMetric.score()-terIncMetric.score()-bleuIncMetric.score());
    if(Math.abs(d) > 1e-3)
      throw new RuntimeException("Error = "+d);
    System.out.printf("TER-BLEU = %.3f\nTER = %.3f\n", -100*lcIncMetric.score(),-100*terIncMetric.score());
    double[] ngramPrecisions = bleuIncMetric.ngramPrecisions();
		System.out.printf("BLEU = %.3f, ", 100*bleuIncMetric.score());
		for (int i = 0; i < ngramPrecisions.length; i++) {
			if (i != 0) {
				System.out.print("/");
			}
			System.out.printf("%.3f", ngramPrecisions[i]*100);
		}
		System.out.printf(" (BP=%.3f, ration=%.3f %d/%d)\n", bleuIncMetric.brevityPenalty(), ((1.0*bleuIncMetric.candidateLength())/bleuIncMetric.effectiveReferenceLength()),
				 bleuIncMetric.candidateLength(), bleuIncMetric.effectiveReferenceLength());
		
		System.out.printf("\nPrecision Details:\n");
		int[][] precCounts = bleuIncMetric.ngramPrecisionCounts();
		for (int i = 0; i < ngramPrecisions.length; i++) {
			System.out.printf("\t%d:%d/%d\n", i, precCounts[i][0], precCounts[i][1]);
		}
  }
}
