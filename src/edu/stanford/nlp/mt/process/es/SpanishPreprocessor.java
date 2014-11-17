package edu.stanford.nlp.mt.process.es;

import java.util.Locale;

import edu.stanford.nlp.international.spanish.process.SpanishTokenizer;
import edu.stanford.nlp.mt.process.CoreNLPPreprocessor;

/**
 * Pre-processes Spanish raw text.
 * 
 * @author Spence Green
 *
 */
public class SpanishPreprocessor extends CoreNLPPreprocessor {
  
  private static final Locale ES_LOCALE = new Locale("es_US");
  private final boolean cased;
  
  public SpanishPreprocessor() {
    this(false);
  }
  
  public SpanishPreprocessor(boolean cased) {
    super(SpanishTokenizer.factory());
    tf.setOptions("invertible=true,ptb3Escaping=false");
    this.cased = cased;
  }

  @Override
  public String toUncased(String input) {
    return cased ? input : input.toLowerCase(ES_LOCALE);
  }
}
