package edu.stanford.nlp.mt.util;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;

/**
 * String->Int vocabulary mapping for both the whole system
 * and for individual decoding threads.
 * 
 * The system-wide mappings are positive integers, and the thread-local
 * mappings are negative integers. The polarity of the mappings are transparent
 * to the user via the IString.toString() method.
 * 
 * TODO(spenceg) Add serialization and deserialization format.
 * 
 * @author Spence Green
 *
 */
public class TranslationModelIndex {

  private static final int INITIAL_SYSTEM_CAPACITY = 1000000;
  private static final Index<String> systemIndex = new ConcurrentHashIndex<String>(INITIAL_SYSTEM_CAPACITY);

  private static final int INITIAL_CAPACITY = 10000;
  private final Index<String> index;

  /**
   * Constructor.
   */
  public TranslationModelIndex() {
    this(INITIAL_CAPACITY);
  }

  /**
   * Constructor.
   * 
   * @param initialCapacity
   */
  public TranslationModelIndex(int initialCapacity) {
    index = new NegativeHashIndex<String>(initialCapacity);
  }

  public static int systemSize() {
    return systemIndex.size();
  }

  public static String systemGet(int i) {
    return systemIndex.get(i);
  }

  public static int systemIndexOf(String o) {
    return systemIndex.indexOf(o);
  }

  public static int systemAdd(String o) {
    return systemIndex.addToIndex(o);
  }

  /**
   * Size of this index.
   * 
   * @return
   */
  public int size() {
    return index.size();
  }

  /**
   * Get the item with index i.
   * 
   * @param i
   * @return
   */
  public String get(int i) {
    return index.get(i);
  }

  /**
   * Get the index of item o.
   * 
   * @param o
   * @return
   */
  public int indexOf(String o) {
    return index.indexOf(o);
  }

  /**
   * Add an item to the index.
   * 
   * @param o
   * @return
   */
  public int add(String o) {
    return index.addToIndex(o);
  }

  @Override
  public String toString() {
    return index.toString();
  }

  // Thread local variable containing each thread's ID
  private static final ThreadLocal<TranslationModelIndex> threadLocalCache =
      new ThreadLocal<TranslationModelIndex>();

  /**
   * Set the thread-local index.
   * 
   * @param index
   */
  public static void setThreadLocalIndex(TranslationModelIndex index) {
    threadLocalCache.set(index);
  }

  /**
   * Get the thread-local index.
   * 
   * @return
   */
  public static TranslationModelIndex getThreadLocalIndex() {
    return threadLocalCache.get();
  }
}
