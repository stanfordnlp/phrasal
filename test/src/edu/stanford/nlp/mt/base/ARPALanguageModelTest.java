package edu.stanford.nlp.mt.base;

import java.io.*;

import junit.framework.TestCase;
import static org.junit.Assert.* ;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;

/**
 * @author Karthik Raghunathan
 * @author Michel Galley (conversion from testng to junit)
 */

public class ARPALanguageModelTest extends TestCase {
  
  static ARPALanguageModel lm;
  
  static {
    try { 
      lm = (ARPALanguageModel) ARPALanguageModel.load("projects/mt/test/inputs/sampleLM.gz");
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public void testLoad() {
    assertTrue(lm.tables.length == 3);
    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
    double score =  LanguageModels.scoreSequence(lm, seq);      
    assertTrue(score == (double)-81.74873375892639);
  }
  
  public void testScore() {
    String sent = "This is a test sentence to be scored by the language model";
    Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
    double score =  lm.score(seq);      
    assertTrue(score == (double)-8.227797508239746);
  }	
  
  public void testExceptions() {
    String[] lmFiles =  new String[] { 
      "projects/mt/test/inputs/nullLM.test",
      "projects/mt/test/inputs/bigNGramsLM.test" 
    };
    for (String lmFile : lmFiles) {
      boolean goodLM = false;
      try { 
        ARPALanguageModel.load(lmFile);
        goodLM = true;
      }
      catch (Exception e) {
      }
      assertFalse(goodLM);
    }
  }	
}
