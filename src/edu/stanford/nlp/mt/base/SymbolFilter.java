package edu.stanford.nlp.mt.base;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class SymbolFilter<TK extends CharSequence> implements
    SequenceFilter<TK> {

  @Override
  public boolean accepts(Sequence<TK> sequence) {
    if (sequence.size() != 1)
      return false;
    TK token = sequence.get(0);
    if (token.length() != 1)
      return false;
    char c = token.charAt(0);
    int type = Character.getType(c);
    return type == Character.OTHER_PUNCTUATION
        || type == Character.START_PUNCTUATION
        || type == Character.END_PUNCTUATION
        || type == Character.DASH_PUNCTUATION;
  }

}
