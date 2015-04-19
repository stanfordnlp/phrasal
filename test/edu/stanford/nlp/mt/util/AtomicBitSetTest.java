package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit test.
 * 
 * @author Spence Green
 *
 */
public class AtomicBitSetTest {

  @Test
  public void testGetSetInt() {
    AtomicBitSet bitSet = new AtomicBitSet(33);
    bitSet.set(0);
    assertTrue(bitSet.get(0));
    bitSet.set(32);
    assertTrue(bitSet.get(32));
    assertFalse(bitSet.get(33));
  }

  @Test
  public void testSetIntInt() {
    AtomicBitSet bitSet = new AtomicBitSet(32);
    bitSet.set(0,2);
    assertTrue(bitSet.get(0));
    assertTrue(bitSet.get(1));
    assertTrue(bitSet.get(2));
    assertFalse(bitSet.get(3));
  }

  @Test
  public void testNextSetBit() {
    AtomicBitSet bitSet = new AtomicBitSet(33);
    bitSet.set(1);
    assertEquals(1, bitSet.nextSetBit(0));
    bitSet.set(32);
    assertEquals(32, bitSet.nextSetBit(2));
  }

  @Test
  public void testAtomicBitSetAtomicBitSet() {
    AtomicBitSet bitSet = new AtomicBitSet(33);
    bitSet.set(0);
    bitSet.set(32);
    AtomicBitSet bitSet2 = new AtomicBitSet(bitSet);
    assertTrue(bitSet2.get(0));
    assertTrue(bitSet2.get(32));
    assertFalse(bitSet2.get(33));    
  }
}
