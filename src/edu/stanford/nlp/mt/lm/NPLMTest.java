package edu.stanford.nlp.mt.lm;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;


public class NPLMTest {
  public static final String PREFIX = ""; // "projects/more/"; //
  public static final String nplmFile = PREFIX + "test/inputs/tgt3.nplm";
  
  @Test
  public void testScoreNgrams() throws Exception {
    int[][] ngrams = new int[5][2];
    ngrams[0] = new int[]{0, 1, 2};
    ngrams[1] = new int[]{2, 3, 4};
    ngrams[2] = new int[]{4, 5, 6};
    ngrams[3] = new int[]{6, 7, 8};
    ngrams[4] = new int[]{8, 9, 10};
    
    NPLM nplm1 = new NPLM(nplmFile, 10, 2);
    double[] scores1 = nplm1.scoreNgrams(ngrams);
    assertEquals(5, scores1.length);
    assertEquals(-1.810809997474124, scores1[0], 1e-5);
    assertEquals(-7.2094700028275325, scores1[1], 1e-5);
    assertEquals(-6.30669999896396, scores1[2], 1e-5);
    assertEquals(-7.290080003186288, scores1[3], 1e-5);
    assertEquals(-4.0514299992022975, scores1[4], 1e-5);
    
    NPLM nplm2 = new NPLM(nplmFile, 10, 5);
    double[] scores2 = nplm2.scoreNgrams(ngrams);
    assertEquals(5, scores2.length);
    assertEquals(-1.810809997474124, scores2[0], 1e-5);
    assertEquals(-7.2094700028275325, scores2[1], 1e-5);
    assertEquals(-6.30669999896396, scores2[2], 1e-5);
    assertEquals(-7.290080003186288, scores2[3], 1e-5);
    assertEquals(-4.0514299992022975, scores2[4], 1e-5);
    
  }

  /**
   * Test method for {@link edu.stanford.nlp.mt.lm.more.lm.NPLM#splitIntoBatches(int[][], int)}.
   */
  @Test
  public void testSplitIntoBatches() {
    int[][] ngrams = new int[5][2];
    ngrams[0] = new int[]{0, 1};
    ngrams[1] = new int[]{2, 3};
    ngrams[2] = new int[]{4, 5};
    ngrams[3] = new int[]{6, 7};
    ngrams[4] = new int[]{8, 9};
    
    List<int[][]> batches = NPLM.splitIntoBatches(ngrams, 2);
    assertEquals(3, batches.size());
    assertEquals("[[0, 1] [2, 3]]", NPLM.sprint(batches.get(0)));
    assertEquals("[[4, 5] [6, 7]]", NPLM.sprint(batches.get(1)));
    assertEquals("[[8, 9]]", NPLM.sprint(batches.get(2)));
    
    batches = NPLM.splitIntoBatches(ngrams, 3);
    assertEquals(2, batches.size());
    assertEquals("[[0, 1] [2, 3] [4, 5]]", NPLM.sprint(batches.get(0)));
    assertEquals("[[6, 7] [8, 9]]", NPLM.sprint(batches.get(1)));
  }

  /**
   * Test method for {@link edu.stanford.nlp.mt.lm.more.lm.NPLM#toOneDimArray(int[][])}.
   */
  @Test
  public void testToOneDimArray() {
    int[][] ngrams = new int[5][2];
    ngrams[0] = new int[]{0, 1};
    ngrams[1] = new int[]{2, 3};
    ngrams[2] = new int[]{4, 5};
    ngrams[3] = new int[]{6, 7};
    ngrams[4] = new int[]{8, 9};
    
    int[] oneDimArray = NPLM.toOneDimArray(ngrams);
    assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]", Arrays.toString(oneDimArray));
  }
}
