package edu.stanford.nlp.mt.decoder.recomb;

import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.util.Generics;

/**
 * Implements n-gram language model recombination.
 * 
 * @author Spence Green
 * 
 */
public class TranslationNgramRecombinationFilter
        implements RecombinationFilter<Derivation<IString, String>> {

  private final List<DerivationFeaturizer<IString,String>> lmFeaturizers;

  /**
   * Constructor.
   * 
   * @param featurizers
   */
  public TranslationNgramRecombinationFilter(
      List<Featurizer<IString, String>> featurizers) {
    lmFeaturizers = Generics.newLinkedList();
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
    int maxLength = -1;
    int hashCode = 0;
    for (DerivationFeaturizer<IString,String> lmFeaturizer : lmFeaturizers) {
      LMState state = (LMState) hyp.featurizable.getState(lmFeaturizer);
      if (state.length() > maxLength) {
        maxLength = state.length();
        hashCode = state.hashCode();
      }
    }
    assert maxLength >= 0;
    return hashCode;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
