/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.util.Util;

/**
 * @author Thang Luong
 *
 */
public class NPLMLanguageModelTest {
	public static final String PREFIX = ""; // "projects/mt/"; //
  public static final String nplmFile = PREFIX + "test/inputs/src5.tgt3.nplm";
  
  
	@Test
	public void testUnk() throws IOException {
//		System.err.println(System.getProperty("user.dir"));
		NPLMLanguageModel nplm = new NPLMLanguageModel(nplmFile, 0);
		String[] tokens = new String[]{"a", "b", "c", "d", "e", "f", "g", "h"};
		Sequence<IString> sequence = Util.getIStringSequence(tokens);
		LMState state = nplm.score(sequence);
//		System.err.println(sequence + "\t" + state.getScore());
		assertEquals(-0.767296, state.getScore(), 1e-5);
	}

	@Test
	public void testInVocab() throws IOException {
		NPLMLanguageModel nplm = new NPLMLanguageModel(nplmFile, 0);
		String[] tokens = new String[]{"一", "个", "中国", "银行", ",", "a", "chinese", "bank"};
		Sequence<IString> sequence = Util.getIStringSequence(tokens);
		LMState state = nplm.score(sequence);
//		System.err.println(sequence + "\t" + state.getScore());
		assertEquals(-4.39918, state.getScore(), 1e-5);
	}
	
	@Test
	public void testMixVocab() throws IOException {
		NPLMLanguageModel nplm = new NPLMLanguageModel(nplmFile, 0);
		String[] tokens = new String[]{"a", "b", "c", "中国", "银行", "d", "chinese", "enterprises"};
		Sequence<IString> sequence = Util.getIStringSequence(tokens);
		LMState state = nplm.score(sequence);
//		System.err.println(sequence + "\t" + state.getScore());
		assertEquals(-4.27661, state.getScore(), 1e-5);
	}
	
	@Test
	public void testExtractNgrams() throws IOException{
		NPLMLanguageModel nplm = new NPLMLanguageModel(nplmFile, 0);
		String srcStr = "建设 法治 政府 , 就 是 为了 使 行政 权力 授予 有 据 , 行使 有规 , 监督 有效 , 做到 依法 治官 , 依法 治权 , 防止 行政 权力 的 缺失 和 滥用 , 带动 全 社会 尊重 法律 , 遵守 法律 , 维护 法律 \" 。";
		Sequence<IString> srcSent = Util.getIStringSequence(srcStr.split("\\s+"));
		String tgtStr = "<s> construction if so in order to 授予 law , is";
		Sequence<IString> tgtSent = Util.getIStringSequence(tgtStr.split("\\s+"));
		int srcPos = 3;
		int tgtPos = 9;
		PhraseAlignment alignment = PhraseAlignment.getPhraseAlignment("(0) (2)");
		List<int[]> ngramList = nplm.extractNgrams(srcSent, tgtSent, alignment, srcPos, tgtPos);
		
		System.err.println("# Ngram lists:");
		for (int[] ngram : ngramList) {
			System.err.println(Util.sprint(ngram));
		}
	}
}
