package edu.stanford.nlp.mt.base;

import java.util.Collection;
import java.io.*;
import org.testng.annotations.*;

/**
 * 
 * @author Karthik Raghunathan
 *
 */
@Test (groups = "base")
public class MosesNBestListTest {
	
		@DataProvider (name = "improperInputFiles")
		public Object[][] improperInputFiles() throws IOException {
	    	return new Object[][] {
		  new Object[] { "/u/nlp/data/testng/inputs/improperIDs.test" },
	          new Object[] { "/u/nlp/data/testng/inputs/outOfOrderIDs.test" },
	          new Object[] { "/u/nlp/data/testng/inputs/improperFeatures.test" }
//	    	  new Object[] { "improperIDs.test" },
//	       	  new Object[] { "outOfOrderIDs.test" },
//	       	  new Object[] { "improperFeatures.test" }
	    	};    	
	    }
	
	 	@Test
	 	public void testConstructor() throws IOException	{
	 		MosesNBestList nbestList = new MosesNBestList("/u/nlp/data/testng/inputs/properSample.test");
	 		
	 		assert(nbestList.nbestLists().size() == 20);			

			assert(getValue(nbestList.nbestLists().get(0).get(0).features,"LM") == -43.621000);
			assert(getValue(nbestList.nbestLists().get(0).get(0).features,"TM:phi(t|f)") == -36.746000);
			assert(nbestList.nbestLists().get(0).get(0).translation.toString().equals("a computer into the means of taiwan ."));
			
			assert(getValue(nbestList.nbestLists().get(1).get(0).features,"LM") == -66.212000);
			assert(getValue(nbestList.nbestLists().get(1).get(0).features,"TM:phi(t|f)") == -11.933000);
      assert(nbestList.nbestLists().get(1).get(0).translation.toString().equals("5 afp following are closing ("));			
	 	} 	

		private double getValue(Collection<FeatureValue<String>> fvs, String name) {
			for (FeatureValue<String> fv : fvs)
				if (name.equals(fv.name))
					return fv.value;
			return 0;
		}
		
	 	@Test
	 	public void testToString() throws IOException	{
	 		MosesNBestList nbestList = new MosesNBestList("/u/nlp/data/testng/inputs/properSample.test");
//	 		MosesNBestList nbestList = new MosesNBestList("properSample.test");
	 			 		
	 		assert(nbestList.toString().length() == 2817412);
	 		assert(nbestList.toString().trim().endsWith("UnknownWord:0.000000"));
			assert(nbestList.toString().startsWith("Moses N-Best List"));			
	 	} 	
	 	
	 	@Test(expectedExceptions = RuntimeException.class, dataProvider = "improperInputFiles")
	 	public void testRuntimeException(String nbestListFilename) throws IOException {
	 		new MosesNBestList(nbestListFilename);
	 	}
}
