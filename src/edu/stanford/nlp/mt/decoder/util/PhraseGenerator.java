package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * 
 * @author Daniel Cer
 * 
 * @param <TK>
 */
public interface PhraseGenerator<TK,FV> extends Cloneable {
  /**
	 * 
	 */
  public List<ConcreteTranslationOption<TK,FV>> translationOptions(
      Sequence<TK> sequence, List<Sequence<TK>> targets, int translationId, Scorer<FV> scorer);

  public Object clone() throws CloneNotSupportedException;

  public void setCurrentSequence(Sequence<TK> foreign,
      List<Sequence<TK>> tranList);

  public int longestSourcePhrase();
}
