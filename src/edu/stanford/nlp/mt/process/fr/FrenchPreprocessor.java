package edu.stanford.nlp.mt.process.fr;

import java.util.Locale;

import edu.stanford.nlp.international.french.process.FrenchTokenizer;
import edu.stanford.nlp.mt.process.CoreNLPPreprocessor;

/**
 * Pre-processes French raw text.
 * 
 * @author Spence Green
 *
 */
public class FrenchPreprocessor extends CoreNLPPreprocessor {
  
  private final boolean cased;
  
  public FrenchPreprocessor() {
    this(true);
  }
  
  public FrenchPreprocessor(boolean cased) {
    super(FrenchTokenizer.factory());
    tf.setOptions("invertible=true,ptb3Escaping=false,untokenizable=allKeep");
    this.cased = cased;
  }

  @Override
  public String toUncased(String input) {
    return cased ? input : input.toLowerCase(Locale.FRENCH);
  }
}
