package mt.base;

import java.io.*;
import org.testng.annotations.*;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

/**
 * 
 * @author Karthik Raghunathan
 * -Xmx512M
 */
public class ARPALanguageModelTest{
	
		@DataProvider (name = "languageModel")
			public Object[][] languageModel() throws IOException {
	    	return new Object[][] {	           
	        new Object[] { ARPALanguageModel.load("testng/src/mt/base/sampleLM.gz") }
	    	};    	
	    }
		
		@DataProvider (name = "improperLanguageModels")
    public Object[][] improperLanguageModels() throws IOException {
      return new Object[][] {
        new Object[] { "testng/src/mt/base/nullLM.test" },
        new Object[] { "testng/src/mt/base/bigNGramsLM.test" }
      };      
    }
	
		@Test (dataProvider = "languageModel")
	 	public void testLoad(ARPALanguageModel lm) {
			
			assert(lm.tables.length == 3);
			      
      String sent = "This is a test sentence to be scored by the language model";
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score =  LanguageModels.scoreSequence(lm, seq);      
      assert(score == (double)-73.03947854042053);   
	 	}
		
		@Test (dataProvider = "languageModel")
    public void testScore(ARPALanguageModel lm) {      
      String sent = "This is a test sentence to be scored by the language model";
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score =  lm.score(seq);      
      assert(score == (double)-11.03230619430542);      
    }	
		
		@Test (dataProvider = "improperLanguageModels", expectedExceptions = RuntimeException.class)
    public void testExceptions(String lmFile) throws IOException {
		  ARPALanguageModel.load(lmFile);
    }	
}