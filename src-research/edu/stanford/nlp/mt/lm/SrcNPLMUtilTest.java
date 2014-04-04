/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.mt.base.PhraseAlignment;

/**
 * @author Thang Luong
 *
 */
public class SrcNPLMUtilTest {

	@Test
	public void test() {
//		String[] srcWords = new String[]{"现代化", "经济", "贸易", "金融", "中心"};
//		String[] tgtWords = new String[]{"modern", "economic", "trade", "and", "financial", "center"};
//		String srcAlignment = "(0) (1) (2) (4) (5)";
//		PhraseAlignment srcPA = PhraseAlignment.getPhraseAlignment(srcAlignment);
		
		String tgtAlignment = "(0) (1) (2) () (3) (4)";
		PhraseAlignment tgtPA = PhraseAlignment.getPhraseAlignment(tgtAlignment);
		assertEquals(0, SrcNPLMUtil.findSrcAvgPos(0, tgtPA));
		assertEquals(1, SrcNPLMUtil.findSrcAvgPos(1, tgtPA));
		assertEquals(2, SrcNPLMUtil.findSrcAvgPos(2, tgtPA));
		assertEquals(3, SrcNPLMUtil.findSrcAvgPos(3, tgtPA));
		assertEquals(3, SrcNPLMUtil.findSrcAvgPos(4, tgtPA));
		assertEquals(4, SrcNPLMUtil.findSrcAvgPos(5, tgtPA));
		
		tgtAlignment = "(0,3) (1) () (2,4)";
		tgtPA = PhraseAlignment.getPhraseAlignment(tgtAlignment);
		System.err.println(SrcNPLMUtil.findSrcAvgPos(0, tgtPA));
		System.err.println(SrcNPLMUtil.findSrcAvgPos(1, tgtPA));
		System.err.println(SrcNPLMUtil.findSrcAvgPos(2, tgtPA));
		System.err.println(SrcNPLMUtil.findSrcAvgPos(3, tgtPA));
		
		assertEquals(1, SrcNPLMUtil.findSrcAvgPos(0, tgtPA));
		assertEquals(1, SrcNPLMUtil.findSrcAvgPos(1, tgtPA));
		assertEquals(3, SrcNPLMUtil.findSrcAvgPos(2, tgtPA));
		assertEquals(3, SrcNPLMUtil.findSrcAvgPos(3, tgtPA));
	}

}
