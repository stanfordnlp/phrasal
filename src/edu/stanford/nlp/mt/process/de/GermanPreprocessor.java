package edu.stanford.nlp.mt.process.de;

import java.util.Locale;

import edu.stanford.nlp.mt.process.CoreNLPPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;

/**
 * Finite-state pre-processor for German based on PTBTokenizer.
 *  
 * @author Spence Green
 *
 */
public class GermanPreprocessor extends CoreNLPPreprocessor {
  
  public GermanPreprocessor() {
    super(PTBTokenizer.coreLabelFactory());
    tf.setOptions("invertible=true,ptb3Escaping=false,asciiQuotes=true,splitAssimilations=false");
  }

  @Override
  public String toUncased(String input) {
    return input.toLowerCase(Locale.GERMAN);
  }
}
