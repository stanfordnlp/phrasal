package edu.stanford.nlp.mt.decoder.util;

import edu.stanford.nlp.mt.base.CoverageSet;

/**
 * 
 * @author danielcer
 * 
 */
public class HypothesisBeams {
  private HypothesisBeams() {
  }

  /**
   * 
   * @param <TK>
   * @param <FV>
   */
  static public <TK, FV> CoverageSet coverageIntersection(
      Iterable<Hypothesis<TK, FV>> hypotheses) {
    CoverageSet c = null;
    for (Hypothesis<TK, FV> hyp : hypotheses) {
      if (c == null) {
        c = new CoverageSet();
        c.or(hyp.sourceCoverage);
      } else {
        c.and(hyp.sourceCoverage);
      }
    }
    return (c == null ? new CoverageSet() : c);
  }
}
