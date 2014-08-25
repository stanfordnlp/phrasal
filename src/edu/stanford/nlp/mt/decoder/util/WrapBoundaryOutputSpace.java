package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;

public class WrapBoundaryOutputSpace<TK, FV> implements OutputSpace<TK, FV> {

  @Override
  public void setSourceSequence(Sequence<TK> sourceSequence) {}

  @Override
  public List<ConcreteRule<TK, FV>> filter(List<ConcreteRule<TK, FV>> ruleList) {
    return ruleList;
  }

  @Override
  public boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteRule<TK, FV> rule) {
    
    if (featurizable == null)
      return true;
    
    Sequence<TK> translation = featurizable.targetPrefix;
    int targetLength = translation.size();

    if (targetLength > 0 && ! translation.get(0).equals(TokenUtils.START_TOKEN))
      return false;
    
    for (int i = 1; i < targetLength; i++) {
      if (translation.get(i).equals(TokenUtils.START_TOKEN) ||
          (i < targetLength - 1 && translation.get(i).equals(TokenUtils.END_TOKEN)))
        return false;
    }
    
    return true;
  }

  @Override
  public boolean allowableFinal(Featurizable<TK, FV> featurizable) {
    
    Sequence<TK> translation = featurizable.targetPrefix;
    int targetLength = translation.size();
    
    if (targetLength < 3) 
      return false;
    
    if (! translation.get(0).equals(TokenUtils.START_TOKEN))
      return false;
    
    if (! translation.get(targetLength - 1).equals(TokenUtils.END_TOKEN))
      return false;
    
    for (int i = 1; i < targetLength - 1; i++) {
      if (translation.get(i).equals(TokenUtils.START_TOKEN) ||
          translation.get(i).equals(TokenUtils.END_TOKEN)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public List<Sequence<TK>> getAllowableSequences() {
    return null;
  }

}
