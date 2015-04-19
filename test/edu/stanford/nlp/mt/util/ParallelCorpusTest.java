package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.mt.util.ParallelCorpus.Alignment;

/**
 * Test of the parallel corpus implementation.
 * 
 * @author Spence Green
 *
 */
public class ParallelCorpusTest {

  @Before
  public void setUp() throws Exception {
  }

  @Test
  public void testAlignStringParse() {
    String alignStr = "0-1 1-3,2 2-0";
    final int srcLen = 3;
    final int tgtLen = 4;
    Alignment a = ParallelCorpus.extractAlignment(alignStr, srcLen, tgtLen);
    assertEquals(1, a.f2e[0][0]);
    assertEquals(2, a.f2e[1][0]);
    assertEquals(3, a.f2e[1][1]);
    assertEquals(0, a.f2e[2][0]);
  }
}
