package edu.stanford.nlp.mt.base;

import it.unimi.dsi.fastutil.longs.*;
import edu.stanford.nlp.util.Function;

/**
 * Implementation of IntegerArrayIndex as a trie. This trie implementation 
 * is optimized for space, by instanciating only one object: an open-address hash table. 
 * Keys stored into the trie are arrays of int primitive types.
 *
 * @author Michel Galley
 */
public class TrieIntegerArrayIndex implements IntegerArrayIndex, IntegerArrayRawIndex {

  private static final int GROWTH_FACTOR = 4; // slow growth, we want to save space

  public static final int IDX_ROOT = 0;
  public static final int IDX_NOSUCCESSOR = Integer.MIN_VALUE;

  private Function<Integer,Integer> transitionNormalizer;

  public final Long2IntOpenHashMap map;
  // maps transitions to next state. Each transition is a long, whose 32 first bits
  // identify an input symbol, and 32 last bits identify the current state.

  private int lastStateIdx = IDX_ROOT;

  public TrieIntegerArrayIndex() {
    this(-1);
  }

  public TrieIntegerArrayIndex(int sz) {
    if(sz > 0)
      map = new Long2IntOpenHashMap(sz);
    else
      map = new Long2IntOpenHashMap();
    map.growthFactor(GROWTH_FACTOR);
    map.defaultReturnValue(IDX_NOSUCCESSOR);
    //System.err.println("TrieIntegerArrayIndex: constructor.");
    this.transitionNormalizer = new Function<Integer,Integer>() {
      public Integer apply(Integer x) { return x; }
    };
  }

  public TrieIntegerArrayIndex(int sz, Function<Integer,Integer> transitionNormalizer) {
    this(sz);
    this.transitionNormalizer = transitionNormalizer;
  }

  public int[] get(int idx) {
		throw new UnsupportedOperationException();
	}

  int supplementalHash(int h) {
    // use the same supplemental hash function used by HashMap
    return ((h << 7) - h + (h >>> 9) + (h >>> 17));
  }

  public synchronized long getTransition(int curState, int input) {
    // Perform some bit manipulations because Long's hashCode is not particularly clever.
    int input2 = supplementalHash(input);
    int curState2 = supplementalHash(curState);
    return (((long)input2) << 32) | (((long)curState2) & 0xffffffffL);
  }

  public synchronized int getSuccessor(int curState, int input) {
    long t = getTransition(curState, input);
    return map.get(t);
  }

  public int size() { return lastStateIdx+1; }

  public void printInfo() {
    System.err.printf("Number of states: %d\n", size());
    System.err.printf("Map size:%d\n", map.size());
  }

  public int indexOf(int[] input) {
    return indexOf(input, false);
  }

  public synchronized int indexOf(int[] input, boolean add) {
    //System.err.println("adding: "+ Arrays.toString(IStrings.toStringArray(input)));
    int curState = IDX_ROOT;
    for (int anInput : input) {
      long transition = getTransition(curState, transitionNormalizer.apply(anInput));
      assert (map != null);
      int nextState = map.get(transition);
      if (nextState == IDX_NOSUCCESSOR) {
        if (!add) return -1;
        if (lastStateIdx == Integer.MAX_VALUE)
          throw new RuntimeException("Running out of state indices!");
        nextState = ++lastStateIdx;
        map.put(transition, nextState);
      }
      curState = nextState;
    }
    return curState;
  }

  public void rehash() {
    map.rehash();
  }

  public int getIndex(int[] array) {
    return indexOf(array,false);
  }

  public int insertIntoIndex(int[] array) {
    return indexOf(array,true);
  }

  public static void main(String[] args) {
    TrieIntegerArrayIndex idx = new TrieIntegerArrayIndex();
    test(idx);
    idx.printInfo();
    //test(new TrieIntegerArrayIndex());
    test(new DynamicIntegerArrayIndex());
  }

  private static void test(IntegerArrayIndex index) { 
    System.out.println("idx(123): " + index.indexOf(new int[] {1, 2, 3}, true));
    System.out.println("idx(456): " + index.indexOf(new int[] {4, 5, 6}, true));
    System.out.println("idx(123): " + index.indexOf(new int[] {1, 2, 3}, true));
    System.out.println("idx(127): " + index.indexOf(new int[] {1, 2, 7}, true));
    System.out.println("idx(12): " + index.indexOf(new int[] {1, 2}, true));
  }
}
