package edu.stanford.nlp.mt.process;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
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
  public SymmetricalWordAlignment processAndAlign(String input);
  
  /**
   * Pre-process and input string, but do not align it with the
   * raw input.
   * 
   * @param input
   * @return
   */
  public Sequence<IString> process(String input);
  
  /**
   * Language-specific lowercasing.
   * 
   * @param input
   * @return
   */
  public String toUncased(String input);
}
