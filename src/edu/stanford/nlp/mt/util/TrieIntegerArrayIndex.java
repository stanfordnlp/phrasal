package edu.stanford.nlp.mt.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of IntegerArrayIndex as a trie. This trie implementation is
 * optimized for space, by instantiating only one object: an open-address hash
 * table. Keys stored into the trie are arrays of int primitive types.
 * 
 * @author Michel Galley
 */
public class TrieIntegerArrayIndex implements IntegerArrayIndex,
    IntegerArrayRawIndex {

  public static final int IDX_ROOT = 0;
  public static final int IDX_NOSUCCESSOR = Integer.MIN_VALUE;

  private boolean locked = false;

  private Function<Integer, Integer> transitionNormalizer;

  public final ConcurrentHashMap<Long,Integer> map;
  // maps transitions to next state. Each transition is a long, whose 32 first
  // bits
  // identify an input symbol, and 32 last bits identify the current state.

  private int lastStateIdx = IDX_ROOT;

  public TrieIntegerArrayIndex() {
    this(-1);
  }

  public TrieIntegerArrayIndex(int sz) {
    if (sz > 0)
      map = new ConcurrentHashMap<>(sz);
    else
      map = new ConcurrentHashMap<>();
    // System.err.println("TrieIntegerArrayIndex: constructor.");
    this.transitionNormalizer = x -> x;
  }

  @Override
  public int[] get(int idx) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int indexOf(int[] input) {
    return indexOf(input, false);
  }

  @Override
  public int getIndex(int[] array) {
    return indexOf(array, false);
  }

  @Override
  public int insertIntoIndex(int[] array) {
    return indexOf(array, true);
  }

  @Override
  public int indexOf(int[] input, boolean add) {
    if (locked)
      return indexOf_unsync(input, add);
    synchronized (this) {
      return indexOf_unsync(input, add);
    }
  }

  @Override
  public int size() {
    return lastStateIdx + 1;
  }

  @Override
  public void lock() {
    this.locked = true;
  }

  public long getTransition(int curState, int input) {
    if (locked)
      return getTransition_unsync(curState, input);
    synchronized (this) {
      return getTransition_unsync(curState, input);
    }
  }

  public int getSuccessor(int curState, int input) {
    if (locked)
      return getSuccessor_unsync(curState, input);
    synchronized (this) {
      return getSuccessor_unsync(curState, input);
    }
  }

  public void printInfo() {
    System.err.printf("Number of states: %d\n", size());
    System.err.printf("Map size:%d\n", map.size());
  }

  private int indexOf_unsync(int[] input, boolean add) {
    // System.err.println("Adding to index: "+
    // Arrays.toString(IStrings.toStringArray(input)));
    int curState = IDX_ROOT;
    for (int anInput : input) {
      long transition = getTransition(curState,
          transitionNormalizer.apply(anInput));
      assert (map != null);
      int nextState = map.getOrDefault(transition, IDX_NOSUCCESSOR);
      if (nextState == IDX_NOSUCCESSOR) {
        if (!add)
          return -1;
        if (lastStateIdx == Integer.MAX_VALUE)
          throw new RuntimeException("Running out of state indices!");
        nextState = ++lastStateIdx;
        map.put(transition, nextState);
      }
      curState = nextState;
    }
    return curState;
  }

  private static int supplementalHash(int h) {
    // use the same supplemental hash function used by HashMap
    return ((h << 7) - h + (h >>> 9) + (h >>> 17));
  }

  private static long getTransition_unsync(int curState, int input) {
    // Perform some bit manipulations because Long's hashCode is not
    // particularly clever.
    int input2 = supplementalHash(input);
    int curState2 = supplementalHash(curState);
    return (((long) input2) << 32) | (((long) curState2) & 0xffffffffL);
  }

  private int getSuccessor_unsync(int curState, int input) {
    long t = getTransition_unsync(curState, input);
    return map.get(t);
  }

  private static void test(IntegerArrayIndex index) {
    System.out.println("idx(123): "
        + index.indexOf(new int[] { 1, 2, 3 }, true));
    System.out.println("idx(456): "
        + index.indexOf(new int[] { 4, 5, 6 }, true));
    System.out.println("idx(123): "
        + index.indexOf(new int[] { 1, 2, 3 }, true));
    System.out.println("idx(127): "
        + index.indexOf(new int[] { 1, 2, 7 }, true));
    System.out.println("idx(12): " + index.indexOf(new int[] { 1, 2 }, true));
  }

  @Override
  public String toString() {
    List<String> vals = new LinkedList<String>();
    for (Map.Entry<Long, Integer> e : map.entrySet()) {
      int v = e.getValue();
      vals.add(TranslationModelIndex.systemGet(v));
    }
    Collections.sort(vals);
    return vals.toString();
  }
  
  /**
   * 
   * @param args
   */
  public static void main(String[] args) {
    TrieIntegerArrayIndex idx = new TrieIntegerArrayIndex();
    test(idx);
    idx.printInfo();
  }
}
