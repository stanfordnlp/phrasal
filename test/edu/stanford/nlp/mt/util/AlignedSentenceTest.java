package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.mt.util.ParallelCorpus.Alignment;

import java.util.Set;

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

  private Integer[] getIntegerArray(Set<Integer> alignment){
    if (alignment == null){
      return new Integer[0];
    }
    return alignment.toArray(new Integer[alignment.size()]);
  }
  
  @Test
  public void testAlignment() {
    Alignment al = ParallelCorpus.extractAlignment(alignment, source.length, target.length);
    AlignedSentence sentence = new AlignedSentence(source, target, al.f2e, al.e2f);
    
    // Source tests
    Integer[] f0Links = getIntegerArray(sentence.f2e(0));
    assertEquals(2, f0Links.length);

    assertEquals(1, (int) f0Links[0]);
    assertEquals(2, (int) f0Links[1]);
    
    Integer[] f1Links = getIntegerArray(sentence.f2e(1));
    assertEquals(1, f1Links.length);
    assertEquals(0, (int) f1Links[0]);
    
    Integer[] f2Links = getIntegerArray(sentence.f2e(2));
    assertEquals(5, f2Links.length);
    assertEquals(3, (int) f2Links[0]);
    assertEquals(4, (int) f2Links[1]);
    assertEquals(5, (int) f2Links[2]);
    assertEquals(6, (int) f2Links[3]);
    assertEquals(7, (int) f2Links[4]);
    
    Integer[] f3Links = getIntegerArray(sentence.f2e(3));
    assertEquals(0, f3Links.length);
    
    // Target tests
    Integer[] e0Links = getIntegerArray(sentence.e2f(0));
    assertEquals(1, e0Links.length);
    assertEquals(1, (int) e0Links[0]);
    
    Integer[] e1Links = getIntegerArray(sentence.e2f(1));
    assertEquals(1, e1Links.length);
    assertEquals(0, (int) e1Links[0]);
    
    Integer[] e2Links = getIntegerArray(sentence.e2f(2));
    assertEquals(1, e2Links.length);
    assertEquals(0, (int) e2Links[0]);
  }
}
