package edu.stanford.nlp.mt.process;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;

/**
 * 
 * @author Spence Green
 *
 */
public interface Postprocessor {
  
  public SymmetricalWordAlignment process(Sequence<IString> input);
}
