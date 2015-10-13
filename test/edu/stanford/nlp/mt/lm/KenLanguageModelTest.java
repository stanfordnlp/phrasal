package edu.stanford.nlp.mt.lm;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import org.junit.Before;
import org.junit.Test;

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
public class KenLanguageModelTest {

  private static double scoreTestSentence(KenLanguageModel lm) {
    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = IStrings.tokenize(sent.toLowerCase());
    Sequence<IString> paddedSequence = 
        Sequences.wrapStartEnd(seq, lm.getStartToken(), lm.getEndToken());
    return lm.score(paddedSequence, 1, null).getScore();
  }

  /**
   * Ignore the tests if KenLM hasn't been compiled.
   * 
   * @return
   */
  private static boolean kenLMIsLoaded() {
    try {
      System.loadLibrary(KenLanguageModel.KENLM_LIBRARY_NAME);
      return true;

    } catch (java.lang.UnsatisfiedLinkError e) {
      return false;
    }
  }

  @Before
  public void setUp() {
    assumeTrue(kenLMIsLoaded());
  }

  @Test
  public void testBinarized() {
    KenLanguageModel lm;
    try {
      lm = (KenLanguageModel) LanguageModelFactory
          .load("kenlm:test-resources/inputs/3gm-probing.bin");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }  
    double score = scoreTestSentence(lm);
    assertEquals("Score mismatch", -72.4647216796875, score, 1e-6);
  }

  @Test
  public void testARPA() {
    assumeTrue(kenLMIsLoaded());
    KenLanguageModel lm;
    try {
      lm = (KenLanguageModel) LanguageModelFactory
          .load("kenlm:test-resources/inputs/3gm-probing.arpa.gz");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }  
    double score = scoreTestSentence(lm);
    assertEquals("Score mismatch", -72.4647216796875, score, 1e-6);
  }
}
