package edu.stanford.nlp.mt.tune;

import edu.stanford.nlp.mt.tune.optimizers.CrossEntropyOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.ExpectedBLEUOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.MIRA1BestHopeFearOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.PairwiseRankingOptimizerSGD;
import edu.stanford.nlp.mt.util.IString;

/**
 * Get an instance of an online optimizer.
 * 
 * @author Spence Green
 *
 */
public final class OnlineOptimizerFactory {

  // Static class
  private OnlineOptimizerFactory() {}

  /**
   * Get an instance of an online optimizer, which is a loss function with configured update
   * rules.
   * 
   * @param optimizerAlg
   * @param optimizerFlags
   * @param tuneSetSize
   * @param expectedNumFeatures
   * @return
   */
  public static OnlineOptimizer<IString, String> configureOptimizer(String optimizerAlg, String[] optimizerFlags,
      int tuneSetSize, int expectedNumFeatures) {
    if (optimizerAlg == null) throw new IllegalArgumentException();

    switch (optimizerAlg) {
    case "mira-1best":
      return new MIRA1BestHopeFearOptimizer(optimizerFlags);

    case "pro-sgd":
      return new PairwiseRankingOptimizerSGD(tuneSetSize, expectedNumFeatures, optimizerFlags);

    case "expectedBLEU":
      return new ExpectedBLEUOptimizer(tuneSetSize, expectedNumFeatures, optimizerFlags);

    case "crossentropy":
      return new CrossEntropyOptimizer(tuneSetSize, expectedNumFeatures, optimizerFlags);

    default:
      throw new IllegalArgumentException("Unsupported optimizer: " + optimizerAlg);
    }
  }
}
