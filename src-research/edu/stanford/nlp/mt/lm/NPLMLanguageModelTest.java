/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.*;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.Util;

/**
 * @author Thang Luong
 *
 */
public class NPLMLanguageModelTest {
	public static final String PREFIX = ""; // "projects/mt/"; //
  public static final String nplmFile = PREFIX + "test/inputs/src5.tgt3.nplm";
  
  
	@Test
	public void testUnk() throws IOException {
		System.err.println(System.getProperty("user.dir"));
		NPLMLanguageModel nplm = new NPLMLanguageModel(nplmFile);
		String[] tokens = new String[]{"a", "b", "c", "d", "e", "f", "g", "h"};
		Sequence<IString> sequence = Util.getIStringSequence(tokens);
		LMState state = nplm.score(sequence);
//		System.err.println(sequence + "\t" + state.getScore());
		assertEquals(-0.767296, state.getScore(), 1e-5);
	}

	@Test
	public void testInVocab() throws IOException {
		System.err.println(System.getProperty("user.dir"));
		NPLMLanguageModel nplm = new NPLMLanguageModel(nplmFile);
		String[] tokens = new String[]{"一", "个", "中国", "银行", ",", "a", "chinese", "bank"};
		Sequence<IString> sequence = Util.getIStringSequence(tokens);
		LMState state = nplm.score(sequence);
//		System.err.println(sequence + "\t" + state.getScore());
		assertEquals(-4.39918, state.getScore(), 1e-5);
	}
	
	@Test
	public void testMixVocab() throws IOException {
		System.err.println(System.getProperty("user.dir"));
		NPLMLanguageModel nplm = new NPLMLanguageModel(nplmFile);
		String[] tokens = new String[]{"a", "b", "c", "中国", "银行", "d", "chinese", "enterprises"};
		Sequence<IString> sequence = Util.getIStringSequence(tokens);
		LMState state = nplm.score(sequence);
//		System.err.println(sequence + "\t" + state.getScore());
		assertEquals(-4.27661, state.getScore(), 1e-5);
	}
}
