package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Map;
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
  
  @Before
  public void setUp() throws Exception {
    values = new int[1000][];
    for (int i = 0; i < values.length; ++i) {
      values[i] = new int[(i % 5)+1];
      for (int j = 0; j < values[i].length; ++j) {
        values[i][j] = i + j;
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
    assertEquals(values.length, index.size());
    for(int i = 0; i < values.length; ++i)
      assertTrue(index.get(i) != null);
  }
}
