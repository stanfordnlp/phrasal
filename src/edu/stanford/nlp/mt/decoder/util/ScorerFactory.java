package edu.stanford.nlp.mt.decoder.util;

import java.io.*;

import edu.stanford.nlp.stats.Counter;

/**
 * 
 * @author danielcer
 * 
 */
public class ScorerFactory {

  static public String STATIC_SCORER = "staticscorer";
  static public String UNIFORM_SCORER = "uniformscorer";
  static public String DEFAULT_SCORER = UNIFORM_SCORER;

  static public String STATIC_SCORER_INLINE = "inline";
  static public String STATIC_SCORER_FILE = "file";

  private ScorerFactory() {
  }

  /**
   * 
   * @throws IOException
   */
  static public Scorer<String> factory(String scorerName, Counter<String> config)
      throws IOException {

    if (scorerName.equals(UNIFORM_SCORER)) {
      return new UniformScorer<String>();
    } else if (scorerName.equals(STATIC_SCORER)) {
      return new StaticScorer(config);
    }

    throw new RuntimeException(String.format("Unknown scorer \"%s\"",
        scorerName));
  }
}
