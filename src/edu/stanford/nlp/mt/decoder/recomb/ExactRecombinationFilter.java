package edu.stanford.nlp.mt.decoder.recomb;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.MurmurHash;


/**
 * Recombination filter that considers all featurizer states.
 * 
 * @author Spence Green
 *
 * @param <T>
 */
public class ExactRecombinationFilter<T> implements
RecombinationFilter<Derivation<IString, String>> {

  private static final FeaturizerState IDENTITY_STATE = new FeaturizerState() {
    @Override
    public boolean equals(Object other) {
      // equality by reference
      return this == other;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  };
  
  private final List<DerivationFeaturizer<IString, String>> featurizers;
  private final RecombinationFilter<Derivation<IString, String>> sourceCoverageFilter;

  public ExactRecombinationFilter(List<Featurizer<IString, String>> featurizers) {
    this.featurizers = new LinkedList<>();
    for (Featurizer<IString, String> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        this.featurizers.add((DerivationFeaturizer<IString, String>) featurizer);
      }
    }
    sourceCoverageFilter = new ForeignCoverageRecombinationFilter<IString, String>();
  }

  @Override
  public boolean combinable(Derivation<IString, String> hypA,
      Derivation<IString, String> hypB) {
    if (hypA.featurizable == null && hypB.featurizable == null) {
      // null hypothesis
      return true;
    } else if (hypA.featurizable == null || hypB.featurizable == null) {
      // one or the other is the null hypothesis
      return false;
    }

    // Check coverage
    if ( ! sourceCoverageFilter.combinable(hypA, hypB)) {
      return false; 
    }

    // Check other stateful featurizers
    for (DerivationFeaturizer<IString, String> featurizer : featurizers) {
      FeaturizerState stateA = (FeaturizerState) hypA.featurizable.getState(featurizer);
      stateA = stateA == null ? IDENTITY_STATE : stateA;
      FeaturizerState stateB = (FeaturizerState) hypB.featurizable.getState(featurizer);
      stateB = stateB == null ? IDENTITY_STATE : stateB;
      
      // Do the two states hash to the same bucket?
      if ( ! (stateA.hashCode() == stateB.hashCode() &&
              stateA.equals(stateB))) {
        return false;
      }
    }
    // All states match.
    return true;
  }

  @Override
  public long recombinationHashCode(Derivation<IString, String> hyp) {
    if (hyp.featurizable == null) {
      // null hypothesis. This hashCode doesn't actually matter because of the checks
      // in the combinable() function above.
      return hyp.sourceSequence.hashCode();
    }

    // Generate a hash code from individual hash codes
    int[] stateHashCodes = new int[featurizers.size()];
    int i = 0;
    for (DerivationFeaturizer<IString, String> featurizer : featurizers) {
      FeaturizerState state = (FeaturizerState) hyp.featurizable.getState(featurizer);
      stateHashCodes[i++] = state == null ? 0 : state.hashCode();
    }
    return MurmurHash.hash64(stateHashCodes, stateHashCodes.length, 1);
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
