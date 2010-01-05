package edu.stanford.nlp.mt.tune;

import edu.stanford.nlp.stats.Counter;

/**
 * @author Michel Galley
 */
public interface NBestOptimizer {

  public Counter<String> optimize(Counter<String> initialWts);
  
}
