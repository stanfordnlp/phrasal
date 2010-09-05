package edu.stanford.nlp.mt.base;

import java.util.Collection;
import java.io.*;

import junit.framework.TestCase;
import static org.junit.Assert.* ;

/**
 * @author Karthik Raghunathan
 * @author Michel Galley (conversion from testng to junit)
 */
public class MosesNBestListTest extends TestCase {

	static MosesNBestList nbestList;
	
	static { 
		try {
  		nbestList = new MosesNBestList("projects/mt/test/inputs/properSample.test");
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void testConstructor() throws IOException	{
		
		assertTrue(nbestList.nbestLists().size() == 2);

		assertTrue(getValue(nbestList.nbestLists().get(0).get(0).features,"LM") == -43.621000);
		assertTrue(getValue(nbestList.nbestLists().get(0).get(0).features,"TM:phi(t|f)") == -36.746000);
		assertTrue(nbestList.nbestLists().get(0).get(0).translation.toString().equals("a computer into the means of taiwan ."));
		
		assertTrue(getValue(nbestList.nbestLists().get(1).get(0).features,"LM") == -66.212000);
		assertTrue(getValue(nbestList.nbestLists().get(1).get(0).features,"TM:phi(t|f)") == -11.933000);
		assertTrue(nbestList.nbestLists().get(1).get(0).translation.toString().equals("5 afp following are closing ("));			
	} 	

	private double getValue(Collection<FeatureValue<String>> fvs, String name) {
		for (FeatureValue<String> fv : fvs)
			if (name.equals(fv.name))
				return fv.value;
		return 0;
	}
	
	public void testToString() throws IOException	{
		assertTrue(nbestList.toString().length() == 168160);
		assertTrue(nbestList.toString().trim().endsWith("LexR::swapWithPrevious:-3.874500"));
		assertTrue(nbestList.toString().startsWith("Moses N-Best List"));			
	} 	
	
	public void testRuntimeException(String nbestListFilename) throws IOException {
		String[] fileNames = new String[] {
			"projects/mt/test/inputs/improperIDs.test",
			"projects/mt/test/inputs/outOfOrderIDs.test",
			"projects/mt/test/inputs/improperFeatures.test" };
		for (String fileName : fileNames) {
			boolean goodFile = false;
			try { 
				new MosesNBestList(nbestListFilename);
			  goodFile = true;
			}
			catch (Exception e) {
			}
			assertFalse(goodFile);
		}
	}
}
