package edu.stanford.nlp.mt.reranker;

/**
 * @author Pi-Chuan Chang
 */
public interface Scorer {
  void reset();

  void add(Stats stats);

  void sub(Stats stats);

  double score();
}
