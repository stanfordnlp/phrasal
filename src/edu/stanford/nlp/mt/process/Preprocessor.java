package edu.stanford.nlp.mt.process;

import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;

/**
 * Convert a raw input into a processed output, which is aligned to the input.
 * 
 * @author Spence Green
 *
 */
public interface Preprocessor {

  public SymmetricalWordAlignment process(String input);
}
