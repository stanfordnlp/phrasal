package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Default decoder output space with no constraints.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class UnconstrainedOutputSpace<TK, FV> implements
    OutputSpace<IString, String> {

  @Override
  public List<ConcreteRule<IString, String>> filter(
      List<ConcreteRule<IString, String>> ruleList) {
    return ruleList;
  }

  @Override
  public boolean allowableContinuation(
      Featurizable<IString, String> featurizable,
      ConcreteRule<IString, String> rule) {
    return true;
  }

  @Override
  public boolean allowableFinal(Featurizable<IString, String> featurizable) {
    return true;
  }

  @Override
  public List<Sequence<IString>> getAllowableSequences() {
    return null;
  }

  @Override
  public void setSourceSequence(Sequence<IString> sourceSequence) {}
}
