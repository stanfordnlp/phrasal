package edu.stanford.nlp.mt.decoder.annotators;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Decoder hypothesis annotator
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public interface Annotator<TK> {
   public Annotator<TK> initalize(Sequence<TK> source);
   public Annotator<TK> extend(ConcreteTranslationOption<TK> option);
}
