/**
 * 
 */
package edu.stanford.nlp.mt.base;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author Thang Luong
 *
 */
public class PhraseAlignmentTest {

  /**
   * Test method for {@link edu.stanford.nlp.mt.base.PhraseAlignment#findSrcAvgPos(int)}.
   */
  @Test
  public final void testFindSrcAvgPos() {
//  String[] srcWords = new String[]{"现代化", "经济", "贸易", "金融", "中心"};
//  String[] tgtWords = new String[]{"modern", "economic", "trade", "and", "financial", "center"};
//  String srcAlignment = "(0) (1) (2) (4) (5)";
//  PhraseAlignment srcPA = PhraseAlignment.getPhraseAlignment(srcAlignment);
  
  String tgtAlignment = "(0) (1) (2) () (3) (4)";
  PhraseAlignment tgtPA = PhraseAlignment.getPhraseAlignment(tgtAlignment);
  assertEquals(0, tgtPA.findSrcAvgPos(0));
  assertEquals(1, tgtPA.findSrcAvgPos(1));
  assertEquals(2, tgtPA.findSrcAvgPos(2));
  assertEquals(3, tgtPA.findSrcAvgPos(3));
  assertEquals(3, tgtPA.findSrcAvgPos(4));
  assertEquals(4, tgtPA.findSrcAvgPos(5));
  
  tgtAlignment = "(0,3) (1) () (2,4)";
  tgtPA = PhraseAlignment.getPhraseAlignment(tgtAlignment);
  System.err.println(tgtPA.findSrcAvgPos(0));
  System.err.println(tgtPA.findSrcAvgPos(1));
  System.err.println(tgtPA.findSrcAvgPos(2));
  System.err.println(tgtPA.findSrcAvgPos(3));
  
  assertEquals(1, tgtPA.findSrcAvgPos(0));
  assertEquals(1, tgtPA.findSrcAvgPos(1));
  assertEquals(3, tgtPA.findSrcAvgPos(2));
  assertEquals(3, tgtPA.findSrcAvgPos(3));
  }

}
