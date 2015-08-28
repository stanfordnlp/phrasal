package edu.stanford.nlp.mt.benchmark;

import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;

/**
 * Test of Sequence concatenation.
 * 
 * @author Spence Green
 *
 */
public class SequenceConcat {

  public static void main(String[] args) {
    String s = "Lorem ipsum sit dolor amit";
    Sequence<IString> seq = IStrings.tokenize(s);
    int numIters = 20000000;
    
    TimeKeeper timer = TimingUtils.start();
    Sequence<IString> c = Sequences.emptySequence();
    for (int i = 0; i < numIters; ++i) {
      if (((i+1) % 5) == 0) {
        c = seq;
      }
      c = c.concat(seq);
    }
    timer.mark(ArraySequence.class.getSimpleName());
    
    System.out.println("Timing: " + timer.toString());
  }
}
