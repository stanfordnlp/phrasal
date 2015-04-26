package edu.stanford.nlp.mt.metrics;

import java.util.*;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

/**
 *
 * Metric that counts the average number of correctly predicted words.
 * Assumes that the scored hypothesis is generated with prefix-constrained decoding.
 * The file containing the prefixes is passed as the first argument (i.e. the first reference).
 *
 * @author Joern Wuebker
 *
 * @param <TK>
 */
public class NumPredictedWordsMetric<TK, FV> extends AbstractMetric<TK, FV> {
  final List<List<Sequence<TK>>> referencesList;

  /**
   *
   */
  public NumPredictedWordsMetric(List<List<Sequence<TK>>> referencesList) {
    if(referencesList == null ||
        referencesList.size() < 1 ||
        referencesList.get(0).size() < 2)
      throw new RuntimeException(
          "NumPredictedWordsMetric requires at least two arguments: the prefix file and one reference.");
    
    this.referencesList = referencesList;
  }

  @Override
  public NumPredictedWordsIncrementalMetric getIncrementalMetric() {
    return new NumPredictedWordsIncrementalMetric();
  }

  @Override
  public NumPredictedWordsIncrementalMetric getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  public class NumPredictedWordsIncrementalMetric implements
        IncrementalEvaluationMetric<TK, FV> {

    private int totalPredictedWords = 0;
    private List<Integer> predictedWords = new ArrayList<Integer>();

    NumPredictedWordsIncrementalMetric() {}
    
    /**
     *
     */
    private NumPredictedWordsIncrementalMetric(NumPredictedWordsIncrementalMetric m) {
      this.predictedWords = new ArrayList<Integer>(m.predictedWords);
      this.totalPredictedWords = m.totalPredictedWords;
    }
    
    @Override
    public int size() {
      throw new UnsupportedOperationException();
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
      return new NumPredictedWordsIncrementalMetric(this);
    }
    
    @Override
    public double maxScore() {
      return Integer.MAX_VALUE;
    }

    @Override
    public String scoreDetails() {
      return "Total predicted words: " + totalPredictedWords + "\n lines: " + predictedWords.size();
    }

    
    
    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> tran) {
      return add(tran == null ? null : tran.translation);
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        Sequence<TK> tran) {
      
      if(referencesList.size() <= predictedWords.size())
        throw new RuntimeException(
            "NumPredictedWordsMetric: insufficient number of references.");
      
      if (tran == null) {
        predictedWords.add(0);
        return this;
      }
      
      int predW = getNumPredictedWords(tran, referencesList.get(predictedWords.size()), predictedWords.size()) ;
      
      totalPredictedWords += predW;
      predictedWords.add(predW);
      return this;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int id,
        ScoredFeaturizedTranslation<TK, FV> trans) { 
      totalPredictedWords -= predictedWords.get(id);
      predictedWords.set(id, getNumPredictedWords(trans.translation, referencesList.get(id), id));
      totalPredictedWords += predictedWords.get(id);
      return this;
    }

    @Override
    public double score() {
      double rv = (double) totalPredictedWords / (double) predictedWords.size();
      // will be multiplied by 100 in edu.stanford.nlp.mt.tools.Evaluate
      return rv / 100;
    }

  }
  
  public static <TK> int getNumPredictedWords(Sequence<TK> tran, List<Sequence<TK>> references, int id) {
    // the first reference is actually the prefix
    assert(references.size() > 1);
    
    Sequence<TK> prefix = references.get(0);
    
    if(!tran.startsWith(prefix))
      throw new RuntimeException(
          "NumPredictedWordsMetric: hypothesis in line " + id + " does not conform to prefix.");
    
    boolean foundRef = false;
    int maxPredictedWords = 0;
    
    for(int i = 1; i < references.size(); ++i) {
      Sequence<TK> ref = references.get(i);
      if(!ref.startsWith(prefix))
        continue;
      
      foundRef = true;
      int predictedWords = 0;
      for(int j = prefix.size(); tran.get(j).equals(ref.get(j)); ++j) {
        predictedWords++;
      }
      
      maxPredictedWords = Math.max(maxPredictedWords, predictedWords);
    }
    
    if(!foundRef)
      throw new RuntimeException(
          "NumPredictedWordsMetric: No reference found with correct prefix in line " + id);
    
    return maxPredictedWords;
  }
  
  @Override
  public double maxScore() {
    return Integer.MAX_VALUE;
  }
}
