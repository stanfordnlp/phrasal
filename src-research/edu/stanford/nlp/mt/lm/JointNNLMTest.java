/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.*;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * @author Thang Luong
 *
 */
public class JointNNLMTest {
	public static final String PREFIX = ""; // "projects/mt/"; //
  public static final String nplmFile = PREFIX + "test/inputs/src5.tgt3.nplm";
  public static final String nplmSingleFile = PREFIX + "test/inputs/src5.tgt3.single.nplm";
  
  
	@Test
	public void testUnk() throws IOException {
//		System.err.println(System.getProperty("user.dir"));
		String[] tokens = new String[]{"a", "b", "c", "d", "e", "f", "g", "h"};
		Sequence<IString> sequence = IStrings.toIStringSequence(Arrays.asList(tokens));
		
		JointNNLM nplm = new JointNNLM(nplmFile, 0, 1);
		double score = nplm.scoreNgram(sequence);
		assertEquals(-1.8107199668884277, score, 1e-5);
//		System.err.println(sequence + "\t" + Arrays.toString(nplm.toId(sequence)) + "\t" + score);
		
		JointNNLM singleNPLM = new JointNNLM(nplmSingleFile, 0, 1);
    score = singleNPLM.scoreNgram(sequence);
    assertEquals(-1.811, score, 1e-5);
	}

	@Test
	public void testInVocab() throws IOException {
		String[] tokens = new String[]{"一", "个", "中国", "银行", ",", "a", "chinese", "bank"};
		Sequence<IString> sequence = IStrings.toIStringSequence(Arrays.asList(tokens));
		
		JointNNLM nplm = new JointNNLM(nplmFile, 0, 1);
		double score = nplm.scoreNgram(sequence);
		assertEquals(-7.802870273590088, score, 1e-5);
		
//		System.err.println(sequence + "\t" + Arrays.toString(nplm.toId(sequence)) + "\t" + score);
		JointNNLM singleNPLM = new JointNNLM(nplmSingleFile, 0, 1);
    score = singleNPLM.scoreNgram(sequence);
    assertEquals(-7.80283, score, 1e-5);
	}
	
	@Test
	public void testMixVocab() throws IOException {
		String[] tokens = new String[]{"a", "b", "c", "中国", "银行", "d", "chinese", "enterprises"};
		Sequence<IString> sequence = IStrings.toIStringSequence(Arrays.asList(tokens));
		
		JointNNLM nplm = new JointNNLM(nplmFile, 0, 1);
		double score = nplm.scoreNgram(sequence);
		assertEquals(-7.744800090789795, score, 1e-5);
		
//		System.err.println(sequence + "\t" + Arrays.toString(nplm.toId(sequence)) + "\t" + score);
		JointNNLM singleNPLM = new JointNNLM(nplmSingleFile, 0, 1);
    score = singleNPLM.scoreNgram(sequence);
    assertEquals(-7.74483, score, 1e-5);
	}
	
	@Test
	public void testExtractNgrams() throws IOException{
		JointNNLM nplm = new JointNNLM(nplmFile, 0, 1);
		String srcStr = "建设 法治 政府 , 就 是 为了 使 行政 权力 授予 有 据 , 行使 有规 , 监督 有效 , 做到 依法 治官 , 依法 治权 , 防止 行政 权力 的 缺失 和 滥用 , 带动 全 社会 尊重 法律 , 遵守 法律 , 维护 法律 \" 。";
		Sequence<IString> srcSent = IStrings.tokenize(srcStr);
		String tgtStr = "<s> construction if so law government ,";
		Sequence<IString> tgtSent = IStrings.tokenize(tgtStr);
		
		// f=政府 , ||| government , ||| (0) (1)
		int srcPos = 2, tgtPos = 5; // 
		PhraseAlignment alignment = PhraseAlignment.getPhraseAlignment("(0) (1)");
		
		int[][] ngrams = nplm.extractNgrams(srcSent, tgtSent, alignment, srcPos, tgtPos);
		
		assertEquals(2, ngrams.length);
		assertEquals("建设 法治 政府 , 就 so law government", nplm.toIString(ngrams[0]).toString());
		assertEquals("法治 政府 , 就 是 law government ,", nplm.toIString(ngrams[1]).toString());
	}
}
