package edu.stanford.nlp.mt.reranker;

import junit.framework.TestCase;

public class SegStatsTest extends TestCase {
  public void testSameSize2() {
    String[] s1 = { "a", "b" };
    String[] r1 = { "a", "b" };
    
    SegStats s = new SegStats(s1.length, r1.length, NGram.distribution(s1), NGram.distribution(r1));
    assertEquals(2, s.correct[0]);
    assertEquals(2, s.total[0]);
    assertEquals(1, s.correct[1]);
    assertEquals(1, s.total[1]);
    assertEquals(0, s.correct[2]);
    assertEquals(0, s.total[2]);
    assertEquals(0, s.correct[3]);
    assertEquals(0, s.total[3]);
  }

  public void testDiffSize2() {
    String[] s1 = { "a", "b" };
    String[] r1 = { "a", "c" };
    
    SegStats s = new SegStats(s1.length, r1.length, NGram.distribution(s1), NGram.distribution(r1));
    assertEquals(1, s.correct[0]);
    assertEquals(2, s.total[0]);
    assertEquals(0, s.correct[1]);
    assertEquals(1, s.total[1]);
    assertEquals(0, s.correct[2]);
    assertEquals(0, s.total[2]);
    assertEquals(0, s.correct[3]);
    assertEquals(0, s.total[3]);
  }

  public void testBigger() {
    String[] s1 = { "the", "quick", "brown", "fox", "ate", "scrambled", "eggs" };
    String[] r1 = { "the", "quick", "red", "fox", "ate", "scrambled", "eggs" };

    SegStats s = new SegStats(s1.length, r1.length, NGram.distribution(s1), NGram.distribution(r1));
    assertEquals(1, s.correct[3]);
    assertEquals(4, s.total[3]);
  }

  public void testClipping() {
    String[] s1 = { "red", "red", "red" };
    String[] r1 = { "the", "quick", "red", "fox" };

    SegStats s = new SegStats(s1.length, r1.length, NGram.distribution(s1), NGram.distribution(r1));
    assertEquals(1, s.correct[0]);
    assertEquals(3, s.total[0]);

    String[] r2 = { "the", "red", "red", "fox" };

    s = new SegStats(s1.length, r1.length, NGram.distribution(s1), NGram.maxDistribution(new String[][] { r1, r2 }));
    assertEquals(2, s.correct[0]);
    assertEquals(3, s.total[0]);
  }

}
