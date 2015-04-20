package edu.stanford.nlp.mt.util;

import java.io.Serializable;

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
 * TODO(spenceg) Serialize with kryo.
 * 
 * @author Spence Green
 *
 */
public class Vocabulary implements Serializable {

  private static final long serialVersionUID = 5124110481914822964L;
  
  // System-wide translation model index
  private static final int INITIAL_SYSTEM_CAPACITY = 1000000;
  private static transient Index<String> systemIndex = new ConcurrentHashIndex<String>(INITIAL_SYSTEM_CAPACITY);
  public static final int UNKNOWN_ID = ConcurrentHashIndex.UNKNOWN_ID;
  
  // Decoder-local translation model index
  private static final int INITIAL_CAPACITY = 10000;
  private final Index<String> index;

  /**
   * Constructor. Creates a decoder-local index.
   */
  public Vocabulary() {
    this(INITIAL_CAPACITY);
  }
  
  /**
   * Constructor.
   * 
   * @param initialCapacity
   */
  public Vocabulary(int initialCapacity) {
    index = new ConcurrentHashIndex<String>(initialCapacity);
  }

  /**
   * System index size.
   * 
   * @return
   */
  public static int systemSize() {
    return systemIndex.size();
  }

  /**
   * Get a string for this id from the system index.
   * 
   * @param i
   * @return
   */
  public static String systemGet(int i) {
    return systemIndex.get(i);
  }

  /**
   * Get the id for a string from the system index.
   * 
   * @param o
   * @return
   */
  public static int systemIndexOf(String o) {
    return systemIndex.indexOf(o);
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
  private static transient final ThreadLocal<Vocabulary> threadLocalCache =
      new ThreadLocal<Vocabulary>();

  /**
   * Set the thread-local vocabulary.
   * 
   * @param index
   */
  public static void setThreadLocalVocabulary(Vocabulary index) {
    threadLocalCache.set(index);
  }

  /**
   * Get the thread-local vocabulary.
   * 
   * @return
   */
  public static Vocabulary getThreadLocalVocabulary() {
    return threadLocalCache.get();
  }
}
