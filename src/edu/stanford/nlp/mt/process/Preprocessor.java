package edu.stanford.nlp.mt.process;

import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;

/**
 * Convert a raw input into a processed output, which is aligned to the input.
 * 
 * @author Spence Green
 *
 */
public interface Preprocessor {

  /**
   * Preprocess a raw input string. The SymmetricalWordAlignment
   * contains the whitespace-tokenized input as the source
   * and pre-processed sequence as the target.
   * 
   * @param input
   * @return
   */
  public SymmetricalWordAlignment process(String input);
  
  public String toUncased(String input);
}
