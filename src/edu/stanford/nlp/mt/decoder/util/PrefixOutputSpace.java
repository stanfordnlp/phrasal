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
  private boolean allowIncompletePrefix = false; // allow the last word of the prefix to be incomplete
  private String lastPrefixWord = null;

  /**
   * Constructor.
   * 
   * @param allowablePrefix
   * @param sourceInputId
   */
  public PrefixOutputSpace(Sequence<IString> allowablePrefix, int sourceInputId) {
    this(allowablePrefix, sourceInputId, false);
  }

  
  /**
   * Constructor.
   * 
   * @param allowablePrefix
   * @param sourceInputId
   * @param allowIncompletePrefix
   */
  public PrefixOutputSpace(Sequence<IString> allowablePrefix, int sourceInputId, boolean allowIncompletePrefix) {
    this.allowablePrefix = allowablePrefix;
    this.allowablePrefixLength = allowablePrefix.size();
    this.allowIncompletePrefix = allowIncompletePrefix && allowablePrefixLength > 0;
    if(this.allowIncompletePrefix) {
      lastPrefixWord = allowablePrefix.get(allowablePrefixLength - 1).toString();
    }
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
    // checking for matches is no longer necessary with the target-synchronous prefix beam search
    //final Sequence<IString> prefix = featurizable == null ? null : featurizable.targetSequence;
    // return exactMatch(prefix, rule.abstractRule.target);
    return true;
  }

  private boolean exactMatch(Sequence<IString> prefix, Sequence<IString> rule) {
    if (prefix == null) {
      if(allowablePrefix.size() > rule.size()) return allowablePrefix.startsWith(rule);
      
      int upperBound = allowablePrefix.size();
      for (int i = 0; i < upperBound; i++) {
        IString next = rule.get(i);
        if ( ! allowablePrefix.get(i).equals(next)) {
          if(allowIncompletePrefix && i == upperBound - 1) {
            String phraseWord = next.toString();
            if(phraseWord.startsWith(lastPrefixWord)) return true; 
          } 
          return false;
        }
      }
      return true;
    } else {
      int prefixLength = prefix.size();
      int upperBound = Math.min(prefixLength + rule.size(), allowablePrefixLength);
      for (int i = 0; i < upperBound; i++) {
        IString next = i >= prefixLength ? rule.get(i-prefixLength) : prefix.get(i);
        if ( ! allowablePrefix.get(i).equals(next)) {
          if(allowIncompletePrefix && i == allowablePrefixLength - 1) {
            String phraseWord = next.toString();
            if(phraseWord.startsWith(lastPrefixWord)) return true; 
          }
          
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
