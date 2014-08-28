package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.mt.util.IString;

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
    
    if (featurizable == null) {
      if (rule != null && rule.abstractRule.target.size() > 0
        && ! ((IString) rule.abstractRule.target.get(0)).word().equals(TokenUtils.START_TOKEN.word()))
        {
         //System.err.println("NOT ALLOWED #4"); 
         return false;
        }
      else
        return true;
    }

    if (rule != null && rule.abstractRule.target.size() > 0 &&
        featurizable.numUntranslatedSourceTokens > rule.abstractRule.source.size() 
        && ((IString) rule.abstractRule.target.get(rule.abstractRule.target.size() - 1)).word().equals(TokenUtils.END_TOKEN.word())) {
      //System.err.println("NOT ALLOWED #10");
      return false;
    }   
 
    Sequence<TK> translation = featurizable.targetPrefix;
    int targetLength = translation.size();

    if (targetLength > 0 && ! ((IString) translation.get(0)).word().equals(TokenUtils.START_TOKEN.word()))
      {
      //System.err.println("NOT ALLOWED #1");
      return false;
   }
    if (targetLength == 0 && rule != null && rule.abstractRule.target.size() > 0 
        && ! ((IString) rule.abstractRule.target.get(0)).word().equals(TokenUtils.START_TOKEN.word())) {
      //System.err.println("NOT ALLOWED #2");  
      return false;
     }

    int x = (rule == null || rule.abstractRule.target.size() == 0) ? 1 : 0;
    for (int i = 1; i < targetLength; i++) {
      if (((IString) translation.get(i)).word().equals(TokenUtils.START_TOKEN.word()) ||
          (i < targetLength - x && ((IString) translation.get(i)).word().equals(TokenUtils.END_TOKEN.word()))) {
         //System.err.println("NOT ALLOWED #3");
        return false;
        }
    }
    
    return true;
  }

  @Override
  public boolean allowableFinal(Featurizable<TK, FV> featurizable) {

    if (featurizable == null)
      return false;    
    Sequence<TK> translation = featurizable.targetPrefix;
    int targetLength = translation.size();
    
    if (targetLength < 2) {
      //System.err.println("NOT ALLOWED #5");
      return false;
    }
    
    if (! ((IString) translation.get(0)).word().equals(TokenUtils.START_TOKEN.word()))
     { //System.err.println("NOT ALLOWED #6"); 
       return false;}
    
    if (! ((IString) translation.get(targetLength - 1)).word().equals(TokenUtils.END_TOKEN.word()))
     {//System.err.println("NOT ALLOWED #7"); 
      return false; }
    
    for (int i = 1; i < targetLength - 1; i++) {
      if (((IString) translation.get(i)).word().equals(TokenUtils.START_TOKEN.word()) ||
          ((IString) translation.get(i)).word().equals(TokenUtils.END_TOKEN.word())) {
        { //System.err.println("NOT ALLOWED #8"); 
          return false;}
      }
    }
    return true;
  }

  @Override
  public List<Sequence<TK>> getAllowableSequences() {
    return null;
  }

}
