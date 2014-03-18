package edu.stanford.nlp.mt.lm;

import java.io.IOException;

import junit.framework.TestCase;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.InsertedStartEndToken;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;

/**
 * Simple unit test for the KenLM loader.
 * 
 * @author Spence Green
 */
public class KenLanguageModelTest extends TestCase {

  private static KenLanguageModel lm;
  static {
    try {
      lm = (KenLanguageModel) LanguageModelFactory
          .load("kenlm:projects/mt/test/inputs/3gm-probing.bin");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testScore() {
    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = IStrings.tokenize(sent.toLowerCase());
    Sequence<IString> paddedSequence = 
        new InsertedStartEndToken<IString>(seq, lm.getStartToken(), lm.getEndToken());
    double score = lm.score(paddedSequence, 1, null).getScore();
    assertTrue(score == (double) -72.4647216796875);
  }
}
