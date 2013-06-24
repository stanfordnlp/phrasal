package edu.stanford.nlp.mt.decoder.annotators;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Decoder hypothesis annotator
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public interface Annotator<TK,FV> {
  public Annotator<TK,FV> initialize(Sequence<TK> source);
  public Annotator<TK,FV> extend(ConcreteRule<TK,FV> option);
}
