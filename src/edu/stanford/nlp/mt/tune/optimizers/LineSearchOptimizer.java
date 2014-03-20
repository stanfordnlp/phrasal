package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.mt.decoder.feat.base.WordPenaltyFeaturizer;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Line Search optimizer to tune one single feature. If no feature name is
 * previded, LineSearchOptimizer tunes the word penalty (useful when one only
 * needs to make translation a little bit shorter or longer).
 * 
 * @author Michel Galley
 */
public class LineSearchOptimizer extends AbstractBatchOptimizer {

  static public final boolean DEBUG = false;

  private final String featureName;

  public LineSearchOptimizer(MERT mert) {
    super(mert);
    featureName = WordPenaltyFeaturizer.FEATURE_NAME;
  }

  public LineSearchOptimizer(MERT mert, String featureName) {
    super(mert);
    this.featureName = featureName;
  }

  @Override
  public Counter<String> optimize(final Counter<String> initialWts) {
    Counter<String> dir = new ClassicCounter<String>();
    dir.incrementCount(featureName, 1.0);
    return mert.lineSearch(nbest, initialWts, dir, emetric);
  }
}