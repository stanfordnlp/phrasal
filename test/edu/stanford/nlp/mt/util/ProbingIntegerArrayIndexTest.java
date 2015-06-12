package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Test;

/**
 * Thread-safety unit test.
 * 
 * @author Spence Green
 *
 */
public class ProbingIntegerArrayIndexTest {

  private int[][] values;
  private int expectedIndexSize;
  @Before
  public void setUp() throws Exception {
    int length = 1000;
    expectedIndexSize = 100;
    values = new int[length][];
    for (int i = 0; i < values.length; ++i) {
      values[i] = new int[(i % expectedIndexSize)+1];
      for (int j = 0; j < values[i].length; ++j) {
        // Lots of duplicates in the array
        values[i][j] = j;
      }
    }
  }

  @Test
  public void testInsertion() {
    final ConcurrentHashMap<Integer,Integer> keyMap = new ConcurrentHashMap<>(values.length);
    final IntegerArrayIndex index = new ProbingIntegerArrayIndex();
    IntStream.range(0, values.length).parallel().forEach(i -> {
      int[] value = values[i];
      int key = index.indexOf(value, true);
      Integer oldValue = keyMap.putIfAbsent(i, key);
      assertEquals(null, oldValue);
    });
    assertEquals(expectedIndexSize, index.size());
    for(int i = 0; i < expectedIndexSize; ++i)
      assertTrue(index.get(i) != null);
  }
}
