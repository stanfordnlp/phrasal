package edu.stanford.nlp.mt.decoder.recomb;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.util.IString;


/**
 * Implements n-gram language model recombination.
 * 
 * @author Spence Green
 * 
 */
public class NGramLMRecombinationFilter implements RecombinationFilter<Derivation<IString, String>> {

  private final List<DerivationFeaturizer<IString,String>> lmFeaturizers;

  /**
   * Constructor.
   * 
   * @param featurizers
   */
  public NGramLMRecombinationFilter(
      List<Featurizer<IString, String>> featurizers) {
    lmFeaturizers = new ArrayList<>();
    for (Featurizer<IString,String> featurizer : featurizers) {
      if (featurizer instanceof NGramLanguageModelFeaturizer) {
        lmFeaturizers.add((NGramLanguageModelFeaturizer) featurizer);
      }
    }
  }

  @Override
  public boolean combinable(Derivation<IString, String> hypA, Derivation<IString, String> hypB) {
    if (hypA.featurizable == null && hypB.featurizable == null) {
      // null hypothesis
      return true;
    } else if (hypA.featurizable == null || hypB.featurizable == null) {
      // one or the other is the null hypothesis
      return false;
    }

    for (DerivationFeaturizer<IString,String> lmFeaturizer : lmFeaturizers) {
      FeaturizerState stateA = (FeaturizerState) hypA.featurizable.getState(lmFeaturizer);
      FeaturizerState stateB = (FeaturizerState) hypB.featurizable.getState(lmFeaturizer);

      // Do the two states hash to the same bucket?
      if ( ! (stateA.hashCode() == stateB.hashCode() &&
              stateA.equals(stateB))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public long recombinationHashCode(Derivation<IString, String> hyp) {
    if (hyp.featurizable == null) {
      // null hypothesis. This hashCode doesn't actually matter because of the checks
      // in the combinable() function above.
      return hyp.sourceSequence.hashCode();
    }
    
    if (lmFeaturizers.size() == 1) {
      return hyp.featurizable.getState(lmFeaturizers.get(0)).hashCode();
    
    } else {
      // Ripped off from MurmurHash3
      final int c1 = 0xcc9e2d51;
      final int c2 = 0x1b873593;

      final int sz = lmFeaturizers.size();
      int h1 = sz*4;

      for (int i=0; i<sz; i++) {
        LMState state = (LMState) hyp.featurizable.getState(lmFeaturizers.get(i));
        int k1 = state.hashCode();
        k1 *= c1;
        k1 = (k1 << 15) | (k1 >>> 17);  // ROTL32(k1,15);
        k1 *= c2;

        h1 ^= k1;
        h1 = (h1 << 13) | (h1 >>> 19);  // ROTL32(h1,13);
        h1 = h1*5+0xe6546b64;
      }
      return h1;
    }
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
