package edu.stanford.nlp.mt.decoder.recomb;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.util.IString;


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
    sourceCoverageFilter = new SourceCoverageRecombinationFilter<IString, String>();
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
    long code = 0x87c37b91114253d5L ^ (featurizers.size()*0x4cf5ad432745937fL);
    for (int i = 0, sz = featurizers.size(); i < sz; ++i) {
      DerivationFeaturizer<IString, String> featurizer = featurizers.get(i);
      int h = hyp.featurizable.getState(featurizer).hashCode();
      if (i % 2 == 0) {
        code ^= (h << 32);
      } else {
        code ^= h;
      }
    }
    return code;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
