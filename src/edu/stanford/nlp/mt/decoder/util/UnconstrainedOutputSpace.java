package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.decoder.AbstractInferer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

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

  @Override
  public void filter(RuleGrid<IString, String> ruleGrid, AbstractInferer<IString, String> inferer) {}
  
  @Override 
  public int getPrefixLength() {
    return 0;
  }
}

