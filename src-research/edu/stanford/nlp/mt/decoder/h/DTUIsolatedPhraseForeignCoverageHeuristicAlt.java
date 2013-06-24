package edu.stanford.nlp.mt.decoder.h;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.IntPair;

import java.util.Iterator;

/**
 * @author Michel Galley
 */
public class DTUIsolatedPhraseForeignCoverageHeuristicAlt<TK, FV> extends
    DTUIsolatedPhraseForeignCoverageHeuristic<TK, FV> {

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  public DTUIsolatedPhraseForeignCoverageHeuristicAlt(
      RuleFeaturizer<TK, FV> phraseFeaturizer) {
    super(phraseFeaturizer);
  }

  /**
   * For a given coverage set C of a given hypothesis, this finds all contiguous
   * segments in the coverage set (e.g., if C = {1,5,6,10}, then segments are
   * {5-6} and {10}), and computes future cost as follows: fcost(C) =
   * fcost(1-10) - fcost(5-6) - fcost(10).
   * 
   * This does not overestimate future cost (as opposed to
   * getHeuristicDeltaStandard), even when dealing with discontinuous phrases.
   * However, future cost estimate is often poorer than with
   * getHeuristicDeltaStandard.
   */
  @Override
  public double getHeuristicDelta(Hypothesis<TK, FV> hyp,
      CoverageSet newCoverage) {

    double oldH = hyp.preceedingHyp.h;

    CoverageSet coverage = hyp.sourceCoverage;
    int startEdge = coverage.nextClearBit(0);
    int endEdge = hyp.sourceSequence.size() - 1;
    if (endEdge < startEdge)
      return 0.0;

    double newH = hSpanScores.getScore(startEdge, endEdge);

    for (Iterator<IntPair> it = coverage.getSegmentIterator(); it.hasNext();) {
      IntPair span = it.next();
      if (span.getSource() < startEdge) { // skip:
        assert (span.getTarget() <= startEdge);
        continue;
      }
      double localH = hSpanScores.getScore(span.getSource(), span.getTarget());
      if (!Double.isNaN(localH) && !Double.isInfinite(localH)) {
        newH -= localH;
      }
    }

    if ((Double.isInfinite(newH) || newH == MINUS_INF)
        && (Double.isInfinite(oldH) || oldH == MINUS_INF))
      return 0.0;

    double deltaH = newH - oldH;
    ErasureUtils.noop(deltaH);
    return deltaH;
  }

}
