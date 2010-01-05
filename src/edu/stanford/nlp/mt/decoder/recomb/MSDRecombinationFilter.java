package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;

/**
 * @author Michel Galley
 */
public class MSDRecombinationFilter<TK, FV> implements RecombinationFilter<Hypothesis<TK, FV>> {

  @SuppressWarnings("unchecked")
	public RecombinationFilter<Hypothesis<TK,FV>> clone() {
		try {
			return (RecombinationFilter<Hypothesis<TK,FV>>)super.clone(); 
		} catch (CloneNotSupportedException e) { return null; /* wnh */ }
	}

  private int lastOptionLeftEdge(Hypothesis<TK,FV> hyp) {
		if (hyp.translationOpt == null)
			return -1;
    return hyp.translationOpt.foreignPos-1;
	}

  private int lastOptionRightEdge(Hypothesis<TK,FV> hyp) {
		if (hyp.translationOpt == null)
			return 0;
    return hyp.translationOpt.foreignCoverage.length();
	}
	
	@Override
	public boolean combinable(Hypothesis<TK, FV> hypA, Hypothesis<TK, FV> hypB) {
		if (lastOptionRightEdge(hypA) != lastOptionRightEdge(hypB))
      // same as LinearDistortionRecombinationFilter:
      return false;
    int leftA = lastOptionLeftEdge(hypA);
    int leftB = lastOptionLeftEdge(hypB);
    if (leftA == leftB)
      return true;
    // Now hypA and hypB may look like this:
    // hypA: y y . . . . x x . . . z z
    // hypB: y y y . . x x x . . . z z
    // where y,z stand for coverage set of previous options (the number of y and z may be zero),
    // and x stands for coverage set of the current option.
    // If the next option (represented with n's) is generated to the right of x's,
    // hypA and hypB will always receive the same orientation, i.e.:
    // (A) Both monotone:
    // hypA: y y . . . . x x n n . z z
    // hypB: y y y . . x x x n n . z z
    // (B) Both discontinuous:
    // hypA: y y . . . . x x . n n z z
    // hypB: y y y . . x x x . n n z z
    // If the next option is generated to the left, we have two cases:
    // (C) Both discontinuous:
    // hypA: y y . n . . x x . . . z z
    // hypB: y y y n . x x x . . . z z
    // (D) One discontinuous, one swap:
    // hypA: y y . . n . x x . . . z z
    // hypB: y y y . n x x x . . . z z
    // So we only need to worry about case (D). The function should return false
    // if case (D) is possible. The condition that makes (D) impossible is:
    // the number of words between the last y and the first x is zero for either
    // hypA or hypB. In this condition is true, (D) is impossible, thus
    // the next option will always receive the same orientation (no matter where
    // it appears), thus hypA and hypB are combinable, thus return true.

    if (leftA < 0 || leftB < 0)
      // Nothing to the left of either hypA or hypB, so (D) is impossible:
      return true;

    if (!hypA.foreignCoverage.get(leftA) && !hypB.foreignCoverage.get(leftA)) {
      // (D) is possible as shown here:
      // hypA: y y . . n x x x . . . z z
      // hypB: y y y . n . x x . . . z z
      return false;
    }

    if (!hypA.foreignCoverage.get(leftB) && !hypB.foreignCoverage.get(leftB)) {
      // (D) is possible as shown here:
      // hypA: y y . . n . x x . . . z z
      // hypB: y y y . n x x x . . . z z
      return false;
    }

    return true;
  }

	@Override
	public long recombinationHashCode(Hypothesis<TK, FV> hyp) {
		return lastOptionRightEdge(hyp);
	}

}
