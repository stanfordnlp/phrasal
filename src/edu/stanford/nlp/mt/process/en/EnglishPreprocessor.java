package edu.stanford.nlp.mt.process.en;

import edu.stanford.nlp.mt.process.CoreNLPPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;

/**
 * 
 * @author Spence Green
 *
 */
public class EnglishPreprocessor extends CoreNLPPreprocessor {

  public EnglishPreprocessor() {
    super(PTBTokenizer.coreLabelFactory());
    tf.setOptions("invertible=true,preserveLines=true,ptb3Escaping=false,asciiQuotes=true");
  }
}
