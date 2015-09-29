package edu.stanford.nlp.mt.decoder.util;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.AbstractInferer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.InputProperties;

/**
 * Constrained output space for prefix decoding.
 * 
 * 
 * @author Spence Green
 *
 * @param <IString>
 * @param <String>
 */
public class PrefixOutputSpace implements OutputSpace<IString, String> {

  private static final Logger logger = LogManager.getLogger(PrefixOutputSpace.class.getName());
  
  private final Sequence<IString> allowablePrefix;
  private final int allowablePrefixLength;

  /**
   * Constructor.
   * 
   * @param sourceSequence
   * @param allowablePrefix
   * @param sourceInputId
   */
  public PrefixOutputSpace(Sequence<IString> allowablePrefix, int sourceInputId) {
    this.allowablePrefix = allowablePrefix;
    this.allowablePrefixLength = allowablePrefix.size();
  }


  @Override
  public void setSourceSequence(Sequence<IString> sourceSequence) {
  }

  @Override
  public void filter(List<ConcreteRule<IString, String>> ruleList, AbstractInferer<IString, String> inferer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void filter(List<ConcreteRule<IString, String>> ruleList, AbstractInferer<IString, String> inferer, 
      InputProperties inputProperties) {
    // noop
  }

  @Override
  public boolean allowableContinuation(Featurizable<IString, String> featurizable,
      ConcreteRule<IString, String> rule) {
    final Sequence<IString> prefix = featurizable == null ? null : featurizable.targetSequence;
    return exactMatch(prefix, rule.abstractRule.target);
  }

  private boolean exactMatch(Sequence<IString> prefix, Sequence<IString> rule) {
    if (prefix == null) {
      return allowablePrefix.size() > rule.size() ? allowablePrefix.startsWith(rule) :
        rule.startsWith(allowablePrefix);

    } else {
      int prefixLength = prefix.size();
      int upperBound = Math.min(prefixLength + rule.size(), allowablePrefixLength);
      for (int i = 0; i < upperBound; i++) {
        IString next = i >= prefixLength ? rule.get(i-prefixLength) : prefix.get(i);
        if ( ! allowablePrefix.get(i).equals(next)) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public boolean allowableFinal(Featurizable<IString, String> featurizable) {
    // Allow everything except for the NULL hypothesis
    return featurizable != null;
  }

  @Override
  public List<Sequence<IString>> getAllowableSequences() {
    return Collections.singletonList(allowablePrefix);
  }

  @Override 
  public int getPrefixLength() {
    return allowablePrefixLength;
  }
}
