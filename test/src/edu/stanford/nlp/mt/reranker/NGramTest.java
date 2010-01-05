package mt.reranker;

import junit.framework.TestCase;
import java.util.HashMap;

public class NGramTest extends TestCase {
  public void testEquality() {
    assertEquals(new NGram("a", 1), new NGram("a", 1));
    assertFalse(new NGram("a", 1) == new NGram("a", 2));
    assertFalse(new NGram("a", 1) == new NGram("b", 1));
  }

  public void testHashCode() {
    assertEquals(new NGram("a", 1).hashCode(), new NGram("a", 1).hashCode());
    assertFalse(new NGram("a", 1).hashCode() == new NGram("a", 2).hashCode());
    assertFalse(new NGram("a", 1).hashCode() == new NGram("b", 1).hashCode());
  }

  public void testDistribution() {
    String[] sent = { "a", "b", "c", "d" };
    HashMap<NGram, Integer> distrib = NGram.distribution(sent, 1, 1);
    assertEquals(4, distrib.size());
    assertTrue(distrib.containsKey(new NGram("a", 1)));
    assertTrue(distrib.containsKey(new NGram("b", 1)));
    assertTrue(distrib.containsKey(new NGram("c", 1)));
    assertTrue(distrib.containsKey(new NGram("d", 1)));

    String[] sent2 = { "a", "b", "a", "d" };
    distrib = NGram.distribution(sent2, 1, 1);
    assertEquals(3, distrib.size());
    assertEquals(2, (int)distrib.get(new NGram("a", 1)));
    assertEquals(1, (int)distrib.get(new NGram("b", 1)));
    assertEquals(1, (int)distrib.get(new NGram("d", 1)));

    distrib = NGram.distribution(sent, 1, 4);
    assertEquals(10, distrib.size());
    assertEquals(1, (int)distrib.get(new NGram("a-b-c-d", 4)));
    assertEquals(1, (int)distrib.get(new NGram("a-b-c", 3)));
    assertEquals(1, (int)distrib.get(new NGram("b-c-d", 3)));
    assertEquals(1, (int)distrib.get(new NGram("a-b", 2)));
    assertEquals(1, (int)distrib.get(new NGram("b-c", 2)));
    assertEquals(1, (int)distrib.get(new NGram("c-d", 2)));
    assertEquals(1, (int)distrib.get(new NGram("a", 1)));
  }

  public void testMultiDistribution() {
    String[] s1 = { "a", "b", "c" };
    String[] s2 = { "d", "b", "c" };
    String[] s3 = { "b", "c", "x" };
    String[] s4 = { "b", "c", "b", "c" };

    String[][] corpus = { s1, s2, s3, s4 };

    HashMap<NGram, Integer> distrib = NGram.maxDistribution(corpus, 1, 4);
    assertEquals(1, (int)distrib.get(new NGram("x", 1)));
    assertFalse(distrib.containsKey(new NGram("c-d", 2)));
    assertEquals(2, (int)distrib.get(new NGram("b-c", 2)));
  }

}
