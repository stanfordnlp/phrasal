package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.Sequence;

/**
 * Computes the rate of correctly predicting the next word under constrained decoding with a prefix.
 * The first file passed as a reference is the prefix.
 * 
 * @author Joern Wuebker
 *
 * @param <TK>
 * @param <FV>
 */
public class LocalNextPredictedWordMetric<TK,FV> implements SentenceLevelMetric<TK, FV> {
  
  /**
   * Constructor.
   */
  public LocalNextPredictedWordMetric() {
  }

  @Override
  public double score(int sourceId, Sequence<TK> source,
      List<Sequence<TK>> references, Sequence<TK> translation) {
    return NumPredictedWordsMetric.getNumPredictedWords(translation, references, sourceId,1);
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {}

  @Override
  public boolean isThreadsafe() {
    return true;
  }
}