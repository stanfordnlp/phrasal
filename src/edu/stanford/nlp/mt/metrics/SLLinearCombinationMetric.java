package edu.stanford.nlp.mt.metrics;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Sentence Level Linear Combination Metric
 *
 *
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class SLLinearCombinationMetric<TK,FV> implements SentenceLevelMetric<TK, FV> {

  public static final boolean VERBOSE = false;

  final List<SentenceLevelMetric<TK,FV>> metrics; 
  final double wts[];

  public SLLinearCombinationMetric(double[] wts, List<SentenceLevelMetric<TK,FV>> metrics) {
    this.metrics = new ArrayList<SentenceLevelMetric<TK,FV>>(metrics);
    this.wts = new double[wts.length];
    System.arraycopy(wts, 0, this.wts, 0, wts.length);
  } 

  @Override
  public double score(int sourceId, Sequence<TK> source, List<Sequence<TK>> references, Sequence<TK> translation) {

    int minLength = Integer.MAX_VALUE;
    for (Sequence<TK> sentence : references) {
      if (sentence.size() < minLength) {
        minLength = sentence.size();
      }
    }

    double score = 0; 
    for (int i = 0; i < wts.length; i++) {
       double mscore = metrics.get(i).score(sourceId, null, references, translation);
       if (VERBOSE) {
         System.err.printf("+= %.2f * %.3f (/%d = %.3f)\n", wts[i], mscore*100,
           minLength, (mscore/minLength)*100);
       } 
       score += wts[i] * mscore;
    }
    return score;
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
      for (SentenceLevelMetric metric : metrics) {
         metric.update(sourceId, references, translation);
      }
  }

  @Override
  public boolean isThreadsafe() {
    for (SentenceLevelMetric metric : metrics) {
      if (!metric.isThreadsafe()) return false;
    }
    return true;
  } 

  public static void main(String[] args) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    SentenceLevelMetric metric = SentenceLevelMetricFactory.getMetric(args[0], null);

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      String[] fields = line.split("\\|\\|\\|");
      Sequence<IString> hyp = IStrings.tokenize(fields[0]);
      List<Sequence<IString>> refs = new ArrayList<Sequence<IString>>();

      for (int i = 1; i < fields.length; i++) {
        refs.add(IStrings.tokenize(fields[i]));
      }

      int minLength = Integer.MAX_VALUE;
      for (Sequence<IString> sentence : refs) {
        if (sentence.size() < minLength) { 
          minLength = sentence.size();
        }
      }

      double[] rWeights = new double[refs.size()];
      Arrays.fill(rWeights, 1); 
      double mscore = metric.score(0, null, refs, hyp);
      System.out.printf("isThreadsafe: %s\n", metric.isThreadsafe());
      System.out.printf("%s: %.3f (/%d = %.3f)\n", args[0], mscore*100, minLength, (mscore/minLength)*100);
    }
  }
}
