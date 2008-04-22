package mt.reranker;

import junit.framework.TestCase;

public class EditStatsTest extends TestCase {
  public void testSameSize2() {
    String s1 = "a b";
    String[] r1 = { "a b" };

    EditStats s = new EditStats(s1, r1);
    assertEquals(2.0, s.avgLen);
    assertEquals(0.0, s.minEdits);
  }

  public void testDiffSize2() {
    String s1 = "a b";
    String[] r1 = { "a c" };

    EditStats s = new EditStats(s1, r1);
    assertEquals(2.0, s.avgLen);
    assertEquals(1.0, s.minEdits);
  }

  public void testBigger() {
    String s1 = "the quick brown fox ate scrambled eggs";
    String[] r1 = { "the quick red fox ate scrambled eggs" };

    EditStats s = new EditStats(s1, r1);
    assertEquals(7.0, s.avgLen);
    assertEquals(1.0, s.minEdits);
  }

  public void testClipping() {
    String s1 = "red red red";
    String[] r1 = { "the quick red fox" };

    EditStats s = new EditStats(s1, r1);
    assertEquals(4.0, s.avgLen);
    assertEquals(3.0, s.minEdits);

    String[] r2 = { "the red red fox" };

    String[] r3 = new String[2];
    r3[0] = r1[0];
    r3[1] = r2[0];

    s = new EditStats(s1, r3);
    assertEquals(4.0, s.avgLen);
    assertEquals(2.0, s.minEdits);
  }

  public void testFromThePaper() {
    String s1 = "THIS WEEK THE SAUDIS denied information published in the new york times";
    String[] r1 = { "SAUDI ARABIA denied THIS WEEK information published in the AMERICAN new york times" };

    EditStats s = new EditStats(s1, r1);
    assertEquals(13.0, s.avgLen);
    assertEquals(4.0, s.minEdits);

  }
}
