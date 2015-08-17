package edu.stanford.nlp.mt.tm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A lexical co-occurrence table. This object is threadsafe.
 * 
 * @author Spence Green
 *
 */
public class LexCoocTable {

  public static final int NULL_ID = Integer.MIN_VALUE + 1;
  private static final int MARGINALIZE = Integer.MIN_VALUE;
  
  private final ConcurrentHashMap<Long,AtomicInteger> counts;
  
  /**
   * Constructor.
   * 
   * @param initialCapacity
   */
  public LexCoocTable(int initialCapacity) {
    counts = new ConcurrentHashMap<>(initialCapacity);
  }
  
  /**
   * Add a word-word cooccurrence.
   * 
   * @param srcId
   * @param tgtId
   */
  public void addCooc(int srcId, int tgtId) {
    increment(pack(srcId, tgtId));
    increment(pack(MARGINALIZE, tgtId));
    increment(pack(srcId, MARGINALIZE));
  }
  
  private void increment(long key) {
    AtomicInteger counter = counts.get(key);
    if (counter == null) {
      counts.putIfAbsent(key, new AtomicInteger());
      counter = counts.get(key);
    }
    counter.incrementAndGet();
  }

  /**
   * Source marginal count.
   * 
   * @param srcId
   * @return
   */
  public int getSrcMarginal(int srcId) { return getJointCount(srcId, MARGINALIZE); }
  
  /**
   * Target marginal count.
   * 
   * @param tgtId
   * @return
   */
  public int getTgtMarginal(int tgtId) { return getJointCount(MARGINALIZE, tgtId); }
  
  /**
   * Joint count.
   * 
   * @param srcId
   * @param tgtId
   * @return
   */
  public int getJointCount(int srcId, int tgtId) { 
    AtomicInteger counter = counts.get(pack(srcId, tgtId));
    return counter == null ? 0 : counter.get();
  }
  
  /**
   * Number of entries in the table.
   * 
   * @return
   */
  public int size() { return counts.size(); }
  
  /**
   * Merge two interger ids into an unsigned long value. This is two unwrapped calls
   * to Integer.toUnsignedLong().
   * 
   * @param srcId
   * @param tgtId
   * @return
   */
  public long pack(int srcId, int tgtId) {
    return ((((long) srcId) & 0xffffffffL) << 32) | ((long) tgtId) & 0xffffffffL;
  }
}