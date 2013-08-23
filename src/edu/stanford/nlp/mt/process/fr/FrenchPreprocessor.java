package edu.stanford.nlp.mt.process.fr;

import java.util.Locale;

import edu.stanford.nlp.international.french.process.FrenchTokenizer;
import edu.stanford.nlp.mt.process.CoreNLPPreprocessor;

/**
 * TODO(spenceg): FrenchTokenizer does not support invertible!
 * 
 * 
 * @author Spence Green
 *
 */
public class FrenchPreprocessor extends CoreNLPPreprocessor {
  public FrenchPreprocessor() {
    super(FrenchTokenizer.factory());
    tf.setOptions("invertible=true,preserveLines=true,ptb3Escaping=false,asciiQuotes=true");
  }

  @Override
  public String toUncased(String input) {
    return input.toLowerCase(Locale.FRENCH);
  }
}
