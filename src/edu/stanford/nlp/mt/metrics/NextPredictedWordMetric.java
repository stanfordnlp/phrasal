package edu.stanford.nlp.mt.metrics;

import java.util.*;

import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

/**
 *
 * Metric that computes the rate of correctly predicting the next word after a given prefix.
 * The file containing the prefixes is passed as the first argument (i.e. the first reference).
 *
 * @author Joern Wuebker
 *
 * @param <TK>
 */
public class NextPredictedWordMetric<TK, FV> extends NumPredictedWordsMetric<TK, FV> {

  /**
   *
   */
  public NextPredictedWordMetric(List<List<Sequence<TK>>> referencesList) {
    super(referencesList);
  }

  @Override
  public NextPredictedWordIncrementalMetric getIncrementalMetric() {
    return new NextPredictedWordIncrementalMetric();
  }


  public class NextPredictedWordIncrementalMetric extends
        NumPredictedWordsIncrementalMetric {

    NextPredictedWordIncrementalMetric() {}
    
    /**
     *
     */
    private NextPredictedWordIncrementalMetric(NextPredictedWordIncrementalMetric m) {
      this.predictedWords = new ArrayList<Integer>(m.predictedWords);
      this.totalPredictedWords = m.totalPredictedWords;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new NextPredictedWordIncrementalMetric(this);
    }
    
    
    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        Sequence<TK> tran) {
      return add(tran, 1);
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int id,
        ScoredFeaturizedTranslation<TK, FV> trans) { 
      totalPredictedWords -= predictedWords.get(id);
      predictedWords.set(id, getNumPredictedWords(trans.translation, referencesList.get(id), id, 1));
      totalPredictedWords += predictedWords.get(id);
      return this;
    }

  }
}
