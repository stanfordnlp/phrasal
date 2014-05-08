package edu.stanford.nlp.mt.base;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.mt.util.ProbingIntegerArrayRawIndex;

public class ProbingIntegerArrayRawIndexTest {
  @Test
  public void testInserts() {
    ProbingIntegerArrayRawIndex test = new ProbingIntegerArrayRawIndex();
    int[] foo = new int[1];
    final int testTo = 1048576;
    for (int i = 0; i < testTo; ++i) {
      foo[0] = i;
      assertTrue(test.find(foo) == -1);
      assertTrue(test.findOrInsert(foo) == i);
      assertTrue(test.find(foo) == i);
    }
    for (int i = 0; i < testTo; ++i) {
      foo[0] = i;
      assertTrue(test.find(foo) == i);
    }
  }
};
