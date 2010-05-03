package edu.stanford.nlp.mt.decoder.recomb;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.feat.HierarchicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.HierarchicalReorderingFeaturizer.HierBlock;
import edu.stanford.nlp.mt.decoder.feat.CollapsedFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.StatefulFeaturizer;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Michel Galley
 */
public class MSDRecombinationFilter implements RecombinationFilter<Hypothesis<IString, String>> {

  // Don't set to true (this dramatically reduces the amount of recombination, and hurts search)
  private static final boolean HIERARCHICAL_RECOMBINATION = false;

  private final List<StatefulFeaturizer<IString,String>> hierFeaturizers = new LinkedList<StatefulFeaturizer<IString, String>>();

  public MSDRecombinationFilter(List<IncrementalFeaturizer<IString, String>> featurizers) {

    System.err.println("MSD recombination enabled.");

    if (HIERARCHICAL_RECOMBINATION) {
      Deque<IncrementalFeaturizer<IString,String>> tmpList = new LinkedList<IncrementalFeaturizer<IString,String>>(featurizers);

      while (!tmpList.isEmpty()) {
        IncrementalFeaturizer<IString,String> el = tmpList.removeLast();
        if (el instanceof CombinedFeaturizer) {
          tmpList.addAll(((CombinedFeaturizer<IString,String>)el).featurizers);
        } else if (el instanceof CollapsedFeaturizer) {
          tmpList.addAll(((CollapsedFeaturizer<IString,String>)el).featurizers);
        } else {
          if (el instanceof HierarchicalReorderingFeaturizer) {
            hierFeaturizers.add((StatefulFeaturizer<IString,String>)el);
            //System.err.println("ADD: "+el);
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
	public RecombinationFilter<Hypothesis<IString,String>> clone() {
		try {
			return (RecombinationFilter<Hypothesis<IString,String>>)super.clone();
		} catch (CloneNotSupportedException e) { return null; /* wnh */ }
	}

  private int lastOptionLeftEdge(Hypothesis<IString,String> hyp) {
		if (hyp.translationOpt == null)
			return -1;
    return hyp.translationOpt.foreignPos-1;
	}

  private int lastOptionRightEdge(Hypothesis<IString,String> hyp) {
		if (hyp.translationOpt == null)
			return 0;
    return hyp.translationOpt.foreignCoverage.length();
	}
	
	@Override
	public boolean combinable(Hypothesis<IString,String> hypA, Hypothesis<IString,String> hypB) {

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

    if (HIERARCHICAL_RECOMBINATION) {
      for (StatefulFeaturizer<IString,String> featurizer : hierFeaturizers) {
        HierBlock hbA = (HierBlock) hypA.featurizable.getState(featurizer);
        HierBlock hbB = (HierBlock) hypB.featurizable.getState(featurizer);
        //System.err.printf("CMP: %d<->%d %d<->%d\n", hbA.fStart(), hbA.fEnd(), hbB.fStart(), hbB.fEnd());
        if (hbA != null && hbB != null)
          if (hbA.fStart() != hbB.fStart() || hbA.fEnd() != hbB.fEnd()) {
            return false;
        }
      }
    }

    return true;
  }

	@Override
	public long recombinationHashCode(Hypothesis<IString,String> hyp) {
		return lastOptionRightEdge(hyp);
	}

}
