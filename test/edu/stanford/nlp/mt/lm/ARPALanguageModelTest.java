package edu.stanford.nlp.mt.lm;

import java.io.IOException;

import junit.framework.TestCase;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.lm.ARPALanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;

/**
 * Simple unit test for Java-based ARPALanguageModel loader.
 * 
 * @author Spence Green
 */
public class ARPALanguageModelTest extends TestCase {

  private static ARPALanguageModel lm;
  static {
    try {
      lm = (ARPALanguageModel) LanguageModelFactory
          .load("test-resources/inputs/3gm-probing.arpa.gz");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testScore() {
    assertTrue(lm.tables.length == 3);
    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = IStrings.tokenize(sent.toLowerCase());
    Sequence<IString> paddedSequence = 
        Sequences.wrapStartEnd(seq, lm.getStartToken(), lm.getEndToken());
    double score = lm.score(paddedSequence, 1, null).getScore();
    assertTrue(score == (double) -72.46472558379173);
  }
}
