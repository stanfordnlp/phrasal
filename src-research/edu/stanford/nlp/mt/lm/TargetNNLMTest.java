/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * @author Thang Luong
 *
 */
public class TargetNNLMTest {
	public static final String PREFIX = ""; // "projects/mt/"; //
  public static final String nplmFile = PREFIX + "test/inputs/tgt3.nplm";
  
  
	@Test
	public void testUnk() throws IOException {
//		System.err.println(System.getProperty("user.dir"));
		TargetNNLM nplm = new TargetNNLM(nplmFile, 0, 1);
		String[] tokens = new String[]{"a", "b", "c"};
		Sequence<IString> sequence = IString.getIStringSequence(tokens);
		double score = nplm.scoreNgram(sequence);
		System.err.println(sequence + "\t" + score);
		assertEquals(-1.81081, score, 1e-5);
	}

	@Test
	public void testInVocab() throws IOException {
		TargetNNLM nplm = new TargetNNLM(nplmFile, 0, 1);
		String[] tokens = new String[]{"a", "chinese", "bank"};
		Sequence<IString> sequence = IString.getIStringSequence(tokens);
		double score = nplm.scoreNgram(sequence);
		System.err.println(sequence + "\t" + score);
		assertEquals(-7.802799999373527, score, 1e-5);
	}
	
	@Test
	public void testMixVocab() throws IOException {
		TargetNNLM nplm = new TargetNNLM(nplmFile, 0, 1);
		String[] tokens = new String[]{"d", "chinese", "enterprises"};
		Sequence<IString> sequence = IString.getIStringSequence(tokens);
		double score = nplm.scoreNgram(sequence);
		System.err.println(sequence + "\t" + score);
		assertEquals(-7.744800090789795, score, 1e-5);
	}
	
	@Test
	public void testExtractNgrams() throws IOException{
	  TargetNNLM nplm = new TargetNNLM(nplmFile, 0, 1);
		String tgtStr = "<s> construction if so law government ,";
		Sequence<IString> tgtSent = IString.getIStringSequence(tgtStr.split("\\s+"));
		int tgtPos = 5; // 
		
		List<int[]> ngramList = nplm.extractNgrams(tgtSent, tgtPos);
		assertEquals(2, ngramList.size());
		assertEquals("so law government", nplm.toIString(ngramList.get(0)).toString());
		assertEquals("law government ,", nplm.toIString(ngramList.get(1)).toString());
	}
}
