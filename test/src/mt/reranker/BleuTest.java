package mt.reranker;

import junit.framework.TestCase;

public class BleuTest extends TestCase {
  private Bleu bleu;

  protected void setUp() {
    bleu = new Bleu();
  }

  public void testSimpleAddSubtract() {
    String[] s1 = { "the", "quick", "brown", "fox" };

    SegStats seg1 = new SegStats(s1.length, s1.length, NGram.distribution(s1), NGram.distribution(s1));
    assertEquals(0.0, bleu.score());
    bleu.add(seg1);
    assertEquals(1.0, bleu.score());
    bleu.sub(seg1);
    assertEquals(0.0, bleu.score());
  }

  public void testBP() {
    String[] s1 = { "the", "quick", "brown" };
    String[] r1 = { "the", "quick", "brown", "fox" };

    SegStats seg1 = new SegStats(s1.length, r1.length, NGram.distribution(s1), NGram.distribution(r1));
    bleu.add(seg1);
    assertEquals(Math.exp(1.0 - (4.0/3.0)), bleu.BP());
  }
  
  public void testNGramScores() {
    String[] s1 = { "the", "quick", "brown" };
    String[] r1 = { "the", "quick", "brown", "fox" };

    SegStats seg1 = new SegStats(s1.length, r1.length, NGram.distribution(s1), NGram.distribution(r1));
    bleu.add(seg1);
    double[] scores = bleu.rawNGramScores();
    assertEquals(1.0, Math.exp(scores[0]));
    assertEquals(1.0, Math.exp(scores[1]));
    assertEquals(1.0, Math.exp(scores[2]));
    assertEquals(0.0, Math.exp(scores[3]));

    bleu.reset();

    String[] s2 = { "the", "quick", "red", "fox" };
    SegStats seg2 = new SegStats(s2.length, r1.length, NGram.distribution(s2), NGram.distribution(r1));
    bleu.add(seg2);
    scores = bleu.rawNGramScores();
    assertEquals(3.0/4.0, Math.exp(scores[0]));
    assertEquals(1.0/3.0, Math.exp(scores[1]));
    assertEquals(0.0, Math.exp(scores[2]));
    assertEquals(0.0, Math.exp(scores[3]));
  }

  public void testMultiRef() {
    String[] s1 = { "the", "sticky", "sticky", "brown", "rice" };
    String[] r1 = { "the", "quick", "brown", "fox" };
    String[] r2 = { "the", "sticky", "brown", "fox" };
    String[] r3 = { "the", "quick", "brown", "rice" };

    SegStats seg1 = new SegStats(s1.length, r1.length, NGram.distribution(s1), NGram.maxDistribution(new String[][] { r1, r2, r3 }));
    bleu.add(seg1);

    double[] scores = bleu.rawNGramScores();
    assertEquals(4.0/5.0, Math.exp(scores[0]));
    assertEquals(3.0/4.0, Math.exp(scores[1]));
    assertEquals(0.0, Math.exp(scores[2]));
    assertEquals(0.0, Math.exp(scores[3]));
  }

  public void testAddSubtractMore() {
    SegStats seg1 = new SegStats(10, 10, new int[] { 4, 3, 2, 1 }, new int[] { 5, 4, 3, 2 });
    SegStats seg2 = new SegStats(10, 10, new int[] { 4, 3, 0, 0 }, new int[] { 5, 4, 3, 2 });
    SegStats seg3 = new SegStats(10, 10, new int[] { 1, 0, 0, 0 }, new int[] { 4, 3, 2, 1 });

    assertEquals(0.0, bleu.score());
    bleu.add(seg1);
    double score1 = bleu.score();
    assertFalse(score1 == 0.0);

    bleu.add(seg2);
    double score2 = bleu.score();
    assertFalse(score2 == 0.0);
    assertFalse(score2 == score1);

    bleu.add(seg3);
    double score3 = bleu.score();
    assertFalse(score3 == 0.0);
    assertFalse(score3 == score2);
    assertFalse(score3 == score1);

    bleu.sub(seg3);
    assertEquals(score2, bleu.score());

    bleu.sub(seg2);
    assertEquals(score1, bleu.score());

    bleu.sub(seg1);
    assertEquals(0.0, bleu.score());

  }

}
