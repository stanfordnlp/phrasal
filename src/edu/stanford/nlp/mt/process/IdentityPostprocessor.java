package edu.stanford.nlp.mt.process;

import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Return a diagonal alignment.
 * 
 * @author Spence Green
 *
 */
public class IdentityPostprocessor implements Postprocessor {

  @Override
  public SymmetricalWordAlignment process(Sequence<IString> input) {
    SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(input, input);
    for (int i = 0, size = input.size(); i < size; ++i) {
      alignment.addAlign(i, i);
    }
    return alignment;
  }
}
