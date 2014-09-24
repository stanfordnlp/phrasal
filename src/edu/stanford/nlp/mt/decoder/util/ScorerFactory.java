package edu.stanford.nlp.mt.decoder.util;

import java.io.IOException;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;

/**
 * Initialize a Scorer.
 *
 * @author danielcer
 * @author Spence Green
 *
 */
public class ScorerFactory {

  public static final String DENSE_SCORER = "staticscorer";
  public static final String UNIFORM_SCORER = "uniformscorer";
  public static final String SPARSE_SCORER = "sparsescorer";
  public static final String DEFAULT_SCORER = UNIFORM_SCORER;

  public static final String STATIC_SCORER_INLINE = "inline";
  public static final String STATIC_SCORER_FILE = "file";

  private ScorerFactory() {}

  /**
   * Creates a scorer.
   *
   * @throws IOException
   */
  public static Scorer<String> factory(String scorerName, Counter<String> config, Index<String> featureIndex)
      throws IOException {

    switch (scorerName) {
      case UNIFORM_SCORER:
        return new UniformScorer<String>();
      case DENSE_SCORER:
        return new DenseScorer(config, featureIndex);
      case SPARSE_SCORER:
        return new SparseScorer(config, featureIndex);
    }

    throw new RuntimeException(String.format("Unknown scorer \"%s\"",
        scorerName));
  }

}
