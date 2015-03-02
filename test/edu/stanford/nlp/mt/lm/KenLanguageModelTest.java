package edu.stanford.nlp.mt.lm;

import java.io.IOException;

import junit.framework.TestCase;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;

/**
 * Simple unit test for the KenLM loader. Tests both
 * the ARPA and binary loaders.
 * 
 * @author Spence Green
 */
public class KenLanguageModelTest extends TestCase {

  private static double scoreTestSentence(KenLanguageModel lm) {
    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = IStrings.tokenize(sent.toLowerCase());
    Sequence<IString> paddedSequence = 
        Sequences.wrapStartEnd(seq, lm.getStartToken(), lm.getEndToken());
    return lm.score(paddedSequence, 1, null).getScore();
  }

  public void testBinarized() {
    KenLanguageModel lm;
    try {
      lm = (KenLanguageModel) LanguageModelFactory
          .load("kenlm:test-resources/inputs/3gm-probing.bin");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }  
    double score = scoreTestSentence(lm);
    assertTrue(score == (double) -72.4647216796875);
  }

  public void testARPA() {
    KenLanguageModel lm;
    try {
      lm = (KenLanguageModel) LanguageModelFactory
          .load("kenlm:test-resources/inputs/3gm-probing.arpa.gz");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }  
    double score = scoreTestSentence(lm);
    assertTrue(score == (double) -72.4647216796875);
  }
}
