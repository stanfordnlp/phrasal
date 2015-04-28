package edu.stanford.nlp.mt.util;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * A threadsafe bit vector implementation. The bit vector must have a positive
 * fixed length.
 * 
 * Inspired by:
 * 
 *   http://stackoverflow.com/questions/12424633/atomicbitset-implementation-for-java
 *   
 * @author Spence Green
 *
 */
public class AtomicBitSet {

  /* Used to shift left or right for a partial word mask */
  private static final int WORD_MASK = 0xffffffff;

  private final AtomicIntegerArray array;
  private final int length;
  
  /**
   * Constructor.
   * 
   * @param length
   */
  public AtomicBitSet(int length) {
    if (length <= 0) throw new IllegalArgumentException("Length must be greater than 0");
    this.length = length;
    int intLength = (length + 31) / 32;
    array = new AtomicIntegerArray(intLength);
  }

  /**
   * Copy constructor.
   * 
   * @param bitSet
   */
  public AtomicBitSet(AtomicBitSet bitSet) {
    this.length = bitSet.length;
    this.array = new AtomicIntegerArray(bitSet.array.length());
    for (int i = 0, sz = bitSet.array.length(); i < sz; ++i) {
      this.array.set(i, bitSet.array.get(i));
    }
  }

  /**
   * Set the bit.
   * 
   * @param n
   */
  public void set(int n) {
    if (n < 0 || n >= length) throw new IndexOutOfBoundsException("n < 0 or n > max length " + n);
    int bit = 1 << n;
    int idx = n >>> 5;
    while (true) {
      int num = array.get(idx);
      int num2 = num | bit;
      if (num == num2 || array.compareAndSet(idx, num, num2))
        return;
    }
  }

  /**
   * Set the bits in the range [i,j].
   * 
   * @param i inclusive
   * @param j inclusive
   */
  public void set(int i, int j) {
    if (i < 0 || j < 0 || j >= length || j-i < 0)
      throw new IndexOutOfBoundsException(String.format("%d,%d // %d ",i,j, length));
    for (int start = i; start <= j; ++start) set(start);
  }

  /**
   * True if the bit is set, and false otherwise.
   * 
   * @param n
   * @return
   */
  public boolean get(int n) {
    if (n < 0 || n >= length)
      throw new IndexOutOfBoundsException("n < 0 or n > max length" + n);
    int bit = 1 << n;
    int idx = n >>> 5;
    if (idx > array.length()) {
      return false;
    } else {
      int num = array.get(idx);
      return (num & bit) != 0;
    }
  }

  /**
   * Returns the index of the first bit that is set to {@code true}
   * that occurs on or after the specified starting index. If no such
   * bit exists then {@code -1} is returned.
   * 
   * @param  fromIndex the index to start checking from (inclusive)
   * @return the index of the next set bit, or {@code -1} if there
   *         is no such bit
   * @throws IndexOutOfBoundsException if the specified index is negative
   * @return
   */
  public int nextSetBit(int fromIndex) {
    if (fromIndex < 0 || fromIndex >= length)
      throw new IndexOutOfBoundsException("fromIndex < 0 or fromIndex > max length " + fromIndex);

    int idx = fromIndex >>> 5;
    if (idx >= array.length())
      return -1;

    int word = array.get(idx) & (WORD_MASK << fromIndex);
    while (true) {
      if (word != 0)
        return (idx*32) + Integer.numberOfTrailingZeros(word);
      if (++idx == array.length())
        return -1;
      word = array.get(idx);
    }
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; ++i) sb.append(get(i) ? "1" : "0");
    return sb.toString();
  }
}
