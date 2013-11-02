package edu.stanford.nlp.mt.decoder.feat;

import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.lm.ARPALanguageModel;

import java.io.*;

import junit.framework.TestCase;

/**
 * @author Karthik Raghunathan
 * @author Michel Galley (conversion from testng to junit)
 */

public class NGramLanguageModelFeaturizerTest extends TestCase {

  static ARPALanguageModel lm;
  static NGramLanguageModelFeaturizer featurizer;

  static {
    try {
      lm = (ARPALanguageModel) ARPALanguageModel
          .load("projects/mt/test/inputs/sampleLM.gz");
      featurizer = new NGramLanguageModelFeaturizer(
          edu.stanford.nlp.mt.lm.ARPALanguageModel
              .load("projects/mt/test/inputs/tinyLM.test"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testConstructor1() {
    NGramLanguageModelFeaturizer featurizer = 
        new NGramLanguageModelFeaturizer(lm);
    assertTrue(featurizer.order() == 3);
    assertTrue(featurizer.lmOrder == 3);

    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = new SimpleSequence<IString>(
        IStrings.toIStringArray(sent.split("\\s")));
    double score = featurizer.lm.score(seq);
    assertTrue(score == (double) -8.227797508239746);
  }

  public void testConstructor2() throws IOException {
    NGramLanguageModelFeaturizer featurizer = new NGramLanguageModelFeaturizer(
        lm, "sampleLM", false);
    assertTrue(featurizer.order() == 3);
    assertTrue(featurizer.lmOrder == 3);
    assertTrue(featurizer.featureName == "sampleLM");

    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = new SimpleSequence<IString>(
        IStrings.toIStringArray(sent.split("\\s")));
    double score = featurizer.lm.score(seq);
    assertTrue(score == (double) -8.227797508239746);
  }

  public void testConstructor3WithLabel() {
    NGramLanguageModelFeaturizer featurizer = new NGramLanguageModelFeaturizer(
        lm, true);
    assertTrue(featurizer.order() == 3);
    assertTrue(featurizer.lmOrder == 3);

    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = new SimpleSequence<IString>(
        IStrings.toIStringArray(sent.split("\\s")));
    double score = featurizer.lm.score(seq);
    assertTrue(score == (double) -8.227797508239746);
  }

  public void testConstructor3WithoutLabel() {
    NGramLanguageModelFeaturizer featurizer = new NGramLanguageModelFeaturizer(
        lm, false);
    assertTrue(featurizer.order() == 3);
    assertTrue(featurizer.lmOrder == 3);

    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = new SimpleSequence<IString>(
        IStrings.toIStringArray(sent.split("\\s")));
    double score = featurizer.lm.score(seq);
    assertTrue(score == (double) -8.227797508239746);
  }

  public void testConstructor4() throws IOException {
    NGramLanguageModelFeaturizer featurizer = new NGramLanguageModelFeaturizer(
        "projects/mt/test/inputs/sampleLM.gz", "sampleLM");
    assertTrue(featurizer.order() == 3);
    assertTrue(featurizer.lmOrder == 3);

    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = new SimpleSequence<IString>(
        IStrings.toIStringArray(sent.split("\\s")));
    double score = featurizer.lm.score(seq);
    assertTrue(score == (double) -8.227797508239746);
  }

  public void testFromFile() throws IOException {
    featurizer = NGramLanguageModelFeaturizer.fromFile(
        "projects/mt/test/inputs/sampleLM.gz", "sampleLM");
    assertTrue(featurizer.order() == 3);
    assertTrue(featurizer.lmOrder == 3);

    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = new SimpleSequence<IString>(
        IStrings.toIStringArray(sent.split("\\s")));
    double score = featurizer.lm.score(seq);
    assertTrue(score == (double) -8.227797508239746);
  }

  public void testExceptionInConstructor4(String lmFile) throws IOException {
    new NGramLanguageModelFeaturizer(
        "projects/mt/test/inputs/sampleLM.gz");
  }

  public void testExceptionInFromFile(
      NGramLanguageModelFeaturizer featurizer) throws IOException {
    featurizer = NGramLanguageModelFeaturizer
        .fromFile("projects/mt/test/inputs/sampleLM.gz");
  }

}
