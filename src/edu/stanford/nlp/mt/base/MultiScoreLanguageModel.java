package edu.stanford.nlp.mt.base;

/**
 * 
 * @author daniel cer
 *
 * @param <T>
 */
public interface MultiScoreLanguageModel<T> extends LanguageModel<T> {
  double[] multiScore(Sequence<T> sequence);
}
