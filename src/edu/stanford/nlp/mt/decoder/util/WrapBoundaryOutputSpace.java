package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.decoder.AbstractInferer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperties;

/**
 * 
 * @author Sebastian Schuster
 *
 * @param <TK>
 * @param <FV>
 */
public class WrapBoundaryOutputSpace<TK, FV> implements OutputSpace<TK, FV> {
   
  @Override
  public void setSourceSequence(Sequence<TK> sourceSequence) {}

  @Override
  public boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteRule<TK, FV> rule) {
    
    // First rule to be put down.
    if (featurizable == null) {
      // First token of the rule has to be the start token.
      if (rule != null && rule.abstractRule.target.size() > 0
        && ! ((IString) rule.abstractRule.target.get(0)).toString().equals(TokenUtils.START_TOKEN.toString())) {
        return false;
      } else {
        return true;
      }
    }

    // Last rule and the last token of the rule is not the stop token.
    if (rule != null && rule.abstractRule.target.size() > 0 &&
        featurizable.numUntranslatedSourceTokens > rule.abstractRule.source.size() 
        && ((IString) rule.abstractRule.target.get(rule.abstractRule.target.size() - 1)).toString().equals(TokenUtils.END_TOKEN.toString())) {
      return false;
    }   
 
    Sequence<TK> translation = featurizable.targetSequence;
    int targetLength = translation.size();

    // First token in translation is not the start token.
    if (targetLength > 0 && ! ((IString) translation.get(0)).toString().equals(TokenUtils.START_TOKEN.toString())) {
      return false;
    }
    
    // Translation is empty so far and first token in the current rule is not 
    // the start token.
    if (targetLength == 0 && rule != null && rule.abstractRule.target.size() > 0 
        && ! ((IString) rule.abstractRule.target.get(0)).toString().equals(TokenUtils.START_TOKEN.toString())) {
      return false;
    }

    int endLen = (rule == null || rule.abstractRule.target.size() == 0) ? 1 : 0;
    // Check for start or stop tokens that appear within a translation.
    for (int i = 1; i < targetLength; i++) {
      if (((IString) translation.get(i)).toString().equals(TokenUtils.START_TOKEN.toString()) ||
          (i < targetLength - endLen && ((IString) translation.get(i)).toString().equals(TokenUtils.END_TOKEN.toString()))) {
        return false;
      }
    }
    
    return true;
  }

  @Override
  public boolean allowableFinal(Featurizable<TK, FV> featurizable) {

    // Empty translation.
    if (featurizable == null) {
      return false;
    }
    
    Sequence<TK> translation = featurizable.targetSequence;
    int targetLength = translation.size();
    
    // Translation too short to contain start and stop tokens.
    if (targetLength < 2) {
      return false;
    }
    
    // First token of the translation is not the start token.
    if ( ! ((IString) translation.get(0)).toString().equals(TokenUtils.START_TOKEN.toString())) { 
      return false;
    }
    
    // Last token of the translation is not the end token.
    if ( ! ((IString) translation.get(targetLength - 1)).toString().equals(TokenUtils.END_TOKEN.toString())) {
      return false; 
    }
    
    // Check if start or stop tokens appear within the translation.
    for (int i = 1; i < targetLength - 1; i++) {
      if (((IString) translation.get(i)).toString().equals(TokenUtils.START_TOKEN.toString()) ||
          ((IString) translation.get(i)).toString().equals(TokenUtils.END_TOKEN.toString())) {
        return false;
      }
    }
    
    return true;
  }

  @Override
  public List<Sequence<TK>> getAllowableSequences() {
    return null;
  }

  @Override 
  public int getPrefixLength() {
    return 0;
  }

  @Override
  public void filter(List<ConcreteRule<TK, FV>> ruleList, AbstractInferer<TK, FV> inferer) {
  }

  @Override
  public void filter(List<ConcreteRule<TK, FV>> ruleList, AbstractInferer<TK, FV> inferer,
      InputProperties inputProperties) {
  }
}
