package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.StringUtils;

/**
 * Constrains the output space such that the
 * output never deviates more than DEVIATION_LIMIT
 * from a specified permutation sequence.
 * 
 * @author Sebastian Schuster
 */

public class PermutationConstrainedOutputSpace<TK, FV> implements OutputSpace<TK, FV> {

  
  private List<Integer> permutationSequence;
  private static final int DEVIATION_LIMIT = 5;
  
  public PermutationConstrainedOutputSpace(List<Integer> permutationSequence) {
    this.permutationSequence = permutationSequence;
  }
  
  public PermutationConstrainedOutputSpace() {
  }

  public void setSourceSequence(Sequence<TK> sourceSequence) {}

  public List<ConcreteRule<TK, FV>> filter(List<ConcreteRule<TK, FV>> ruleList) {
    return ruleList;
  }

  
  private List<Integer> getPermutationSequence(Featurizable<TK, FV> featurizable) {
    List<Integer> permutationSequence = new LinkedList<Integer>();
    
    Featurizable<TK, FV> prior = featurizable;
    while (prior != null) {
      int end = prior.sourcePosition + prior.sourcePhrase.size() - 1;
      int start = prior.sourcePosition;
      for (int i = end; i >= start; i--) {
        permutationSequence.add(i);
      }
      prior = prior.prior;
    }
    System.err.println("-----------------------------------");
    System.err.println("Source Phrase:" + featurizable.sourceSentence.toString(" "));
    System.err.println("Permutation Sequence:" + StringUtils.join(permutationSequence));
    System.err.println("Current target translation: " + featurizable.targetPrefix.toString(" ") + " " + featurizable.targetPhrase.toString(" "));
    System.err.println("-----------------------------------");

    return permutationSequence;
  }
  
  public boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteRule<TK, FV> rule) {
    
    getPermutationSequence(featurizable);
    return true;
  }

  public boolean allowableFinal(Featurizable<TK, FV> featurizable) {
    // TODO Auto-generated method stub
    return false;
  }

  public List<Sequence<TK>> getAllowableSequences() {
    // TODO Auto-generated method stub
    return null;
  }

}
