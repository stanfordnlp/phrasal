package edu.stanford.nlp.mt.process;

import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Generic interface for text post-processors.
 * 
 * @author Spence Green
 *
 */
public interface Postprocessor {
  
  /**
   * Postprocess a pre-processed input. Typically the input has been
   * pre-processed with a class that implements the Preprocessor
   * interface.
   * 
   * @param input
   * @return
   */
  public SymmetricalWordAlignment process(Sequence<IString> input);
}
