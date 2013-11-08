package edu.stanford.nlp.mt.decoder.recomb;

import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.base.DTULinearDistortionFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.LinearFutureCostFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.util.Generics;

/**
 * 
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public class LinearDistortionRecombinationFilter<TK, FV> implements
RecombinationFilter<Derivation<TK, FV>> {

  private final DerivationFeaturizer<TK, FV> distortionFeaturizer;

  public LinearDistortionRecombinationFilter(List<Featurizer<TK, FV>> featurizers) {
    List<DerivationFeaturizer<TK,FV>> distortionFeaturizers = Generics.newLinkedList();
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof LinearFutureCostFeaturizer ||
          featurizer instanceof DTULinearDistortionFeaturizer) {
        distortionFeaturizers.add((DerivationFeaturizer<TK,FV>) featurizer);
      }
    }
    if (distortionFeaturizers.size() > 1) {
      throw new RuntimeException("Recombination only supports one distortion cost estimate!");
    }
    distortionFeaturizer = distortionFeaturizers.get(0);
  }

  @Override
  public boolean combinable(Derivation<TK, FV> hypA, Derivation<TK, FV> hypB) {
    if (hypA.featurizable == null && hypB.featurizable == null) {
      // null hypothesis
      return true;
    } else if (hypA.featurizable == null || hypB.featurizable == null) {
      // one or the other is the null hypothesis
      return false;
    }
    FeaturizerState stateA = (FeaturizerState) hypA.featurizable.getState(distortionFeaturizer);
    FeaturizerState stateB = (FeaturizerState) hypB.featurizable.getState(distortionFeaturizer);

    // Do the two states hash to the same bucket?
    return stateA.hashCode() == stateB.hashCode() && stateA.equals(stateB);
  }

  @Override
  public long recombinationHashCode(Derivation<TK, FV> hyp) {
    if (hyp.featurizable == null) {
      return 0;
    }
    FeaturizerState state = (FeaturizerState) hyp.featurizable.getState(distortionFeaturizer);
    return state.hashCode();
  }

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
