package edu.stanford.nlp.mt.lm;

import java.io.IOException;

import junit.framework.TestCase;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;

/**
 * Simple unit test for the KenLM loader. Tests both
 * the ARPA and binary loaders.
 * 
 * @author Spence Green
 */
public class KenLanguageModelITest extends TestCase {

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
          .load("kenlm:/u/nlp/data/phrasal_test/3gm-probing.bin");
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
          .load("kenlm:/u/nlp/data/phrasal_test/3gm-probing.arpa.gz");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }  
    double score = scoreTestSentence(lm);
    assertTrue(score == (double) -72.4647216796875);
  }
}
