package edu.stanford.nlp.mt.process;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;

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
