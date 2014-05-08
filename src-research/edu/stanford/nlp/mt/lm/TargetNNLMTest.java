/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import java.io.IOException;

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
  public static final String nplmSingleFile = PREFIX + "test/inputs/tgt3.single.nplm";
  
  
	@Test
	public void testUnk() throws IOException {
//		System.err.println(System.getProperty("user.dir"));
		
		String[] tokens = new String[]{"a", "b", "c"};
		Sequence<IString> sequence = IString.getIStringSequence(tokens);
		
		TargetNNLM nplm = new TargetNNLM(nplmFile, 0, 1);
		double score = nplm.scoreNgram(sequence);
		assertEquals(-1.81081, score, 1e-5);
		
		TargetNNLM singleNPLM = new TargetNNLM(nplmSingleFile, 0, 1);
    score = singleNPLM.scoreNgram(sequence);
    System.err.println(sequence + "\t" + score);
    assertEquals(-1.81149, score, 1e-5);
	}

	@Test
	public void testInVocab() throws IOException {
		String[] tokens = new String[]{"a", "chinese", "bank"};
		Sequence<IString> sequence = IString.getIStringSequence(tokens);
		
		TargetNNLM nplm = new TargetNNLM(nplmFile, 0, 1);
		double score = nplm.scoreNgram(sequence);
		assertEquals(-7.802799999373527, score, 1e-5);
		
    TargetNNLM singleNPLM = new TargetNNLM(nplmSingleFile, 0, 1);
    score = singleNPLM.scoreNgram(sequence);
    assertEquals(-7.80284, score, 1e-5);
	}
	
	@Test
	public void testMixVocab() throws IOException {
		String[] tokens = new String[]{"d", "chinese", "enterprises"};
		Sequence<IString> sequence = IString.getIStringSequence(tokens);
		
		TargetNNLM nplm = new TargetNNLM(nplmFile, 0, 1);
		double score = nplm.scoreNgram(sequence);
		assertEquals(-7.744800090789795, score, 1e-5);
		
    TargetNNLM singleNPLM = new TargetNNLM(nplmSingleFile, 0, 1);
    score = singleNPLM.scoreNgram(sequence);
    assertEquals(-7.74473, score, 1e-5);
	}
	
	@Test
	public void testExtractNgrams() throws IOException{
	  TargetNNLM nplm = new TargetNNLM(nplmFile, 0, 1);
		String tgtStr = "<s> construction if so law government ,";
		Sequence<IString> tgtSent = IString.getIStringSequence(tgtStr.split("\\s+"));
		int tgtPos = 5; // 
		
		int[][] ngrams = nplm.extractNgrams(null, tgtSent, null, -1, tgtPos);
		assertEquals(2, ngrams.length);
		assertEquals("so law government", nplm.toIString(ngrams[0]).toString());
		assertEquals("law government ,", nplm.toIString(ngrams[1]).toString());
	}
}
