/**
 * 
 */
package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import edu.stanford.nlp.lm.NPLM;

/**
 * @author Thang Luong
 *
 */
public class NNLMUtilTest {
  /**
   * Test method for {@link edu.stanford.nlp.mt.util.NNLMUtil#toByteArray(int[])}.
   */
  @Test
  public void testToByteArray() {
    // 127  1111111
    // 32639 111111101111111
    int[] ints = new int[]{(1<<7)-1, (1<<15)-1 - (1<<7)};
    //for(int value : ints) { System.err.println(value + "\t" + Integer.toBinaryString(value)); }
    
    byte[] bytes = NNLMUtil.toByteArray(ints);
    byte[] bytes1 = new byte[]{0, 0, 0, 127, 0, 0, 127, 127};
    
    assertEquals(bytes.length, bytes1.length);
    for (int i = 0; i < bytes.length; i++) {
      //System.err.println(Integer.toBinaryString(bytes[i] & 0xff) + "\t" + Integer.toBinaryString(bytes1[i] & 0xff));
      assertEquals(bytes[i], bytes1[i]);
    }
  }

  /**
   * Test method for {@link edu.stanford.nlp.mt.util.NNLMUtil#toByteArray1(int[])}.
   */
  @Test
  public void testToByteArray1() {
 // create a random array of ints
    int size = 100;
    int[] ints = new int[size];
    Random r = new Random(System.currentTimeMillis());
    for (int i = 0; i < ints.length; i++) {
      ints[i] = r.nextInt();
    }
    
    byte[] bytes = NNLMUtil.toByteArray(ints);
    byte[] bytes1 = NNLMUtil.toByteArray1(ints);
    
    assertEquals(bytes.length, bytes1.length);
    for (int i = 0; i < bytes.length; i++) {
      assertEquals(bytes[i], bytes1[i]);
    }
  }
}

