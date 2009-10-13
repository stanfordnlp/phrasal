package mt.decoder.feat;

import mt.base.Sequence;
import mt.base.SimpleSequence;
import mt.base.IString;
import mt.base.IStrings;


import java.io.*;
import org.testng.annotations.*;

/**
 * 
 * @author Karthik Raghunathan
 * -Xmx512M
 */

@Test (groups = "decoder")
public class NGramLanguageModelFeaturizerTest {
  
    @DataProvider (name = "languageModel")
      public Object[][] languageModel() throws IOException {
        return new Object[][] {           
          new Object[] { mt.base.ARPALanguageModel.load("/u/nlp/data/testng/inputs/sampleLM.gz") }
       };      
      }
    
    @DataProvider (name = "featurizer")
    public Object[][] featurizer() throws IOException {
      return new Object[][] {           
        new Object[] { new NGramLanguageModelFeaturizer<IString>(mt.base.ARPALanguageModel.load("/u/nlp/data/testng/inputs/tinyLM.test")) }
      };      
    }
    
    @Test (dataProvider = "languageModel")
    public void testConstructor1(mt.base.ARPALanguageModel lm) {      
      NGramLanguageModelFeaturizer<IString> featurizer = new NGramLanguageModelFeaturizer<IString>(lm);
      assert(featurizer.order() == 3);      
      assert(featurizer.lmOrder == 3);   
      
      String sent = "This is a test sentence to be scored by the language model";
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score =  featurizer.lm.score(seq);      
      assert(score == (double)-11.03230619430542);   
    }
    
    @Test (dataProvider = "languageModel")
    public void testConstructor2(mt.base.ARPALanguageModel lm) throws IOException{
      NGramLanguageModelFeaturizer<IString> featurizer = new NGramLanguageModelFeaturizer<IString>(lm, "sampleLM", false);
      assert(featurizer.order() == 3);      
      assert(featurizer.lmOrder == 3);
      assert(featurizer.featureName == "sampleLM");   
      
      String sent = "This is a test sentence to be scored by the language model";
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score =  featurizer.lm.score(seq);      
      assert(score == (double)-11.03230619430542);     
    }
    
    @Test (dataProvider = "languageModel")
    public void testConstructor3WithLabel(mt.base.ARPALanguageModel lm) {      
      NGramLanguageModelFeaturizer<IString> featurizer = new NGramLanguageModelFeaturizer<IString>(lm, true);
      assert(featurizer.order() == 3);      
      assert(featurizer.lmOrder == 3);   
        
      String sent = "This is a test sentence to be scored by the language model";
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score =  featurizer.lm.score(seq);      
      assert(score == (double)-11.03230619430542);   
    }
    
    @Test (dataProvider = "languageModel")
    public void testConstructor3WithoutLabel(mt.base.ARPALanguageModel lm) {      
      NGramLanguageModelFeaturizer<IString> featurizer = new NGramLanguageModelFeaturizer<IString>(lm, false);
      assert(featurizer.order() == 3);      
      assert(featurizer.lmOrder == 3);   
          
      String sent = "This is a test sentence to be scored by the language model";
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score =  featurizer.lm.score(seq);      
      assert(score == (double)-11.03230619430542);   
    }
    
    @Test (dataProvider = "languageModel")
    public void testConstructor4(mt.base.ARPALanguageModel lm) throws IOException{
      NGramLanguageModelFeaturizer<IString> featurizer = new NGramLanguageModelFeaturizer<IString>("/u/nlp/data/testng/inputs/sampleLM.gz", "sampleLM");      
      assert(featurizer.order() == 3);      
      assert(featurizer.lmOrder == 3);
      
      String sent = "This is a test sentence to be scored by the language model";
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score =  featurizer.lm.score(seq);      
      assert(score == (double)-11.03230619430542);  
    }
    
    @Test (dataProvider = "featurizer")
    public void testFromFile(NGramLanguageModelFeaturizer<IString> featurizer) throws IOException{
      featurizer = NGramLanguageModelFeaturizer.fromFile("/u/nlp/data/testng/inputs/sampleLM.gz", "sampleLM");      
      assert(featurizer.order() == 3);      
      assert(featurizer.lmOrder == 3);
      
      String sent = "This is a test sentence to be scored by the language model";
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score =  featurizer.lm.score(seq);      
      assert(score == (double)-11.03230619430542);   
    }
    
    @Test (expectedExceptions = RuntimeException.class)
    public void testExceptionInConstructor4(String lmFile) throws IOException {
      new NGramLanguageModelFeaturizer<IString>("/u/nlp/data/testng/inputs/sampleLM.gz");
    }
    
    @Test (dataProvider = "featurizer", expectedExceptions = RuntimeException.class)
    public void testExceptionInFromFile(NGramLanguageModelFeaturizer<IString> featurizer) throws IOException{
      featurizer = NGramLanguageModelFeaturizer.fromFile("/u/nlp/data/testng/inputs/sampleLM.gz");
    } 
}
