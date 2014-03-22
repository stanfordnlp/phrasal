package edu.stanford.nlp.mt.tune;

import edu.stanford.nlp.stats.Counter;

/**
 * @author Michel Galley, Daniel Cer
 */
public interface BatchOptimizer {

  public Counter<String> optimize(Counter<String> initialWts);

  public boolean doNormalization();
}
