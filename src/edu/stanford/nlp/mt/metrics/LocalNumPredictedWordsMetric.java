package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.Sequence;

/**
 * Number of correctly predicted words under constrained decoding with a prefix.
 * The first file passed as a reference is the prefix.
 * 
 * @author Joern Wuebker
 *
 * @param <TK>
 * @param <FV>
 */
public class LocalNumPredictedWordsMetric<TK,FV> implements SentenceLevelMetric<TK, FV> {
  
  /**
   * Constructor.
   */
  public LocalNumPredictedWordsMetric() {
  }

  @Override
  public double score(int sourceId, Sequence<TK> source,
      List<Sequence<TK>> references, Sequence<TK> translation) {
    return NumPredictedWordsMetric.getNumPredictedWords(translation, references, sourceId);
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {}

  @Override
  public boolean isThreadsafe() {
    return true;
  }
}
