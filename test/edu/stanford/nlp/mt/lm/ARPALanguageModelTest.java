package edu.stanford.nlp.mt.lm;

import java.io.IOException;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

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
public class ARPALanguageModelTest {

  private ARPALanguageModel lm;

  @Before
  public void setUp() {
    try {
      lm = (ARPALanguageModel) LanguageModelFactory
          .load("test-resources/inputs/3gm-probing.arpa.gz");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
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
