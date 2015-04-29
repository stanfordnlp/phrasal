package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.mt.util.ParallelCorpus.Alignment;

/**
 * Unit test.
 * 
 * @author Spence Green
 *
 */
public class AlignedSentenceTest {

  private static final int[] source = new int[]{0, 1, 2, 3};
  private static final int[] target = new int[]{4, 5, 6, 7, 8, 9, 10, 11};
  private static final String alignment = "0-1,2 1-0 2-7,3,4,5,6";
  
  @Test
  public void testAlignment() {
    Alignment al = ParallelCorpus.extractAlignment(alignment, source.length, target.length);
    AlignedSentence sentence = new AlignedSentence(source, target, al.f2e, al.e2f);
    
    // Source tests
    int[] f0Links = sentence.f2e(0);
    assertEquals(2, f0Links.length);
    assertEquals(1, f0Links[0]);
    assertEquals(2, f0Links[1]);
    
    int[] f1Links = sentence.f2e(1);
    assertEquals(1, f1Links.length);
    assertEquals(0, f1Links[0]);
    
    int[] f2Links = sentence.f2e(2);
    assertEquals(4, f2Links.length);
    assertEquals(3, f2Links[0]);
    assertEquals(4, f2Links[1]);
    assertEquals(5, f2Links[2]);
    assertEquals(7, f2Links[3]);
    
    int[] f3Links = sentence.f2e(3);
    assertEquals(0, f3Links.length);
    
    // Target tests
    int[] e0Links = sentence.e2f(0);
    assertEquals(1, e0Links.length);
    assertEquals(1, e0Links[0]);
    
    int[] e1Links = sentence.e2f(1);
    assertEquals(1, e1Links.length);
    assertEquals(0, e1Links[0]);
    
    int[] e2Links = sentence.e2f(2);
    assertEquals(1, e2Links.length);
    assertEquals(0, e2Links[0]);
  }
}
