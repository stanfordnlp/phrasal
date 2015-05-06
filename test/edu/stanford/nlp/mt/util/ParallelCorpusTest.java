package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import edu.stanford.nlp.mt.util.ParallelCorpus.Alignment;

/**
 * Test of the parallel corpus implementation.
 * 
 * @author Spence Green
 *
 */
public class ParallelCorpusTest {

  private static int get(Set<Integer>[] algn, int i, int j) {
    List<Integer> points = new ArrayList<>(algn[i]);
    Collections.sort(points);
    return points.get(j);
  }
  
  @Test
  public void testAlignStringParse() {
    String alignStr = "0-1 1-3,2 2-0";
    final int srcLen = 3;
    final int tgtLen = 4;
    Alignment a = ParallelCorpus.extractAlignment(alignStr, srcLen, tgtLen);
    assertEquals(1, get(a.f2e, 0, 0));
    assertEquals(2, get(a.f2e, 1, 0));
    assertEquals(3, get(a.f2e, 1, 1));
    assertEquals(0, get(a.f2e, 2, 0));
    
    assertEquals(1, get(a.e2f, 3, 0));
    assertEquals(2, get(a.e2f, 0, 0));
  }
}
