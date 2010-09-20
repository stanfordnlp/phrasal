package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SequenceFilter;

/**
 * 
 * @author danielcer
 * 
 */
public class NumericFilter<TK extends CharSequence> implements
    SequenceFilter<TK> {

  @Override
  public boolean accepts(Sequence<TK> sequence) {
    for (TK token : sequence) {
      int length = token.length();
      boolean hasNumeric = false;
      for (int i = 0; i < length; i++) {
        char c = token.charAt(i);
        if (Character.isDigit(c)) {
          hasNumeric = true;
          break;
        }
      }
      if (!hasNumeric)
        return false;
    }
    return true;
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out
          .printf("Usage:\n\tjava ...NumericFilter (token0) (token1) ... (tokenN)\n");
      System.exit(-1);
    }
    Sequence<String> s = new RawSequence<String>(args);
    System.out.printf("%s accepted: %s\n", s,
        (new NumericFilter<String>()).accepts(s));
  }
}
