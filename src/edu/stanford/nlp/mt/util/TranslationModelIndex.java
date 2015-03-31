package edu.stanford.nlp.mt.util;

import edu.stanford.nlp.util.HashIndex;
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

  // System-wide translation model index
  private static final int INITIAL_SYSTEM_CAPACITY = 1000000;
  private static final Index<String> systemIndex = new ConcurrentHashIndex<String>(INITIAL_SYSTEM_CAPACITY);
  public static final int UNKNOWN_ID = ConcurrentHashIndex.UNKNOWN_ID;
  
  // Thread-local translation model index
  private static final int INITIAL_CAPACITY = 10000;
  private final Index<String> index;

  /**
   * Constructor.
   */
  public TranslationModelIndex() {
    this(INITIAL_CAPACITY, false);
  }
  
  /**
   * Constructor.
   * 
   * @param initialCapacity
   * @param isSystemIndex -- Create a system index (e.g., during TM extraction) instead
   * of a decoder-local index.
   */
  public TranslationModelIndex(int initialCapacity, boolean isSystemIndex) {
    index = isSystemIndex ? new HashIndex<String>(initialCapacity) : 
      new DecoderLocalIndex<String>(initialCapacity);
  }

  /**
   * System index size.
   * 
   * @return
   */
  public static int systemSize() {
    TranslationModelIndex localIndex = threadLocalCache.get();
    return localIndex == null ? systemIndex.size() : 
      systemIndex.size() + localIndex.size();
  }

  /**
   * Get a string for this id from the system index.
   * 
   * @param i
   * @return
   */
  public static String systemGet(int i) {
    TranslationModelIndex localIndex = threadLocalCache.get();
    return (localIndex != null && i <= DecoderLocalIndex.MIN_INDEX) ? 
      localIndex.get(i) : systemIndex.get(i);
  }

  /**
   * Get the id for a string from the system index.
   * 
   * @param o
   * @return
   */
  public static int systemIndexOf(String o) {
    TranslationModelIndex localIndex = threadLocalCache.get();
    if (localIndex == null) {
      return systemIndex.indexOf(o);
    } else {
      int index = systemIndex.indexOf(o);
      return index == UNKNOWN_ID ? localIndex.indexOf(o) : index;
    }
  }

  /**
   * Add a string to the system index. Additions to decoder-local
   * indices are not allowed.
   * 
   * @param o
   * @return
   */
  public static int systemAdd(String o) {
    return systemIndex.addToIndex(o);
  }
  
  /**
   * Clear the system index.
   * 
   */
  public static void systemClear() {
    systemIndex.clear();
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

  // Thread local copies of translation model indices
  private static transient final ThreadLocal<TranslationModelIndex> threadLocalCache =
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
