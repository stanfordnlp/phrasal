package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.mt.util.ProbingIntegerArrayRawIndex;

/**
 * Unit test.
 * 
 * @author Kenneth Heafield
 *
 */
public class ProbingIntegerArrayRawIndexTest {
  
  @Test
  public void testInserts() {
    ProbingIntegerArrayRawIndex test = new ProbingIntegerArrayRawIndex();
    int[] foo = new int[1];
    final int testTo = 1048576;
    for (int i = 0; i < testTo; ++i) {
      foo[0] = i;
      assertEquals(-1, test.find(foo));
      assertEquals(i, test.findOrInsert(foo));
      assertEquals(i, test.find(foo));
    }
    for (int i = 0; i < testTo; ++i) {
      foo[0] = i;
      assertEquals(i, test.find(foo));
    }
  }
};
